package com.example.mymed

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

/**
 * SnoozeManager - Verwaltet Snooze-Einstellungen und plant Snooze-Alarme
 *
 * Einstellungen werden in SharedPreferences gespeichert (bleiben nach App-Restart erhalten)
 *
 * SharedPreferences = einfaches Key/Value Speicher für kleine Einstellungen
 * Wie: Windows Registry oder .properties-Dateien, nur für Android
 */
object SnoozeManager {

    // --- Konstanten: Default-Werte ---
    const val DEFAULT_SNOOZE_MINUTES = 10   // Standard: 10 Minuten
    const val DEFAULT_MAX_SNOOZE_COUNT = 3  // Standard: max. 3x Snooze pro Tag
    private const val SNOOZE_ALARM_ID = 9999  // Eindeutige ID für Snooze-Alarm

    // SharedPreferences Keys
    private const val PREFS_NAME = "mymed_snooze_prefs"
    private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
    private const val KEY_MAX_COUNT = "max_snooze_count"
    private const val KEY_SNOOZE_COUNT = "snooze_count_session"       // Pro Alarm-Session
    private const val KEY_CURRENT_ALARM_ID = "current_alarm_id"       // Welcher Alarm läuft gerade

    // Verfügbare Snooze-Zeiten (Minuten) für die UI-Auswahl
    val SNOOZE_OPTIONS = listOf(5, 10, 20, 30)

    // Verfügbare Max-Anzahl-Optionen für die UI-Auswahl
    val MAX_COUNT_OPTIONS = listOf(1, 2, 3, 5)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSnoozeMinutes(context: Context): Int =
        prefs(context).getInt(KEY_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES)

    fun getMaxSnoozeCount(context: Context): Int =
        prefs(context).getInt(KEY_MAX_COUNT, DEFAULT_MAX_SNOOZE_COUNT)

    fun setSnoozeMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_SNOOZE_MINUTES, minutes).apply()
    }

    fun setMaxSnoozeCount(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_MAX_COUNT, count).apply()
    }

    /**
     * Wird aufgerufen wenn ein NEUER regulärer Alarm feuert (nicht Snooze).
     * Setzt den Zähler zurück → jeder Alarm bekommt frisch X Snoozes.
     *
     * @param alarmId  Die Reminder-ID die gerade feuert
     */
    fun onNewAlarmFired(context: Context, alarmId: Int) {
        prefs(context).edit()
            .putInt(KEY_SNOOZE_COUNT, 0)
            .putInt(KEY_CURRENT_ALARM_ID, alarmId)
            .apply()
        Log.d("SnoozeManager", "Neuer Alarm $alarmId - Snooze-Zähler zurückgesetzt")
    }

    fun getSnoozeCountToday(context: Context): Int =
        prefs(context).getInt(KEY_SNOOZE_COUNT, 0)

    fun canSnoozeToday(context: Context): Boolean =
        getSnoozeCountToday(context) < getMaxSnoozeCount(context)

    // Zähler für aktuelle Alarm-Session zurücksetzen
    fun resetTodayCount(context: Context) {
        prefs(context).edit().putInt(KEY_SNOOZE_COUNT, 0).apply()
    }

    private fun incrementSnoozeCount(context: Context) {
        val current = getSnoozeCountToday(context)
        prefs(context).edit().putInt(KEY_SNOOZE_COUNT, current + 1).apply()
    }

    /**
     * Snooze ausführen:
     * 1. Zähler erhöhen
     * 2. Neuen Alarm in X Minuten planen
     *
     * @return true wenn Snooze erfolgreich, false wenn Max-Anzahl erreicht
     */
    fun snooze(context: Context): Boolean {
        if (!canSnoozeToday(context)) return false

        incrementSnoozeCount(context)

        val snoozeMinutes = getSnoozeMinutes(context)
        val triggerAt = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

        // Einmaliger Alarm (kein setRepeating!) nach X Minuten
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("is_snooze", true)  // Damit AlarmReceiver weiß: das ist ein Snooze
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            SNOOZE_ALARM_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Fallback: ungenauer Alarm
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                return true
            }
        }

        // Exakter Alarm (weckt Handy auf)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )

        return true
    }

    /**
     * Snooze-Alarm abbrechen (z.B. wenn User die App selbst öffnet)
     */
    fun cancelSnooze(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            SNOOZE_ALARM_ID,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }
}

