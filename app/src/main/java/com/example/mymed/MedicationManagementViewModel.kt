package com.example.mymed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class MedicationManagementViewModel(
    application: Application,
    private val dao: MedicationDao
) : AndroidViewModel(application) {

    val medications: StateFlow<List<MyMedication>> = dao.getAllMedications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Medication CRUD with error handling ---
    fun insert(medication: MyMedication) {
        viewModelScope.launch {
            try {
                dao.insert(medication)
                Timber.d("Medication inserted: ${medication.name}")
            } catch (e: Exception) {
                Timber.e(e, "Error inserting medication")
            }
        }
    }

    fun update(medication: MyMedication) {
        viewModelScope.launch {
            try {
                dao.update(medication)
                Timber.d("Medication updated: ${medication.name}")
            } catch (e: Exception) {
                Timber.e(e, "Error updating medication")
            }
        }
    }

    fun delete(medication: MyMedication) {
        viewModelScope.launch {
            try {
                // Delete this medication's reminders first (avoid orphans)
                dao.deleteRemindersForMedication(medication.id)
                dao.delete(medication)
                rescheduleAlarms()
                Timber.d("Medication deleted: ${medication.name}")
            } catch (e: Exception) {
                Timber.e(e, "Error deleting medication")
            }
        }
    }

    suspend fun getById(id: Int): MyMedication? = try {
        dao.getById(id)
    } catch (e: Exception) {
        Timber.e(e, "Error fetching medication by id: $id")
        null
    }

    // --- Reminder CRUD with error handling ---
    fun getRemindersForMedication(medicationId: Int) =
        dao.getRemindersForMedication(medicationId)

    fun insertReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                dao.insertReminder(reminder)
                rescheduleAlarms()
                Timber.d("Reminder inserted for medication ${reminder.medicationId} at ${reminder.hour}:${String.format("%02d", reminder.minute)}")
            } catch (e: Exception) {
                Timber.e(e, "Error inserting reminder")
            }
        }
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                dao.updateReminder(reminder)
                rescheduleAlarms()
                Timber.d("Reminder updated for medication ${reminder.medicationId} at ${reminder.hour}:${String.format("%02d", reminder.minute)}")
            } catch (e: Exception) {
                Timber.e(e, "Error updating reminder")
            }
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                dao.deleteReminder(reminder)
                rescheduleAlarms()
                Timber.d("Reminder deleted for medication ${reminder.medicationId}")
            } catch (e: Exception) {
                Timber.e(e, "Error deleting reminder")
            }
        }
    }

    private suspend fun rescheduleAlarms() {
        try {
            AlarmScheduler.rescheduleFromDb(getApplication())
            Timber.d("Alarms rescheduled")
        } catch (e: Exception) {
            Timber.e(e, "Error rescheduling alarms")
        }
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
