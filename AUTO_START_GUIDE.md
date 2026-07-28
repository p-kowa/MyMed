# 🔄 Auto-Start & Dauerhafter Betrieb

## Was wurde implementiert?

Die App nutzt **3 Mechanismen**, um sicherzustellen, dass deine Frau immer ihre Medikamenten-Erinnerung bekommt:

### 1. ⚡ Boot Receiver (Auto-Start nach Neustart)
**`BootReceiver.kt`**
- Startet automatisch wenn das Handy eingeschaltet wird
- Aktiviert den Foreground Service und Alarme
- **Permission:** `RECEIVE_BOOT_COMPLETED`

### 2. 🔔 Foreground Service (Dauerhafter Hintergrund-Prozess)
**`MedicationReminderService.kt`**
- Läuft permanent im Hintergrund
- Zeigt eine dauerhafte Notification (Android-Pflicht für Foreground Services)
- **Startet sich automatisch neu** wenn beendet (`START_STICKY`)
- Wird vom System nur selten beendet (hohe Priorität)

### 3. ⏰ AlarmManager (Zeitbasierte Erinnerungen)
**`AlarmScheduler.kt`**
- Löst zu festen Zeiten einen Alarm aus
- **Standard-Zeiten:**
  - **07:00 Uhr** - Morgens
  - **12:00 Uhr** - Mittags
  - **19:00 Uhr** - Abends
- Öffnet automatisch die App zur Erinnerungszeit
- Funktioniert auch wenn App komplett geschlossen ist

---

## 📱 Wie es funktioniert

### Ablauf beim App-Start:
1. User öffnet App
2. App fragt Notification-Permission (Android 13+)
3. **Foreground Service** wird gestartet → zeigt dauerhafte Notification
4. **Alarme** werden eingerichtet (3x täglich)
5. Service läuft im Hintergrund

### Ablauf beim Handy-Neustart:
1. Handy wird eingeschaltet
2. **BootReceiver** wird vom System aufgerufen
3. BootReceiver startet automatisch:
   - Foreground Service
   - Alarme

### Ablauf zur Erinnerungszeit (z.B. 07:00):
1. **AlarmManager** löst Alarm aus
2. **AlarmReceiver** wird aufgerufen
3. App öffnet sich automatisch
4. Medikamenten-Liste wird angezeigt

---

## ⚙️ Erinnerungszeiten anpassen

In **`AlarmScheduler.kt`**, Zeile 15-19:

```kotlin
private val REMINDER_TIMES = listOf(
    7 to 0,   // 07:00 Uhr morgens
    12 to 0,  // 12:00 Uhr mittags
    19 to 0   // 19:00 Uhr abends
    // Füge weitere hinzu:
    // 20 to 30  // 20:30 Uhr
)
```

---

## 🔧 Wichtige Hinweise für verschiedene Hersteller

### ⚠️ Xiaomi, Huawei, OnePlus, Samsung:
Diese Hersteller haben **aggressive Battery Saver**, die Apps beenden können!

**Manuelle Schritte erforderlich:**

#### Xiaomi (MIUI):
1. **Settings** → **Apps** → **MyMed**
2. **Battery Saver** → **No restrictions**
3. **Autostart** → **On**
4. **Lock App in Recent Apps** (nicht wegwischen!)

#### Huawei:
1. **Settings** → **Battery** → **App launch**
2. **MyMed** → **Manage manually**
3. Alle 3 Optionen aktivieren:
   - Auto-launch
   - Secondary launch
   - Run in background

#### Samsung:
1. **Settings** → **Apps** → **MyMed**
2. **Battery** → **Optimize battery usage** → **All**
3. **MyMed** deaktivieren (nicht optimieren)

#### OnePlus:
1. **Settings** → **Battery** → **Battery optimization**
2. **MyMed** → **Don't optimize**

📚 **Mehr Infos:** https://dontkillmyapp.com/

---

## 🧪 Testen

### 1. Auto-Start nach Neustart:
```powershell
# Am Handy:
# 1. Handy neustarten
# 2. Nach Boot: Notifications checken
# 3. Sollte "💊 Medikamenten-Erinnerung aktiv" erscheinen
```

