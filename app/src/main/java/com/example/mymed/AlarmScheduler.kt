package com.example.mymed

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * AlarmScheduler - Plant exakte, tägliche Medikamenten-Alarme
 *
 * WICHTIG: Verwendet setExactAndAllowWhileIdle (nicht setRepeating!)
 * → Grund: setRepeating ist seit Android 4.4 INEXAKT und wird im
 *   Doze-Modus (Handy im Standby) verschoben oder gar nicht ausgelöst.
 *
 * Da exakte Alarme sich NICHT wiederholen, plant der AlarmReceiver
 * nach jedem Auslösen die nächste Instanz neu (siehe scheduleNext()).
 */
object AlarmScheduler {

    fun scheduleAlarms(context: Context, reminders: List<Reminder>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("AlarmScheduler", "Keine Exact-Alarm-Permission! Fallback auf ungenaue Alarme.")
            }
        }

        cancelAllAlarms(context, alarmManager)

        val enabled = reminders.filter { it.enabled }
        enabled.forEach { scheduleReminder(context, alarmManager, it) }
        Log.d("AlarmScheduler", "${enabled.size} Reminder geplant")
    }

    fun scheduleReminder(context: Context, alarmManager: AlarmManager, reminder: Reminder) {
        val triggerTime = nextTriggerTime(reminder) ?: run {
            Log.w("AlarmScheduler", "Reminder ${reminder.id}: keine gültigen Wochentage")
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
            putExtra("medication_id", reminder.medicationId)
            putExtra("days_of_week", reminder.daysOfWeek)
            putExtra("hour", reminder.hour)
            putExtra("minute", reminder.minute)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, reminder.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Exakter Alarm der auch im Doze-Modus (Standby) feuert
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            val cal = Calendar.getInstance().apply { timeInMillis = triggerTime }
            Log.d("AlarmScheduler", "Alarm ${reminder.id}: %02d:%02d (in %d Min)".format(
                reminder.hour, reminder.minute,
                (triggerTime - System.currentTimeMillis()) / 60000
            ))
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            Log.w("AlarmScheduler", "Ungenauer Alarm (keine Permission): ${e.message}")
        }
    }

    /**
     * Berechnet den nächsten Zeitpunkt an dem dieser Reminder feuern soll
     * - unter Berücksichtigung der Wochentage (1=Mo ... 7=So).
     */
    private fun nextTriggerTime(reminder: Reminder): Long? {
        val days = reminder.daysOfWeek.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
        if (days.isEmpty()) return null

        val now = Calendar.getInstance()

        // Heute + nächste 7 Tage prüfen
        for (offset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, reminder.hour)
                set(Calendar.MINUTE, reminder.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Calendar: Sonntag=1..Samstag=7 → unser Format: Montag=1..Sonntag=7
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
     * Nach dem Auslösen: nächste Instanz dieses Reminders planen.
     * Wird vom AlarmReceiver aufgerufen.
     */
    fun scheduleNext(context: Context, reminderId: Int, medicationId: Int,
                     daysOfWeek: String, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reminder = Reminder(
            id = reminderId,
            medicationId = medicationId,
            hour = hour,
            minute = minute,
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
        // Sicherheitsnetz: Snooze-Alarm hat eine eigene ID (9999) außerhalb
        // des obigen Bereichs - explizit mit abbrechen, damit keine
        // "Geister-Alarme" von früheren Snooze-Tests übrig bleiben.
        SnoozeManager.cancelSnooze(context)
    }

    suspend fun rescheduleFromDb(context: Context) {
        val dao = AppDatabase.getInstance(context).medicationDao()
        // Verwaiste Reminder aufräumen (deren Medikament gelöscht wurde)
        dao.deleteOrphanReminders()
        val reminders = dao.getAllEnabledReminders()
        scheduleAlarms(context, reminders)
    }
}
