package com.autoexpense.app.notification

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
        serviceScope.launch {
            NotificationProcessor.process(sbn)
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
