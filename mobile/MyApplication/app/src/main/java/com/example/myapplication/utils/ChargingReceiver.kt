package com.example.myapplication.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalTime

class ChargingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == Intent.ACTION_POWER_CONNECTED) {
            Log.d("ChargingReceiver", "Power connected detected globally")
            ChargingSessionStore(context.applicationContext).saveSessionStart(
                plugInTimeMs = System.currentTimeMillis(),
                predictionHourRef = LocalTime.now().hour
            )
            NotificationHelper(context.applicationContext).showChargingWarningNotification()
        }
    }
}
