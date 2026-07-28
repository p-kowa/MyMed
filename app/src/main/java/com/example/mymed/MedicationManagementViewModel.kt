package com.example.mymed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MedicationManagementViewModel(
    application: Application,
    private val dao: MedicationDao
) : AndroidViewModel(application) {

    val medications: StateFlow<List<MyMedication>> = dao.getAllMedications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Medication CRUD ---
    fun insert(medication: MyMedication) { viewModelScope.launch { dao.insert(medication) } }
    fun update(medication: MyMedication) { viewModelScope.launch { dao.update(medication) } }
    fun delete(medication: MyMedication) {
        viewModelScope.launch {
            // Delete this medication's reminders first (avoid orphans)
            dao.deleteRemindersForMedication(medication.id)
            dao.delete(medication)
            rescheduleAlarms()
        }
    }
    suspend fun getById(id: Int): MyMedication? = dao.getById(id)

    // --- Reminder CRUD ---
    fun getRemindersForMedication(medicationId: Int) =
        dao.getRemindersForMedication(medicationId)

    fun insertReminder(reminder: Reminder) {
        viewModelScope.launch { dao.insertReminder(reminder); rescheduleAlarms() }
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch { dao.updateReminder(reminder); rescheduleAlarms() }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch { dao.deleteReminder(reminder); rescheduleAlarms() }
    }

    private suspend fun rescheduleAlarms() {
        AlarmScheduler.rescheduleFromDb(getApplication())
    }

    companion object {
        fun factory(application: Application, dao: MedicationDao): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return MedicationManagementViewModel(application, dao) as T
                }
            }
        }
    }
}
