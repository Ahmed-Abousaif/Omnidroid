package com.omnidroid.app.shared.rumble

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.roundToInt

/**
 * Plays short touch-control haptic pulses with adjustable intensity.
 * Used instead of PadKit's fixed-amplitude effects so touch feedback
 * shares the same intensity preference as rumble.
 */
class TouchHapticPlayer(context: Context) {
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    fun play(
        isPress: Boolean,
        intensity: Float,
    ) {
        if (intensity <= 0f || !vibrator.hasVibrator()) return

        val durationMs = if (isPress) PRESS_DURATION_MS else RELEASE_DURATION_MS
        val amplitude =
            (intensity * if (isPress) PRESS_AMPLITUDE else RELEASE_AMPLITUDE)
                .roundToInt()
                .coerceIn(1, 255)

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effectAmplitude =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && vibrator.hasAmplitudeControl()) {
                        amplitude
                    } else {
                        VibrationEffect.DEFAULT_AMPLITUDE
                    }
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, effectAmplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        }
    }

    companion object {
        private const val PRESS_DURATION_MS = 30L
        private const val RELEASE_DURATION_MS = 15L
        private const val PRESS_AMPLITUDE = 255
        private const val RELEASE_AMPLITUDE = 180
    }
}
