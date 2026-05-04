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
import java.util.Locale
import kotlin.math.roundToInt

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

    fun showNotification(title: String, message: String, expandedMessage: String = message) {
        if (!canPostNotifications()) {
            Log.w("NotificationHelper", "Notification skipped because permission is missing or notifications are disabled")
            return
        }

        Log.d("NotificationHelper", "Attempting to show notification: $title")
        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.green_pause)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedMessage))
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

    fun showChargingWarningNotification() {
        val snapshot = NotificationInsightStore(appContext).getLatestSnapshot()
        val notificationContent = buildChargingWarningContent(snapshot)

        showNotification(
            title = notificationContent.title,
            message = notificationContent.message,
            expandedMessage = notificationContent.expandedMessage
        )
    }

    private fun buildChargingWarningContent(snapshot: NotificationInsightSnapshot?): ChargingNotificationContent {
        if (snapshot == null) {
            return ChargingNotificationContent(
                title = "Charging warning",
                message = "Charging now may raise your carbon impact.",
                expandedMessage = "Charging now may raise your carbon impact. Open GreenPause to compare now with a cleaner moment later."
            )
        }

        // 1.5 gCO2eq is roughly the carbon cost of charging on a ~170 gCO2/kWh grid.
        // It gives a practical split between relatively clean vs clearly dirty windows.
        val thresholdGrams = 1.5f
        val analogy = if (snapshot.carbonProducedGrams < thresholdGrams) {
            "Charging now is closer to driving a regular car for a short trip."
        } else {
            "Charging now is closer to a high-impact choice, more like air travel than normal driving."
        }

        return if (snapshot.isNowBest) {
            ChargingNotificationContent(
                title = "Good time to charge",
                message = "This is one of the cleaner moments to charge.",
                expandedMessage = "$analogy No clearly better charging window was found soon."
            )
        } else {
            ChargingNotificationContent(
                title = "Charging warning",
                message = "This is not a clean moment to charge.",
                expandedMessage = "$analogy Waiting a bit could lower the carbon impact of this charge."
            )
        }
    }

    private data class ChargingNotificationContent(
        val title: String,
        val message: String,
        val expandedMessage: String
    )

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
