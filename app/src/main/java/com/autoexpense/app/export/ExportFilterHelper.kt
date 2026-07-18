package com.autoexpense.app.export

import com.autoexpense.app.data.PeriodType
import com.autoexpense.app.data.TransactionEntity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ExportFilterHelper {

    const val DATE_THIS_WEEK = "This Week"
    const val DATE_THIS_MONTH = "This Month"
    const val DATE_LAST_MONTH = "Last Month"
    const val DATE_ALL_TIME = "All Time"
    const val DATE_CUSTOM = "Custom Date Range"

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

    /**
     * Filters transactions ensuring:
     * - Only CONFIRMED outgoing expenses are included.
     * - Excludes Needs Review, Ignored, Incoming, Failed, and Refund transactions.
     * - Applies the selected date range and optional category filter.
     */
    fun filterTransactions(
        allTransactions: List<TransactionEntity>,
        dateFilter: String,
        categoryFilter: String,
        customStartMs: Long? = null,
        customEndMs: Long? = null,
        nowMs: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault()
    ): List<TransactionEntity> {
        // 1. Exclude non-confirmed and non-outgoing transactions
        val confirmedOutgoing = allTransactions.filter { isConfirmedOutgoingExpense(it) }

        // 2. Apply date range
        val dateFiltered = filterDateRange(confirmedOutgoing, dateFilter, customStartMs, customEndMs, nowMs, tz)

        // 3. Apply category filter
        val finalFiltered = filterCategory(dateFiltered, categoryFilter)

        // Sort newest first
        return finalFiltered.sortedByDescending { it.timestamp }
    }

    fun isConfirmedOutgoingExpense(t: TransactionEntity): Boolean {
        if (!t.status.equals("confirmed", ignoreCase = true)) {
            return false
        }
        val lowerStatus = t.status.lowercase(Locale.US)
        if (lowerStatus == "review" || lowerStatus == "ignored" || lowerStatus == "failed" || lowerStatus == "refund" || lowerStatus == "incoming") {
            return false
        }
        val lowerDesc = "${t.merchantOrRecipient} ${t.note} ${t.detectionReason}".lowercase(Locale.US)
        if (lowerDesc.contains("refund") || lowerDesc.contains("incoming payment") || lowerDesc.contains("credit received")) {
            // If explicit refund/incoming
            return false
        }
        // Check amount string sign if explicit + exists
        val amtStr = t.amount.trim()
        if (amtStr.startsWith("+") || amtStr.contains("+₹")) {
            return false
        }
        return true
    }

    fun filterDateRange(
        transactions: List<TransactionEntity>,
        dateFilter: String,
        customStartMs: Long? = null,
        customEndMs: Long? = null,
        nowMs: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault()
    ): List<TransactionEntity> {
        val bounds = getDateRangeBounds(dateFilter, customStartMs, customEndMs, nowMs, tz)
        val start = bounds.first
        val end = bounds.second
        return transactions.filter { it.timestamp in start..end }
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
            DATE_CUSTOM -> {
                val start = customStartMs ?: 0L
                val end = customEndMs ?: Long.MAX_VALUE
                Pair(start, end)
            }
            else -> {
                // All Time
                Pair(0L, Long.MAX_VALUE)
            }
        }
    }

    fun filterCategory(
        transactions: List<TransactionEntity>,
        categoryFilter: String
    ): List<TransactionEntity> {
        if (categoryFilter == CAT_ALL || categoryFilter.isEmpty()) {
            return transactions
        }
        return transactions.filter { matchesCategory(it.category, categoryFilter) }
    }

    fun matchesCategory(transactionCategory: String, categoryFilter: String): Boolean {
        if (categoryFilter == CAT_ALL) return true
        val cleanTx = cleanCategoryString(transactionCategory)
        val cleanFilter = cleanCategoryString(categoryFilter)

        if (categoryFilter == CAT_OTHER) {
            val knownKeywords = listOf(
                "food", "dining", "transport", "groceries", "shopping",
                "healthcare", "entertainment", "rent", "bills", "personal", "transfer", "travel"
            )
            val customNames = com.autoexpense.app.data.CustomCategoryRepository.customCategories.value.map { cleanCategoryString(it.name) }
            val isKnown = knownKeywords.any { cleanTx.contains(it, ignoreCase = true) } || customNames.any { cleanTx.equals(it, ignoreCase = true) }
            return !isKnown
        }

        return cleanTx.equals(cleanFilter, ignoreCase = true) ||
                cleanTx.contains(cleanFilter, ignoreCase = true) ||
                cleanFilter.contains(cleanTx, ignoreCase = true)
    }

    private fun cleanCategoryString(cat: String): String {
        return cat.replace(Regex("[^a-zA-Z0-9 &/]"), "").trim()
    }

    fun calculateTotalSpent(transactions: List<TransactionEntity>): Double {
        return transactions.sumOf { parseAmount(it.amount) }
    }

    fun parseAmount(amountStr: String): Double {
        return amountStr
            .replace("−₹", "")
            .replace("₹", "")
            .replace("-", "")
            .replace("−", "")
            .replace(",", "")
            .trim()
            .toDoubleOrNull() ?: 0.0
    }

    fun formatIndianCurrency(amount: Double): String {
        val formatted = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(amount)
        return "₹$formatted"
    }

    fun escapeCsv(value: String): String {
        val needsQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
        return if (needsQuote) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    fun generateCsvContent(transactions: List<TransactionEntity>): String {
        val sb = StringBuilder()
        sb.append("Transaction ID,Date,Time,Merchant or Recipient,Category,Payment Source,Payment Method,Note,Amount,Currency,Status\r\n")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

        for (t in transactions) {
            val dateStr = if (t.timestamp > 0) dateFormat.format(Date(t.timestamp)) else ""
            val timeStr = if (t.timestamp > 0) timeFormat.format(Date(t.timestamp)) else ""
            val cleanAmt = String.format(Locale.US, "%.2f", parseAmount(t.amount))

            val row = listOf(
                escapeCsv(t.id),
                escapeCsv(dateStr),
                escapeCsv(timeStr),
                escapeCsv(t.merchantOrRecipient),
                escapeCsv(t.category),
                escapeCsv(t.source),
                escapeCsv(com.autoexpense.app.data.PaymentMethod.labelFor(t.paymentMethod)),
                escapeCsv(t.note),
                escapeCsv(cleanAmt),
                escapeCsv(if (t.currency.isNotEmpty()) t.currency else "INR"),
                escapeCsv(t.status)
            )
            sb.append(row.joinToString(separator = ",")).append("\r\n")
        }
        return sb.toString()
    }
}
