package com.example.mymed

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var currentToneTitle by remember { mutableStateOf(AlarmTonePreferences.getCurrentToneTitle(context)) }
    var isUsingSystemDefault by remember { mutableStateOf(AlarmTonePreferences.isUsingSystemDefault(context)) }
    var autoSnoozeSeconds by remember { mutableStateOf(SnoozeManager.getAutoSnoozeSeconds(context)) }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedUri = extractPickedRingtoneUri(result.data)
            if (pickedUri == null) {
                AlarmTonePreferences.resetToSystemDefault(context)
            } else {
                AlarmTonePreferences.saveAlarmToneUri(context, pickedUri)
            }
            currentToneTitle = AlarmTonePreferences.getCurrentToneTitle(context)
            isUsingSystemDefault = AlarmTonePreferences.isUsingSystemDefault(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alarm_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AlarmSettingsCard(
                    icon = {
                        Icon(
                            Icons.Default.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = stringResource(R.string.alarm_settings_sound_title),
                    body = stringResource(R.string.alarm_settings_sound_body)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.alarm_settings_current_tone_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentToneTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val pickerIntent = buildRingtonePickerIntent(
                                    context = context,
                                    existingUri = AlarmTonePreferences.getEffectiveAlarmToneUri(context)
                                )

                                if (pickerIntent.resolveActivity(context.packageManager) != null) {
                                    ringtonePickerLauncher.launch(pickerIntent)
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.alarm_settings_picker_unavailable),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ) {
                            Text(stringResource(R.string.alarm_settings_choose_button))
                        }

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                AlarmTonePreferences.resetToSystemDefault(context)
                                currentToneTitle = AlarmTonePreferences.getCurrentToneTitle(context)
                                isUsingSystemDefault = true
                            },
                            enabled = !isUsingSystemDefault
                        ) {
                            Text(stringResource(R.string.alarm_settings_reset_button))
                        }
                    }
                }
            }
            item {
                AlarmSettingsCard(
                    icon = {
                        Icon(
                            Icons.Default.Vibration,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = stringResource(R.string.alarm_settings_behavior_title),
                    body = stringResource(R.string.alarm_settings_behavior_body)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.alarm_settings_auto_snooze_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SnoozeManager.AUTO_SNOOZE_SECONDS_OPTIONS.forEach { option ->
                            val selected = autoSnoozeSeconds == option
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    SnoozeManager.setAutoSnoozeSeconds(context, option)
                                    autoSnoozeSeconds = SnoozeManager.getAutoSnoozeSeconds(context)
                                }
                            ) {
                                val suffix = if (selected) " \u2713" else ""
                                Text("${option}s$suffix")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun extractPickedRingtoneUri(data: Intent?): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }
}

/**
 * Builds the ringtone picker intent and, if possible, targets the device's
 * dedicated sound picker (e.g. Samsung "SecSoundPicker" or AOSP's
 * "com.android.soundpicker") directly. This avoids Android showing an
 * unnecessary "Complete action using..." chooser with unrelated apps
 * (file manager, music player, etc.) that also register for this intent.
 */
private fun buildRingtonePickerIntent(context: Context, existingUri: Uri?): Intent {
    val baseIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        putExtra(
            RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        )
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
    }

    val candidates: List<ResolveInfo> = try {
        context.packageManager.queryIntentActivities(baseIntent, 0)
    } catch (e: Exception) {
        emptyList()
    }

    // Prefer the dedicated sound picker (Samsung "SecSoundPicker", AOSP
    // "com.android.soundpicker") over any other app that happens to also
    // declare this intent filter.
    val preferred = candidates.firstOrNull {
        it.activityInfo.packageName.contains("soundpicker", ignoreCase = true) ||
            it.activityInfo.name.contains("soundpicker", ignoreCase = true)
    } ?: candidates.firstOrNull {
        it.activityInfo.packageName.contains("providers.media", ignoreCase = true)
    }

    if (preferred != null) {
        baseIntent.setClassName(preferred.activityInfo.packageName, preferred.activityInfo.name)
    }

    return baseIntent
}

@Composable
private fun AlarmSettingsCard(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            icon()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium
            )
            content()
        }
    }
}

