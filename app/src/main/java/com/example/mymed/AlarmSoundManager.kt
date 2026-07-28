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
 * AlarmSoundManager - Singleton der den Alarm-Sound + Vibration verwaltet
 *
 * Wie ein Wecker:
 * - Startet kontinuierlichen Ton (läuft bis stop() aufgerufen wird)
 * - Startet wiederholende Vibration
 * - Stoppt beides auf Befehl
 *
 * Singleton (object) = Es gibt genau eine Instanz im gesamten App-Leben
 * → So kann jeder im Code stoppen, egal von wo
 */
object AlarmSoundManager {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var isPlaying = false

    /**
     * Alarm starten (Sound + Vibration)
     * Läuft kontinuierlich bis stop() aufgerufen wird
     */
    fun start(context: Context) {
        if (isPlaying) return  // Nicht doppelt starten
        isPlaying = true
        Log.d("AlarmSoundManager", "Alarm startet")

        // --- Sound ---
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            ringtone = RingtoneManager.getRingtone(context.applicationContext, alarmUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true  // Wiederholt automatisch!
                }
                // Alarm-Audio-Stream (laut, ignoriert Stumm-Modus)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build()
                }
                play()
            }
        } catch (e: Exception) {
            Log.e("AlarmSoundManager", "Sound-Fehler: ${e.message}")
        }

        // --- Vibration (wiederholt) ---
        startVibration(context)
    }

    /**
     * Alarm stoppen (Sound + Vibration)
     */
    fun stop(context: Context) {
        if (!isPlaying) return
        isPlaying = false
        Log.d("AlarmSoundManager", "Alarm gestoppt")

        // Sound stoppen
        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {
            Log.e("AlarmSoundManager", "Stop-Fehler: ${e.message}")
        }

        // Vibration stoppen
        stopVibration(context)
    }

    fun isAlarmPlaying(): Boolean = isPlaying

    private fun startVibration(context: Context) {
        // Muster: warte 0ms, vibriere 800ms, pause 400ms, vibriere 800ms, pause 400ms...
        // repeat = 0 → wiederholt ab Index 0 endlos
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
            Log.e("AlarmSoundManager", "Vibrations-Fehler: ${e.message}")
        }
    }

    private fun stopVibration(context: Context) {
        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            Log.e("AlarmSoundManager", "Vibrations-Stop-Fehler: ${e.message}")
        }
    }
}

