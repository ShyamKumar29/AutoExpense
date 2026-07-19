package com.autoexpense.app.notification

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import com.autoexpense.app.BuildConfig
import com.autoexpense.app.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Single source of truth for Notification Listener health, heartbeat tracking,
 * and reliability actions in AutoExpense.
 *
 * Persists only lightweight health information locally via SharedPreferences.
 */
object NotificationHealthRepository {

    private const val PREFS_NAME = "autoexpense_notification_health_prefs"
    private const val KEY_LAST_HEARTBEAT_TIME = "last_heartbeat_time"
    private const val KEY_LAST_CONNECT_TIME = "last_connect_time"
    private const val KEY_LAST_RECONNECT_TIME = "last_reconnect_time"
    private const val KEY_LAST_PAYMENT_DETECTED_TIME = "last_payment_detected_time"

    private const val STALE_HEARTBEAT_THRESHOLD_MS = 24L * 60 * 60 * 1000L // 24 hours

    private val _sessionConnected = MutableStateFlow(false)
    val sessionConnected: StateFlow<Boolean> = _sessionConnected.asStateFlow()

    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    private val _reconnectStatusMessage = MutableStateFlow<String?>(null)
    val reconnectStatusMessage: StateFlow<String?> = _reconnectStatusMessage.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)

    fun recordServiceConnected(context: Context) {
        _sessionConnected.value = true
        _isReconnecting.value = false
        val now = System.currentTimeMillis()
        saveLong(context, KEY_LAST_HEARTBEAT_TIME, now)
        saveLong(context, KEY_LAST_CONNECT_TIME, now)
        if (_reconnectStatusMessage.value != null) {
            _reconnectStatusMessage.value = "Listener reconnected successfully."
        }
    }

    fun recordServiceDisconnected(context: Context) {
        _sessionConnected.value = false
    }

    fun recordServiceReconnected(context: Context) {
        _sessionConnected.value = true
        _isReconnecting.value = false
        val now = System.currentTimeMillis()
        saveLong(context, KEY_LAST_HEARTBEAT_TIME, now)
        saveLong(context, KEY_LAST_RECONNECT_TIME, now)
    }

    fun recordNotificationHeartbeat(context: Context) {
        saveLong(context, KEY_LAST_HEARTBEAT_TIME, System.currentTimeMillis())
    }

    fun recordPaymentDetected(context: Context, timestamp: Long) {
        val ts = if (timestamp > 0L) timestamp else System.currentTimeMillis()
        saveLong(context, KEY_LAST_PAYMENT_DETECTED_TIME, ts)
    }

    fun getLastPaymentDetectedTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getLong(KEY_LAST_PAYMENT_DETECTED_TIME, 0L)
        if (saved > 0L) return saved

        // Fallback to existing transactions in Room detected via notification
        val latestFromTransactions = TransactionRepository.transactions.value
            .filter { it.detectionReason.isNotBlank() || it.notificationExcerpt.isNotBlank() }
            .maxOfOrNull { it.timestamp } ?: 0L

        if (latestFromTransactions > 0L) {
            saveLong(context, KEY_LAST_PAYMENT_DETECTED_TIME, latestFromTransactions)
            return latestFromTransactions
        }
        return 0L
    }

    fun getLastHeartbeatTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_HEARTBEAT_TIME, 0L)
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val target = "${context.packageName}/com.autoexpense.app.notification.AutoExpenseNotificationListener"
        return enabledListeners.contains(target)
    }

    fun getListenerStatus(context: Context): String {
        val enabled = isNotificationListenerEnabled(context)
        if (!enabled) return "Attention needed"
        if (_isReconnecting.value) return "Reconnecting"
        val sessionConn = _sessionConnected.value
        val lastHeartbeat = getLastHeartbeatTime(context)
        val now = System.currentTimeMillis()
        val isStale = lastHeartbeat > 0 && (now - lastHeartbeat) > STALE_HEARTBEAT_THRESHOLD_MS
        if (!sessionConn || isStale) return "Attention needed"
        return "Active"
    }

    fun getHealthWarning(context: Context): String? {
        if (!isNotificationListenerEnabled(context)) {
            return "Payment detection is off. Zors cannot capture payment notifications."
        }
        if (!_sessionConnected.value) {
            return "Notification access is enabled, but the listener has not connected yet."
        }
        val lastHeartbeat = getLastHeartbeatTime(context)
        val now = System.currentTimeMillis()
        if (lastHeartbeat > 0 && (now - lastHeartbeat) > STALE_HEARTBEAT_THRESHOLD_MS) {
            return "Payment detection may need attention."
        }
        return null
    }

    fun formatLastPaymentTime(timestamp: Long): String {
        if (timestamp <= 0L) return "No payments detected yet"
        val now = System.currentTimeMillis()
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val tsCal = Calendar.getInstance().apply { timeInMillis = timestamp }

        val timeFormatter = SimpleDateFormat("h:mm a", Locale.US)
        val timeStr = timeFormatter.format(Date(timestamp))

        if (nowCal.get(Calendar.YEAR) == tsCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.DAY_OF_YEAR) == tsCal.get(Calendar.DAY_OF_YEAR)) {
            return "Today, $timeStr"
        }

        val yestCal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_MONTH, -1)
        }
        if (yestCal.get(Calendar.YEAR) == tsCal.get(Calendar.YEAR) &&
            yestCal.get(Calendar.DAY_OF_YEAR) == tsCal.get(Calendar.DAY_OF_YEAR)) {
            return "Yesterday, $timeStr"
        }

        val dateFormatter = SimpleDateFormat("MMM d, h:mm a", Locale.US)
        return dateFormatter.format(Date(timestamp))
    }

    fun reconnectListener(context: Context) {
        if (!isNotificationListenerEnabled(context)) {
            _reconnectStatusMessage.value = "Please enable notification access first."
            return
        }
        if (_isReconnecting.value) return // Prevent duplicate requests or loops
        _isReconnecting.value = true
        _reconnectStatusMessage.value = "Requesting listener rebind..."

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val componentName = ComponentName(
                    context,
                    AutoExpenseNotificationListener::class.java
                )
                NotificationListenerService.requestRebind(componentName)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("NotificationHealth", "requestRebind failed", e)
            }
        }

        scope.launch {
            delay(3000)
            if (_isReconnecting.value) {
                _isReconnecting.value = false
                if (!_sessionConnected.value) {
                    _reconnectStatusMessage.value = "Rebind requested. If status stays inactive, try toggling access off and on in Settings."
                } else {
                    _reconnectStatusMessage.value = "Listener reconnected successfully."
                }
            }
        }
    }

    fun clearReconnectStatusMessage() {
        _reconnectStatusMessage.value = null
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            true
        }
    }

    enum class BackgroundDetectionStatus {
        REQUIRED,
        RECOMMENDED,
        COMPLETED
    }

    fun getBackgroundDetectionStatus(
        context: Context,
        isPaymentSetupCompleted: Boolean = false
    ): BackgroundDetectionStatus {
        val notificationEnabled = isNotificationListenerEnabled(context)
        if (!notificationEnabled) {
            return BackgroundDetectionStatus.REQUIRED
        }
        val batteryExempt = isIgnoringBatteryOptimizations(context)
        if (batteryExempt || isPaymentSetupCompleted) {
            return BackgroundDetectionStatus.COMPLETED
        }
        return BackgroundDetectionStatus.RECOMMENDED
    }

    private fun saveLong(context: Context, key: String, value: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(key, value).apply()
    }
}
