package com.example.mymed

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDetailScreen(
    medicationId: Int,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val viewModel: MedicationManagementViewModel = viewModel(
        factory = MedicationManagementViewModel.factory(
            context.applicationContext as android.app.Application,
            db.medicationDao()
        )
    )
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }
    var showReminderSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // ML Kit Scan States
    var scanPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var scanResult by remember { mutableStateOf<ScanResult?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }

    val isEditMode = medicationId != -1

    LaunchedEffect(medicationId) {
        if (isEditMode) {
            viewModel.getById(medicationId)?.let { med ->
                name = med.name
                dosage = med.dosage ?: ""
                notes = med.notes ?: ""
                active = med.active
            }
        }
    }

    // Kamera-Launcher: Foto aufnehmen → ML Kit verarbeiten
    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            scanPhotoUri?.let { uri ->
                isScanning = true
                scanError = null
                MedicationScanHelper.scanImage(
                    context = context,
                    imageUri = uri,
                    onSuccess = { result ->
                        scanResult = result
                        isScanning = false
                        try { File(uri.path ?: "").delete() } catch (_: Exception) {}
                    },
                    onError = { e ->
                        scanError = "Scan fehlgeschlagen: ${e.message}"
                        isScanning = false
                    }
                )
            }
        }
    }

    // Hilfsfunktion: Temp-Datei erstellen und Kamera starten
    fun launchCamera() {
        val tmpFile = File(context.cacheDir, "scan_tmp_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tmpFile
        )
        scanPhotoUri = uri
        takePicture.launch(uri)
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            scanError = "Kamera-Berechtigung verweigert. Bitte in den App-Einstellungen erlauben."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Medikament bearbeiten" else "Neues Medikament") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                },
                actions = {
                    if (isEditMode) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "Löschen", tint = Color.Red)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- ML Kit Scan Button ---
            OutlinedButton(
                onClick = {
                    // Erst Permission prüfen, dann Kamera starten
                    when {
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED -> {
                            // Permission bereits da → direkt starten
                            launchCamera()
                        }
                        else -> {
                            // Permission fehlt → Dialog anzeigen
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !isScanning
            ) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Erkenne Text...", fontSize = 16.sp)
                } else {
                    Icon(Icons.Default.DocumentScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text("📷 Packung scannen (ML Kit)", fontSize = 16.sp)
                }
            }

            // Scan-Fehler anzeigen
            if (scanError != null) {
                Text(
                    text = scanError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }

            HorizontalDivider()

            // --- Formular-Felder ---
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name *") },
                placeholder = { Text("z.B. Aspirin") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = dosage,
                onValueChange = { dosage = it },
                label = { Text("Dosis") },
                placeholder = { Text("z.B. 100mg oder 1 Tablette") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notizen") },
                placeholder = { Text("z.B. nach dem Essen nehmen") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            // Aktiv/Inaktiv Toggle
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aktiv", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text(
                            "Erinnerungen werden angezeigt",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }

            // Erinnerungen Button (nur Edit-Modus)
            if (isEditMode) {
                OutlinedButton(
                    onClick = { showReminderSheet = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.Notifications, null)
                    Spacer(Modifier.width(8.dp))
                    Text("⏰ Erinnerungen verwalten", fontSize = 16.sp)
                }
            }

            // Speichern Button
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val medication = MyMedication(
                        id = if (isEditMode) medicationId else 0,
                        name = name.trim(),
                        dosage = dosage.trim().ifBlank { null },
                        notes = notes.trim().ifBlank { null },
                        active = active
                    )
                    if (isEditMode) viewModel.update(medication)
                    else viewModel.insert(medication)
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = name.isNotBlank()
            ) {
                Text(
                    text = if (isEditMode) "Speichern" else "Hinzufügen",
                    fontSize = 16.sp
                )
            }
        }
    }

    // Löschen Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Medikament löschen?") },
            text = { Text("\"$name\" wird dauerhaft gelöscht.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.getById(medicationId)?.let { viewModel.delete(it) }
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    // Reminder Bottom Sheet
    if (showReminderSheet) {
        ReminderBottomSheet(
            medicationId = medicationId,
            medicationName = name,
            onDismiss = { showReminderSheet = false }
        )
    }

    // ML Kit Review Dialog - erscheint nach erfolgreichem Scan
    scanResult?.let { result ->
        ScanReviewDialog(
            scanResult = result,
            onConfirm = { confirmedName, confirmedDosage, confirmedNotes ->
                // Felder mit erkanntem Text vorausfüllen (nur wenn nicht leer)
                if (confirmedName.isNotBlank()) name = confirmedName
                if (confirmedDosage.isNotBlank()) dosage = confirmedDosage
                if (confirmedNotes.isNotBlank()) notes = confirmedNotes
                scanResult = null
            },
            onDismiss = { scanResult = null }
        )
    }
}

/**
 * Dialog: Zeigt erkannten Text zur Bestätigung/Korrektur
 * Alle Felder sind editierbar bevor übernommen wird
 */
@Composable
fun ScanReviewDialog(
    scanResult: ScanResult,
    onConfirm: (name: String, dosage: String, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var editName by remember { mutableStateOf(scanResult.name) }
    var editDosage by remember { mutableStateOf(scanResult.dosage) }
    var editNotes by remember { mutableStateOf(scanResult.notes) }
    var showRawText by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("📋 Scan-Ergebnis", fontWeight = FontWeight.Bold)
                Text(
                    "Bitte überprüfe und korrigiere die erkannten Felder",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = editDosage,
                    onValueChange = { editDosage = it },
                    label = { Text("Dosis") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = editNotes,
                    onValueChange = { editNotes = it },
                    label = { Text("Notizen") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                // Volltext ein-/ausklappen (zum Nachschauen)
                TextButton(
                    onClick = { showRawText = !showRawText },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (showRawText) "▲ Volltext ausblenden" else "▼ Vollständigen Text anzeigen",
                        fontSize = 12.sp
                    )
                }
                if (showRawText) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = scanResult.rawText.take(500)
                                .let { if (scanResult.rawText.length > 500) "$it..." else it },
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(editName, editDosage, editNotes) }) {
                Text("Übernehmen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Verwerfen") }
        }
    )
}
