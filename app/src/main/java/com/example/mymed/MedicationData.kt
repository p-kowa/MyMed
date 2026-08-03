package com.example.mymed

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

@Entity(tableName = "medications")
data class MyMedication(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val dosage: String? = null,
    val notes: String? = null,
    val active: Boolean = true
    // imagePath removed - ML Kit scans text directly from package
)

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val medicationId: Int,
    val hour: Int,
    val minute: Int,
    val snoozeMinutes: Int = SnoozeManager.DEFAULT_SNOOZE_MINUTES,
    val enabled: Boolean = true,
    // Weekdays as comma-separated list: "1,2,3,4,5,6,7" = every day
    // "1,3,5" = Monday, Wednesday, Friday
    val daysOfWeek: String = "1,2,3,4,5,6,7",
    // Timestamp when THIS scheduled reminder was taken.
    // It counts as "taken" only on the same calendar day.
    val takenAt: Long? = null
) {
    fun isScheduledForToday(now: Long = System.currentTimeMillis()): Boolean {
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val todayNumber = when (today.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 0
        }

        return daysOfWeek.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .contains(todayNumber)
    }

    fun isTakenToday(now: Long = System.currentTimeMillis()): Boolean {
        val takenTimestamp = takenAt ?: return false
        val taken = Calendar.getInstance().apply { timeInMillis = takenTimestamp }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        return taken.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            taken.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }
}

@Entity(tableName = "medication_history")
data class MedicationHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val medicationId: Int,
    val reminderId: Int? = null,     // Which alarm triggered this entry?
    val takenAt: Long,               // Timestamp in milliseconds
    val skipped: Boolean = false,    // true = intentionally skipped
    val note: String? = null         // e.g. "half dose taken"
)

