package com.shenlong.sports.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import com.shenlong.sports.viewmodel.VoiceEvent
import java.util.Locale

/**
 * 反馈管理器 - 提示音 + 震动 + 语音（本地TTS优先，网络TTS降级）
 *
 * 语音策略：
 * 1. 优先尝试本地TTS引擎（速度快，无需网络）
 * 2. 本地TTS不可用时，降级使用网络TTS（Google Translate TTS接口）
 * 3. 提示音+震动始终可用，语音是锦上添花
 */
class FeedbackHelper(private val context: Context) : TextToSpeech.OnInitListener {

    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("feedback_prefs", Context.MODE_PRIVATE)

    // 提示音播放器
    private val tonePlayer = TonePlayer(context)

    // 网络TTS播放器（本地TTS不可用时的降级方案）
    private val networkTts = NetworkTtsPlayer(context)

    // 三个开关（持久化）
    var toneEnabled: Boolean
        get() = prefs.getBoolean("tone_enabled", true)
        set(value) { prefs.edit().putBoolean("tone_enabled", value).apply() }

    var voiceEnabled: Boolean
        get() = prefs.getBoolean("voice_enabled", true)
        set(value) { prefs.edit().putBoolean("voice_enabled", value).apply() }

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean("vibration_enabled", true)
        set(value) { prefs.edit().putBoolean("vibration_enabled", value).apply() }

    // 本地TTS
    private var tts: TextToSpeech? = null
    private var localTtsReady = false
    private var activityRef: Activity? = null
    private var ttsInitStarted = false

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun setActivity(activity: Activity) {
        activityRef = activity
    }

    fun onActivityResumed() {
        if (!ttsInitStarted) {
            ttsInitStarted = true
            handler.postDelayed({ startTtsInit() }, 2000)
        }
    }

    private fun startTtsInit() {
        val activity = activityRef
        if (activity == null || activity.isFinishing || activity.isDestroyed) return

        try {
            Log.i(TAG, "尝试初始化本地TTS")
            tts = TextToSpeech(activity, this)
            // 10秒超时
            handler.postDelayed({
                if (!localTtsReady) {
                    Log.w(TAG, "本地TTS初始化超时，将使用网络TTS")
                }
            }, 10000)
        } catch (e: Exception) {
            Log.e(TAG, "本地TTS初始化异常: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locales = listOf(Locale.CHINA, Locale.SIMPLIFIED_CHINESE, Locale.CHINESE, Locale("zh", "CN"), Locale.getDefault())
            for (locale in locales) {
                try {
                    val result = tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) break
                } catch (_: Exception) { }
            }
            localTtsReady = true
            tts?.setSpeechRate(1.0f)
            tts?.setPitch(1.0f)
            Log.i(TAG, "本地TTS就绪")
        } else {
            Log.w(TAG, "本地TTS不可用(status=$status)，将使用网络TTS")
        }
    }

    // ===== 语音播放 =====

    /**
     * 语音播报：本地TTS优先，不可用时降级到网络TTS
     */
    private fun speakIfAvailable(text: String) {
        if (!voiceEnabled) return

        if (localTtsReady) {
            try {
                tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "shenlong_${System.currentTimeMillis()}")
            } catch (_: Exception) { }
        } else {
            // 本地TTS不可用，使用网络TTS
            networkTts.speak(text)
        }
    }

    // ===== 震动 =====

    private fun vibrateShort() {
        if (!vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            else { @Suppress("DEPRECATION") vibrator.vibrate(80) }
        } catch (_: Exception) { }
    }

    private fun vibrateLong() {
        if (!vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            else { @Suppress("DEPRECATION") vibrator.vibrate(300) }
        } catch (_: Exception) { }
    }

    private fun vibrateDouble() {
        if (!vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator.vibrate(VibrationEffect.createWaveform(
                    longArrayOf(0, 100, 80, 100),
                    intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE), -1
                ))
            else { @Suppress("DEPRECATION") vibrator.vibrate(longArrayOf(0, 100, 80, 100), -1) }
        } catch (_: Exception) { }
    }

    // ===== 统一处理事件 =====

    fun handleEvent(event: VoiceEvent) {
        when (event) {
            is VoiceEvent.AllLap -> { if (toneEnabled) tonePlayer.playAllLap(); vibrateShort(); speakIfAvailable("所有运动员记一圈") }
            is VoiceEvent.OneLap -> { if (toneEnabled) tonePlayer.playLap(); vibrateShort(); speakIfAvailable("${event.number}号记一圈") }
            is VoiceEvent.LastLap -> { if (toneEnabled) tonePlayer.playLastLap(); vibrateDouble(); speakIfAvailable("${event.number}号还剩最后一圈") }
            is VoiceEvent.Finished -> { if (toneEnabled) tonePlayer.playFinish(); vibrateLong(); speakIfAvailable("${event.number}号完成比赛") }
        }
    }

    // ===== 外部接口 =====

    /**
     * 语音是否可用：本地TTS就绪 或 网络TTS可用
     */
    val isTtsReady: Boolean
        get() = localTtsReady || networkTts.isAvailable

    /**
     * 语音类型描述
     */
    val voiceType: String
        get() = when {
            localTtsReady -> "本地TTS"
            networkTts.isAvailable -> "网络TTS"
            else -> "未就绪"
        }

    fun release() {
        tonePlayer.release()
        networkTts.release()
        tts?.stop(); tts?.shutdown(); tts = null; localTtsReady = false
        handler.removeCallbacksAndMessages(null)
    }

    companion object { private const val TAG = "FeedbackHelper" }
}
