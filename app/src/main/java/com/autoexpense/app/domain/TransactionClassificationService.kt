package com.autoexpense.app.domain

import com.autoexpense.app.Transaction
import com.autoexpense.app.data.TransactionEntity

data class TransactionClassification(
    val transactionType: TransactionType,
    val confidenceScore: Float,
    val category: String,
    val merchant: String,
    val reason: String,
    val isAutoDetected: Boolean
)

object TransactionClassificationService {
    fun classify(transaction: Transaction): TransactionType {
        return classifyTransaction(
            amount = transaction.amount,
            status = transaction.status,
            merchant = transaction.merchant,
            category = transaction.category,
            text = listOf(
                transaction.merchant,
                transaction.category,
                transaction.note,
                transaction.detectionReason,
                transaction.notificationExcerpt
            ).joinToString(" ")
        )
    }

    fun classify(entity: TransactionEntity): TransactionType {
        return classifyTransaction(
            amount = entity.amount,
            status = entity.status,
            merchant = entity.merchantOrRecipient,
            category = entity.category,
            text = listOf(
                entity.merchantOrRecipient,
                entity.category,
                entity.note,
                entity.detectionReason,
                entity.safeNotificationExcerpt
            ).joinToString(" ")
        )
    }

    fun classifyEvent(
        rawText: String,
        amount: String = "",
        status: String = "",
        merchant: String = "",
        category: String = "",
        isAutoDetected: Boolean = true
    ): TransactionClassification {
        val lower = rawText.normalizedFinanceText()
        val transactionType = classifyTransaction(amount, status, merchant, category, rawText)
        val inferredCategory = when (transactionType) {
            TransactionType.INCOME -> incomeCategory(lower)
            TransactionType.REFUND -> "Refund"
            TransactionType.CASHBACK -> "Cashback"
            TransactionType.INTEREST -> "Interest"
            TransactionType.TRANSFER -> "Transfer"
            TransactionType.CREDIT_CARD_PAYMENT -> "Credit Card Payment"
            TransactionType.CREDIT_CARD_PURCHASE -> category.ifBlank { "Credit Card Purchase" }
            TransactionType.EXPENSE -> category.ifBlank { "❓ Unknown" }
            TransactionType.UNKNOWN -> category.ifBlank { "❓ Unknown" }
        }
        val reason = reasonFor(transactionType, lower)
        val confidence = when {
            transactionType == TransactionType.UNKNOWN -> 0.2f
            lower.isNotBlank() && reason != "No strong classification signal" -> 0.92f
            amount.isNotBlank() -> 0.65f
            else -> 0.45f
        }
        return TransactionClassification(
            transactionType = transactionType,
            confidenceScore = confidence,
            category = inferredCategory,
            merchant = merchant.ifBlank { extractCounterparty(rawText).ifBlank { "Unknown Merchant" } },
            reason = reason,
            isAutoDetected = isAutoDetected
        )
    }

    fun classifyNotificationText(text: String): TransactionType {
        val lower = text.normalizedFinanceText()
        return when {
            hasAny(lower, "cashback credited", "cashback received", "reward credited", "reward points converted", "cashback") -> TransactionType.CASHBACK
            hasAny(lower, "refund processed", "refund initiated", "refund successful", "merchant refund", "amazon refund", "flipkart refund", "payment reversed", "refund", "reversal", "reversed") -> TransactionType.REFUND
            hasAny(lower, "interest credited", "interest received", "savings interest", "fd interest", "bank interest") -> TransactionType.INTEREST
            hasAny(
                lower,
                "salary credited",
                "salary deposited",
                "amount credited",
                "rs credited",
                "inr credited",
                "upi received",
                "money received",
                "received from",
                "transfer received",
                "neft received",
                "imps received",
                "rtgs received",
                "bank transfer received",
                "credit alert",
                "deposit received",
                "cash deposit",
                "rental income",
                "freelancing payment",
                "business payment",
                "credited",
                "you received"
            ) -> TransactionType.INCOME
            hasAny(lower, "credit card payment", "card bill payment") -> TransactionType.CREDIT_CARD_PAYMENT
            hasAny(lower, "credit card", "card purchase") && hasAny(lower, "spent", "debited", "purchase") -> TransactionType.CREDIT_CARD_PURCHASE
            hasAny(lower, "transfer to self", "account transfer", "wallet transfer", "transferred between", "self transfer") -> TransactionType.TRANSFER
            hasAny(lower, "debited", "debit", "spent", "paid", "purchase", "withdrawal", "upi payment", "pos transaction", "atm withdrawal", "card purchase", "transaction successful") -> TransactionType.EXPENSE
            hasAny(lower, "transferred") && hasAny(lower, "to", "from") -> TransactionType.TRANSFER
            else -> TransactionType.UNKNOWN
        }
    }

