package com.example.mymed

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    // --- Medications ---

    // All medications as Flow (updates automatically on DB changes)
    @Query("SELECT * FROM medications ORDER BY name")
    fun getAllMedications(): Flow<List<MyMedication>>

    // All active medications as a plain list (for notification action)
    @Query("SELECT * FROM medications WHERE active = 1")
    suspend fun getAllActiveMedications(): List<MyMedication>

    // Load a single medication by ID
    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: Int): MyMedication?

    // Insert new medication, returns generated ID
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(medication: MyMedication): Long

    // Update existing medication
    @Update
    suspend fun update(medication: MyMedication)

    // Delete medication
    @Delete
    suspend fun delete(medication: MyMedication)

    // --- Reminders ---

    // ALL reminders as Flow (for main screen - times next to medications)
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

    // Delete all reminders of a medication (when deleting the medication)
    @Query("DELETE FROM reminders WHERE medicationId = :medicationId")
    suspend fun deleteRemindersForMedication(medicationId: Int)

    // Delete orphan reminders (their medication no longer exists)
    @Query("DELETE FROM reminders WHERE medicationId NOT IN (SELECT id FROM medications)")
    suspend fun deleteOrphanReminders()

    // --- History ---

    @Insert
    suspend fun insertHistory(history: MedicationHistory)

    // All medication IDs already taken TODAY (since midnight)
    @Query("SELECT medicationId FROM medication_history WHERE takenAt >= :startOfDay AND skipped = 0")
    fun getTakenTodayIds(startOfDay: Long): Flow<List<Int>>

    // Delete today's entry (when checkbox is unchecked)
    @Query("DELETE FROM medication_history WHERE medicationId = :medicationId AND takenAt >= :startOfDay")
    suspend fun deleteTodayEntry(medicationId: Int, startOfDay: Long)

    @Query("SELECT * FROM medication_history WHERE medicationId = :medicationId ORDER BY takenAt DESC")
    fun getHistory(medicationId: Int): Flow<List<MedicationHistory>>
}

