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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Calendar

// Wrapper: medication from DB + whether it was taken TODAY + its reminder times
data class MedicationCheckItem(
    val medication: MyMedication,
    val isChecked: Boolean = false,
    val reminders: List<Reminder> = emptyList()  // New: reminder times for this medication
) {
    val id: Int get() = medication.id
    val name: String get() = medication.name
    val dosage: String? get() = medication.dosage

    // Formatted time list for the UI: ["07:00", "19:00"]
    val reminderTimes: List<String> get() = reminders
        .filter { it.enabled }
        .sortedWith(compareBy({ it.hour }, { it.minute }))
        .map { "%02d:%02d".format(it.hour, it.minute) }
}

class MedicationViewModel(
    private val dao: MedicationDao,
    private val app: Application
) : AndroidViewModel(app) {

    // Calculates midnight for today (start of day)
    private fun startOfToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Combines: active meds + taken-today state + reminder times
    val medications: StateFlow<List<MedicationCheckItem>> = combine(
        dao.getAllMedications().map { list -> list.filter { it.active } },
        dao.getTakenTodayIds(startOfToday()),
        dao.getAllReminders()          // all reminders from DB
    ) { activeMeds, takenTodayIds, allReminders ->
        activeMeds.map { med ->
            MedicationCheckItem(
                medication = med,
                isChecked = takenTodayIds.contains(med.id),
                reminders = allReminders.filter { it.medicationId == med.id }
            )
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
            val alreadyTaken = medications.value.find { it.id == id }?.isChecked ?: false
            if (alreadyTaken) {
                // Delete today's history entry (uncheck)
                dao.deleteTodayEntry(id, startOfToday())
            } else {
                // Insert new history entry (check)
                dao.insertHistory(
                    MedicationHistory(
                        medicationId = id,
                        takenAt = System.currentTimeMillis()
                    )
                )
                // If this makes ALL medications taken:
                // cancel pending snooze alarm (nothing left to remind)
                val nowAllTaken = medications.value.all {
                    it.id == id || it.isChecked
                }
                if (nowAllTaken) {
                    SnoozeManager.cancelSnooze(app)
                }
            }
        }
    }

    // Snooze status
    private val _snoozeCountToday = kotlinx.coroutines.flow.MutableStateFlow(
        SnoozeManager.getSnoozeCountToday(app)
    )
    val snoozeCountToday: StateFlow<Int> = _snoozeCountToday
    val maxSnoozeCount: Int get() = SnoozeManager.getMaxSnoozeCount(app)
    val snoozeMinutes: Int get() = SnoozeManager.getSnoozeMinutes(app)
    val canSnooze: Boolean get() = SnoozeManager.canSnoozeToday(app)

    // Refresh counter from SharedPreferences (e.g., after midnight or settings dialog)
    fun refreshSnoozeCount() {
        _snoozeCountToday.value = SnoozeManager.getSnoozeCountToday(app)
    }

    // Reset snooze counter for today (e.g., from settings dialog)
    fun resetSnoozeCountToday() {
        SnoozeManager.resetTodayCount(app)
        _snoozeCountToday.value = 0
    }

    fun snooze() {
        // 1. Schedule alarm (handled by SnoozeManager)
        val success = SnoozeManager.snooze(app)
        if (!success) return  // Max count reached

        // 2. Reset checkboxes (delete today's history)
        viewModelScope.launch {
            medications.value.forEach { item ->
                dao.deleteTodayEntry(item.id, startOfToday())
            }
            // 3. Update counter so UI reacts immediately
            _snoozeCountToday.value = SnoozeManager.getSnoozeCountToday(app)
        }
    }

    fun resetReminder() {
        // Reset: delete all today's entries
        viewModelScope.launch {
            medications.value.forEach { item ->
                dao.deleteTodayEntry(item.id, startOfToday())
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


