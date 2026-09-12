package com.mahamart.essae

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object ConnectionFeedback {

    fun connected(context: Context) {
        // Positive ACK sound
        try {
            ToneGenerator(
                AudioManager.STREAM_NOTIFICATION,
                100
            ).apply {
                startTone(ToneGenerator.TONE_PROP_ACK, 1000)
            }
        } catch (_: Exception) {
        }
    }

    fun error(context: Context) {
        // Error sound
        try {
            ToneGenerator(
                AudioManager.STREAM_NOTIFICATION,
                100
            ).apply {
                startTone(ToneGenerator.TONE_PROP_NACK, 700)
            }
        } catch (_: Exception) {
        }

        // Vibration
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                            as VibratorManager

                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(
                        500,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator =
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (_: Exception) {
        }
    }
}