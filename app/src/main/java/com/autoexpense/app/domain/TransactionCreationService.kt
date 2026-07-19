package com.autoexpense.app.domain

object TransactionCreationService {
    suspend fun create(transaction: FinancialTransaction): FinancialTransaction {
        val now = System.currentTimeMillis()
        val normalized = transaction.copy(
            amount = transaction.amount.coerceAtLeast(0.0),
            currency = transaction.currency.ifBlank { "INR" },
            title = transaction.title.ifBlank { transaction.merchant }.ifBlank { defaultTitle(transaction.transactionType) },
            merchant = transaction.merchant.ifBlank { transaction.title }.ifBlank { defaultTitle(transaction.transactionType) },
            category = transaction.category.ifBlank {
                DefaultCategoryProvider.getCategories(transaction.transactionType).firstOrNull()?.name ?: "Other"
            },
            createdAt = transaction.createdAt.takeIf { it > 0L } ?: now,
            updatedAt = now,
            status = transaction.status.ifBlank { "confirmed" }
        )
        FinancialTransactionRepository.insert(normalized)
        return normalized
    }

    private fun defaultTitle(type: TransactionType): String {
        return when (type) {
            TransactionType.INCOME -> "Income"
            TransactionType.REFUND -> "Refund"
            TransactionType.CASHBACK -> "Cashback"
            TransactionType.INTEREST -> "Interest"
            TransactionType.CREDIT_CARD_PAYMENT -> "Credit Card Payment"
            TransactionType.CREDIT_CARD_PURCHASE -> "Credit Card Purchase"
            TransactionType.TRANSFER -> "Transfer"
            else -> "Expense"
        }
    }
}
