package com.autoexpense.app.domain

import android.content.Context
import android.util.Log
import com.autoexpense.app.BuildConfig
import com.autoexpense.app.TransactionRepository
import com.autoexpense.app.data.BillRepository
import com.autoexpense.app.data.RecurringPaymentRepository
import com.autoexpense.app.data.TransactionEntity
import com.autoexpense.app.data.UserPreferencesRepository
import com.autoexpense.app.notification.KnownBanks
import com.autoexpense.app.notification.NotificationHealthRepository
import com.autoexpense.app.notification.PaymentNotificationParser
import com.autoexpense.app.notification.ParsedPayment
import com.autoexpense.app.notification.SmartMerchantCleaner
import com.autoexpense.app.notification.SmartPaymentsFeedback
import com.autoexpense.app.notification.SupportedPaymentSources
import kotlinx.coroutines.flow.first
import java.util.Locale

data class FinancialEventPipelineResult(
    val inserted: Boolean,
    val duplicate: Boolean,
    val transactionId: String?,
    val classification: TransactionClassification?
)

object FinancialEventPipeline {
    private const val TAG = "FinancialEventPipeline"

    suspend fun processParsedPayment(
        payment: ParsedPayment,
        context: Context?,
        origin: String,
        sourceId: String
    ): FinancialEventPipelineResult {
        val classification = classify(payment)
        if (DuplicateDetectionService.isDuplicate(payment)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DUPLICATE origin=$origin sourceId=$sourceId fingerprint=${DuplicateDetectionService.fingerprintFor(payment)}")
            }
            return FinancialEventPipelineResult(false, true, payment.id, classification)
        }

