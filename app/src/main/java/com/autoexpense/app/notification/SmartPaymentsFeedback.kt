package com.autoexpense.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.autoexpense.app.MainActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.abs

data class SmartPaymentFeedbackEvent(
    val message: String,
    val paymentTab: String? = null,
    val focusSuggestions: Boolean = false
)

object SmartPaymentsFeedback {
    private const val CHANNEL_ID = "smart_payments"
    private const val CHANNEL_NAME = "Smart Payments"
    const val EXTRA_TARGET_SCREEN = "target_screen"
    const val EXTRA_PAYMENT_TAB = "payment_tab"
    const val EXTRA_FOCUS_SUGGESTIONS = "focus_suggestions"

    @Volatile
    private var appVisible = false

    private val _events = MutableSharedFlow<SmartPaymentFeedbackEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun setAppVisible(visible: Boolean) {
        appVisible = visible
    }

    fun publishAutoPaid(
        context: Context?,
        name: String,
        paymentType: String
    ) {
        val label = if (paymentType.equals("SUBSCRIPTION", ignoreCase = true)) {
            "$name subscription renewed and marked as Paid."
        } else {
            "$name bill automatically marked as Paid."
        }
        publish(
            context = context,
            event = SmartPaymentFeedbackEvent(
                message = label,
                paymentTab = if (paymentType.equals("SUBSCRIPTION", ignoreCase = true)) "subscriptions" else "bills"
            )
        )
    }

    fun publishRecurringDetected(
        context: Context?,
        merchant: String,
        frequency: String
    ) {
        val readableFrequency = frequency.lowercase()
            .replace('_', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        publish(
            context = context,
            event = SmartPaymentFeedbackEvent(
                message = "$merchant appears to be a recurring $readableFrequency payment.",
                paymentTab = "subscriptions",
                focusSuggestions = true
            )
        )
    }

    private fun publish(context: Context?, event: SmartPaymentFeedbackEvent) {
        if (appVisible) {
            _events.tryEmit(event)
        } else if (context != null) {
            showSystemNotification(context.applicationContext, event)
        }
    }

    private fun showSystemNotification(context: Context, event: SmartPaymentFeedbackEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Helpful updates from Smart Payments"
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_SCREEN, "payments")
            event.paymentTab?.let { putExtra(EXTRA_PAYMENT_TAB, it) }
            putExtra(EXTRA_FOCUS_SUGGESTIONS, event.focusSuggestions)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            abs(event.message.hashCode()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Zors")
            .setContentText(event.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(event.message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(abs(event.message.hashCode()), notification)
    }
}
