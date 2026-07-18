package com.autoexpense.app.notification

import android.content.Context
import android.util.Log
import com.autoexpense.app.BuildConfig
import com.autoexpense.app.TransactionRepository
import com.autoexpense.app.data.BillRepository
import com.autoexpense.app.data.RecurringPaymentRepository
import com.autoexpense.app.data.TransactionEntity
import com.autoexpense.app.data.UserPreferencesRepository
import kotlinx.coroutines.flow.first

/**
 * Shared payment insertion path for notification and direct-SMS detection.
 *
 * Keeps duplicate detection and privacy-safe persistence identical across sources.
 */
object PaymentIngestion {
    private const val TAG = "AutoExpenseIngestion"
    private const val DEDUP_WINDOW_MS = 5 * 60 * 1000L

    const val DIRECT_SMS_PACKAGE = "com.autoexpense.sms"

    @Volatile
    private var fingerprintMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

    suspend fun ingestParsedPayment(
        payment: ParsedPayment,
        context: Context?,
        origin: String,
        sourceId: String
    ): Boolean {
        if (isDuplicate(payment)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DUPLICATE origin=$origin sourceId=$sourceId fingerprint=${fingerprintFor(payment)}")
            }
            return false
        }

        return try {
            TransactionRepository.addTransactionEntity(toTransactionEntity(payment))
            val autoMatchingEnabled = context?.let {
                UserPreferencesRepository.getInstance(it).isSmartAutoMatchingEnabled.first()
            } ?: true
            val autoMarkPaidEnabled = context?.let {
                UserPreferencesRepository.getInstance(it).isSmartAutoMarkPaidEnabled.first()
            } ?: true
            val autoPaidNotificationsEnabled = context?.let {
                UserPreferencesRepository.getInstance(it).isSmartAutoPaidNotificationsEnabled.first()
            } ?: true
            if (autoMatchingEnabled && autoMarkPaidEnabled) {
                val matchedBill = BillRepository.markMatchingPaymentPaid(
                    transactionId = payment.id,
                    merchantOrRecipient = payment.merchantOrRecipient,
                    amount = payment.amount,
                    paidAt = payment.timestamp
                )
                val matchedSubscription = RecurringPaymentRepository.markMatchingPaymentPaid(
                    merchantOrRecipient = payment.merchantOrRecipient,
                    amount = payment.amount,
                    paidAt = payment.timestamp
                )
                if (autoPaidNotificationsEnabled) {
                    matchedBill?.let {
                        SmartPaymentsFeedback.publishAutoPaid(context, it.provider, "BILL")
                    }
                    matchedSubscription?.let {
                        SmartPaymentsFeedback.publishAutoPaid(context, it.merchant, "SUBSCRIPTION")
                    }
                }
            }
            if (context != null) {
                NotificationHealthRepository.recordPaymentDetected(context, payment.timestamp)
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "INSERTED origin=$origin sourceId=$sourceId txnId=${payment.id}")
            }
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "INSERT_FAILED origin=$origin sourceId=$sourceId", e)
            }
            false
        }
    }

    internal suspend fun isDuplicate(payment: ParsedPayment): Boolean {
        val now = System.currentTimeMillis()

        val primary = payment.bankRefNumber
            ?.takeIf { it.isNotBlank() }
            ?.let { "ref|$it" }

        if (primary != null) {
            val lastSeen = fingerprintMap[primary]
            if (lastSeen != null && (now - lastSeen) < DEDUP_WINDOW_MS) return true
            fingerprintMap[primary] = now
            if (TransactionRepository.existsByFingerprint(primary)) return true
            fingerprintMap.entries.removeIf { (_, value) -> (now - value) > DEDUP_WINDOW_MS }
            return false
        }

        val fallback = fallbackFingerprintFor(payment)
        val lastSeenFallback = fingerprintMap[fallback]
        if (lastSeenFallback != null && (now - lastSeenFallback) < DEDUP_WINDOW_MS) return true
        fingerprintMap[fallback] = now

        if (TransactionRepository.existsByFingerprint(fallback)) return true

        fingerprintMap.entries.removeIf { (_, value) -> (now - value) > DEDUP_WINDOW_MS }
        return false
    }

    internal fun fingerprintFor(payment: ParsedPayment): String {
        if (!payment.bankRefNumber.isNullOrBlank()) {
            return "ref|${payment.bankRefNumber}"
        }
        return fallbackFingerprintFor(payment)
    }

    private fun fallbackFingerprintFor(payment: ParsedPayment): String {
        val normalizedRecipient = payment.merchantOrRecipient.lowercase().replace(Regex("\\s+"), "")
        val bank = KnownBanks.detect(payment.sourceApplication)?.shortName
            ?: payment.sourcePackage.take(8)
        return "t2|${payment.amount}|$normalizedRecipient|$bank"
    }

    internal fun toTransactionEntity(payment: ParsedPayment): TransactionEntity {
        val source = SupportedPaymentSources.findSource(payment.sourcePackage)
            ?.takeIf { !it.isMessaging }
            ?.shortName
            ?: KnownBanks.detect(payment.sourceApplication)?.shortName
            ?: "banksms"

        val amountStr = if (payment.amount % 1.0 == 0.0) {
            "−₹${String.format(java.util.Locale.US, "%,.0f", payment.amount)}"
        } else {
            "−₹${String.format(java.util.Locale.US, "%,.2f", payment.amount)}"
        }

        val rawMerchant = payment.merchantOrRecipient
        val cleanedMerchant = SmartMerchantCleaner.cleanMerchant(rawMerchant)

        return TransactionEntity(
            id = payment.id,
            merchantOrRecipient = cleanedMerchant,
            sub = payment.sourceApplication,
            amount = amountStr,
            currency = "INR",
            source = source,
            category = "❓ Unknown",
            status = "review",
            timestamp = payment.timestamp,
            confidence = 1.0f,
            detectionReason = payment.detectionReason,
            safeNotificationExcerpt = payment.safeNotificationExcerpt,
            transactionFingerprint = fingerprintFor(payment),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            rawMerchant = rawMerchant,
            paymentMethod = payment.paymentMethod.name
        )
    }
}
