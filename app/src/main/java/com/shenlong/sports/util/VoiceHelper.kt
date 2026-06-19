package com.shenlong.sports.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.shenlong.sports.viewmodel.VoiceEvent
import java.util.Locale

/**
 * 语音播报管理器 - 使用 Android TTS
 */
class VoiceHelper(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pendingMessages = mutableListOf<String>()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.CHINA)
            ready = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onError(utteranceId: String?) {
                    Log.w("VoiceHelper", "TTS error for utterance: $utteranceId")
                }
                override fun onDone(utteranceId: String?) {}
            })

            // Speak any pending messages
            if (ready) {
                for (msg in pendingMessages) {
                    speakInternal(msg)
                }
                pendingMessages.clear()
            }
        } else {
            Log.e("VoiceHelper", "TTS init failed with status: $status")
        }
    }

    private fun speakInternal(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "lap_${System.currentTimeMillis()}")
    }

    fun speak(text: String) {
        if (ready) {
            speakInternal(text)
        } else {
            // TTS not ready yet, queue the message
            pendingMessages.add(text)
            // Try re-initializing if TTS is null
            if (tts == null) {
                tts = TextToSpeech(context.applicationContext, this)
            }
        }
    }

    fun handleEvent(event: VoiceEvent) {
        val text = when (event) {
            is VoiceEvent.AllLap -> "所有运动员记一圈"
            is VoiceEvent.OneLap -> "${event.number}号运动员记一圈"
            is VoiceEvent.LastLap -> "${event.number}号运动员还剩一圈"
            is VoiceEvent.Finished -> "${event.number}号运动员完成比赛"
            is VoiceEvent.AllFinished -> "所有运动员完成比赛"
        }
        speak(text)
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
