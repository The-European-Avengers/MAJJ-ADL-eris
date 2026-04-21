package com.example.myapplication.utils

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.MainActivity

class NotificationHelper(private val context: Context) {
    private val appContext = context.applicationContext
    private val channelId = "charging_channel"

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
                appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("NotificationHelper", "Notification Channel Created")
        }
    }

    fun showNotification(title: String, message: String) {
        if (!canPostNotifications()) {
            Log.w("NotificationHelper", "Notification skipped because permission is missing or notifications are disabled")
            return
        }

        Log.d("NotificationHelper", "Attempting to show notification: $title")
        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.green_pause)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Subimos prioridad
            .setDefaults(NotificationCompat.DEFAULT_ALL)   // Añade sonido/vibración por defecto
            .setContentIntent(createContentIntent())
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(appContext).notify(System.currentTimeMillis().toInt(), builder.build())
            Log.d("NotificationHelper", "Notification sent to manager")
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Error showing notification", e)
        }
    }

    private fun canPostNotifications(): Boolean {
        val notificationsEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        if (!notificationsEnabled) {
            return false
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createContentIntent(): PendingIntent {
        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            appContext,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        fun requestPermissionIfNeeded(
            context: Context,
            permissionLauncher: ActivityResultLauncher<String>
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return
            }

            if (context !is Activity) {
                Log.w("NotificationHelper", "Cannot request notification permission without an Activity context")
                return
            }

            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
