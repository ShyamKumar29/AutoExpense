package com.autoexpense.app.domain

import com.autoexpense.app.Transaction

data class TransactionCategory(
    val name: String,
    val id: String
)

interface CategoryProvider {
    fun getCategories(transactionType: TransactionType): List<TransactionCategory>
    fun suggestCategory(transaction: FinancialTransaction): TransactionCategory?
}

object DefaultCategoryProvider : CategoryProvider {
    private val expenseCategories = listOf(
        "Food & Dining",
        "Travel",
        "Shopping",
        "Entertainment",
        "Bills",
        "Healthcare",
        "Education",
        "Fuel",
        "Groceries",
        "Other Expense"
    ).toCategories("expense")

    private val incomeCategories = listOf(
        "Salary",
        "Personal Transfer",
        "Business",
        "Freelancing",
        "Refund",
        "Cashback",
        "Interest",
        "Rental Income",
        "Gift",
        "Investment Return",
        "Bonus",
        "Other Income"
    ).toCategories("income")

    private val creditCardCategories = listOf(
        "Credit Card Purchase",
        "Credit Card Payment",
        "Fees",
        "Interest",
        "Rewards",
        "Other Credit"
    ).toCategories("credit")

    override fun getCategories(transactionType: TransactionType): List<TransactionCategory> {
        return when (transactionType) {
            TransactionType.INCOME,
            TransactionType.REFUND,
            TransactionType.CASHBACK,
            TransactionType.INTEREST -> incomeCategories
            TransactionType.CREDIT_CARD_PURCHASE,
            TransactionType.CREDIT_CARD_PAYMENT -> creditCardCategories
            else -> expenseCategories
        }
    }

    override fun suggestCategory(transaction: FinancialTransaction): TransactionCategory? {
        val text = listOf(
            transaction.title,
            transaction.merchant,
            transaction.category,
            transaction.notes,
            transaction.notificationSource,
            transaction.metadata["classificationReason"].orEmpty(),
            transaction.metadata["rawText"].orEmpty(),
            transaction.smsBody
        ).joinToString(" ").normalizedFinanceText()
        val categoryName = when (transaction.transactionType) {
            TransactionType.INCOME -> suggestIncome(text)
            TransactionType.REFUND -> "Refund"
            TransactionType.CASHBACK -> "Cashback"
            TransactionType.INTEREST -> "Interest"
            TransactionType.EXPENSE,
            TransactionType.CREDIT_CARD_PURCHASE -> suggestExpense(text)
            TransactionType.CREDIT_CARD_PAYMENT -> "Credit Card Payment"
            else -> getCategories(transaction.transactionType).firstOrNull()?.name
        } ?: return null
        return categoryByName(transaction.transactionType, categoryName)
    }

    fun suggestCategory(transaction: Transaction): TransactionCategory? {
        return suggestCategory(FinancialTransactionMapper.fromUiTransaction(transaction))
    }

    fun categoriesFor(transaction: Transaction): List<TransactionCategory> {
        return getCategories(FinancialTransactionMapper.fromUiTransaction(transaction).transactionType)
    }

    private fun suggestIncome(text: String): String {
        return when {
            text.contains("salary") -> "Salary"
            text.contains("refund") -> "Refund"
            text.contains("cashback") -> "Cashback"
            text.contains("interest") -> "Interest"
            text.contains("rent") || text.contains("rental") -> "Rental Income"
            text.contains("gift") -> "Gift"
            text.contains("bonus") -> "Bonus"
            text.contains("investment") || text.contains("dividend") || text.contains("return") -> "Investment Return"
            text.contains("business") -> "Business"
            text.contains("freelance") -> "Freelancing"
            text.contains("transfer") || text.contains("received") || text.contains("credited") -> "Personal Transfer"
            else -> "Other Income"
        }
    }

    private fun suggestExpense(text: String): String {
        return when {
            text.contains("food") || text.contains("dining") || text.contains("restaurant") || text.contains("swiggy") || text.contains("zomato") -> "Food & Dining"
            text.contains("travel") || text.contains("flight") || text.contains("hotel") -> "Travel"
            text.contains("shopping") || text.contains("amazon") || text.contains("flipkart") -> "Shopping"
            text.contains("entertainment") || text.contains("movie") || text.contains("netflix") -> "Entertainment"
            text.contains("bill") || text.contains("utility") || text.contains("electric") -> "Bills"
            text.contains("health") || text.contains("medical") || text.contains("pharma") -> "Healthcare"
            text.contains("school") || text.contains("education") || text.contains("tuition") -> "Education"
            text.contains("fuel") || text.contains("petrol") || text.contains("gas") -> "Fuel"
            text.contains("grocery") || text.contains("groceries") || text.contains("big basket") -> "Groceries"
            else -> "Other Expense"
        }
    }

    private fun categoryByName(type: TransactionType, name: String): TransactionCategory? {
        return getCategories(type).firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    private fun List<String>.toCategories(prefix: String): List<TransactionCategory> {
        return map { name ->
            TransactionCategory(
                name = name,
                id = "${prefix}_${name.lowercase().replace(Regex("""[^a-z0-9]+"""), "_").trim('_')}"
            )
        }
    }
}
