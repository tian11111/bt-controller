package com.fangzhou.carcontrol.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 震动反馈管理器
 * - light(): 轻触反馈 (10ms) — 普通按钮
 * - medium(): 中等反馈 (25ms) — 夹爪操作
 * - heavy(): 重度反馈 (50ms) — 停止/紧急操作
 * - success(): 成功反馈 (短-长) — 连接成功
 * - error(): 错误反馈 (三连短) — 连接失败
 */
class HapticManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        mgr?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun light() {
        vibrator?.vibrate(VibrationEffect.createOneShot(10, 80))
    }

    fun medium() {
        vibrator?.vibrate(VibrationEffect.createOneShot(25, 130))
    }

    fun heavy() {
        vibrator?.vibrate(VibrationEffect.createOneShot(50, 200))
    }

    fun success() {
        val pattern = longArrayOf(0, 15, 50, 30)
        val amplitudes = intArrayOf(0, 80, 0, 160)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
    }

    fun error() {
        val pattern = longArrayOf(0, 20, 40, 20, 40, 20)
        val amplitudes = intArrayOf(0, 150, 0, 150, 0, 150)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
    }

    fun tick() {
        vibrator?.vibrate(VibrationEffect.createOneShot(5, 50))
    }
}

@Composable
fun rememberHaptic(): HapticManager {
    val context = LocalContext.current
    return remember { HapticManager(context) }
}
