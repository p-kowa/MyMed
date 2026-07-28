package com.example.mymed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationListScreen(
    onNavigateToDetail: (Int) -> Unit,   // ID des Medikaments
    onNavigateToAdd: () -> Unit,          // Neues Medikament
    onNavigateBack: () -> Unit            // Zurück zur Hauptseite
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val viewModel: MedicationManagementViewModel = viewModel(
        factory = MedicationManagementViewModel.factory(
            context.applicationContext as android.app.Application,
            db.medicationDao()
        )
    )

    // Holt die Medikamenten-Liste aus der DB (Flow → State)
    val medications by viewModel.medications.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medikamente verwalten") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        },
        // FAB = Floating Action Button (der runde + Button unten rechts)
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAdd) {
                Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
            }
        }
    ) { innerPadding ->

        if (medications.isEmpty()) {
            // Leere Liste - Hinweis anzeigen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Noch keine Medikamente", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Tippe auf + um eines hinzuzufügen",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(medications) { medication ->
                    MedicationListItem(
                        medication = medication,
                        onClick = { onNavigateToDetail(medication.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun MedicationListItem(
    medication: MyMedication,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Grüner/Grauer Kreis = aktiv/inaktiv
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (medication.active) Color(0xFF4CAF50) // Grün
                        else Color(0xFF9E9E9E)                   // Grau
                    )
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medication.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (!medication.dosage.isNullOrBlank()) {
                    Text(
                        text = medication.dosage,
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
                Text(
                    text = if (medication.active) "Aktiv" else "Inaktiv",
                    fontSize = 12.sp,
                    color = if (medication.active) Color(0xFF4CAF50) else Color.Gray
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }
}

