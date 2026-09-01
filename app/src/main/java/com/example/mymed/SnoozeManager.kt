package com.example.mymed

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

/**
 * SnoozeManager - Manages snooze settings and schedules snooze alarms.
 *
 * Settings are stored in SharedPreferences (persist across app restarts).
 *
 * SharedPreferences = simple key/value storage for small settings,
 * similar to a lightweight local properties store.
 */
object SnoozeManager {

    // --- Constants: default values ---
    const val DEFAULT_SNOOZE_MINUTES = 10
    const val DEFAULT_AUTO_SNOOZE_SECONDS = 25
    private const val SNOOZE_ALARM_ID = 9999  // Unique ID for the snooze alarm

    // SharedPreferences keys
    private const val PREFS_NAME = "mymed_snooze_prefs"
    private const val KEY_CURRENT_SNOOZE_MINUTES = "current_snooze_minutes"
    private const val KEY_AUTO_SNOOZE_SECONDS = "auto_snooze_seconds"

    // Available snooze durations (minutes) for UI selection while creating reminders
    val SNOOZE_OPTIONS = listOf(5, 10, 15, 20, 30)
    val AUTO_SNOOZE_SECONDS_OPTIONS = listOf(20, 25, 30)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCurrentSnoozeMinutes(context: Context): Int =
        prefs(context).getInt(KEY_CURRENT_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES)

    fun getAutoSnoozeSeconds(context: Context): Int {
        val saved = prefs(context).getInt(KEY_AUTO_SNOOZE_SECONDS, DEFAULT_AUTO_SNOOZE_SECONDS)
        return if (saved in AUTO_SNOOZE_SECONDS_OPTIONS) saved else DEFAULT_AUTO_SNOOZE_SECONDS
    }

    fun setAutoSnoozeSeconds(context: Context, seconds: Int) {
        if (seconds !in AUTO_SNOOZE_SECONDS_OPTIONS) return
        prefs(context).edit()
            .putInt(KEY_AUTO_SNOOZE_SECONDS, seconds)
            .apply()
    }

    /**
     * Called when a NEW regular alarm fires (not a snooze alarm).
     * Resets the counter so each alarm session gets fresh snoozes.
     *
     * @param alarmId Reminder ID that is currently firing
     */
    fun onNewAlarmFired(context: Context, alarmId: Int, snoozeMinutes: Int) {
        prefs(context).edit()
            .putInt(KEY_CURRENT_SNOOZE_MINUTES, snoozeMinutes)
            .apply()
        Log.d("SnoozeManager", "New alarm $alarmId - snooze interval: $snoozeMinutes min")
    }

    /**
     * Executes snooze:
     * Schedules a new alarm in X minutes.
     */
    fun snooze(context: Context): Boolean {
        val snoozeMinutes = getCurrentSnoozeMinutes(context)
        val triggerAt = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        val ringingIds = RingingMedicationsTracker.serializeCurrentReminderIds(context)

        // One-time alarm (no setRepeating) after X minutes
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("is_snooze", true)  // Lets AlarmReceiver know this is snooze
            putExtra("snooze_minutes", snoozeMinutes)
            putExtra("ringing_reminder_ids", ringingIds)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            SNOOZE_ALARM_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Fallback: inexact alarm
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                return true
            }
        }

        // Exact alarm (wakes the device)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )

        return true
    }

    /**
     * Cancels the pending snooze alarm (e.g., when user opens the app manually).
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

