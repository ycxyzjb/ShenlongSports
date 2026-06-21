package com.shenlong.sports.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/**
 * 缓存TTS播放器 - 预置音频 + 系统TTS降级
 *
 * 策略：
 * 1. APP内置205个预录制WAV音频（5个固定短语 + 1-200号），首次启动解压到内部存储
 * 2. 播放时直接读缓存文件，零延迟
 * 3. 超出200号的号码用系统TTS实时合成，并缓存供下次使用
 * 4. 无侵权风险（不使用任何第三方非公开接口）
 * 5. 后续可用IndexTTS等高质量TTS替换缓存文件
 */
class CachedTtsPlayer(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsInitStarted = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    // 缓存目录
    private val cacheDir = File(context.filesDir, "tts_cache").also { it.mkdirs() }

    // 预置音频是否已解压
    private val prefs = context.getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)
    private var assetsExtracted: Boolean
        get() = prefs.getBoolean("assets_extracted_v2", false)
        set(value) { prefs.edit().putBoolean("assets_extracted_v2", value).apply() }

    // 待合成的队列（TTS未就绪时暂存）
    private val pendingSynth = mutableListOf<Pair<String, String>>()

    companion object {
        private const val TAG = "CachedTtsPlayer"

        val FIXED_PHRASES = mapOf(
            "last_lap" to "还剩最后一圈",
            "finished" to "完成比赛",
            "all_lap" to "所有运动员记一圈",
            "all_finished" to "所有运动员完成比赛",
            "lap" to "记一圈",
        )

        const val PRE_CACHE_MAX = 200
    }

    fun init() {
        // 首次启动：从assets解压预置音频
        if (!assetsExtracted) {
            extractAssetsAsync()
        }

        // 初始化系统TTS（作为超出200号的降级方案）
        if (ttsInitStarted) return
        ttsInitStarted = true
        try {
            tts = TextToSpeech(context.applicationContext, this)
            handler.postDelayed({
                if (!ttsReady) Log.w(TAG, "TTS初始化超时")
            }, 10000)
        } catch (e: Exception) {
            Log.e(TAG, "TTS初始化异常: ${e.message}")
        }
    }

    /**
     * 从assets解压预置音频到内部存储
     */
    private fun extractAssetsAsync() {
        Thread {
            try {
                val assetFiles = context.assets.list("tts_cache") ?: emptyArray()
                var count = 0
                for (name in assetFiles) {
                    val targetFile = File(cacheDir, name)
                    // 始终覆盖，确保assets更新后能生效
                    context.assets.open("tts_cache/$name").use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    count++
                }
                assetsExtracted = true
                Log.i(TAG, "从assets解压了 $count 个音频文件")
            } catch (e: Exception) {
                Log.e(TAG, "解压assets失败: ${e.message}")
            }
        }.start()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locales = listOf(Locale.CHINA, Locale.SIMPLIFIED_CHINESE, Locale.CHINESE, Locale("zh", "CN"))
            for (locale in locales) {
                try {
                    val result = tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) break
                } catch (_: Exception) { }
            }
            tts?.setSpeechRate(1.0f)
            tts?.setPitch(1.0f)
            ttsReady = true
            Log.i(TAG, "系统TTS就绪（降级方案）")

            // 处理待合成队列
            for ((text, filename) in pendingSynth) {
                synthesizeToFile(text, filename)
            }
            pendingSynth.clear()
        } else {
            Log.w(TAG, "系统TTS不可用(status=$status)，仅使用预置音频")
        }
    }

    /**
     * 同步合成并等待完成（用于超出200号的号码）
     */
    private fun synthesizeAndWait(text: String, file: File) {
        if (!ttsReady) return
        try {
            val latch = java.util.concurrent.CountDownLatch(1)
            val utteranceId = "synth_${System.currentTimeMillis()}"
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { latch.countDown() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { latch.countDown() }
            })
            val params = hashMapOf<String, String>(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to utteranceId)
            @Suppress("DEPRECATION")
            val result = tts?.synthesizeToFile(text, params, file.absolutePath)
            if (result == TextToSpeech.SUCCESS) {
                latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "合成失败: $text - ${e.message}")
        }
    }

    /**
     * 异步合成到文件
     */
    private fun synthesizeToFile(text: String, filename: String) {
        if (!ttsReady) {
            pendingSynth.add(text to filename)
            return
        }
        val file = File(cacheDir, filename)
        if (file.exists() && file.length() > 0) return
        try {
            val utteranceId = "synth_${System.currentTimeMillis()}"
            val params = hashMapOf<String, String>(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to utteranceId)
            @Suppress("DEPRECATION")
            tts?.synthesizeToFile(text, params, file.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "合成失败: $text")
        }
    }

    /**
     * 播放语音：优先缓存文件，降级实时TTS
     * 支持拆分播放：如"1号还剩最后一圈"拆为"1号"+"最后一圈"依次播放
     */
    fun speak(text: String) {
        // 尝试拆分为号码+短语（优先拆分，避免只播号码不播短语）
        val numMatch = Regex("^(\\d+)号(.+)$").find(text)
        if (numMatch != null) {
            val numStr = numMatch.groupValues[1].trimStart('0').ifEmpty { "0" }
            val suffix = numMatch.groupValues[2]
            val numFile = File(cacheDir, "num_${numStr}.wav")
            val phraseFile = findPhraseFile(suffix)

            if (numFile.exists() && numFile.length() > 0 && phraseFile != null && phraseFile.exists() && phraseFile.length() > 0) {
                // 先播号码，再播短语
                playWavFiles(listOf(numFile, phraseFile))
                return
            }
        }

        // 尝试完整匹配（固定短语等）
        val cacheFile = findCacheFile(text)
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0) {
            playWavFile(cacheFile)
            return
        }

        // 降级到系统TTS
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "speak_${System.currentTimeMillis()}")
        }
    }

    /**
     * 查找短语对应的缓存文件
     */
    private fun findPhraseFile(suffix: String): File? {
        val trimmed = suffix.trim()
        for ((key, phrase) in FIXED_PHRASES) {
            if (trimmed == phrase) return File(cacheDir, "${key}.wav")
        }
        // 常见短语映射
        val phraseMap = mapOf(
            "记一圈" to "lap",
            "还剩最后一圈" to "last_lap",
            "最后一圈" to "last_lap",
            "完成比赛" to "finished",
            "运动员记一圈" to "lap",
            "运动员还剩最后一圈" to "last_lap",
            "运动员完成比赛" to "finished",
        )
        val key = phraseMap[trimmed] ?: return null
        return File(cacheDir, "${key}.wav")
    }

    /**
     * 依次播放多个WAV文件
     */
    private fun playWavFiles(files: List<File>) {
        Thread {
            for (file in files) {
                try {
                    playWavFileSync(file)
                } catch (e: Exception) {
                    Log.e(TAG, "播放音频失败: ${file.name} - ${e.message}")
                }
            }
        }.start()
    }

    /**
     * 同步播放WAV文件（阻塞直到播放完成）
     */
    private fun playWavFileSync(file: File) {
        FileInputStream(file).use { fis ->
            val header = ByteArray(44)
            fis.read(header)

            val sampleRate = ((header[27].toInt() and 0xFF) shl 24) or
                    ((header[26].toInt() and 0xFF) shl 16) or
                    ((header[25].toInt() and 0xFF) shl 8) or
                    (header[24].toInt() and 0xFF)
            val channels = ((header[23].toInt() and 0xFF) shl 8) or (header[22].toInt() and 0xFF)
            val channelMask = if (channels >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
            val effectiveSampleRate = if (sampleRate > 0) sampleRate else 22050

            val bufSize = 8192
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(effectiveSampleRate)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setBufferSizeInBytes(bufSize.coerceAtLeast(AudioTrack.getMinBufferSize(
                    effectiveSampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT
                )))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()

            val buffer = ByteArray(bufSize)
            var read: Int
            while (fis.read(buffer).also { read = it } > 0) {
                track.write(buffer, 0, read)
            }

            // 等待播放完成
            Thread.sleep(300)
            track.stop()
            track.release()
        }
    }

    /**
     * 查找文本对应的缓存文件
     */
    private fun findCacheFile(text: String): File? {
        val filename = textToFilename(text) ?: return null
        return File(cacheDir, filename).takeIf { it.exists() && it.length() > 0 }
    }

    /**
     * 将文本转为缓存文件名
     */
    private fun textToFilename(text: String): String? {
        // 固定短语
        for ((key, phrase) in FIXED_PHRASES) {
            if (text == phrase) return "${key}.wav"
        }
        // 号码格式："X号" 或 "X号运动员..."（去除前导零：01→1, 001→1）
        val numMatch = Regex("^(\\d+)号").find(text)
        if (numMatch != null) {
            val numStr = numMatch.groupValues[1].trimStart('0').ifEmpty { "0" }
            return "num_${numStr}.wav"
        }
        // 其他文本用hash
        return "custom_${text.hashCode()}.wav"
    }

    /**
     * 播放WAV文件
     */
    private fun playWavFile(file: File) {
        Thread {
            try {
                FileInputStream(file).use { fis ->
                    // 读取WAV头获取实际参数
                    val header = ByteArray(44)
                    fis.read(header)

                    // 从WAV头解析采样率和声道数
                    val sampleRate = ((header[27].toInt() and 0xFF) shl 24) or
                            ((header[26].toInt() and 0xFF) shl 16) or
                            ((header[25].toInt() and 0xFF) shl 8) or
                            (header[24].toInt() and 0xFF)
                    val channels = ((header[23].toInt() and 0xFF) shl 8) or (header[22].toInt() and 0xFF)
                    val channelMask = if (channels >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO

                    val effectiveSampleRate = if (sampleRate > 0) sampleRate else 22050
                    val effectiveChannelMask = channelMask

                    val bufSize = 8192
                    val track = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(effectiveSampleRate)
                                .setChannelMask(effectiveChannelMask)
                                .build()
                        )
                        .setBufferSizeInBytes(bufSize.coerceAtLeast(AudioTrack.getMinBufferSize(
                            effectiveSampleRate, effectiveChannelMask, AudioFormat.ENCODING_PCM_16BIT
                        )))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()

                    track.play()

                    val buffer = ByteArray(bufSize)
                    var read: Int
                    while (fis.read(buffer).also { read = it } > 0) {
                        track.write(buffer, 0, read)
                    }

                    // 等待播放完成
                    Thread.sleep(200)
                    track.stop()
                    track.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "播放缓存音频失败: ${e.message}")
            }
        }.start()
    }

    /**
     * 清除所有缓存（用于调试或更换TTS引擎后重新生成）
     */
    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
        assetsExtracted = false
    }

    val cacheCount: Int
        get() = cacheDir.listFiles()?.size ?: 0

    val isReady: Boolean
        get() = cacheCount > 0 || ttsReady

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        handler.removeCallbacksAndMessages(null)
    }
}
