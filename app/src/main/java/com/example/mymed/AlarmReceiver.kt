package com.example.mymed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * AlarmReceiver - Empfängt zeitbasierte Alarme
 *
 * Wird aufgerufen von:
 * - AlarmScheduler (tägliche Erinnerungszeiten)
 * - SnoozeManager (Snooze-Alarm nach X Minuten)
 *
 * Zeigt jetzt eine "Wecker-artige" Notification statt die App direkt zu öffnen
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isSnooze = intent.getBooleanExtra("is_snooze", false)
        val reminderId = intent.getIntExtra("reminder_id", -1)
        Log.d("AlarmReceiver", if (isSnooze) "Snooze-Alarm" else "Regulärer Alarm (Reminder $reminderId)")

        if (!isSnooze) {
            // Neuer regulärer Alarm → Snooze-Zähler für diese Session zurücksetzen
            SnoozeManager.onNewAlarmFired(context, reminderId)

            // WICHTIG: Nächste Instanz dieses Alarms planen (da exakte Alarme
            // sich nicht selbst wiederholen!)
            val medicationId = intent.getIntExtra("medication_id", -1)
            val daysOfWeek = intent.getStringExtra("days_of_week") ?: "1,2,3,4,5,6,7"
            val hour = intent.getIntExtra("hour", -1)
            val minute = intent.getIntExtra("minute", -1)
            if (reminderId != -1 && hour != -1 && minute != -1) {
                AlarmScheduler.scheduleNext(context, reminderId, medicationId, daysOfWeek, hour, minute)
            }
        }

        // Service sicherstellen
        val serviceIntent = Intent(context, MedicationReminderService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // Kontinuierlichen Alarm-Sound + Vibration starten
        AlarmSoundManager.start(context)

        // Alarm-Notification anzeigen
        AlarmNotificationManager.showAlarmNotification(
            context = context,
            snoozeMinutes = SnoozeManager.getSnoozeMinutes(context),
            canSnooze = SnoozeManager.canSnoozeToday(context)
        )
    }
}
