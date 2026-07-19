package com.autoexpense.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.autoexpense.app.domain.FinancialTransaction
import com.autoexpense.app.domain.TransactionType

@Entity(tableName = "financial_transactions")
data class FinancialTransactionEntity(
    @PrimaryKey
    val id: String,
    val transactionType: String,
    val amount: Double,
    val currency: String = "INR",
    val title: String,
    val category: String,
    val subCategory: String = "",
    val merchant: String = "",
    val accountId: String? = null,
    val paymentMethod: String = PaymentMethod.UNKNOWN.name,
    val referenceNumber: String = "",
    val notes: String = "",
    val date: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val location: String = "",
    val tags: String = "",
    val isRecurring: Boolean = false,
    val isAutoDetected: Boolean = false,
    val smsBody: String = "",
    val notificationSource: String = "",
    val metadata: String = "",
    val isDeleted: Boolean = false,
    val budgetId: Long? = null,
    val billId: String? = null,
    val subscriptionId: String? = null,
    val creditCardId: String? = null,
    val status: String = ""
) {
    fun toDomain(): FinancialTransaction {
        return FinancialTransaction(
            id = id,
            transactionType = runCatching { TransactionType.valueOf(transactionType) }.getOrDefault(TransactionType.UNKNOWN),
            amount = amount,
            currency = currency,
            title = title,
            category = category,
            subCategory = subCategory,
            merchant = merchant,
            accountId = accountId,
            paymentMethod = paymentMethod,
            referenceNumber = referenceNumber,
            notes = notes,
            date = date,
            createdAt = createdAt,
            updatedAt = updatedAt,
            location = location,
            tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
            isRecurring = isRecurring,
            isAutoDetected = isAutoDetected,
            smsBody = smsBody,
            notificationSource = notificationSource,
            metadata = metadata.split("|")
                .mapNotNull {
                    val idx = it.indexOf("=")
                    if (idx <= 0) null else it.substring(0, idx) to it.substring(idx + 1)
                }
                .toMap(),
            isDeleted = isDeleted,
            status = status
        )
    }

    companion object {
        fun fromDomain(transaction: FinancialTransaction): FinancialTransactionEntity {
            return FinancialTransactionEntity(
                id = transaction.id,
                transactionType = transaction.transactionType.name,
                amount = transaction.amount,
                currency = transaction.currency,
                title = transaction.title,
                category = transaction.category,
                subCategory = transaction.subCategory,
                merchant = transaction.merchant,
                accountId = transaction.accountId,
                paymentMethod = transaction.paymentMethod,
                referenceNumber = transaction.referenceNumber,
                notes = transaction.notes,
                date = transaction.date,
                createdAt = transaction.createdAt,
                updatedAt = transaction.updatedAt,
                location = transaction.location,
                tags = transaction.tags.joinToString(","),
                isRecurring = transaction.isRecurring,
                isAutoDetected = transaction.isAutoDetected,
                smsBody = transaction.smsBody,
                notificationSource = transaction.notificationSource,
                metadata = transaction.metadata.entries.joinToString("|") { "${it.key}=${it.value}" },
                isDeleted = transaction.isDeleted,
                status = transaction.status
            )
        }
    }
}
