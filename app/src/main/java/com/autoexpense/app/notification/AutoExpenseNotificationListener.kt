package com.autoexpense.app.notification

import android.os.Build
import android.content.ComponentName
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
        processActiveNotifications("connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onListenerDisconnected")
        }
        serviceScope.launch {
            NotificationHealthRepository.recordServiceDisconnected(applicationContext)
        }
        requestSystemRebind()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        handlePostedNotification(sbn, "posted")
    }

    private fun handlePostedNotification(sbn: StatusBarNotification?, reason: String) {
        if (sbn == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "handlePostedNotification reason=$reason sbn=null")
            }
            return
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "handlePostedNotification reason=$reason pkg=${sbn.packageName}")
        }

        serviceScope.launch {
            NotificationHealthRepository.recordNotificationHeartbeat(applicationContext)
            NotificationProcessor.process(sbn, applicationContext, isRecoverySweep = reason != "posted")
        }
    }

    private fun processActiveNotifications(reason: String) {
        val active = try {
            activeNotifications ?: emptyArray()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "active notification sweep failed reason=$reason", e)
            }
            emptyArray()
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "active notification sweep reason=$reason count=${active.size}")
        }

        for (sbn in active) {
            handlePostedNotification(sbn, "active_$reason")
        }
    }

    private fun requestSystemRebind() {
        try {
            val componentName = ComponentName(
                applicationContext,
                AutoExpenseNotificationListener::class.java
            )
            NotificationListenerService.requestRebind(componentName)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "requestRebind submitted")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "requestRebind failed", e)
            }
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
