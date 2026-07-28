package com.example.mymed

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mymed.ui.theme.MyMedTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startMedicationService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permission + start service (service schedules alarms)
        requestNotificationPermission()

        enableEdgeToEdge()
        setContent {
            MyMedTheme {
                val navController = rememberNavController()
                // isAlarmActive: true when app is opened from an alarm
                var isAlarmActive by remember {
                    mutableStateOf(AlarmSoundManager.isAlarmPlaying())
                }
                AppNavigation(
                    navController = navController,
                    isAlarmActive = isAlarmActive,
                    onStopAlarm = {
                        AlarmSoundManager.stop(this@MainActivity)
                        AlarmNotificationManager.dismissAlarmNotification(this@MainActivity)
                        isAlarmActive = false
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // When returning to app, alarm state is reflected by recomposition
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> startMedicationService()
                else -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startMedicationService()
        }
    }

    private fun startMedicationService() {
        val serviceIntent = Intent(this, MedicationReminderService::class.java)
        startForegroundService(serviceIntent)
    }
}

// --- Navigation setup ---
@Composable
fun AppNavigation(
    navController: NavHostController,
    isAlarmActive: Boolean = false,
    onStopAlarm: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = "reminder"
    ) {
        composable("reminder") {
            MedicationReminderScreen(
                onNavigateToMedications = { navController.navigate("medications") },
                isAlarmActive = isAlarmActive,
                onStopAlarm = onStopAlarm
            )
        }
        composable("medications") {
            MedicationListScreen(
                onNavigateToDetail = { id -> navController.navigate("medication_detail/$id") },
                onNavigateToAdd = { navController.navigate("medication_detail/-1") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "medication_detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: -1
            MedicationDetailScreen(
                medicationId = id,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationReminderScreen(
    onNavigateToMedications: () -> Unit = {},
    isAlarmActive: Boolean = false,
    onStopAlarm: () -> Unit = {}
) {
    // Create ViewModel with DB factory
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val viewModel: MedicationViewModel = viewModel(
        factory = MedicationViewModel.factory(
            dao = db.medicationDao(),
            app = context.applicationContext as android.app.Application
        )
    )

    // StateFlow -> State (Compose reacts automatically to updates)
    val medications by viewModel.medications.collectAsState()
    val snoozeCountToday by viewModel.snoozeCountToday.collectAsState()

    var showSnoozeDialog by remember { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf(getCurrentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            currentTime = getCurrentTime()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MyMed") },
                actions = {
                    IconButton(onClick = onNavigateToMedications) {
                        Icon(Icons.Default.Settings, contentDescription = "Medikamente verwalten")
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        // List is always shown (no separate "done" screen)
        // Taken medications display strikethrough times
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

                // 🔴 STOP button: visible only while alarm is active (sound playing)
                if (isAlarmActive) {
                    Button(
                        onClick = onStopAlarm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = "⏹ Alarm stoppen",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Spacer(modifier = Modifier.height(if (isAlarmActive) 0.dp else 40.dp))

                Text(
                    text = currentTime,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Zeit für deine Medikamente!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (medications.isEmpty()) {
                    // No medications in DB -> show hint
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("💊", fontSize = 48.sp)
                            Text("Keine Medikamente vorhanden", fontSize = 16.sp)
                            Text(
                                "Tippe auf ⚙️ um Medikamente hinzuzufügen",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(medications) { item ->
                            MedicationItem(
                                item = item,
                                onCheckedChange = { viewModel.toggleMedication(item.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Snooze button - shows current duration + counter
                val snoozesLeft = viewModel.maxSnoozeCount - snoozeCountToday
                // All medications already taken today? Then nothing to snooze.
                val allTaken = medications.isNotEmpty() && medications.all { it.isChecked }
                val snoozeButtonEnabled = medications.isNotEmpty() && !allTaken
                val snoozeActive = snoozeButtonEnabled && viewModel.canSnooze
                Button(
                    onClick = {
                        if (viewModel.canSnooze) {
                            viewModel.snooze()
                        } else {
                            showSnoozeDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = snoozeButtonEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (snoozeActive)
                            MaterialTheme.colorScheme.secondary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    when {
                        allTaken -> Text("✓ Heute alles genommen", fontSize = 16.sp)
                        viewModel.canSnooze -> Text(
                            "⏰ Snooze (${viewModel.snoozeMinutes} Min)  •  noch ${snoozesLeft}x",
                            fontSize = 16.sp
                        )
                        else -> Text("⏰ Snooze nicht mehr verfügbar", fontSize = 16.sp)
                    }
                }

                // Settings link for Snooze
                TextButton(onClick = { showSnoozeDialog = true }) {
                    Text(
                        "Snooze-Einstellungen",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // Snooze settings dialog
    if (showSnoozeDialog) {
        SnoozeSettingsDialog(
            currentMinutes = viewModel.snoozeMinutes,
            currentMaxCount = viewModel.maxSnoozeCount,
            snoozeCountToday = snoozeCountToday,
            context = LocalContext.current,
            onReset = { viewModel.resetSnoozeCountToday() },
            onDismiss = {
                viewModel.refreshSnoozeCount()  // Refresh counter after dialog
                showSnoozeDialog = false
            }
        )
    }
}

@Composable
fun SnoozeSettingsDialog(
    currentMinutes: Int,
    currentMaxCount: Int,
    snoozeCountToday: Int,
    context: android.content.Context,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMinutes by remember { mutableIntStateOf(currentMinutes) }
    var selectedMaxCount by remember { mutableIntStateOf(currentMaxCount) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⏰ Snooze-Einstellungen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Snooze-Zeit:", fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SnoozeManager.SNOOZE_OPTIONS.forEach { mins ->
                        FilterChip(
                            selected = selectedMinutes == mins,
                            onClick = { selectedMinutes = mins },
                            label = { Text("${mins}m") }
                        )
                    }
                }
                Text("Maximal pro Tag:", fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SnoozeManager.MAX_COUNT_OPTIONS.forEach { count ->
                        FilterChip(
                            selected = selectedMaxCount == count,
                            onClick = { selectedMaxCount = count },
                            label = { Text("${count}x") }
                        )
                    }
                }

                // Today's counter + reset button
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Heute: ${snoozeCountToday}x von ${selectedMaxCount}x",
                        fontSize = 13.sp,
                        color = if (snoozeCountToday >= selectedMaxCount)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (snoozeCountToday > 0) {
                        TextButton(onClick = onReset) {
                            Text("Zurücksetzen", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                SnoozeManager.setSnoozeMinutes(context, selectedMinutes)
                SnoozeManager.setMaxSnoozeCount(context, selectedMaxCount)
                onDismiss()
            }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
fun MedicationItem(
    item: MedicationCheckItem,
    onCheckedChange: (Boolean) -> Unit
) {
    // Taken medications are displayed with reduced emphasis
    val contentAlpha = if (item.isChecked) 0.5f else 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isChecked)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = onCheckedChange
            )
            Spacer(modifier = Modifier.width(12.dp))

            // Name + dosage (left, takes remaining space)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    // Strike through name when taken
                    textDecoration = if (item.isChecked)
                        androidx.compose.ui.text.style.TextDecoration.LineThrough
                    else null
                )
                val dosage = item.dosage
                if (!dosage.isNullOrBlank()) {
                    Text(
                        text = dosage, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f * contentAlpha)
                    )
                }
            }

            // Alarm times (right) - strikethrough when taken
            if (item.reminderTimes.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.End) {
                    item.reminderTimes.forEach { time ->
                        Text(
                            text = time,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (item.isChecked)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            else
                                MaterialTheme.colorScheme.primary,
                            textDecoration = if (item.isChecked)
                                androidx.compose.ui.text.style.TextDecoration.LineThrough
                            else null
                        )
                    }
                }
            }
        }
    }
}

fun getCurrentTime(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.GERMAN)
    return sdf.format(Date())
}

@Preview(showBackground = true)
@Composable
fun MedicationReminderPreview() {
    MyMedTheme {
        MedicationReminderScreen()
    }
}