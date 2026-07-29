package com.example.mymed

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * AlarmScheduler - Schedules exact daily medication alarms.
 *
 * IMPORTANT: Uses setExactAndAllowWhileIdle (not setRepeating!).
 * Reason: setRepeating has been inexact since Android 4.4 and can be
 * delayed or skipped in Doze mode (device standby).
 *
 * Since exact alarms do not repeat automatically, AlarmReceiver
 * schedules the next occurrence after each trigger (see scheduleNext()).
 */
object AlarmScheduler {

    fun scheduleAlarms(context: Context, reminders: List<Reminder>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("AlarmScheduler", "No exact-alarm permission. Falling back to inexact alarms.")
            }
        }

        cancelAllAlarms(context, alarmManager)

        val enabled = reminders.filter { it.enabled }
        enabled.forEach { scheduleReminder(context, alarmManager, it) }
        Log.d("AlarmScheduler", "${enabled.size} reminder(s) scheduled")
    }

    fun scheduleReminder(context: Context, alarmManager: AlarmManager, reminder: Reminder) {
        val triggerTime = nextTriggerTime(reminder) ?: run {
            Log.w("AlarmScheduler", "Reminder ${reminder.id}: no valid weekdays")
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
            putExtra("medication_id", reminder.medicationId)
            putExtra("days_of_week", reminder.daysOfWeek)
            putExtra("hour", reminder.hour)
            putExtra("minute", reminder.minute)
            putExtra("snooze_minutes", reminder.snoozeMinutes)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, reminder.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Exact alarm that also fires in Doze mode (standby)
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            val cal = Calendar.getInstance().apply { timeInMillis = triggerTime }
            Log.d("AlarmScheduler", "Alarm ${reminder.id}: %02d:%02d (in %d min)".format(
                reminder.hour, reminder.minute,
                (triggerTime - System.currentTimeMillis()) / 60000
            ))
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            Log.w("AlarmScheduler", "Inexact alarm (no permission): ${e.message}")
        }
    }

    /**
     * Calculates the next trigger timestamp for this reminder,
     * taking weekdays into account (1=Mon ... 7=Sun).
     */
    private fun nextTriggerTime(reminder: Reminder): Long? {
        val days = reminder.daysOfWeek.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
        if (days.isEmpty()) return null

        val now = Calendar.getInstance()

        // Check today plus the next 7 days
        for (offset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, reminder.hour)
                set(Calendar.MINUTE, reminder.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Calendar: Sunday=1..Saturday=7 -> our format: Monday=1..Sunday=7
            val ourDayOfWeek = when (candidate.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 0
            }

            if (days.contains(ourDayOfWeek) && candidate.timeInMillis > now.timeInMillis) {
                return candidate.timeInMillis
            }
        }
        return null
    }

    /**
     * After firing, schedule the next occurrence of this reminder.
     * Called from AlarmReceiver.
     */
    fun scheduleNext(context: Context, reminderId: Int, medicationId: Int,
                     daysOfWeek: String, hour: Int, minute: Int, snoozeMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reminder = Reminder(
            id = reminderId,
            medicationId = medicationId,
            hour = hour,
            minute = minute,
            snoozeMinutes = snoozeMinutes,
            enabled = true,
            daysOfWeek = daysOfWeek
        )
        scheduleReminder(context, alarmManager, reminder)
    }

    private fun cancelAllAlarms(context: Context, alarmManager: AlarmManager) {
        for (id in 1..2000) {
            val pi = PendingIntent.getBroadcast(
                context, id, Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pi?.let { alarmManager.cancel(it) }
        }
        // Safety net: Snooze alarm uses a dedicated ID (9999) outside
        // the range above, so cancel it explicitly to avoid
        // stale "ghost alarms" from earlier snooze tests.
        SnoozeManager.cancelSnooze(context)
    }

    suspend fun rescheduleFromDb(context: Context) {
        val dao = AppDatabase.getInstance(context).medicationDao()
        // Clean up orphan reminders (their medication was deleted)
        dao.deleteOrphanReminders()
        val reminders = dao.getAllEnabledReminders()
        scheduleAlarms(context, reminders)
    }
}