    private fun classifyTransaction(
        amount: String,
        status: String,
        merchant: String,
        category: String,
        text: String
    ): TransactionType {
        val lower = text.normalizedFinanceText()
        if (status.equals("refund", ignoreCase = true)) return TransactionType.REFUND
        if (status.equals("incoming", ignoreCase = true)) return TransactionType.INCOME
        if (category.contains("refund", ignoreCase = true)) return TransactionType.REFUND
        if (category.contains("cashback", ignoreCase = true)) return TransactionType.CASHBACK
        if (category.contains("income", ignoreCase = true) || category.contains("salary", ignoreCase = true)) return TransactionType.INCOME

        val textType = classifyNotificationText("$merchant $category $lower")
        if (textType != TransactionType.UNKNOWN) return textType

        val trimmedAmount = amount.trim()
        return when {
            trimmedAmount.startsWith("+") -> TransactionType.INCOME
            trimmedAmount.startsWith("-") || trimmedAmount.startsWith("\u2212") -> TransactionType.EXPENSE
            else -> TransactionType.UNKNOWN
        }
    }

    private fun hasAny(text: String, vararg terms: String): Boolean =
        terms.any { text.contains(it) }

    private fun incomeCategory(text: String): String {
        return when {
            text.contains("salary") -> "Salary"
            text.contains("rent") || text.contains("rental") -> "Rental Income"
            text.contains("freelance") -> "Freelancing"
            text.contains("business") -> "Business Income"
            text.contains("deposit") -> "Deposit"
            else -> "Income"
        }
    }

    private fun reasonFor(type: TransactionType, text: String): String {
        return when (type) {
            TransactionType.INCOME -> "Incoming credit or received-money signal detected"
            TransactionType.REFUND -> "Refund or reversal signal detected"
            TransactionType.CASHBACK -> "Cashback or reward credit signal detected"
            TransactionType.INTEREST -> "Interest credit signal detected"
            TransactionType.TRANSFER -> "Internal transfer signal detected"
            TransactionType.CREDIT_CARD_PURCHASE -> "Credit card purchase signal detected"
            TransactionType.CREDIT_CARD_PAYMENT -> "Credit card payment signal detected"
            TransactionType.EXPENSE -> "Outgoing debit, spend, paid, purchase, or withdrawal signal detected"
            TransactionType.UNKNOWN -> if (text.isBlank()) "No text available for classification" else "No strong classification signal"
        }
    }

    private fun extractCounterparty(text: String): String {
        val normalized = text.replace(Regex("""\s+"""), " ").trim()
        val patterns = listOf(
            Regex("""(?i)received from\s+([A-Za-z0-9 ._@-]{2,40})"""),
            Regex("""(?i)paid to\s+([A-Za-z0-9 ._@-]{2,40})"""),
            Regex("""(?i)transferred to\s+([A-Za-z0-9 ._@-]{2,40})""")
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(normalized)?.groupValues?.getOrNull(1)?.trim()
        }.orEmpty()
    }
}
