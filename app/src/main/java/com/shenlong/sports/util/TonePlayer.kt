package com.shenlong.sports.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * 提示音播放器 - 程序生成不同频率的提示音，不依赖任何TTS引擎
 *
 * 提示音设计（体育场景专用，高辨识度）：
 * - 记一圈：短促高音 "嘀" (880Hz, 150ms)
 * - 所有人记一圈：双音 "嘀-嘀" (880Hz, 100ms x2) - 节奏均匀
 * - 最后一圈：三连急促高音 "嘀嘀嘀" (1046Hz, 100ms x3) - 更高更急促，警示感
 * - 完成比赛：胜利号角 "哆-咪-嗦—" (523→659→784Hz) - 上行音阶，胜利感
 */
class TonePlayer(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val sampleRate = 44100

    /** 记一圈：短促高音 */
    fun playLap() {
        playToneSequence(listOf(Tone(880.0, 150, 0)))
    }

    /** 所有人记一圈：双音 */
    fun playAllLap() {
        playToneSequence(listOf(
            Tone(880.0, 120, 80),
            Tone(880.0, 120, 0)
        ))
    }

    /** 最后一圈：三连急促高音（警示感） */
    fun playLastLap() {
        playToneSequence(listOf(
            Tone(1046.0, 100, 50),
            Tone(1046.0, 100, 50),
            Tone(1046.0, 100, 0)
        ))
    }

    /** 完成比赛：胜利号角（上行音阶） */
    fun playFinish() {
        playToneSequence(listOf(
            Tone(523.0, 200, 50),   // 哆
            Tone(659.0, 200, 50),   // 咪
            Tone(784.0, 500, 0)     // 嗦—（长音）
        ))
    }

    private data class Tone(val freq: Double, val durationMs: Int, val gapMs: Int)

    private fun playToneSequence(tones: List<Tone>) {
        Thread {
            try {
                val totalMs = tones.sumOf { it.durationMs + it.gapMs }
                val totalSamples = (sampleRate * totalMs / 1000.0).toInt()
                val buffer = ShortArray(totalSamples)
                var offset = 0

                for (tone in tones) {
                    val toneSamples = (sampleRate * tone.durationMs / 1000.0).toInt()
                    for (i in 0 until toneSamples) {
                        val t = i.toDouble() / sampleRate
                        val fadeIn = if (i < 200) i / 200.0 else 1.0
                        val fadeOut = if (i > toneSamples - 200) (toneSamples - i) / 200.0 else 1.0
                        val fade = fadeIn * fadeOut
                        val sample = (sin(2.0 * PI * tone.freq * t) * 0.5 * fade * Short.MAX_VALUE).toInt()
                        if (offset + i < buffer.size) {
                            buffer[offset + i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        }
                    }
                    offset += toneSamples
                    offset += (sampleRate * tone.gapMs / 1000.0).toInt()
                }

                val bufSize = buffer.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufSize.coerceAtLeast(AudioTrack.getMinBufferSize(
                        sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                    )))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()

                handler.postDelayed({
                    try { track.stop(); track.release() } catch (_: Exception) { }
                }, (totalMs + 500).toLong())

            } catch (e: Exception) {
                Log.e(TAG, "播放提示音失败: ${e.message}")
            }
        }.start()
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
    }

    companion object { private const val TAG = "TonePlayer" }
}
