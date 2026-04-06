package com.example.myapplication.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplication.R

class NotificationHelper(private val context: Context) {
    private val channelId = "charging_channel"
    private val notificationId = 1

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Charging Notifications"
            val descriptionText = "Notifications for charging state"
            val importance = NotificationManager.IMPORTANCE_HIGH // Subimos importancia
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("NotificationHelper", "Notification Channel Created")
        }
    }

    fun showNotification(title: String, message: String) {
        Log.d("NotificationHelper", "Attempting to show notification: $title")
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.green_pause)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Subimos prioridad
            .setDefaults(NotificationCompat.DEFAULT_ALL)   // Añade sonido/vibración por defecto
            .setAutoCancel(true)

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        try {
            notificationManager.notify(notificationId, builder.build())
            Log.d("NotificationHelper", "Notification sent to manager")
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Error showing notification", e)
        }
    }
}
