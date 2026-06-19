package com.shenlong.sports.util

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * 网络TTS播放器 - 通过在线TTS接口将文字转为语音播放
 *
 * 当本地TTS引擎不可用（如HyperOS限制）时，使用网络TTS作为降级方案。
 * 原理：将文字发送到在线TTS服务，获取音频数据，用MediaPlayer播放。
 *
 * 支持的接口（按优先级尝试）：
 * 1. 有道词典TTS（国内可访问，免费，无需API Key）
 * 2. 百度翻译TTS（国内可访问，免费，无需API Key）
 */
class NetworkTtsPlayer(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val queue = mutableListOf<String>()
    private var isPlaying = false
    private var mediaPlayer: MediaPlayer? = null

    // 网络TTS是否可用（至少成功播放过一次）
    var isAvailable: Boolean = false
        private set

    /**
     * 将文字加入播放队列
     */
    fun speak(text: String) {
        synchronized(queue) {
            queue.add(text)
        }
        processQueue()
    }

    private fun processQueue() {
        if (isPlaying) return
        val text: String
        synchronized(queue) {
            text = queue.removeFirstOrNull() ?: return
        }
        isPlaying = true
        executor.execute {
            fetchAndPlay(text)
        }
    }

    private fun fetchAndPlay(text: String) {
        // 按优先级尝试多个国内可访问的TTS接口
        val urls = buildTtsUrls(text)

        for (urlStr in urls) {
            try {
                Log.i(TAG, "尝试TTS接口: ${urlStr.take(60)}...")
                val connection = URL(urlStr).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                connection.setRequestProperty("Referer", extractReferer(urlStr))
                connection.connectTimeout = 6000
                connection.readTimeout = 6000

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    Log.w(TAG, "TTS接口返回 HTTP $responseCode，尝试下一个")
                    continue
                }

                // 检查Content-Type是否为音频
                val contentType = connection.contentType ?: ""
                if (contentType.contains("audio") || contentType.contains("octet-stream") || connection.contentLength > 0) {
                    val tempFile = File(context.cacheDir, "net_tts_${System.currentTimeMillis()}.mp3")
                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (tempFile.length() > 0) {
                        Log.i(TAG, "网络TTS下载成功: ${tempFile.length()} bytes, text=$text")
                        isAvailable = true
                        handler.post { playAudioFile(tempFile) }
                        return
                    } else {
                        Log.w(TAG, "网络TTS返回空音频，尝试下一个")
                        tempFile.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "TTS接口异常: ${e.message}，尝试下一个")
            }
        }

        Log.e(TAG, "所有网络TTS接口均不可用")
        onPlayFailed()
    }

    /**
     * 构建多个TTS接口URL（国内可访问）
     */
    private fun buildTtsUrls(text: String): List<String> {
        val encoded = URLEncoder.encode(text, "UTF-8")
        return listOf(
            // 有道词典TTS - 国内直连，中文支持好
            "https://dict.youdao.com/dictvoice?audio=$encoded&le=zh&type=2",
            // 百度翻译TTS - 国内直连
            "https://fanyi.baidu.com/gettts?lan=zh&text=$encoded&spd=5&source=web"
        )
    }

    private fun extractReferer(urlStr: String): String {
        return try {
            val url = URL(urlStr)
            "${url.protocol}://${url.host}/"
        } catch (_: Exception) {
            ""
        }
    }

    private fun playAudioFile(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    this@NetworkTtsPlayer.isPlaying = false
                    file.delete()
                    this@NetworkTtsPlayer.processQueue()
                }
                setOnErrorListener { _, _, _ ->
                    Log.w(TAG, "MediaPlayer播放失败")
                    this@NetworkTtsPlayer.isPlaying = false
                    file.delete()
                    this@NetworkTtsPlayer.processQueue()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放音频异常: ${e.message}")
            isPlaying = false
            file.delete()
            processQueue()
        }
    }

    private fun onPlayFailed() {
        handler.post {
            isPlaying = false
            processQueue()
        }
    }

    /**
     * 停止当前播放并清空队列
     */
    fun stop() {
        synchronized(queue) { queue.clear() }
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) { }
        mediaPlayer = null
        isPlaying = false
    }

    fun release() {
        stop()
        executor.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        // 清理临时文件
        context.cacheDir.listFiles()?.filter { it.name.startsWith("net_tts_") }?.forEach { it.delete() }
    }

    companion object {
        private const val TAG = "NetworkTtsPlayer"
    }
}