### 2. Foreground Service läuft:
- In Notifications sollte dauerhaft stehen:
  - **"💊 Medikamenten-Erinnerung aktiv"**
  - **"Erinnerungen sind eingerichtet"**

### 3. Alarm testet (optional):
In **`AlarmScheduler.kt`** temporär ändern auf z.B. 5 Minuten später:

```kotlin
private val REMINDER_TIMES = listOf(
    14 to 35,  // Aktuelle Zeit + 5 min
)
```

---

## 📋 Berechtigungen (Permissions)

Die App braucht folgende Permissions (automatisch angefragt):

- ✅ `POST_NOTIFICATIONS` - Benachrichtigungen anzeigen (Android 13+)
- ✅ `RECEIVE_BOOT_COMPLETED` - Auto-Start nach Neustart
- ✅ `FOREGROUND_SERVICE` - Hintergrund-Service
- ✅ `SCHEDULE_EXACT_ALARM` - Exakte Uhrzeiten für Alarme
- ✅ `WAKE_LOCK` - Handy aufwecken bei Alarm

---

## 🐛 Troubleshooting

### Problem: Service wird beendet
**Lösung:**
- In Handy-Settings: Battery Optimization **deaktivieren**
- App in Recent Apps **locken** (meist per Lock-Symbol)
- Autostart erlauben (Hersteller-spezifisch)

### Problem: Keine Notification
**Lösung:**
- Settings → Apps → MyMed → Notifications → **Erlauben**
- Android 13+: Permission-Dialog muss akzeptiert werden

### Problem: Alarm kommt nicht
**Lösung:**
- Settings → Apps → MyMed → Permissions
- **Alarme & Erinnerungen** erlauben
- Check ob Zeit korrekt eingestellt (siehe AlarmScheduler.kt)

### Problem: Nach Neustart startet App nicht
**Lösung:**
- **Autostart** in Hersteller-Settings erlauben (siehe oben)
- Check Logcat: `adb logcat | grep BootReceiver`

---

## 📊 Architektur-Übersicht

```
┌─────────────────────────────────────────────┐
│          Android System                     │
└─────────────────────────────────────────────┘
        │                    │
        │ BOOT_COMPLETED     │ ALARM_TRIGGERED
        ▼                    ▼
┌──────────────┐      ┌──────────────┐
│ BootReceiver │      │AlarmReceiver │
└──────────────┘      └──────────────┘
        │                    │
        └────────┬───────────┘
                 ▼
    ┌────────────────────────┐
    │ MedicationReminder     │
    │ Service (Foreground)   │
    └────────────────────────┘
                 │
                 │ Manages
                 ▼
    ┌────────────────────────┐
    │   AlarmScheduler       │
    │  (3x täglich)          │
    └────────────────────────┘
                 │
                 │ Opens
                 ▼
    ┌────────────────────────┐
    │    MainActivity        │
    │  (Medikamenten-Liste)  │
    └────────────────────────┘
```

---

## 🚀 Nächste Schritte (Optional)

### Verbesserungen:
1. **Eigenes Icon** für Notification (statt Standard-Icon)
2. **Snooze Zeit** konfigurierbar (z.B. 5, 10, 15 Minuten)
3. **Persistent Storage** - Medikamente in Datenbank speichern
4. **History** - Welche Medis wurden wann genommen?
5. **Vibration + Sound** bei Alarm
6. **Widget** für schnellen Zugriff

### Erweiterte Features:
- **Multiple Profiles** (mehrere Personen)
- **Photo** von Medikament speichern
- **Dosierung** & **Notizen**
- **Cloud Sync** für Backup
- **Arzt-Export** als PDF

---

## ✅ Zusammenfassung

**Was die App jetzt kann:**
- ✅ Startet automatisch mit Android
- ✅ Läuft dauerhaft im Hintergrund (Foreground Service)
- ✅ Startet sich selbst neu wenn beendet
- ✅ Zeigt Erinnerungen zu festen Zeiten (3x täglich)
- ✅ Öffnet sich automatisch zur Erinnerungszeit
- ✅ Funktioniert auch bei geschlossener App

**Was der User machen muss:**
1. App installieren
2. Notification-Permission erlauben
3. **Wichtig:** Battery Optimization deaktivieren (siehe oben)
4. *(Optional)* Hersteller-spezifische Autostart-Settings

Viel Erfolg! 💊

