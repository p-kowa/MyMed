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
 * Shows an alarm-style notification similar to a clock alarm:
 * - Full-screen on the lock screen
 * - High priority (heads-up over other apps)
 * - Alarm sound
 * - Vibration
 * - Action buttons: "✅ All taken" and "⏰ Snooze"
 */
object AlarmNotificationManager {

    const val ALARM_NOTIFICATION_ID = 2001
    const val ALARM_CHANNEL_ID = "medication_alarm_channel"

    // Action strings: unique names for button actions
    const val ACTION_ALL_TAKEN = "com.example.mymed.ACTION_ALL_TAKEN"
    const val ACTION_SNOOZE    = "com.example.mymed.ACTION_SNOOZE"

    /**
     * Creates the notification channel (required once, safe to call multiple times).
     *
     * Channel = category for notifications.
     * Each channel has its own settings (sound, vibration, priority).
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
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH  // HIGH = heads-up + sound
            ).apply {
                description = context.getString(R.string.alarm_channel_desc)
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
     * Displays the alarm notification.
     *
     * @param snoozeMinutes Current snooze duration (for button label)
     * @param canSnooze     Whether snooze is still available
     */
    fun showAlarmNotification(context: Context, snoozeMinutes: Int, canSnooze: Boolean) {
        createAlarmChannel(context)

        // --- PendingIntents for actions ---

        // Tap notification -> open app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_REMINDER", true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "✅ All taken" button
        val allTakenIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_ALL_TAKEN
        }
        val allTakenPending = PendingIntent.getBroadcast(
            context, 1, allTakenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "⏰ Snooze" button
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_SNOOZE
        }
        val snoozePending = PendingIntent.getBroadcast(
            context, 2, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full-screen intent: appears on lock screen like an alarm
        val fullScreenIntent = PendingIntent.getActivity(
            context, 3, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // --- Build notification ---
        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.alarm_notif_title))
            .setContentText(context.getString(R.string.alarm_notif_text))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.alarm_notif_big_text)))
            .setPriority(NotificationCompat.PRIORITY_MAX)  // MAX = heads-up
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // Visible on lock screen
            .setContentIntent(openAppPendingIntent)
            .setFullScreenIntent(fullScreenIntent, true)  // Full-screen like an alarm
            .setAutoCancel(false)  // Stays until user reacts
            .setOngoing(false)     // Can be dismissed by swipe
            // Alarm sound (from channel, repeated for older Android versions)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            // Action buttons
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.alarm_action_all_taken),
                allTakenPending
            )
            .apply {
                // Show Snooze only when available
                if (canSnooze) {
                    addAction(
                        android.R.drawable.ic_lock_idle_alarm,
                        context.getString(R.string.alarm_action_snooze, snoozeMinutes),
                        snoozePending
                    )
                }
            }
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(ALARM_NOTIFICATION_ID, notification)

        // Trigger additional vibration (if channel vibration is insufficient)
        vibrate(context)
    }

    /**
     * Dismisses the notification (after "All taken" or "Snooze").
     */
    fun dismissAlarmNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(ALARM_NOTIFICATION_ID)
    }

    /**
     * Triggers vibration.
     * Pattern: 500ms on, 200ms off, 500ms on, 200ms off, 500ms on.
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

