package com.fangzhou.carcontrol.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class HapticManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        mgr?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun light() {
        vibrator?.vibrate(VibrationEffect.createOneShot(15, 120))
    }

    fun medium() {
        vibrator?.vibrate(VibrationEffect.createOneShot(40, 200))
    }

    fun heavy() {
        vibrator?.vibrate(VibrationEffect.createOneShot(80, 255))
    }

    fun success() {
        val pattern = longArrayOf(0, 20, 60, 40)
        val amplitudes = intArrayOf(0, 150, 0, 255)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
    }

    fun error() {
        val pattern = longArrayOf(0, 30, 50, 30, 50, 30)
        val amplitudes = intArrayOf(0, 200, 0, 200, 0, 200)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
    }

    fun tick() {
        vibrator?.vibrate(VibrationEffect.createOneShot(8, 80))
    }
}

@Composable
fun rememberHaptic(): HapticManager {
    val context = LocalContext.current
    return remember { HapticManager(context) }
}
