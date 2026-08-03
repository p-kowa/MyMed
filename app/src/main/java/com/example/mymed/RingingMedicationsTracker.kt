package com.example.mymed

import android.content.Context

/**
 * Keeps track of which reminder IDs are currently ringing.
 *
 * This is intentionally tiny and only solves one problem: when the user taps
 * "Taken", only the reminder(s) of the current alarm session should be marked
 * as taken - not every medication in the app.
 */
object RingingMedicationsTracker {

	private const val PREFS_NAME = "ringing_reminders"
	private const val KEY_IDS = "current_ids"
	private const val SEPARATOR = ","

	fun onRegularAlarmFired(context: Context, reminderId: Int) {
		if (reminderId == -1) return

		val currentIds = if (AlarmSoundManager.isAlarmPlaying()) {
			getCurrentReminderIds(context).toMutableSet()
		} else {
			mutableSetOf()
		}

		currentIds.add(reminderId)
		save(context, currentIds)
	}

	fun getCurrentReminderIds(context: Context): Set<Int> {
		val raw = prefs(context).getString(KEY_IDS, null).orEmpty()
		if (raw.isBlank()) return emptySet()
		return raw.split(SEPARATOR)
			.mapNotNull { it.toIntOrNull() }
			.toSet()
	}

	fun serializeCurrentReminderIds(context: Context): String =
		getCurrentReminderIds(context).sorted().joinToString(SEPARATOR)

	fun restoreSerializedReminderIds(context: Context, raw: String?) {
		if (raw.isNullOrBlank()) return
		val ids = raw.split(SEPARATOR)
			.mapNotNull { it.toIntOrNull() }
			.toSet()
		if (ids.isNotEmpty()) save(context, ids)
	}

	fun clear(context: Context) {
		prefs(context).edit().remove(KEY_IDS).apply()
	}

	private fun save(context: Context, ids: Set<Int>) {
		val value = ids.sorted().joinToString(SEPARATOR)
		prefs(context).edit().putString(KEY_IDS, value).apply()
	}

	private fun prefs(context: Context) =
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

