package com.example.mymed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * MedicationReminderService - Foreground Service der dauerhaft läuft
 * Zeigt eine dauerhafte Notification und stellt sicher, dass die App aktiv bleibt
 */
class MedicationReminderService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "medication_reminder_channel"
        private const val CHANNEL_NAME = "Medikamenten-Erinnerung"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MedicationService", "Service erstellt")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MedicationService", "Service gestartet")

        // Starte als Foreground Service mit Notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Alarme aus DB laden und planen
        CoroutineScope(Dispatchers.IO).launch {
            AlarmScheduler.rescheduleFromDb(this@MedicationReminderService)
        }

        // START_STICKY = Service wird neu gestartet wenn beendet
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MedicationService", "Service wird beendet - starte neu...")

        // Automatischer Neustart
        val restartIntent = Intent(applicationContext, MedicationReminderService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(restartIntent)
        } else {
            applicationContext.startService(restartIntent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // LOW = keine Sounds/Vibration
            ).apply {
                description = "Zeigt an, dass die Medikamenten-Erinnerung aktiv ist"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Intent zum Öffnen der App wenn auf Notification geklickt wird
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("💊 Medikamenten-Erinnerung aktiv")
            .setContentText("Erinnerungen sind eingerichtet")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}

