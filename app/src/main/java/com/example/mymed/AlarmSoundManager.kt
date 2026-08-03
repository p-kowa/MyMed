package com.example.mymed

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * AlarmSoundManager - Singleton that controls alarm sound and vibration.
 *
 * Alarm-clock behavior:
 * - Starts continuous sound (runs until stop() is called)
 * - Starts repeating vibration
 * - Stops both on command
 *
 * Singleton (object) = exactly one instance during app lifetime,
 * so any caller can stop the alarm from anywhere.
 */
object AlarmSoundManager {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var isPlaying = false

    /**
     * Starts the alarm (sound + vibration).
     * Runs continuously until stop() is called.
     */
    fun start(context: Context) {
        if (isPlaying) return  // Do not start twice
        isPlaying = true
        Log.d("AlarmSoundManager", "Alarm starting")

        // --- Sound ---
        try {
            val alarmUri = AlarmTonePreferences.getEffectiveAlarmToneUri(context)

            ringtone = RingtoneManager.getRingtone(context.applicationContext, alarmUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true  // Auto-repeat
                }
                // Alarm audio stream (loud, ignores silent mode)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build()
                }
                play()
            }

            if (ringtone == null) {
                Log.w("AlarmSoundManager", "Selected alarm tone unavailable, trying system fallback")
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

                ringtone = fallbackUri?.let { uri ->
                    RingtoneManager.getRingtone(context.applicationContext, uri)?.apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            isLooping = true
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            audioAttributes = AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setLegacyStreamType(AudioManager.STREAM_ALARM)
                                .build()
                        }
                        play()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmSoundManager", "Sound error: ${e.message}")
        }

        // --- Repeating vibration ---
        startVibration(context)
    }

    /**
     * Stops the alarm (sound + vibration).
     */
    fun stop(context: Context) {
        if (!isPlaying) return
        isPlaying = false
        Log.d("AlarmSoundManager", "Alarm stopped")

        // Stop sound
        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {
            Log.e("AlarmSoundManager", "Stop error: ${e.message}")
        }

        // Stop vibration
        stopVibration(context)
    }

    fun isAlarmPlaying(): Boolean = isPlaying

    private fun startVibration(context: Context) {
        // Pattern: wait 0ms, vibrate 800ms, pause 400ms, ...
        // repeat = 0 -> repeats from index 0 forever
        val pattern = longArrayOf(0, 800, 400, 800, 400)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(VibratorManager::class.java)
                vibrator = vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 = repeat from index 0
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0) // 0 = repeat
            }
        } catch (e: Exception) {
            Log.e("AlarmSoundManager", "Vibration error: ${e.message}")
        }
    }

    private fun stopVibration(context: Context) {
        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            Log.e("AlarmSoundManager", "Vibration stop error: ${e.message}")
        }
    }
}

