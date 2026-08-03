package com.example.mymed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * AlarmReceiver - receives time-based alarms.
 *
 * Called by:
 * - AlarmScheduler (daily reminder times)
 * - SnoozeManager (snooze alarm after X minutes)
 *
 * Shows an alarm-style notification instead of opening the app directly.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isSnooze = intent.getBooleanExtra("is_snooze", false)
        val reminderId = intent.getIntExtra("reminder_id", -1)
        val snoozeMinutes = intent.getIntExtra("snooze_minutes", SnoozeManager.DEFAULT_SNOOZE_MINUTES)
        Log.d("AlarmReceiver", if (isSnooze) "Snooze alarm" else "Regular alarm (Reminder $reminderId)")

        if (!isSnooze) {
            // New regular alarm -> reset snooze counter for this session
            SnoozeManager.onNewAlarmFired(context, reminderId, snoozeMinutes)

            // IMPORTANT: Schedule the next occurrence of this alarm,
            // because exact alarms do not auto-repeat.
            val medicationId = intent.getIntExtra("medication_id", -1)
            val daysOfWeek = intent.getStringExtra("days_of_week") ?: "1,2,3,4,5,6,7"
            val hour = intent.getIntExtra("hour", -1)
            val minute = intent.getIntExtra("minute", -1)
            if (reminderId != -1 && hour != -1 && minute != -1) {
                AlarmScheduler.scheduleNext(
                    context,
                    reminderId,
                    medicationId,
                    daysOfWeek,
                    hour,
                    minute,
                    snoozeMinutes
                )
            }

            // Remember which reminder is currently ringing so that pressing
            // "Taken" only marks THIS reminder as taken.
            RingingMedicationsTracker.onRegularAlarmFired(context, reminderId)
        } else {
            RingingMedicationsTracker.restoreSerializedReminderIds(
                context,
                intent.getStringExtra("ringing_reminder_ids")
            )
        }

        // Ensure service is running
        val serviceIntent = Intent(context, MedicationReminderService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // Start continuous alarm sound + vibration
        AlarmSoundManager.start(context)

        // Show alarm notification
        AlarmNotificationManager.showAlarmNotification(
            context = context,
            snoozeMinutes = SnoozeManager.getCurrentSnoozeMinutes(context),
            canSnooze = true
        )
    }
}
