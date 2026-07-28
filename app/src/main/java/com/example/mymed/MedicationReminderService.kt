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
 * MedicationReminderService - long-running foreground service.
 * Shows a persistent notification and keeps reminder infrastructure active.
 */
class MedicationReminderService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "medication_reminder_channel"
        private const val CHANNEL_NAME = "Medikamenten-Erinnerung"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MedicationService", "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MedicationService", "Service started")

        // Start as foreground service with notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Load and schedule alarms from DB
        CoroutineScope(Dispatchers.IO).launch {
            AlarmScheduler.rescheduleFromDb(this@MedicationReminderService)
        }

        // START_STICKY = service is restarted after process death
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MedicationService", "Service destroyed - restarting...")

        // Automatic restart
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
                NotificationManager.IMPORTANCE_LOW // LOW = no sound/vibration
            ).apply {
                description = "Zeigt an, dass die Medikamenten-Erinnerung aktiv ist"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Intent to open app when notification is tapped
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

