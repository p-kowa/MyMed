package com.example.mymed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReminderTimeItem(
    val reminderId: Int,
    val time: String,
    val isTakenToday: Boolean
)

// Wrapper: active medication + today's relevant reminder rows + whether all of
// today's reminder rows are already taken.
data class MedicationCheckItem(
    val medication: MyMedication,
    val reminders: List<Reminder> = emptyList()
) {
    val id: Int get() = medication.id
    val name: String get() = medication.name
    val dosage: String? get() = medication.dosage
    val isChecked: Boolean get() = reminders.isNotEmpty() && reminders.all { it.isTakenToday() }

    val reminderTimes: List<ReminderTimeItem> get() = reminders
        .sortedWith(compareBy({ it.hour }, { it.minute }))
        .map {
            ReminderTimeItem(
                reminderId = it.id,
                time = "%02d:%02d".format(it.hour, it.minute),
                isTakenToday = it.isTakenToday()
            )
        }
}

class MedicationViewModel(
    private val dao: MedicationDao,
    private val app: Application
) : AndroidViewModel(app) {

    // Combines: active meds + today's relevant reminder times.
    // A medication is only shown if at least one enabled reminder is scheduled
    // for TODAY. Each reminder row carries its own taken/not-taken state.
    val medications: StateFlow<List<MedicationCheckItem>> = combine(
        dao.getAllMedications().map { list -> list.filter { it.active } },
        dao.getAllReminders()
    ) { activeMeds, allReminders ->
        activeMeds.mapNotNull { med ->
            val todaysReminders = allReminders
                .filter { it.medicationId == med.id && it.enabled && it.isScheduledForToday() }

            if (todaysReminders.isEmpty()) {
                null
            } else {
                MedicationCheckItem(
                    medication = med,
                    reminders = todaysReminders
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Whether the reminder screen should be visible
    // true as long as not all medications are taken
    val isReminderVisible: StateFlow<Boolean> = medications
        .map { list ->
            // Visible if list is empty OR not all are checked
            list.isEmpty() || !list.all { it.isChecked }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    fun toggleMedication(id: Int) {
        viewModelScope.launch {
            val item = medications.value.find { it.id == id } ?: return@launch
            val now = System.currentTimeMillis()

            if (item.isChecked) {
                item.reminders.forEach { reminder ->
                    dao.resetReminderTaken(reminder.id)
                }
            } else {
                item.reminders
                    .filterNot { it.isTakenToday(now) }
                    .forEach { reminder ->
                        dao.markReminderTaken(reminder.id, now)
                    }

                val nowAllTaken = medications.value.all {
                    it.id == id || it.isChecked
                }
                if (nowAllTaken) {
                    SnoozeManager.cancelSnooze(app)
                }
            }
        }
    }

    fun snooze() {
        SnoozeManager.snooze(app)
    }

    fun markCurrentAlarmTaken() {
        viewModelScope.launch {
            val ringingIds = RingingMedicationsTracker.getCurrentReminderIds(app)
            if (ringingIds.isEmpty()) return@launch

            val now = System.currentTimeMillis()
            ringingIds.forEach { reminderId ->
                dao.markReminderTaken(reminderId, now)
            }
            RingingMedicationsTracker.clear(app)
            SnoozeManager.cancelSnooze(app)
        }
    }

    fun resetReminder() {
        viewModelScope.launch {
            medications.value.forEach { item ->
                item.reminders.forEach { reminder ->
                    dao.resetReminderTaken(reminder.id)
                }
            }
        }
    }

    companion object {
        fun factory(dao: MedicationDao, app: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return MedicationViewModel(dao, app) as T
                }
            }
        }
    }
}


