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
    const val DEFAULT_SNOOZE_MINUTES = 10   // Default: 10 minutes
    const val DEFAULT_MAX_SNOOZE_COUNT = 3  // Default: max. 3x snooze per alarm session
    private const val SNOOZE_ALARM_ID = 9999  // Unique ID for the snooze alarm

    // SharedPreferences keys
    private const val PREFS_NAME = "mymed_snooze_prefs"
    private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
    private const val KEY_MAX_COUNT = "max_snooze_count"
    private const val KEY_SNOOZE_COUNT = "snooze_count_session"       // Per alarm session
    private const val KEY_CURRENT_ALARM_ID = "current_alarm_id"       // Current active alarm

    // Available snooze durations (minutes) for UI selection
    val SNOOZE_OPTIONS = listOf(5, 10, 20, 30)

    // Available max-count options for UI selection
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
     * Called when a NEW regular alarm fires (not a snooze alarm).
     * Resets the counter so each alarm session gets fresh snoozes.
     *
     * @param alarmId Reminder ID that is currently firing
     */
    fun onNewAlarmFired(context: Context, alarmId: Int) {
        prefs(context).edit()
            .putInt(KEY_SNOOZE_COUNT, 0)
            .putInt(KEY_CURRENT_ALARM_ID, alarmId)
            .apply()
        Log.d("SnoozeManager", "New alarm $alarmId - snooze counter reset")
    }

    fun getSnoozeCountToday(context: Context): Int =
        prefs(context).getInt(KEY_SNOOZE_COUNT, 0)

    fun canSnoozeToday(context: Context): Boolean =
        getSnoozeCountToday(context) < getMaxSnoozeCount(context)

    // Reset counter for the current alarm session
    fun resetTodayCount(context: Context) {
        prefs(context).edit().putInt(KEY_SNOOZE_COUNT, 0).apply()
    }

    private fun incrementSnoozeCount(context: Context) {
        val current = getSnoozeCountToday(context)
        prefs(context).edit().putInt(KEY_SNOOZE_COUNT, current + 1).apply()
    }

    /**
     * Executes snooze:
     * 1) increments counter
     * 2) schedules a new alarm in X minutes
     *
     * @return true if snooze succeeded, false if max count was reached
     */
    fun snooze(context: Context): Boolean {
        if (!canSnoozeToday(context)) return false

        incrementSnoozeCount(context)

        val snoozeMinutes = getSnoozeMinutes(context)
        val triggerAt = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

        // One-time alarm (no setRepeating) after X minutes
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("is_snooze", true)  // Lets AlarmReceiver know this is snooze
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

