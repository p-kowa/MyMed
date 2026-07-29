# MyMed - Medication Reminder App

An Android app built with Jetpack Compose to remind you to take your medications - with alarm-style notifications, persistent storage, and package scanning via ML Kit.

## Features

- ⏰ **Current time display** - Always visible on the main screen
- 💊 **Medication list** - Persisted in a local Room database
- ✅ **Checkbox tracking** - Mark medications as taken; taken items show a strikethrough on name and time
- 📅 **Per-medication reminders** - Configure multiple alarm times per medication, with weekday selection (Mon–Sun)
- 🔔 **Alarm-style notifications** - Full-screen alert, continuous sound + vibration (like a real alarm clock); alarm mode shows two buttons: "Snooze" and "Taken"
- 😴 **Snooze** - Per-reminder configurable snooze interval (set when creating the reminder), unlimited snooze attempts per alarm session
- 📷 **ML Kit text recognition** - Scan a medication package with the camera; the app extracts name, dosage, and usage notes automatically
- 🔁 **Auto-start & resilience** - Foreground service, boot receiver, and exact alarms (`setExactAndAllowWhileIdle`) so reminders work even after reboot or when the app is closed
- 🗑️ **Full CRUD** - Add, edit, delete medications and their reminders through the UI

## Project Structure

```
app/src/main/java/com/example/mymed/
├── MainActivity.kt                  # Main reminder screen + navigation host
├── MedicationData.kt                # Room entities: MyMedication, Reminder, MedicationHistory
├── MedicationDao.kt                 # Room DAO (queries, inserts, updates, deletes)
├── AppDatabase.kt                   # Room database singleton
├── MedicationViewModel.kt           # Main screen state (today's checklist)
├── MedicationManagementViewModel.kt # CRUD state for medications/reminders
├── MedicationListScreen.kt          # List of all configured medications
├── MedicationDetailScreen.kt        # Add/edit a medication (+ camera scan)
├── ReminderBottomSheet.kt           # Configure alarm times per medication
├── MedicationScanHelper.kt          # ML Kit text recognition + field parsing
├── AlarmScheduler.kt                # Plans exact, weekday-aware alarms
├── AlarmReceiver.kt                 # Handles alarm triggers, reschedules next occurrence
├── AlarmSoundManager.kt             # Continuous alarm sound + vibration (start/stop)
├── AlarmNotificationManager.kt      # Builds the full-screen alarm notification
├── NotificationActionReceiver.kt    # Handles notification button taps
├── SnoozeManager.kt                 # Snooze scheduling (unlimited attempts, respects per-reminder interval)
├── MedicationReminderService.kt     # Foreground service (keeps reminders active)
├── BootReceiver.kt                  # Re-schedules alarms after device reboot
└── ui/theme/                        # Compose Material3 theme
```

## How It Works

### Data layer (Room)
Three entities back the app:
- **`MyMedication`** - name, dosage, notes, active flag
- **`Reminder`** - hour/minute, weekday mask, enabled flag, snooze interval (in minutes), linked to a medication
- **`MedicationHistory`** - timestamped "taken" records, used to determine today's checklist state

### Main screen (`MedicationViewModel`)
Combines active medications with today's history entries (via `Flow.combine`) so the checklist always reflects the current day, resetting automatically at midnight.

### Alarms (`AlarmScheduler` + `AlarmReceiver`)
Uses `setExactAndAllowWhileIdle` instead of `setRepeating` for precision, since repeating alarms are inexact and get delayed in Doze mode. Each alarm reschedules its own next occurrence after firing, respecting the configured weekdays.

### Alarm experience (`AlarmSoundManager` + `AlarmNotificationManager`)
When a reminder fires: a full-screen notification appears (like a real alarm clock), continuous sound and vibration start. Once the app opens, the alarm-mode screen shows two prominent buttons:
- **"Snooze"** – reschedules the alarm for X minutes later (configurable per reminder, unlimited attempts) and resets checkboxes
- **"Taken"** – marks all medications as taken, cancels any pending snooze

The user can also act directly from the notification via the same two actions.

### Package scanning (`MedicationScanHelper`)
Takes a photo via the camera, runs on-device ML Kit text recognition, and parses the result to pre-fill name, dosage, and usage notes. The photo itself is discarded after scanning - only the recognized text is kept.

## Building & Running

```powershell
cd C:\Daten\Android\MyMed
.\gradlew.bat installDebug
```

Or use the **Run ▶️** button in Android Studio.

## First-Time Setup Checklist

After installing on the target device:

1. ✅ Grant **notification permission** (prompted automatically on Android 13+)
2. ✅ Grant **camera permission** when scanning a package for the first time
3. ✅ **Disable battery optimization** for the app (see `BATTERY_OPTIMIZATION_GUIDE.md`) - otherwise the OS may kill the background service on some manufacturers (Xiaomi, Huawei, Samsung, etc.)
4. ✅ Allow **exact alarms** if prompted (Android 12+ Settings → Alarms & reminders)

## Jetpack Compose Concepts Used

- **`@Composable`** - functions that describe UI
- **`remember` / `mutableStateOf`** - local UI state
- **`StateFlow` / `collectAsState()`** - reactive state from ViewModels backed by Room `Flow`
- **`ViewModel` / `AndroidViewModel`** - screen state independent of UI lifecycle
- **`NavHost` / `NavController`** - navigation between screens
- **`LazyColumn`** - scrollable lists
- **`ModalBottomSheet`**, **`AlertDialog`**, **`TimePicker`** - Material3 components

## Possible Next Steps

- Home screen widget
- Multiple user profiles

Good luck! 💊
