package com.autoexpense.app.export

import com.autoexpense.app.data.PaymentMethod
import com.autoexpense.app.domain.FinancialTransaction
import com.autoexpense.app.domain.TransactionType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ExportSummary(
    val income: Double,
    val expenses: Double,
    val netSavings: Double,
    val cashFlow: Double,
    val largestIncome: Double,
    val largestExpense: Double,
    val transactionCount: Int,
    val dateRangeLabel: String
)

object ExportFilterHelper {

    const val DATE_THIS_WEEK = "This Week"
    const val DATE_THIS_MONTH = "This Month"
    const val DATE_LAST_MONTH = "Last Month"
    const val DATE_ALL_TIME = "All Time"
    const val DATE_CUSTOM = "Custom Date Range"

    const val TYPE_ALL = "All Transactions"
    const val TYPE_EXPENSES = "Expenses Only"
    const val TYPE_INCOME = "Income Only"
    const val TYPE_REFUNDS = "Refunds"
    const val TYPE_CASHBACK = "Cashback"
    const val TYPE_TRANSFERS = "Transfers"

    const val CAT_ALL = "All Categories"
    const val CAT_FOOD = "Food & Dining"
    const val CAT_TRANSPORT = "Transport"
    const val CAT_SHOPPING = "Shopping"
    const val CAT_GROCERIES = "Groceries"
    const val CAT_HEALTHCARE = "Healthcare"
    const val CAT_ENTERTAINMENT = "Entertainment"
    const val CAT_RENT = "Rent / Bills"
    const val CAT_PERSONAL = "Personal Transfer"
    const val CAT_OTHER = "Other"

    const val MERCHANT_ALL = "All Merchants"
    const val PAYMENT_ALL = "All Payment Methods"

    fun filterTransactions(
        allTransactions: List<FinancialTransaction>,
        dateFilter: String,
        categoryFilter: String,
        transactionTypeFilter: String = TYPE_ALL,
        merchantFilter: String = MERCHANT_ALL,
        paymentMethodFilter: String = PAYMENT_ALL,
        customStartMs: Long? = null,
        customEndMs: Long? = null,
        nowMs: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault()
    ): List<FinancialTransaction> {
        val confirmed = allTransactions
            .asSequence()
            .filter { it.isConfirmed }
            .filterNot { it.isDeleted }
            .toList()

        return confirmed
            .let { filterDateRange(it, dateFilter, customStartMs, customEndMs, nowMs, tz) }
            .let { filterTransactionType(it, transactionTypeFilter) }
            .let { filterCategory(it, categoryFilter) }
            .let { filterMerchant(it, merchantFilter) }
            .let { filterPaymentMethod(it, paymentMethodFilter) }
            .distinctBy { it.id }
            .sortedByDescending { it.date }
    }

    fun filterTransactionType(
        transactions: List<FinancialTransaction>,
        typeFilter: String
    ): List<FinancialTransaction> {
        return when (typeFilter) {
            TYPE_EXPENSES -> transactions.filter { isExpenseType(it.transactionType) }
            TYPE_INCOME -> transactions.filter { it.transactionType == TransactionType.INCOME }
            TYPE_REFUNDS -> transactions.filter { it.transactionType == TransactionType.REFUND }
            TYPE_CASHBACK -> transactions.filter { it.transactionType == TransactionType.CASHBACK }
            TYPE_TRANSFERS -> transactions.filter { it.transactionType == TransactionType.TRANSFER }
            TYPE_ALL, "" -> transactions
            else -> transactions.filter { it.transactionType.name.equals(typeFilter, ignoreCase = true) }
        }
    }

    fun filterDateRange(
        transactions: List<FinancialTransaction>,
        dateFilter: String,
        customStartMs: Long? = null,
        customEndMs: Long? = null,
        nowMs: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault()
    ): List<FinancialTransaction> {
        val bounds = getDateRangeBounds(dateFilter, customStartMs, customEndMs, nowMs, tz)
        return transactions.filter { it.date in bounds.first..bounds.second }
    }

