package com.autoexpense.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.autoexpense.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsPaymentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!SmsPaymentScanner.hasSmsPermission(context)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "sms receive skipped reason=missing_sms_permission")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isEmpty()) return@launch

                val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
                val timestamp = messages.minOfOrNull { it.timestampMillis }
                    ?.takeIf { it > 0L }
                    ?: System.currentTimeMillis()
                val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }

                SmsPaymentScanner.parseAndIngest(
                    context = context.applicationContext,
                    sender = sender,
                    body = body,
                    timestamp = timestamp,
                    origin = "sms_receiver",
                    sourceId = "incoming_${timestamp}"
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.d(TAG, "sms receive failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AutoExpenseSmsReceiver"
    }
}
