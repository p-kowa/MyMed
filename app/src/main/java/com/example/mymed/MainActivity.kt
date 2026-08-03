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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
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

    private var isAlarmActiveState by mutableStateOf(false)

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
                val isAlarmActive = isAlarmActiveState
                AppNavigation(
                    navController = navController,
                    isAlarmActive = isAlarmActive,
                    onDismissAlarm = {
                        AlarmSoundManager.stop(this@MainActivity)
                        AlarmNotificationManager.dismissAlarmNotification(this@MainActivity)
                        isAlarmActiveState = false
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isAlarmActiveState = AlarmSoundManager.isAlarmPlaying()
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
    onDismissAlarm: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = "reminder"
    ) {
        composable("reminder") {
            MedicationReminderScreen(
                onNavigateToMedications = { navController.navigate("medications") },
                onNavigateToAlarmSettings = { navController.navigate("alarm_settings") },
                isAlarmActive = isAlarmActive,
                onDismissAlarm = onDismissAlarm
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
        composable("alarm_settings") {
            AlarmSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationReminderScreen(
    onNavigateToMedications: () -> Unit = {},
    onNavigateToAlarmSettings: () -> Unit = {},
    isAlarmActive: Boolean = false,
    onDismissAlarm: () -> Unit = {}
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
    var currentTime by remember { mutableStateOf(getCurrentTime()) }
    var localAlarmActive by remember { mutableStateOf(isAlarmActive) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(isAlarmActive) {
        localAlarmActive = isAlarmActive
    }

    // Falls der Alarm startet waehrend die App schon offen ist.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500L)
            localAlarmActive = AlarmSoundManager.isAlarmPlaying()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            currentTime = getCurrentTime()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_title)) },
                actions = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_open_settings))
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

                // Alarmmodus: genau zwei Aktionen wie beim Wecker.
                if (localAlarmActive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                onDismissAlarm()
                                viewModel.snooze()
                                localAlarmActive = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                text = stringResource(R.string.main_alarm_snooze),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = {
                                onDismissAlarm()
                                viewModel.markCurrentAlarmTaken()
                                localAlarmActive = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                text = stringResource(R.string.main_alarm_taken),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Spacer(modifier = Modifier.height(if (localAlarmActive) 0.dp else 40.dp))

                Text(
                    text = currentTime,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.main_time_for_medications),
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
                            Text(stringResource(R.string.main_no_medications), fontSize = 16.sp)
                            Text(
                                stringResource(R.string.main_add_medications_hint),
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

                val allTaken = medications.isNotEmpty() && medications.all { it.isChecked }
                if (allTaken && !localAlarmActive) {
                    Text(stringResource(R.string.main_all_taken_today), fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false }
        ) {
            Text(
                text = stringResource(R.string.settings_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_menu_medications_title)) },
                supportingContent = { Text(stringResource(R.string.settings_menu_medications_subtitle)) },
                leadingContent = {
                    Icon(
                        Icons.Default.Medication,
                        contentDescription = null
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showSettingsSheet = false
                        onNavigateToMedications()
                    },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_menu_alarm_title)) },
                supportingContent = { Text(stringResource(R.string.settings_menu_alarm_subtitle)) },
                leadingContent = {
                    Icon(
                        Icons.Default.Alarm,
                        contentDescription = null
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showSettingsSheet = false
                        onNavigateToAlarmSettings()
                    },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

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

            // Today's reminder times (right) - each time has its own taken-state
            if (item.reminderTimes.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.End) {
                    item.reminderTimes.forEach { reminderTime ->
                        Text(
                            text = reminderTime.time,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (reminderTime.isTakenToday)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            else
                                MaterialTheme.colorScheme.primary,
                            textDecoration = if (reminderTime.isTakenToday)
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