package com.autoexpense.app.notification

import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.autoexpense.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Receives every posted notification and forwards it to [NotificationProcessor].
 *
 * Privacy: no notification content is logged here. Only the package name is logged
 * in debug builds for diagnostic purposes.
 */
class AutoExpenseNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onListenerConnected")
        }
        serviceScope.launch {
            NotificationHealthRepository.recordServiceConnected(applicationContext)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onListenerDisconnected")
        }
        serviceScope.launch {
            NotificationHealthRepository.recordServiceDisconnected(applicationContext)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onNotificationPosted: sbn was null, ignoring")
            }
            return
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onNotificationPosted pkg=${sbn.packageName}")
        }

        if (sbn.id == NotificationHealthRepository.TEST_NOTIFICATION_ID && sbn.packageName == applicationContext.packageName) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onNotificationPosted: test notification detected")
            }
            NotificationHealthRepository.recordNotificationHeartbeat(applicationContext)
            NotificationHealthRepository.onTestNotificationDetected()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cancelNotification(sbn.key)
                } else {
                    @Suppress("DEPRECATION")
                    cancelNotification(sbn.packageName, sbn.tag, sbn.id)
                }
            } catch (_: Exception) {
                try {
                    val nm = applicationContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                    nm?.cancel(NotificationHealthRepository.TEST_NOTIFICATION_ID)
                } catch (_: Exception) {}
            }
            return
        }

        serviceScope.launch {
            NotificationHealthRepository.recordNotificationHeartbeat(applicationContext)
            NotificationProcessor.process(sbn, applicationContext)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) { /* no-op */ }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "AutoExpenseNotification"
    }
}
