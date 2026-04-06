package com.example.myapplication.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ChargingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_POWER_CONNECTED) {
            Log.d("ChargingReceiver", "Power connected detected globally")
            val notificationHelper = NotificationHelper(context)
            notificationHelper.showNotification(
                "¡Charger detected!",
                "You've plugged in your device. Is it a good time to charge it?"
            )
        }
    }
}
