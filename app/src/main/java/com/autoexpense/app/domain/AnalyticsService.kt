package com.autoexpense.app.domain

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class CategorySummary(
    val category: String,
    val amount: Double,
    val count: Int,
    val percentage: Double
)

data class TimeBucketSummary(
    val label: String,
    val amount: Float,
    val count: Int
)

data class MerchantSummary(
    val merchant: String,
    val amount: Double,
    val count: Int
)

object AnalyticsService {
    fun categorySummaries(
        transactions: List<FinancialTransaction>,
        startMs: Long,
        endMs: Long,
        cleanCategory: (String) -> String = { it }
    ): List<CategorySummary> {
        val expenses = CashFlowService.confirmedExpenses(transactions)
            .filter { it.date in startMs..endMs }
        val total = expenses.sumOf { it.amount }
        if (expenses.isEmpty()) return emptyList()
        return expenses
            .groupBy { cleanCategory(it.category).ifBlank { "Other" } }
            .map { (category, items) ->
                val amount = items.sumOf { it.amount }
                CategorySummary(
                    category = category,
                    amount = amount,
                    count = items.size,
                    percentage = if (total > 0.0) (amount / total) * 100.0 else 0.0
                )
            }
            .sortedByDescending { it.amount }
    }

    fun incomeByCategory(transactions: List<FinancialTransaction>): List<CategorySummary> {
        return breakdownByCategory(CashFlowService.confirmedIncome(transactions))
    }

    fun incomeBySource(transactions: List<FinancialTransaction>): List<CategorySummary> {
        return breakdownByLabel(CashFlowService.confirmedIncome(transactions)) { formatSourceName(it.notificationSource) }
    }

    fun expenseBreakdown(transactions: List<FinancialTransaction>): List<CategorySummary> {
        return breakdownByCategory(CashFlowService.confirmedExpenses(transactions))
    }

    fun incomeBreakdown(transactions: List<FinancialTransaction>): List<CategorySummary> {
        return incomeByCategory(transactions)
    }

    fun categoryBreakdown(transactions: List<FinancialTransaction>, type: TransactionType? = null): List<CategorySummary> {
        val scoped = if (type == null) {
            transactions.filter { it.isConfirmed && !it.isDeleted }
        } else {
            CashFlowService.confirmedByType(transactions, type)
        }
        return breakdownByCategory(scoped)
    }

    fun weeklyBuckets(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): List<TimeBucketSummary> {
        val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val mondayMs = DateRangeService.getMondayOfWeek(nowMs)
        return labels.mapIndexed { index, label ->
            val start = mondayMs + index * DateRangeService.DAY_MS
            val end = start + DateRangeService.DAY_MS - 1L
            val items = CashFlowService.confirmedExpenses(transactions).filter { it.date in start..end }
            TimeBucketSummary(label, items.sumOf { it.amount }.toFloat(), items.size)
        }
    }

    fun monthlyBuckets(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): List<TimeBucketSummary> {
        val labels = listOf("Wk 1", "Wk 2", "Wk 3", "Wk 4", "Wk 5")
        val (monthStart, monthEnd) = DateRangeService.getCurrentMonthBounds(nowMs)
        val monthItems = CashFlowService.confirmedExpenses(transactions).filter { it.date in monthStart..monthEnd }
        val sums = FloatArray(5)
        val counts = IntArray(5)
        val calendar = Calendar.getInstance()
        monthItems.forEach { transaction ->
            calendar.timeInMillis = transaction.date
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val bucket = when {
                day <= 7 -> 0
                day <= 14 -> 1
                day <= 21 -> 2
                day <= 28 -> 3
                else -> 4
            }
            sums[bucket] += transaction.amount.toFloat()
            counts[bucket] += 1
        }
        return labels.mapIndexed { index, label -> TimeBucketSummary(label, sums[index], counts[index]) }
    }

    fun incomeTrend(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): List<TimeBucketSummary> {
        return weeklyTrend(CashFlowService.confirmedIncome(transactions), nowMs)
    }

    fun expenseTrend(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): List<TimeBucketSummary> {
        return weeklyTrend(CashFlowService.confirmedExpenses(transactions), nowMs)
    }

