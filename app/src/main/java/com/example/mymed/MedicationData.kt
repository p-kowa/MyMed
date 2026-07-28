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
    // imagePath entfernt - ML Kit scannt Text direkt von Packung
)

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val medicationId: Int,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    // Wochentage als komma-getrennte Liste: "1,2,3,4,5,6,7" = jeden Tag
    // "1,3,5" = Montag, Mittwoch, Freitag
    val daysOfWeek: String = "1,2,3,4,5,6,7"
)

@Entity(tableName = "medication_history")
data class MedicationHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val medicationId: Int,
    val reminderId: Int? = null,     // Welcher Alarm hat ausgelöst?
    val takenAt: Long,               // Zeitstempel in Millisekunden
    val skipped: Boolean = false,    // true = bewusst übersprungen
    val note: String? = null         // z.B. "halbe Dosis genommen"
)