        return try {
            val transactionEntity = toTransactionEntity(payment, classification)
            TransactionRepository.addTransactionEntity(transactionEntity)
            persistFinancialTransaction(payment, classification, transactionEntity, context)
            runAutoMatching(payment, context)
            if (context != null) {
                NotificationHealthRepository.recordPaymentDetected(context, payment.timestamp)
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "INSERTED origin=$origin sourceId=$sourceId txnId=${payment.id} type=${classification.transactionType}")
            }
            FinancialEventPipelineResult(true, false, payment.id, classification)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "INSERT_FAILED origin=$origin sourceId=$sourceId", e)
            }
            FinancialEventPipelineResult(false, false, payment.id, classification)
        }
    }

    fun fingerprintFor(payment: ParsedPayment): String = DuplicateDetectionService.fingerprintFor(payment)

    suspend fun isDuplicate(payment: ParsedPayment): Boolean = DuplicateDetectionService.isDuplicate(payment)

    fun classify(payment: ParsedPayment): TransactionClassification {
        val rawText = listOf(
            payment.merchantOrRecipient,
            payment.sourceApplication,
            payment.safeNotificationExcerpt,
            payment.detectionReason
        ).joinToString(" ")
        return TransactionClassificationService.classifyEvent(
            rawText = rawText,
            amount = payment.amount.toString(),
            merchant = payment.merchantOrRecipient,
            category = "",
            isAutoDetected = true
        ).let { classification ->
            if (classification.transactionType == TransactionType.UNKNOWN && payment.transactionType != TransactionType.UNKNOWN) {
                classification.copy(transactionType = payment.transactionType)
            } else {
                classification
            }
        }
    }

    fun toTransactionEntity(payment: ParsedPayment, classification: TransactionClassification = classify(payment)): TransactionEntity {
        val source = SupportedPaymentSources.findSource(payment.sourcePackage)
            ?.takeIf { !it.isMessaging }
            ?.shortName
            ?: KnownBanks.detect(payment.sourceApplication)?.shortName
            ?: "banksms"

        val amountStr = formatLegacyAmount(payment.amount, classification.transactionType)
        val rawMerchant = payment.merchantOrRecipient
        val cleanedMerchant = SmartMerchantCleaner.cleanMerchant(
            resolveDisplayTitle(payment, classification)
        )

        return TransactionEntity(
            id = payment.id,
            merchantOrRecipient = cleanedMerchant,
            sub = payment.sourceApplication,
            amount = amountStr,
            currency = payment.currency.ifBlank { "INR" },
            source = source,
            category = classification.category,
            status = "review",
            timestamp = payment.timestamp,
            confidence = classification.confidenceScore,
            detectionReason = classification.reason,
            safeNotificationExcerpt = payment.safeNotificationExcerpt,
            transactionFingerprint = DuplicateDetectionService.fingerprintFor(payment),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            rawMerchant = rawMerchant,
            paymentMethod = payment.paymentMethod.name
        )
    }

    private suspend fun persistFinancialTransaction(
        payment: ParsedPayment,
        classification: TransactionClassification,
        legacyEntity: TransactionEntity,
        context: Context?
    ) {
        val displayTitle = resolveDisplayTitle(payment, classification)
        val financialTransaction = FinancialTransaction(
            id = payment.id,
            transactionType = classification.transactionType,
            amount = payment.amount,
            currency = payment.currency.ifBlank { "INR" },
            title = displayTitle,
            category = classification.category,
            merchant = displayTitle,
            paymentMethod = payment.paymentMethod.name,
            referenceNumber = payment.bankRefNumber ?: legacyEntity.transactionFingerprint.removePrefix("ref|"),
            notes = "",
            date = payment.timestamp,
            createdAt = legacyEntity.createdAt,
            updatedAt = legacyEntity.updatedAt,
            isAutoDetected = classification.isAutoDetected,
            notificationSource = legacyEntity.source,
            metadata = mapOf(
                "originSource" to payment.sourceApplication,
                "sourcePackage" to payment.sourcePackage,
                "fingerprint" to legacyEntity.transactionFingerprint,
                "classificationReason" to classification.reason
            ),
            status = "review"
        )
        TransactionCreationService.create(financialTransaction)
    }

    private suspend fun runAutoMatching(payment: ParsedPayment, context: Context?) {
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
    }

    private fun formatLegacyAmount(amount: Double, type: TransactionType): String {
        val sign = if (type == TransactionType.INCOME || type == TransactionType.REFUND || type == TransactionType.CASHBACK || type == TransactionType.INTEREST) "+" else "\u2212"
        val formatted = if (amount % 1.0 == 0.0) {
            String.format(Locale.US, "%,.0f", amount)
        } else {
            String.format(Locale.US, "%,.2f", amount)
        }
        return "$sign\u20B9$formatted"
    }

    private fun resolveDisplayTitle(
        payment: ParsedPayment,
        classification: TransactionClassification
    ): String {
        val counterparty = PaymentNotificationParser.extractIncomingCounterparty(payment.safeNotificationExcerpt).orEmpty()
        val merchant = listOf(counterparty, classification.merchant, payment.merchantOrRecipient)
            .firstOrNull { it.isMeaningfulTitle() }
        if (merchant != null) return merchant

        val bank = KnownBanks.detect(payment.sourceApplication)?.displayName
            ?: KnownBanks.detect(payment.safeNotificationExcerpt)?.displayName
        if (!bank.isNullOrBlank()) return bank

        return when (classification.transactionType) {
            TransactionType.REFUND -> "Refund"
            TransactionType.CASHBACK -> "Cashback"
            TransactionType.INTEREST -> "Interest"
            TransactionType.INCOME -> "Income"
            else -> "Unknown Merchant"
        }
    }

    private fun String.isMeaningfulTitle(): Boolean {
        val normalized = trim()
        if (normalized.isBlank()) return false
        val lower = normalized.lowercase(Locale.US)
        if (lower in setOf("income", "credited", "amount credited", "credit", "unknown merchant", "rs", "rs.", "inr", "\u20B9")) return false
        if (lower.matches(Regex("""^(?:rs\.?|inr|₹)?\s*[+-]?\s*[\d,]+(?:\.\d{1,2})?$"""))) return false
        return true
    }
}
