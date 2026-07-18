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
import java.security.MessageDigest

object SmsPaymentScanner {
    private const val TAG = "AutoExpenseSmsScanner"
    private const val PREFS_NAME = "autoexpense_sms_detection_prefs"
    private const val KEY_FIRST_ENABLED_AT = "first_enabled_at"
    private const val KEY_LAST_SCAN_TIME = "last_scan_time"
    private const val KEY_PROCESSED_SMS = "processed_sms_keys"
    private const val MAX_ROWS_PER_SCAN = 250
    private const val MAX_PROCESSED_KEYS = 5000

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
        val firstEnabledAt = prefs.getLong(KEY_FIRST_ENABLED_AT, 0L)
        val lastScan = prefs.getLong(KEY_LAST_SCAN_TIME, 0L)
        if (firstEnabledAt <= 0L && lastScan <= 0L) {
            prefs.edit()
                .putLong(KEY_FIRST_ENABLED_AT, now)
                .putLong(KEY_LAST_SCAN_TIME, now)
                .apply()
            if (BuildConfig.DEBUG) Log.d(TAG, "scan initialized baseline=$now inserted=0")
            return@withContext 0
        }

        val since = when {
            lastScan > 0L -> lastScan
            firstEnabledAt > 0L -> firstEnabledAt
            else -> now
        }
        if (firstEnabledAt <= 0L) {
            prefs.edit().putLong(KEY_FIRST_ENABLED_AT, since).apply()
        }

        var inserted = 0
        var scanned = 0

        val projection = arrayOf(
            Telephony.Sms._ID,
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
            val idIndex = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (it.moveToNext() && scanned < MAX_ROWS_PER_SCAN) {
                scanned += 1
                val smsId = it.getLong(idIndex).takeIf { value -> value > 0L }?.toString()
                val address = it.getString(addressIndex).orEmpty()
                val body = it.getString(bodyIndex).orEmpty()
                val date = it.getLong(dateIndex).takeIf { value -> value > 0L } ?: now
                val processedKey = processedKeyFor(address, body, date)

                if (isSmsProcessed(context, processedKey)) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "sms skipped already_processed sourceId=${smsId ?: "row$scanned"}")
                    continue
                }

                if (parseAndIngest(context, address, body, date, "inbox_scan", smsId ?: "row$scanned")) {
                    inserted += 1
                }
                markSmsProcessed(context, processedKey)
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

    suspend fun parseAndIngestOnce(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long,
        origin: String,
        sourceId: String
    ): Boolean {
        val processedKey = processedKeyFor(sender, body, timestamp)
        val firstEnabledAt = ensureFirstEnabledAt(context)
        if (timestamp <= firstEnabledAt) {
            if (BuildConfig.DEBUG) Log.d(TAG, "sms skipped before_baseline origin=$origin sourceId=$sourceId")
            markSmsProcessed(context, processedKey)
            return false
        }
        if (isSmsProcessed(context, processedKey)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "sms skipped already_processed origin=$origin sourceId=$sourceId")
            return false
        }
        val inserted = parseAndIngest(context, sender, body, timestamp, origin, sourceId)
        markSmsProcessed(context, processedKey)
        return inserted
    }

    private fun isSmsProcessed(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PROCESSED_SMS, emptySet()).orEmpty().contains(key)
    }

    private fun markSmsProcessed(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_PROCESSED_SMS, emptySet()).orEmpty()
        val next = LinkedHashSet<String>(current.size + 1)
        next.addAll(current.toList().takeLast(MAX_PROCESSED_KEYS - 1))
        next.add(key)
        prefs.edit().putStringSet(KEY_PROCESSED_SMS, next).apply()
    }

    private fun ensureFirstEnabledAt(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getLong(KEY_FIRST_ENABLED_AT, 0L)
        if (existing > 0L) return existing

        val baseline = prefs.getLong(KEY_LAST_SCAN_TIME, 0L).takeIf { it > 0L }
            ?: System.currentTimeMillis()
        prefs.edit()
            .putLong(KEY_FIRST_ENABLED_AT, baseline)
            .putLong(KEY_LAST_SCAN_TIME, baseline)
            .apply()
        return baseline
    }

    private fun processedKeyFor(sender: String, body: String, timestamp: Long): String {
        val normalizedSender = sender.lowercase().replace(Regex("""[^a-z0-9]+"""), "")
        return "sms|$timestamp|$normalizedSender|${sha256(body)}"
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
