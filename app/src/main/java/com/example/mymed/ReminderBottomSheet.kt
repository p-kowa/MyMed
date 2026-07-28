package com.example.mymed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// Helper: "1,2,3,4,5" -> "Mo Di Mi Do Fr"
fun formatDaysOfWeek(daysOfWeek: String): String {
    val names = mapOf(1 to "Mo", 2 to "Di", 3 to "Mi", 4 to "Do", 5 to "Fr", 6 to "Sa", 7 to "So")
    if (daysOfWeek == "1,2,3,4,5,6,7") return "Täglich"
    if (daysOfWeek == "1,2,3,4,5") return "Mo–Fr"
    if (daysOfWeek == "6,7") return "Wochenende"
    return daysOfWeek.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .mapNotNull { names[it] }
        .joinToString(" ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderBottomSheet(
    medicationId: Int,
    medicationName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val viewModel: MedicationManagementViewModel = viewModel(
        factory = MedicationManagementViewModel.factory(
            context.applicationContext as android.app.Application,
            db.medicationDao()
        )
    )

    // Reminder list for this medication as reactive Flow
    val reminders by viewModel.getRemindersForMedication(medicationId)
        .collectAsState(initial = emptyList())

    // Controls "new reminder" dialog visibility
    var showAddDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Title
            Text(
                text = "⏰ Erinnerungen",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = medicationName,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(16.dp))

            if (reminders.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Noch keine Erinnerungen.\nTippe auf + um eine hinzuzufügen.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                // Existing reminders list
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(reminders) { reminder ->
                        ReminderItem(
                            reminder = reminder,
                            onToggleEnabled = {
                                viewModel.updateReminder(reminder.copy(enabled = !reminder.enabled))
                            },
                            onDelete = { viewModel.deleteReminder(reminder) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Button: add new reminder
            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Neue Erinnerung hinzufügen")
            }
        }
    }

    // Dialog for new reminder
    if (showAddDialog) {
        AddReminderDialog(
            onConfirm = { hour, minute, daysOfWeek ->
                viewModel.insertReminder(
                    Reminder(
                        medicationId = medicationId,
                        hour = hour,
                        minute = minute,
                        daysOfWeek = daysOfWeek
                    )
                )
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

// Single reminder row
@Composable
fun ReminderItem(
    reminder: Reminder,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (reminder.enabled)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show time prominently
            Text(
                text = "%02d:%02d".format(reminder.hour, reminder.minute),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(80.dp)
            )

            // Weekdays
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDaysOfWeek(reminder.daysOfWeek),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Active/inactive toggle
            Switch(
                checked = reminder.enabled,
                onCheckedChange = { onToggleEnabled() }
            )

            Spacer(Modifier.width(4.dp))

            // Delete
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Löschen", tint = Color.Red.copy(alpha = 0.7f))
            }
        }
    }
}

// Dialog: configure a new reminder
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderDialog(
    onConfirm: (hour: Int, minute: Int, daysOfWeek: String) -> Unit,
    onDismiss: () -> Unit
) {
    // TimePicker state (24-hour format)
    val timePickerState = rememberTimePickerState(
        initialHour = 7,
        initialMinute = 0,
        is24Hour = true
    )

    // Weekday state: which days are selected?
    val dayLabels = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    var selectedDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6, 7)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Erinnerung") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // TimePicker (Material3)
                TimePicker(state = timePickerState)

                // Select weekdays
                Text("Wochentage:", fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    dayLabels.forEachIndexed { index, label ->
                        val dayNum = index + 1
                        DayToggleButton(
                            label = label,
                            selected = dayNum in selectedDays,
                            onClick = {
                                selectedDays = if (dayNum in selectedDays)
                                    selectedDays - dayNum
                                else
                                    selectedDays + dayNum
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val daysString = if (selectedDays.isEmpty()) "1,2,3,4,5,6,7"
                    else selectedDays.sorted().joinToString(",")
                    onConfirm(timePickerState.hour, timePickerState.minute, daysString)
                },
                enabled = selectedDays.isNotEmpty()
            ) {
                Text("Hinzufügen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

// Compact round day toggle button (fits all 7 days in one row)
@Composable
fun DayToggleButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

