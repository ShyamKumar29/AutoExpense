package com.autoexpense.app.domain

import com.autoexpense.app.Transaction
import com.autoexpense.app.data.TransactionEntity
import java.util.Locale

enum class TransactionType {
    EXPENSE,
    INCOME,
    REFUND,
    TRANSFER,
    CASHBACK,
    INTEREST,
    CREDIT_CARD_PURCHASE,
    CREDIT_CARD_PAYMENT,
    UNKNOWN
}

data class FinancialTransaction(
    val id: String,
    val transactionType: TransactionType,
    val amount: Double,
    val currency: String,
    val title: String,
    val category: String,
    val subCategory: String = "",
    val merchant: String = "",
    val accountId: String? = null,
    val paymentMethod: String = "",
    val referenceNumber: String = "",
    val notes: String = "",
    val date: Long,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val location: String = "",
    val tags: List<String> = emptyList(),
    val isRecurring: Boolean = false,
    val isAutoDetected: Boolean = false,
    val smsBody: String = "",
    val notificationSource: String = "",
    val metadata: Map<String, String> = emptyMap(),
    val isDeleted: Boolean = false,
    val status: String = ""
) {
    val isConfirmed: Boolean
        get() = status.equals("confirmed", ignoreCase = true)
}

object FinancialTransactionMapper {
    fun fromUiTransaction(transaction: Transaction): FinancialTransaction {
        val type = TransactionClassificationService.classify(transaction)
        return FinancialTransaction(
            id = transaction.id,
            transactionType = type,
            amount = CashFlowService.parseAmount(transaction.amount),
            currency = "INR",
            title = transaction.merchant,
            category = transaction.category,
            merchant = transaction.merchant,
            paymentMethod = transaction.paymentMethod,
            referenceNumber = transaction.referenceNumber,
            notes = transaction.note,
            date = transaction.timestamp,
            isAutoDetected = transaction.notificationExcerpt.isNotBlank() || transaction.detectionReason.isNotBlank(),
            notificationSource = transaction.source,
            metadata = mapOf(
                "source" to transaction.source,
                "rawMerchant" to transaction.rawMerchant,
                "detectionReason" to transaction.detectionReason,
                "notificationExcerpt" to transaction.notificationExcerpt
            ).filterValues { it.isNotBlank() },
            status = transaction.status
        )
    }

    fun fromEntity(entity: TransactionEntity): FinancialTransaction {
        val type = TransactionClassificationService.classify(entity)
        return FinancialTransaction(
            id = entity.id,
            transactionType = type,
            amount = CashFlowService.parseAmount(entity.amount),
            currency = entity.currency.ifBlank { "INR" },
            title = entity.merchantOrRecipient,
            category = entity.category,
            merchant = entity.merchantOrRecipient,
            paymentMethod = entity.paymentMethod,
            referenceNumber = entity.transactionFingerprint.removePrefix("ref|"),
            notes = entity.note,
            date = entity.timestamp,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isAutoDetected = entity.safeNotificationExcerpt.isNotBlank() || entity.detectionReason.isNotBlank(),
            notificationSource = entity.source,
            metadata = mapOf(
                "source" to entity.source,
                "rawMerchant" to entity.rawMerchant,
                "detectionReason" to entity.detectionReason,
                "notificationExcerpt" to entity.safeNotificationExcerpt,
                "fingerprint" to entity.transactionFingerprint
            ).filterValues { it.isNotBlank() },
            status = entity.status
        )
    }

    fun List<Transaction>.toFinancialTransactions(): List<FinancialTransaction> =
        map { fromUiTransaction(it) }

    fun List<TransactionEntity>.entitiesToFinancialTransactions(): List<FinancialTransaction> =
        map { fromEntity(it) }
}

internal fun String.normalizedFinanceText(): String =
    lowercase(Locale.US).replace(Regex("""\s+"""), " ").trim()
