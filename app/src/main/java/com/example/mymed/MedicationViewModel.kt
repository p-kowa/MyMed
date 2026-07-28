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

// Wrapper: Medikament aus DB + ob es HEUTE bereits genommen wurde + seine Erinnerungszeiten
data class MedicationCheckItem(
    val medication: MyMedication,
    val isChecked: Boolean = false,
    val reminders: List<Reminder> = emptyList()  // ← NEU: Alarm-Zeiten dieses Medikaments
) {
    val id: Int get() = medication.id
    val name: String get() = medication.name
    val dosage: String? get() = medication.dosage

    // Formatierte Zeitliste für die UI: ["07:00", "19:00"]
    val reminderTimes: List<String> get() = reminders
        .filter { it.enabled }
        .sortedWith(compareBy({ it.hour }, { it.minute }))
        .map { "%02d:%02d".format(it.hour, it.minute) }
}

class MedicationViewModel(
    private val dao: MedicationDao,
    private val app: Application
) : AndroidViewModel(app) {

    // Berechnet Mitternacht heute (Beginn des Tages)
    private fun startOfToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Kombiniert: aktive Medis + ob heute genommen + Erinnerungszeiten
    val medications: StateFlow<List<MedicationCheckItem>> = combine(
        dao.getAllMedications().map { list -> list.filter { it.active } },
        dao.getTakenTodayIds(startOfToday()),
        dao.getAllReminders()          // ← alle Reminders aus DB
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

    // Ob der Reminder-Screen sichtbar ist
    // true solange noch nicht alle genommen wurden
    val isReminderVisible: StateFlow<Boolean> = medications
        .map { list ->
            // Sichtbar wenn: Liste leer ODER noch nicht alle gecheckt
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
                // Heutigen Eintrag aus History löschen (uncheckzen)
                dao.deleteTodayEntry(id, startOfToday())
            } else {
                // Neuen History-Eintrag schreiben (checkzen)
                dao.insertHistory(
                    MedicationHistory(
                        medicationId = id,
                        takenAt = System.currentTimeMillis()
                    )
                )
                // Wenn dadurch ALLE Medikamente genommen wurden:
                // pendenten Snooze-Alarm abbrechen (nichts mehr zu erinnern)
                val nowAllTaken = medications.value.all {
                    it.id == id || it.isChecked
                }
                if (nowAllTaken) {
                    SnoozeManager.cancelSnooze(app)
                }
            }
        }
    }

    // Snooze-Status
    private val _snoozeCountToday = kotlinx.coroutines.flow.MutableStateFlow(
        SnoozeManager.getSnoozeCountToday(app)
    )
    val snoozeCountToday: StateFlow<Int> = _snoozeCountToday
    val maxSnoozeCount: Int get() = SnoozeManager.getMaxSnoozeCount(app)
    val snoozeMinutes: Int get() = SnoozeManager.getSnoozeMinutes(app)
    val canSnooze: Boolean get() = SnoozeManager.canSnoozeToday(app)

    // Zähler aus SharedPreferences neu lesen (z.B. nach Mitternacht oder nach Settings-Dialog)
    fun refreshSnoozeCount() {
        _snoozeCountToday.value = SnoozeManager.getSnoozeCountToday(app)
    }

    // Snooze-Zähler für heute zurücksetzen (z.B. im Settings-Dialog)
    fun resetSnoozeCountToday() {
        SnoozeManager.resetTodayCount(app)
        _snoozeCountToday.value = 0
    }

    fun snooze() {
        // 1. Alarm planen (SnoozeManager übernimmt das)
        val success = SnoozeManager.snooze(app)
        if (!success) return  // Max-Anzahl erreicht

        // 2. Checkboxen zurücksetzen (History löschen)
        viewModelScope.launch {
            medications.value.forEach { item ->
                dao.deleteTodayEntry(item.id, startOfToday())
            }
            // 3. Zähler aktualisieren damit UI sofort reagiert
            _snoozeCountToday.value = SnoozeManager.getSnoozeCountToday(app)
        }
    }

    fun resetReminder() {
        // Zurücksetzen: alle heutigen Einträge löschen
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


