package com.ivan1arriola.soldadowidget

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object FeedbackEffects {

    fun playTap(context: Context) {
        vibrate(context, durationMs = 22L, amplitude = 90)
        playTone(ToneGenerator.TONE_PROP_BEEP, durationMs = 45)
    }

    fun playLongPress(context: Context) {
        vibrate(context, durationMs = 50L, amplitude = 150)
        playTone(ToneGenerator.TONE_PROP_ACK, durationMs = 70)
    }

    fun playCommand(context: Context) {
        vibrate(context, durationMs = 34L, amplitude = 120)
        playTone(ToneGenerator.TONE_PROP_BEEP2, durationMs = 60)
    }

    private fun vibrate(context: Context, durationMs: Long, amplitude: Int) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255))
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        var generator: ToneGenerator? = null
        try {
            generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            generator.startTone(toneType, durationMs)
        } catch (_: Throwable) {
            // Ignorar para no interrumpir la interaccion principal.
        } finally {
            generator?.release()
        }
    }
}