    fun getDateRangeBounds(
        dateFilter: String,
        customStartMs: Long? = null,
        customEndMs: Long? = null,
        nowMs: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault()
    ): Pair<Long, Long> {
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = nowMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return when (dateFilter) {
            DATE_THIS_WEEK -> {
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                val daysToMon = (dow - Calendar.MONDAY + 7) % 7
                cal.add(Calendar.DAY_OF_MONTH, -daysToMon)
                val startMs = cal.timeInMillis

                cal.add(Calendar.DAY_OF_MONTH, 7)
                cal.add(Calendar.MILLISECOND, -1)
                Pair(startMs, cal.timeInMillis)
            }
            DATE_THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val startMs = cal.timeInMillis

                cal.add(Calendar.MONTH, 1)
                cal.add(Calendar.MILLISECOND, -1)
                Pair(startMs, cal.timeInMillis)
            }
            DATE_LAST_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MILLISECOND, -1)
                val endMs = cal.timeInMillis

                cal.add(Calendar.MILLISECOND, 1)
                cal.add(Calendar.MONTH, -1)
                val startMs = cal.timeInMillis
                Pair(startMs, endMs)
            }
            DATE_CUSTOM -> Pair(customStartMs ?: 0L, customEndMs ?: Long.MAX_VALUE)
            else -> Pair(0L, Long.MAX_VALUE)
        }
    }

    fun filterCategory(
        transactions: List<FinancialTransaction>,
        categoryFilter: String
    ): List<FinancialTransaction> {
        if (categoryFilter == CAT_ALL || categoryFilter.isEmpty()) {
            return transactions
        }
        return transactions.filter { matchesCategory(it.category, categoryFilter) }
    }

    fun filterMerchant(
        transactions: List<FinancialTransaction>,
        merchantFilter: String
    ): List<FinancialTransaction> {
        if (merchantFilter == MERCHANT_ALL || merchantFilter.isEmpty()) {
            return transactions
        }
        return transactions.filter {
            it.merchant.equals(merchantFilter, ignoreCase = true) ||
                it.title.equals(merchantFilter, ignoreCase = true)
        }
    }

    fun filterPaymentMethod(
        transactions: List<FinancialTransaction>,
        paymentMethodFilter: String
    ): List<FinancialTransaction> {
        if (paymentMethodFilter == PAYMENT_ALL || paymentMethodFilter.isEmpty()) {
            return transactions
        }
        return transactions.filter {
            paymentMethodLabel(it.paymentMethod).equals(paymentMethodFilter, ignoreCase = true) ||
                it.paymentMethod.equals(paymentMethodFilter, ignoreCase = true)
        }
    }

    fun matchesCategory(transactionCategory: String, categoryFilter: String): Boolean {
        if (categoryFilter == CAT_ALL) return true
        val cleanTx = cleanCategoryString(transactionCategory)
        val cleanFilter = cleanCategoryString(categoryFilter)

        if (categoryFilter == CAT_OTHER) {
            val knownKeywords = listOf(
                "food", "dining", "transport", "groceries", "shopping",
                "healthcare", "entertainment", "rent", "bills", "personal",
                "transfer", "travel", "salary", "refund", "cashback", "interest",
                "business", "freelancing", "gift", "investment", "bonus"
            )
            val customNames = com.autoexpense.app.data.CustomCategoryRepository.customCategories.value
                .map { cleanCategoryString(it.name) }
            val isKnown = knownKeywords.any { cleanTx.contains(it, ignoreCase = true) } ||
                customNames.any { cleanTx.equals(it, ignoreCase = true) }
            return !isKnown
        }

        return cleanTx.equals(cleanFilter, ignoreCase = true) ||
            cleanTx.contains(cleanFilter, ignoreCase = true) ||
            cleanFilter.contains(cleanTx, ignoreCase = true)
    }

    fun calculateTotalSpent(transactions: List<FinancialTransaction>): Double {
        return transactions.filter { isExpenseType(it.transactionType) }.sumOf { it.amount }
    }

    fun calculateSummary(
        transactions: List<FinancialTransaction>,
        dateRangeLabel: String
    ): ExportSummary {
        val income = transactions.filter { isIncomeType(it.transactionType) }.sumOf { it.amount }
        val expenses = transactions.filter { isExpenseType(it.transactionType) }.sumOf { it.amount }
        return ExportSummary(
            income = income,
            expenses = expenses,
            netSavings = income - expenses,
            cashFlow = income - expenses,
            largestIncome = transactions.filter { isIncomeType(it.transactionType) }.maxOfOrNull { it.amount } ?: 0.0,
            largestExpense = transactions.filter { isExpenseType(it.transactionType) }.maxOfOrNull { it.amount } ?: 0.0,
            transactionCount = transactions.size,
            dateRangeLabel = dateRangeLabel
        )
    }

    fun signedAmount(transaction: FinancialTransaction): Double {
        return when {
            isExpenseType(transaction.transactionType) -> -transaction.amount
            isIncomeType(transaction.transactionType) -> transaction.amount
            else -> transaction.amount
        }
    }

    fun formatSignedAmount(transaction: FinancialTransaction): String {
        val sign = when {
            isExpenseType(transaction.transactionType) -> "-"
            isIncomeType(transaction.transactionType) -> "+"
            else -> ""
        }
        return sign + formatIndianCurrency(transaction.amount)
    }

    fun isExpenseType(type: TransactionType): Boolean {
        return type == TransactionType.EXPENSE ||
            type == TransactionType.CREDIT_CARD_PURCHASE ||
            type == TransactionType.CREDIT_CARD_PAYMENT
    }

    fun isIncomeType(type: TransactionType): Boolean {
        return type == TransactionType.INCOME ||
            type == TransactionType.REFUND ||
            type == TransactionType.CASHBACK ||
            type == TransactionType.INTEREST
    }

    fun formatIndianCurrency(amount: Double): String {
        val formatted = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(amount)
        return "\u20B9$formatted"
    }

    fun formatPlainAmount(amount: Double): String {
        return String.format(Locale.US, "%.2f", amount)
    }

    fun paymentMethodLabel(paymentMethod: String): String {
        val raw = paymentMethod.trim()
        if (raw.isBlank()) return PaymentMethod.UNKNOWN.label
        val known = PaymentMethod.fromStored(raw)
        return if (known == PaymentMethod.UNKNOWN && !raw.equals(PaymentMethod.UNKNOWN.name, ignoreCase = true)) {
            raw.replace('_', ' ')
        } else {
            known.label
        }
    }

    fun escapeCsv(value: String): String {
        val needsQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
        return if (needsQuote) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    fun generateCsvContent(transactions: List<FinancialTransaction>): String {
        val sb = StringBuilder()
        sb.append("Date,Time,Transaction Type,Title,Counterparty,Category,Merchant,Payment Method,Amount,Currency,Notes,Reference Number,Auto Detected,Recurring,Status\r\n")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

        for (t in transactions) {
            val dateStr = if (t.date > 0) dateFormat.format(Date(t.date)) else ""
            val timeStr = if (t.date > 0) timeFormat.format(Date(t.date)) else ""
            val title = t.title.ifBlank { t.merchant }.ifBlank { t.transactionType.name.replace('_', ' ') }
            val counterparty = t.merchant.ifBlank { title }
            val amount = signedAmount(t)
            val amountStr = if (amount > 0) {
                "+${formatPlainAmount(amount)}"
            } else {
                formatPlainAmount(amount)
            }

            val row = listOf(
                escapeCsv(dateStr),
                escapeCsv(timeStr),
                escapeCsv(t.transactionType.name),
                escapeCsv(title),
                escapeCsv(counterparty),
                escapeCsv(t.category),
                escapeCsv(t.merchant),
                escapeCsv(paymentMethodLabel(t.paymentMethod)),
                escapeCsv(amountStr),
                escapeCsv(t.currency.ifBlank { "INR" }),
                escapeCsv(t.notes),
                escapeCsv(t.referenceNumber),
                escapeCsv(t.isAutoDetected.toString()),
                escapeCsv(t.isRecurring.toString()),
                escapeCsv(t.status)
            )
            sb.append(row.joinToString(separator = ",")).append("\r\n")
        }
        return sb.toString()
    }

    fun periodLabel(
        dateFilter: String,
        customStartMs: Long? = null,
        customEndMs: Long? = null,
        nowMs: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault()
    ): String {
        val (start, end) = getDateRangeBounds(dateFilter, customStartMs, customEndMs, nowMs, tz)
        if (dateFilter == DATE_ALL_TIME || start == 0L || end == Long.MAX_VALUE) {
            return DATE_ALL_TIME
        }
        val format = SimpleDateFormat("d MMM yyyy", Locale.US)
        return "${format.format(Date(start))} - ${format.format(Date(end))}"
    }

    private fun cleanCategoryString(cat: String): String {
        return cat.replace(Regex("[^a-zA-Z0-9 &/]"), "").trim()
    }
}
