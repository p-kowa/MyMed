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
 * Verarbeitet Klicks auf die Notification-Buttons:
 * - "✅ Alle genommen" → alle Medikamente als genommen markieren
 * - "⏰ Snooze"        → Snooze-Alarm planen, Notification schließen
 *
 * Hinweis: BroadcastReceiver hat sehr kurze Lebenszeit.
 * Für DB-Operationen (suspend functions) brauchen wir goAsync() + CoroutineScope.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            // ✅ "Alle genommen" Button gedrückt
            AlarmNotificationManager.ACTION_ALL_TAKEN -> {
                Log.d("NotificationAction", "Alle Medikamente genommen")

                // Alarm-Sound + Vibration SOFORT stoppen
                AlarmSoundManager.stop(context)

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dao = AppDatabase.getInstance(context).medicationDao()
                        val activeMeds = dao.getAllActiveMedications()
                        val now = System.currentTimeMillis()
                        activeMeds.forEach { med ->
                            dao.insertHistory(MedicationHistory(medicationId = med.id, takenAt = now))
                        }
                        Log.d("NotificationAction", "${activeMeds.size} Medikamente als genommen markiert")
                    } catch (e: Exception) {
                        Log.e("NotificationAction", "Fehler: ${e.message}")
                    } finally {
                        AlarmNotificationManager.dismissAlarmNotification(context)
                        pendingResult.finish()
                    }
                }
            }

            // ⏰ "Snooze" Button gedrückt
            AlarmNotificationManager.ACTION_SNOOZE -> {
                Log.d("NotificationAction", "Snooze gedrückt")

                // Alarm-Sound + Vibration SOFORT stoppen
                AlarmSoundManager.stop(context)

                SnoozeManager.snooze(context)
                AlarmNotificationManager.dismissAlarmNotification(context)
            }
        }
    }
}

