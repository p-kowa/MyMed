package com.example.mymed

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
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

    // IMPORTANT: Once a NotificationChannel is created with a sound, that
    // sound can become "sticky" on some OEM skins (observed on Samsung/One UI):
    // deleting and recreating the channel with the SAME id does not reliably
    // clear the old sound, causing it to play together with the tone we start
    // manually in AlarmSoundManager (2 tones at once).
    // Fix: use a channel id that was never associated with any sound, and
    // never give it one. The actual alarm tone is always played exclusively
    // by AlarmSoundManager, so this channel must stay silent forever.
    const val ALARM_CHANNEL_ID = "medication_alarm_channel_silent_v2"
    private const val LEGACY_ALARM_CHANNEL_ID = "medication_alarm_channel"

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
            val manager = context.getSystemService(NotificationManager::class.java)

            // Remove the old channel once; it may still carry a legacy sound
            // setting on some devices. We don't reuse its id.
            manager.deleteNotificationChannel(LEGACY_ALARM_CHANNEL_ID)

            // Channel already exists with correct (silent) settings -> nothing to do.
            // We intentionally do NOT delete+recreate ALARM_CHANNEL_ID on every call,
            // since this channel is only ever created once, silent, and never changed.
            if (manager.getNotificationChannel(ALARM_CHANNEL_ID) != null) return

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
                setSound(null, audioAttributes)
                enableVibration(false)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

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
    }

    /**
     * Dismisses the notification (after "All taken" or "Snooze").
     */
    fun dismissAlarmNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(ALARM_NOTIFICATION_ID)
    }

}

