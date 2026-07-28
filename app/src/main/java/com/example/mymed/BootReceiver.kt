package com.example.mymed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Device booted - loading alarms from DB")
            val serviceIntent = Intent(context, MedicationReminderService::class.java)
            context.startForegroundService(serviceIntent)
            // Load alarms from DB (uses coroutine for DB access)
            CoroutineScope(Dispatchers.IO).launch {
                AlarmScheduler.rescheduleFromDb(context)
            }
        }
    }
}

