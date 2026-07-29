package com.example.mymed

import android.content.Context
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// Helper: "1,2,3,4,5" -> localized weekday text
fun formatDaysOfWeek(context: Context, daysOfWeek: String): String {
    val names = mapOf(
        1 to context.getString(R.string.day_mon),
        2 to context.getString(R.string.day_tue),
        3 to context.getString(R.string.day_wed),
        4 to context.getString(R.string.day_thu),
        5 to context.getString(R.string.day_fri),
        6 to context.getString(R.string.day_sat),
        7 to context.getString(R.string.day_sun)
    )
    if (daysOfWeek == "1,2,3,4,5,6,7") return context.getString(R.string.days_daily)
    if (daysOfWeek == "1,2,3,4,5") return context.getString(R.string.days_workdays)
    if (daysOfWeek == "6,7") return context.getString(R.string.days_weekend)
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
                text = stringResource(R.string.reminder_sheet_title),
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
                        stringResource(R.string.reminder_sheet_empty),
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
                Text(stringResource(R.string.reminder_add_new))
            }
        }
    }

    // Dialog for new reminder
    if (showAddDialog) {
        AddReminderDialog(
            onConfirm = { hour, minute, daysOfWeek, snoozeMinutes ->
                viewModel.insertReminder(
                    Reminder(
                        medicationId = medicationId,
                        hour = hour,
                        minute = minute,
                        daysOfWeek = daysOfWeek,
                        snoozeMinutes = snoozeMinutes
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
    val context = LocalContext.current
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
                    text = formatDaysOfWeek(context, reminder.daysOfWeek),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = stringResource(R.string.reminder_snooze_info, reminder.snoozeMinutes),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                Icon(Icons.Default.Delete, stringResource(R.string.cd_delete_reminder), tint = Color.Red.copy(alpha = 0.7f))
            }
        }
    }
}

// Dialog: configure a new reminder
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderDialog(
    onConfirm: (hour: Int, minute: Int, daysOfWeek: String, snoozeMinutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    // TimePicker state (24-hour format)
    val timePickerState = rememberTimePickerState(
        initialHour = 7,
        initialMinute = 0,
        is24Hour = true
    )

    // Weekday state: which days are selected?
    val dayLabels = listOf(
        stringResource(R.string.day_mon),
        stringResource(R.string.day_tue),
        stringResource(R.string.day_wed),
        stringResource(R.string.day_thu),
        stringResource(R.string.day_fri),
        stringResource(R.string.day_sat),
        stringResource(R.string.day_sun)
    )
    var selectedDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6, 7)) }
    var selectedSnoozeMinutes by remember { mutableIntStateOf(SnoozeManager.DEFAULT_SNOOZE_MINUTES) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // TimePicker (Material3)
                TimePicker(state = timePickerState)

                // Select weekdays
                Text(stringResource(R.string.reminder_weekdays), fontWeight = FontWeight.Medium)
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

                Text(stringResource(R.string.reminder_snooze_interval), fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SnoozeManager.SNOOZE_OPTIONS.forEach { mins ->
                        FilterChip(
                            selected = selectedSnoozeMinutes == mins,
                            onClick = { selectedSnoozeMinutes = mins },
                            label = { Text("${mins}m") }
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
                    onConfirm(timePickerState.hour, timePickerState.minute, daysString, selectedSnoozeMinutes)
                },
                enabled = selectedDays.isNotEmpty()
            ) {
                Text(stringResource(R.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
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

