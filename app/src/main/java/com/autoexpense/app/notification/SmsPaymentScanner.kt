package com.autoexpense.app.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.autoexpense.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsPaymentScanner {
    private const val TAG = "AutoExpenseSmsScanner"
    private const val PREFS_NAME = "autoexpense_sms_detection_prefs"
    private const val KEY_LAST_SCAN_TIME = "last_scan_time"
    private const val INITIAL_LOOKBACK_MS = 3L * 24L * 60L * 60L * 1000L
    private const val MAX_ROWS_PER_SCAN = 250

    fun hasSmsPermission(context: Context): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val receiveGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && receiveGranted
    }

    suspend fun scanRecent(context: Context): Int = withContext(Dispatchers.IO) {
        if (!hasSmsPermission(context)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "scan skipped reason=missing_sms_permission")
            return@withContext 0
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastScan = prefs.getLong(KEY_LAST_SCAN_TIME, 0L)
        val since = if (lastScan > 0L) lastScan else now - INITIAL_LOOKBACK_MS

        var inserted = 0
        var scanned = 0

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        val cursor = try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                "${Telephony.Sms.DATE} > ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} DESC"
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "scan query failed", e)
            null
        }

        cursor?.use {
            val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (it.moveToNext() && scanned < MAX_ROWS_PER_SCAN) {
                scanned += 1
                val address = it.getString(addressIndex).orEmpty()
                val body = it.getString(bodyIndex).orEmpty()
                val date = it.getLong(dateIndex).takeIf { value -> value > 0L } ?: now

                if (parseAndIngest(context, address, body, date, "inbox_scan", "row$scanned")) {
                    inserted += 1
                }
            }
        }

        prefs.edit().putLong(KEY_LAST_SCAN_TIME, now).apply()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "scan completed scanned=$scanned inserted=$inserted since=$since")
        }
        inserted
    }

    suspend fun parseAndIngest(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long,
        origin: String,
        sourceId: String
    ): Boolean {
        val payment = PaymentNotificationParser.parse(
            title = sender,
            body = body,
            packageName = PaymentIngestion.DIRECT_SMS_PACKAGE,
            timestamp = timestamp
        )

        if (payment == null) {
            val bill = BillNotificationParser.parse(
                title = sender,
                body = body,
                packageName = PaymentIngestion.DIRECT_SMS_PACKAGE,
                timestamp = timestamp
            )
            if (bill != null) {
                return BillIngestion.ingestParsedBill(
                    bill = bill,
                    origin = origin,
                    sourceId = sourceId
                )
            }
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "sms rejected origin=$origin sourceId=$sourceId " +
                        "senderLen=${sender.length} bodyLen=${body.length} " +
                        "reason=${PaymentNotificationParser.rejectionReason(sender, body, PaymentIngestion.DIRECT_SMS_PACKAGE)}"
                )
            }
            return false
        }

        return PaymentIngestion.ingestParsedPayment(
            payment = payment,
            context = context,
            origin = origin,
            sourceId = sourceId
        )
    }
}
