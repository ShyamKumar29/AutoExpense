package com.autoexpense.app.budget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.autoexpense.app.MainActivity
import com.autoexpense.app.R

/**
 * Sends budget-alert Android notifications after a spending threshold is crossed.
 *
 * Channel: "Budget Alerts" (IMPORTANCE_HIGH)
 * Does NOT fire on app re-open — only called explicitly after a confirmation event.
 */
object BudgetNotificationHelper {

    private const val CHANNEL_ID   = "budget_alerts"
    private const val CHANNEL_NAME = "Budget Alerts"

    /** Create the notification channel (safe to call multiple times). */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when spending approaches or exceeds a budget limit."
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Post a notification for [warning].
     * On Android 13+ (TIRAMISU), the caller must have already requested
     * POST_NOTIFICATIONS before calling this.
     */
    fun sendWarning(context: Context, warning: BudgetWarning, notificationId: Int) {
        if (!hasNotificationPermission(context)) return

        val (title, message) = buildNotificationContent(warning)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "budget")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted; silently skip
        }
    }

    private fun buildNotificationContent(warning: BudgetWarning): Pair<String, String> {
        val title = when (warning.level) {
            BudgetLevel.EXCEEDED -> "Budget exceeded"
            else                 -> "Budget warning"
        }
        return Pair(title, warning.message)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
