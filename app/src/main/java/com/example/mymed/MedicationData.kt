package com.example.mymed

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val enabled: Boolean = true,
    // Weekdays as comma-separated list: "1,2,3,4,5,6,7" = every day
    // "1,3,5" = Monday, Wednesday, Friday
    val daysOfWeek: String = "1,2,3,4,5,6,7"
)

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

