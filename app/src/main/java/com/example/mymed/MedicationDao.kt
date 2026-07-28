package com.example.mymed

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    // --- Medikamente ---

    // Alle Medikamente als Flow (aktualisiert sich automatisch bei DB-Änderungen)
    @Query("SELECT * FROM medications ORDER BY name")
    fun getAllMedications(): Flow<List<MyMedication>>

    // Alle aktiven Medikamente als einfache Liste (für Notification-Action)
    @Query("SELECT * FROM medications WHERE active = 1")
    suspend fun getAllActiveMedications(): List<MyMedication>

    // Ein einzelnes Medikament per ID laden
    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: Int): MyMedication?

    // Neues Medikament einfügen, gibt neue ID zurück
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(medication: MyMedication): Long

    // Bestehendes Medikament aktualisieren
    @Update
    suspend fun update(medication: MyMedication)

    // Medikament löschen
    @Delete
    suspend fun delete(medication: MyMedication)

    // --- Erinnerungen ---

    // ALLE Reminders als Flow (für Haupt-Screen - Zeiten neben Medikamenten)
    @Query("SELECT * FROM reminders ORDER BY hour, minute")
    fun getAllReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE medicationId = :medicationId ORDER BY hour, minute")
    fun getRemindersForMedication(medicationId: Int): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun getAllEnabledReminders(): List<Reminder>

    @Insert
    suspend fun insertReminder(reminder: Reminder): Long

    @Update
    suspend fun updateReminder(reminder: Reminder)

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    // Alle Reminder eines Medikaments löschen (beim Löschen des Medikaments)
    @Query("DELETE FROM reminders WHERE medicationId = :medicationId")
    suspend fun deleteRemindersForMedication(medicationId: Int)

    // Verwaiste Reminder löschen (deren Medikament nicht mehr existiert)
    @Query("DELETE FROM reminders WHERE medicationId NOT IN (SELECT id FROM medications)")
    suspend fun deleteOrphanReminders()

    // --- History ---

    @Insert
    suspend fun insertHistory(history: MedicationHistory)

    // Alle medicationIds die HEUTE bereits genommen wurden (seit Mitternacht)
    @Query("SELECT medicationId FROM medication_history WHERE takenAt >= :startOfDay AND skipped = 0")
    fun getTakenTodayIds(startOfDay: Long): Flow<List<Int>>

    // Heutigen Eintrag löschen (wenn Checkbox wieder abgehakt wird)
    @Query("DELETE FROM medication_history WHERE medicationId = :medicationId AND takenAt >= :startOfDay")
    suspend fun deleteTodayEntry(medicationId: Int, startOfDay: Long)

    @Query("SELECT * FROM medication_history WHERE medicationId = :medicationId ORDER BY takenAt DESC")
    fun getHistory(medicationId: Int): Flow<List<MedicationHistory>>
}

