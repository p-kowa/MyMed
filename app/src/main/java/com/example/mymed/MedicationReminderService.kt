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
        Log.d("MedicationService", "Service destroyed")
        // NOTE: No self-restart here anymore.
        // Reminders are driven entirely by AlarmManager (AlarmReceiver),
        // which fires independently of whether this service is alive.
        // An unconditional self-restart is an anti-pattern that some OEM
        // battery managers (Xiaomi, Huawei, Samsung, ...) detect and punish
        // by blocklisting the app's autostart entirely - the opposite of
        // what we want. START_STICKY already lets the OS restart the
        // service in a throttled, system-controlled way if needed.
        // The service is re-started on: app open (MainActivity), device
        // boot (BootReceiver), and each fired alarm (AlarmReceiver).
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW // LOW = no sound/vibration
            ).apply {
                description = getString(R.string.service_channel_desc)
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
            .setContentTitle(getString(R.string.service_notif_title))
            .setContentText(getString(R.string.service_notif_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}

