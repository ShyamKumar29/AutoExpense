package com.autoexpense.app.domain

import java.util.Locale

data class DashboardCategoryHighlight(
    val label: String,
    val amount: Double,
    val count: Int
)

data class DashboardFinancialSummary(
    val todayIncome: Double,
    val todayExpense: Double,
    val monthlyIncome: Double,
    val monthlyExpense: Double,
    val netSavings: Double,
    val largestIncome: FinancialTransaction?,
    val largestExpense: FinancialTransaction?,
    val cashFlowSummary: CashFlowSummary,
    val transactionCounts: Map<TransactionType, Int>
)

object DashboardService {
    fun todayIncome(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): Double {
        val (start, end) = DateRangeService.getDayBounds(nowMs)
        return CashFlowService.confirmedIncome(transactions).filter { it.date in start..end }.sumOf { it.amount }
    }

    fun todayExpense(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): Double {
        val (start, end) = DateRangeService.getDayBounds(nowMs)
        return CashFlowService.confirmedExpenses(transactions).filter { it.date in start..end }.sumOf { it.amount }
    }

    fun currentMonthIncome(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): Double {
        val (start, end) = DateRangeService.getCurrentMonthBounds(nowMs)
        return CashFlowService.confirmedIncome(transactions)
            .filter { it.date in start..end }
            .sumOf { it.amount }
    }

    fun currentMonthSpent(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): Double {
        val (start, end) = DateRangeService.getCurrentMonthBounds(nowMs)
        return CashFlowService.confirmedExpenses(transactions)
            .filter { it.date in start..end }
            .sumOf { it.amount }
    }

    fun dashboardSummary(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): DashboardFinancialSummary {
        val confirmed = transactions.filter { it.isConfirmed && !it.isDeleted }
        val monthlyIncome = currentMonthIncome(transactions, nowMs)
        val monthlyExpense = currentMonthSpent(transactions, nowMs)
        return DashboardFinancialSummary(
            todayIncome = todayIncome(transactions, nowMs),
            todayExpense = todayExpense(transactions, nowMs),
            monthlyIncome = monthlyIncome,
            monthlyExpense = monthlyExpense,
            netSavings = monthlyIncome - monthlyExpense,
            largestIncome = CashFlowService.confirmedIncome(transactions).maxByOrNull { it.amount },
            largestExpense = CashFlowService.confirmedExpenses(transactions).maxByOrNull { it.amount },
            cashFlowSummary = CashFlowService.summarize(transactions),
            transactionCounts = confirmed.groupingBy { it.transactionType }.eachCount()
        )
    }

    fun monthlyChangePercent(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): Double? {
        val (thisStart, thisEnd) = DateRangeService.getCurrentMonthBounds(nowMs)
        val (lastStart, lastEnd) = DateRangeService.getPreviousMonthBounds(thisStart)
        val expenses = CashFlowService.confirmedExpenses(transactions)
        val thisSum = expenses.filter { it.date in thisStart..thisEnd }.sumOf { it.amount }
        val lastSum = expenses.filter { it.date in lastStart..lastEnd }.sumOf { it.amount }
        if (lastSum == 0.0) return if (thisSum == 0.0) 0.0 else null
        return ((thisSum - lastSum) / lastSum) * 100.0
    }

    fun topCategory(
        transactions: List<FinancialTransaction>,
        nowMs: Long = System.currentTimeMillis(),
        cleanCategory: (String) -> String = { it }
    ): DashboardCategoryHighlight? {
        val (start, end) = DateRangeService.getCurrentMonthBounds(nowMs)
        return AnalyticsService.categorySummaries(transactions, start, end, cleanCategory)
            .firstOrNull()
            ?.let { DashboardCategoryHighlight(it.category, it.amount, it.count) }
    }

    fun weeklyChart(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): List<TimeBucketSummary> {
        return AnalyticsService.weeklyBuckets(transactions, nowMs)
    }

    fun monthlyChart(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): List<TimeBucketSummary> {
        return AnalyticsService.monthlyBuckets(transactions, nowMs)
    }

    fun totalSpentLabel(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): String {
        return CashFlowService.formatCompactIndianCurrency(currentMonthSpent(transactions, nowMs))
    }

    fun topCategorySubText(highlight: DashboardCategoryHighlight?): String {
        if (highlight == null) return "\u20B90 · 0 txns"
        val sumStr = "\u20B9" + if (highlight.amount == 0.0) "0" else String.format(Locale.US, "%,.0f", highlight.amount)
        return "$sumStr · ${highlight.count} " + if (highlight.count == 1) "txn" else "txns"
    }
}
