package com.example.mymed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * NotificationActionReceiver
 *
 * Handles taps on notification action buttons:
 * - "✅ Taken"  -> mark only the CURRENT ringing reminder(s) as taken
 * - "⏰ Snooze" -> schedule snooze alarm, close notification
 *
 * Note: BroadcastReceiver has a very short lifetime.
 * DB operations (suspend functions) use goAsync() + CoroutineScope.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            // ✅ Current reminder(s) taken
            AlarmNotificationManager.ACTION_ALL_TAKEN -> {
                Log.d("NotificationAction", "Current reminder(s) marked as taken")

                // Stop alarm sound + vibration immediately
                AlarmSoundManager.stop(context)
                // All taken -> no snooze needed anymore
                SnoozeManager.cancelSnooze(context)

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dao = AppDatabase.getInstance(context).medicationDao()
                        val ringingIds = RingingMedicationsTracker.getCurrentReminderIds(context)
                        val now = System.currentTimeMillis()
                        ringingIds.forEach { reminderId ->
                            dao.markReminderTaken(reminderId, now)
                        }
                        RingingMedicationsTracker.clear(context)
                        Log.d("NotificationAction", "${ringingIds.size} reminder(s) marked as taken")
                    } catch (e: Exception) {
                        Log.e("NotificationAction", "Error: ${e.message}")
                    } finally {
                        AlarmNotificationManager.dismissAlarmNotification(context)
                        pendingResult.finish()
                    }
                }
            }

            // ⏰ "Snooze" button tapped
            AlarmNotificationManager.ACTION_SNOOZE -> {
                Log.d("NotificationAction", "Snooze tapped")

                // Stop alarm sound + vibration immediately
                AlarmSoundManager.stop(context)

                SnoozeManager.snooze(context)
                AlarmNotificationManager.dismissAlarmNotification(context)
            }
        }
    }
}