    fun cashFlowTrend(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): List<TimeBucketSummary> {
        val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val mondayMs = DateRangeService.getMondayOfWeek(nowMs)
        return labels.mapIndexed { index, label ->
            val start = mondayMs + index * DateRangeService.DAY_MS
            val end = start + DateRangeService.DAY_MS - 1L
            val day = transactions.filter { it.date in start..end }
            TimeBucketSummary(label, CashFlowService.cashFlow(day).toFloat(), day.count { it.isConfirmed && !it.isDeleted })
        }
    }

    fun monthlySummary(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): CashFlowPeriodSummary {
        return CashFlowService.monthlyCashFlow(transactions, nowMs)
    }

    fun weeklySummary(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): CashFlowPeriodSummary {
        return CashFlowService.weeklyCashFlow(transactions, nowMs)
    }

    fun merchantStatistics(transactions: List<FinancialTransaction>): List<MerchantSummary> {
        return transactions
            .filter { it.isConfirmed && !it.isDeleted }
            .groupBy { it.merchant.ifBlank { it.title }.ifBlank { "Unknown Merchant" } }
            .map { (merchant, items) -> MerchantSummary(merchant, items.sumOf { it.amount }, items.size) }
            .sortedByDescending { it.amount }
    }

    fun recurringStatistics(transactions: List<FinancialTransaction>): List<MerchantSummary> {
        return merchantStatistics(transactions.filter { it.isRecurring })
    }

    fun sourceNames(transactions: List<FinancialTransaction>): List<String> {
        return CashFlowService.confirmedExpenses(transactions)
            .map { formatSourceName(it.notificationSource) }
            .filter { it.isNotBlank() && !it.equals("other", ignoreCase = true) }
            .distinct()
    }

    fun formatSourceName(source: String): String {
        return when (source.lowercase(Locale.US)) {
            "gpay", "googlepay" -> "GPay"
            "phonepe" -> "PhonePe"
            "paytm" -> "Paytm"
            "bhim" -> "BHIM"
            "banksms", "sms" -> "Bank SMS"
            else -> if (source.isNotBlank()) source.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() } else "Other"
        }
    }

    private fun weeklyTrend(transactions: List<FinancialTransaction>, nowMs: Long): List<TimeBucketSummary> {
        val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val mondayMs = DateRangeService.getMondayOfWeek(nowMs)
        return labels.mapIndexed { index, label ->
            val start = mondayMs + index * DateRangeService.DAY_MS
            val end = start + DateRangeService.DAY_MS - 1L
            val items = transactions.filter { it.date in start..end }
            TimeBucketSummary(label, items.sumOf { it.amount }.toFloat(), items.size)
        }
    }

    private fun breakdownByCategory(transactions: List<FinancialTransaction>): List<CategorySummary> {
        return breakdownByLabel(transactions) { it.category.ifBlank { "Other" } }
    }

    private fun breakdownByLabel(
        transactions: List<FinancialTransaction>,
        label: (FinancialTransaction) -> String
    ): List<CategorySummary> {
        val total = transactions.sumOf { it.amount }
        if (transactions.isEmpty()) return emptyList()
        return transactions
            .groupBy { label(it).ifBlank { "Other" } }
            .map { (name, items) ->
                val amount = items.sumOf { it.amount }
                CategorySummary(
                    category = name,
                    amount = amount,
                    count = items.size,
                    percentage = if (total > 0.0) (amount / total) * 100.0 else 0.0
                )
            }
            .sortedByDescending { it.amount }
    }
}

object DateRangeService {
    const val DAY_MS: Long = 86_400_000L

    fun getCurrentMonthBounds(nowMs: Long = System.currentTimeMillis(), timeZone: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        return start to calendar.timeInMillis
    }

    fun getPreviousMonthBounds(currentMonthStartMs: Long, timeZone: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = currentMonthStartMs
            add(Calendar.MILLISECOND, -1)
        }
        val end = calendar.timeInMillis
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis to end
    }

    fun getDayBounds(nowMs: Long = System.currentTimeMillis(), timeZone: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        return start to calendar.timeInMillis
    }

    fun getYearBounds(nowMs: Long = System.currentTimeMillis(), timeZone: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.YEAR, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        return start to calendar.timeInMillis
    }

    fun getMondayOfWeek(nowMs: Long = System.currentTimeMillis(), timeZone: TimeZone = TimeZone.getDefault()): Long {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs
            firstDayOfWeek = Calendar.MONDAY
        }
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = (dayOfWeek - Calendar.MONDAY + 7) % 7
        calendar.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
