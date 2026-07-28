package com.example.mymed

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * AlarmNotificationManager
 *
 * Zeigt eine "Alarm-artige" Notification - ähnlich einem Wecker:
 * - Erscheint als Vollbild auf dem Sperrbildschirm
 * - Hohe Priorität (Heads-Up: erscheint über andere Apps)
 * - Alarm-Sound
 * - Vibration
 * - Action-Buttons: "✅ Alle genommen" und "⏰ Snooze"
 */
object AlarmNotificationManager {

    const val ALARM_NOTIFICATION_ID = 2001
    const val ALARM_CHANNEL_ID = "medication_alarm_channel"

    // Action-Strings: eindeutige Namen für Button-Aktionen
    const val ACTION_ALL_TAKEN = "com.example.mymed.ACTION_ALL_TAKEN"
    const val ACTION_SNOOZE    = "com.example.mymed.ACTION_SNOOZE"

    /**
     * Notification Channel erstellen (einmalig nötig, sicher mehrfach aufrufbar)
     *
     * Channel = Kategorie für Notifications
     * Jeder Channel hat eigene Einstellungen (Sound, Vibration, Priorität)
     */
    fun createAlarmChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Medikamenten-Alarm",
                NotificationManager.IMPORTANCE_HIGH  // HIGH = Heads-Up + Sound
            ).apply {
                description = "Erinnerung zur Medikamenteneinnahme"
                setSound(alarmSound, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Alarm-Notification anzeigen
     *
     * @param snoozeMinutes Aktuelle Snooze-Zeit (für Button-Label)
     * @param canSnooze     Snooze noch verfügbar?
     */
    fun showAlarmNotification(context: Context, snoozeMinutes: Int, canSnooze: Boolean) {
        createAlarmChannel(context)

        // --- PendingIntents für Actions ---

        // Tippen auf Notification → App öffnen
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_REMINDER", true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Button "✅ Alle genommen"
        val allTakenIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_ALL_TAKEN
        }
        val allTakenPending = PendingIntent.getBroadcast(
            context, 1, allTakenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Button "⏰ Snooze"
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_SNOOZE
        }
        val snoozePending = PendingIntent.getBroadcast(
            context, 2, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Vollbild-Intent: zeigt auf Sperrbildschirm wie ein Wecker
        val fullScreenIntent = PendingIntent.getActivity(
            context, 3, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // --- Notification bauen ---
        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("💊 Zeit für deine Medikamente!")
            .setContentText("Tippe um die Liste zu öffnen")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Es ist Zeit deine Medikamente einzunehmen.\nBitte überprüfe die Liste."))
            .setPriority(NotificationCompat.PRIORITY_MAX)  // MAX = Heads-Up
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // Auf Sperrbildschirm sichtbar
            .setContentIntent(openAppPendingIntent)
            .setFullScreenIntent(fullScreenIntent, true)  // ← Vollbild wie Wecker!
            .setAutoCancel(false)  // Bleibt bis User reagiert
            .setOngoing(false)     // Kann weggewischt werden
            // Alarm-Sound (aus Channel, aber nochmal für alte Android-Versionen)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            // Action-Buttons
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "✅ Alle genommen",
                allTakenPending
            )
            .apply {
                // Snooze nur anzeigen wenn noch verfügbar
                if (canSnooze) {
                    addAction(
                        android.R.drawable.ic_lock_idle_alarm,
                        "⏰ Snooze ${snoozeMinutes}min",
                        snoozePending
                    )
                }
            }
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(ALARM_NOTIFICATION_ID, notification)

        // Vibration zusätzlich auslösen (falls Channel-Vibration nicht reicht)
        vibrate(context)
    }

    /**
     * Notification entfernen (nach "Erledigt" oder "Snooze")
     */
    fun dismissAlarmNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(ALARM_NOTIFICATION_ID)
    }

    /**
     * Vibration auslösen
     * Muster: 500ms an, 200ms aus, 500ms an, 200ms aus, 500ms an
     */
    private fun vibrate(context: Context) {
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(VibratorManager::class.java)
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }
}

