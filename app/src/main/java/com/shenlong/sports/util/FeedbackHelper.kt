package com.shenlong.sports.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.shenlong.sports.viewmodel.VoiceEvent

/**
 * 反馈管理器 - 提示音 + 震动 + 语音（缓存TTS）
 *
 * 语音策略：
 * 1. 优先播放缓存音频（零延迟，首次使用系统TTS合成后永久缓存）
 * 2. 无缓存时实时TTS播报，同时后台缓存供下次使用
 * 3. 提示音+震动始终可用，语音是锦上添花
 * 4. 无侵权风险（不使用任何第三方非公开接口）
 */
class FeedbackHelper(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("feedback_prefs", Context.MODE_PRIVATE)

    // 提示音播放器
    private val tonePlayer = TonePlayer(context)

    // 缓存TTS播放器（首次用系统TTS合成，之后直接播放缓存文件）
    private val cachedTts = CachedTtsPlayer(context)

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
        // 启动TTS初始化和预缓存
        cachedTts.init()
    }

    fun onActivityResumed() {
        cachedTts.init()
    }

    // ===== 语音播放 =====

    /**
     * 语音播报：优先缓存，降级实时TTS
     */
    private fun speakIfAvailable(text: String) {
        if (!voiceEnabled) return
        cachedTts.speak(text)
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
            is VoiceEvent.OneLap -> { if (toneEnabled) tonePlayer.playLap(); vibrateShort(); speakIfAvailable("${event.number}号") }
            is VoiceEvent.LastLap -> { if (toneEnabled) tonePlayer.playLastLap(); vibrateDouble(); speakIfAvailable("${event.number}号还剩最后一圈") }
            is VoiceEvent.Finished -> { if (toneEnabled) tonePlayer.playFinish(); vibrateLong(); speakIfAvailable("${event.number}号") }
            is VoiceEvent.AllFinished -> { if (toneEnabled) tonePlayer.playFinish(); vibrateLong(); speakIfAvailable("所有运动员完成比赛") }
        }
    }

    // ===== 外部接口 =====

    val isTtsReady: Boolean
        get() = cachedTts.isReady

    val voiceType: String
        get() = if (cachedTts.cacheCount > 0) "缓存TTS(${cachedTts.cacheCount}条)" else if (cachedTts.isReady) "本地TTS" else "未就绪"

    fun release() {
        tonePlayer.release()
        cachedTts.release()
        handler.removeCallbacksAndMessages(null)
    }

    companion object { private const val TAG = "FeedbackHelper" }
}
