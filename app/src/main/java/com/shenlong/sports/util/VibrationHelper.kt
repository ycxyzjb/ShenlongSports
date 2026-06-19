package com.shenlong.sports.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 震动反馈管理器 - 记圈时震动提示
 */
class VibrationHelper(private val context: Context) {

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /** 短震动 - 记一圈时 */
    fun vibrateLap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(80)
        }
    }

    /** 长震动 - 完赛时 */
    fun vibrateFinish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }
    }

    /** 双震 - 还剩一圈提醒 */
    fun vibrateLastLap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100), intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 100, 80, 100), -1)
        }
    }

    fun handleEvent(event: com.shenlong.sports.viewmodel.VoiceEvent) {
        when (event) {
            is com.shenlong.sports.viewmodel.VoiceEvent.AllLap -> vibrateLap()
            is com.shenlong.sports.viewmodel.VoiceEvent.OneLap -> vibrateLap()
            is com.shenlong.sports.viewmodel.VoiceEvent.LastLap -> vibrateLastLap()
            is com.shenlong.sports.viewmodel.VoiceEvent.Finished -> vibrateFinish()
            is com.shenlong.sports.viewmodel.VoiceEvent.AllFinished -> vibrateFinish()
        }
    }
}
