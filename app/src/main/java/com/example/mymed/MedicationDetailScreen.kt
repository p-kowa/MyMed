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
import androidx.compose.ui.res.stringResource
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

    // ML Kit scan states
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

    // Camera launcher: take photo -> process with ML Kit
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
                        scanError = context.getString(R.string.detail_scan_error, e.message ?: "")
                        isScanning = false
                    }
                )
            }
        }
    }

    // Helper: create temp file and launch camera
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

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            scanError = context.getString(R.string.detail_camera_permission_denied)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) stringResource(R.string.detail_title_edit) else stringResource(R.string.detail_title_new)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (isEditMode) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.common_delete), tint = Color.Red)
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

            // --- ML Kit scan button ---
            OutlinedButton(
                onClick = {
                    // First check permission, then launch camera
                    when {
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED -> {
                            // Permission already granted -> start immediately
                            launchCamera()
                        }
                        else -> {
                            // Permission missing -> show system dialog
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
                    Text(stringResource(R.string.detail_scan_processing), fontSize = 16.sp)
                } else {
                    Icon(Icons.Default.DocumentScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.detail_scan_button), fontSize = 16.sp)
                }
            }

            // Show scan error
            if (scanError != null) {
                Text(
                    text = scanError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }

            HorizontalDivider()

            // --- Form fields ---
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.detail_name_required)) },
                placeholder = { Text(stringResource(R.string.detail_name_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = dosage,
                onValueChange = { dosage = it },
                label = { Text(stringResource(R.string.detail_dose)) },
                placeholder = { Text(stringResource(R.string.detail_dose_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.detail_notes)) },
                placeholder = { Text(stringResource(R.string.detail_notes_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            // Active/inactive toggle
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.detail_active), fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text(
                            stringResource(R.string.detail_active_subtitle),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }

            // Reminders button (edit mode only)
            if (isEditMode) {
                OutlinedButton(
                    onClick = { showReminderSheet = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.Notifications, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.detail_manage_reminders), fontSize = 16.sp)
                }
            }

            // Save button
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
                    text = if (isEditMode) stringResource(R.string.common_save) else stringResource(R.string.common_add),
                    fontSize = 16.sp
                )
            }
        }
    }

    // Delete dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_text, name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.getById(medicationId)?.let { viewModel.delete(it) }
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // Reminder bottom sheet
    if (showReminderSheet) {
        ReminderBottomSheet(
            medicationId = medicationId,
            medicationName = name,
            onDismiss = { showReminderSheet = false }
        )
    }

     // ML Kit review dialog - appears after successful scan
     scanResult?.let { result ->
         ScanReviewDialog(
             scanResult = result,
             dao = db.medicationDao(),
             onConfirm = { confirmedName, confirmedDosage, confirmedNotes ->
                 // Pre-fill fields with recognized text (only if non-empty)
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
 * Dialog: Shows recognized text for confirmation/correction with AutoComplete for medication names.
 * All fields are editable before applying.
 */
@Composable
fun ScanReviewDialog(
     scanResult: ScanResult,
     dao: MedicationDao,
     onConfirm: (name: String, dosage: String, notes: String) -> Unit,
     onDismiss: () -> Unit
) {
     var editName by remember { mutableStateOf(scanResult.name) }
     var editDosage by remember { mutableStateOf(scanResult.dosage) }
     var editNotes by remember { mutableStateOf(scanResult.notes) }
     var showRawText by remember { mutableStateOf(false) }
     var nameSuggestions by remember { mutableStateOf(listOf<String>()) }
     var showNameSuggestions by remember { mutableStateOf(false) }

     val scope = rememberCoroutineScope()

     // Load name suggestions when user types
     val onNameChange: (String) -> Unit = { newName ->
         editName = newName
         showNameSuggestions = newName.length > 1
         if (newName.length > 1) {
             scope.launch {
                 nameSuggestions = dao.searchMedicationNames(newName)
             }
         } else {
             nameSuggestions = emptyList()
         }
     }

     AlertDialog(
         onDismissRequest = onDismiss,
         title = {
             Column {
                 Text(stringResource(R.string.scan_review_title), fontWeight = FontWeight.Bold)
                 Text(
                     stringResource(R.string.scan_review_subtitle),
                     fontSize = 12.sp,
                     color = Color.Gray
                 )
             }
         },
         text = {
             Column(
                 verticalArrangement = Arrangement.spacedBy(12.dp)
             ) {
                 // Name field with AutoComplete dropdown
                 Box(modifier = Modifier.fillMaxWidth()) {
                     OutlinedTextField(
                         value = editName,
                         onValueChange = onNameChange,
                         label = { Text(stringResource(R.string.detail_name_required).removeSuffix(" *")) },
                         modifier = Modifier.fillMaxWidth(),
                         singleLine = true
                     )
                     
                     // AutoComplete dropdown
                     if (showNameSuggestions && nameSuggestions.isNotEmpty()) {
                         Surface(
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .padding(top = 48.dp),
                             shape = MaterialTheme.shapes.small,
                             color = MaterialTheme.colorScheme.surface,
                             shadowElevation = 8.dp
                         ) {
                             Column(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .padding(8.dp)
                             ) {
                                 nameSuggestions.forEach { suggestion ->
                                     TextButton(
                                         onClick = {
                                             editName = suggestion
                                             showNameSuggestions = false
                                             nameSuggestions = emptyList()
                                         },
                                         modifier = Modifier.fillMaxWidth()
                                     ) {
                                         Text(
                                             text = suggestion,
                                             modifier = Modifier.fillMaxWidth(),
                                             color = MaterialTheme.colorScheme.onSurface
                                         )
                                     }
                                 }
                             }
                         }
                     }
                 }

                 OutlinedTextField(
                     value = editDosage,
                     onValueChange = { editDosage = it },
                     label = { Text(stringResource(R.string.detail_dose)) },
                     modifier = Modifier.fillMaxWidth(),
                     singleLine = true
                 )
                 OutlinedTextField(
                     value = editNotes,
                     onValueChange = { editNotes = it },
                     label = { Text(stringResource(R.string.detail_notes)) },
                     modifier = Modifier.fillMaxWidth(),
                     minLines = 2
                 )

                 // Expand/collapse full text for reference
                 TextButton(
                     onClick = { showRawText = !showRawText },
                     modifier = Modifier.fillMaxWidth()
                 ) {
                     Text(
                         if (showRawText) stringResource(R.string.scan_hide_full_text) else stringResource(R.string.scan_show_full_text),
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
                 Text(stringResource(R.string.scan_apply))
             }
         },
         dismissButton = {
             TextButton(onClick = onDismiss) { Text(stringResource(R.string.scan_discard)) }
         }
     )
}
