package com.autoexpense.app.dashboard

import com.autoexpense.app.Transaction
import com.autoexpense.app.domain.AnalyticsService
import com.autoexpense.app.domain.CashFlowService
import com.autoexpense.app.domain.CategorySummary
import com.autoexpense.app.domain.DashboardFinancialSummary
import com.autoexpense.app.domain.DashboardService
import com.autoexpense.app.domain.FinancialTransaction
import com.autoexpense.app.domain.FinancialTransactionMapper
import com.autoexpense.app.domain.TimeBucketSummary
import com.autoexpense.app.domain.TransactionType

data class DashboardMetric(
    val label: String,
    val value: String,
    val supportingText: String = ""
)

data class DashboardRenderState(
    val transactions: List<Transaction>,
    val financialTransactions: List<FinancialTransaction>,
    val summary: DashboardFinancialSummary,
    val totalSpentLabel: String,
    val transactionCount: Int,
    val averageExpenseLabel: String,
    val pendingReviewCount: Int,
    val incomeMetric: DashboardMetric,
    val expenseMetric: DashboardMetric,
    val savingsMetric: DashboardMetric,
    val cashFlowMetric: DashboardMetric,
    val largestIncomeMetric: DashboardMetric,
    val largestExpenseMetric: DashboardMetric,
    val incomeCount: Int,
    val expenseCount: Int,
    val incomeTrend: List<TimeBucketSummary>,
    val expenseTrend: List<TimeBucketSummary>,
    val cashFlowTrend: List<TimeBucketSummary>,
    val incomeBreakdown: List<CategorySummary>,
    val expenseBreakdown: List<CategorySummary>,
    val merchantBreakdown: List<com.autoexpense.app.domain.MerchantSummary>
)

object DashboardRenderStateBuilder {
    fun build(
        transactions: List<Transaction>,
        pendingReviewCount: Int,
        nowMs: Long = System.currentTimeMillis()
    ): DashboardRenderState {
        val financialTransactions = with(FinancialTransactionMapper) {
            transactions.toFinancialTransactions()
        }
        val summary = DashboardService.dashboardSummary(financialTransactions, nowMs)
        val confirmedExpenses = CashFlowService.confirmedExpenses(financialTransactions)
        val totalSpent = DashboardService.totalSpentLabel(financialTransactions, nowMs)
        val averageExpense = if (confirmedExpenses.isNotEmpty()) {
            summary.monthlyExpense / confirmedExpenses.size
        } else {
            0.0
        }
        val incomeCount = summary.transactionCounts[TransactionType.INCOME] ?: 0
        val expenseCount = confirmedExpenses.size
        val incomeGrowth = CashFlowService.incomeGrowth(financialTransactions, nowMs)
        val expenseGrowth = CashFlowService.expenseGrowth(financialTransactions, nowMs)
        val topIncomeSource = AnalyticsService.incomeBySource(financialTransactions).firstOrNull()
        val topExpenseCategory = AnalyticsService.expenseBreakdown(financialTransactions).firstOrNull()

        return DashboardRenderState(
            transactions = transactions,
            financialTransactions = financialTransactions,
            summary = summary,
            totalSpentLabel = totalSpent,
            transactionCount = confirmedExpenses.size,
            averageExpenseLabel = CashFlowService.formatCompactIndianCurrency(averageExpense),
            pendingReviewCount = pendingReviewCount,
            incomeMetric = DashboardMetric(
                label = "Total Income",
                value = CashFlowService.formatCompactIndianCurrency(summary.monthlyIncome),
                supportingText = growthText(incomeGrowth, positiveIsGood = true)
            ),
            expenseMetric = DashboardMetric(
                label = "Total Expenses",
                value = CashFlowService.formatCompactIndianCurrency(summary.monthlyExpense),
                supportingText = growthText(expenseGrowth, positiveIsGood = false)
            ),
            savingsMetric = DashboardMetric(
                label = "Net Savings",
                value = CashFlowService.formatCompactIndianCurrency(summary.netSavings),
                supportingText = "Savings rate ${savingsRate(summary.monthlyIncome, summary.monthlyExpense)}"
            ),
            cashFlowMetric = DashboardMetric(
                label = "Cash Flow",
                value = CashFlowService.formatCompactIndianCurrency(summary.cashFlowSummary.netCashFlow),
                supportingText = if (summary.cashFlowSummary.netCashFlow >= 0.0) "Positive flow" else "Negative flow"
            ),
            largestIncomeMetric = DashboardMetric(
                label = "Largest Income",
                value = CashFlowService.formatCompactIndianCurrency(summary.largestIncome?.amount ?: 0.0),
                supportingText = topIncomeSource?.category ?: summary.largestIncome?.merchant.orEmpty().ifBlank { "No income yet" }
            ),
            largestExpenseMetric = DashboardMetric(
                label = "Largest Expense",
                value = CashFlowService.formatCompactIndianCurrency(summary.largestExpense?.amount ?: 0.0),
                supportingText = topExpenseCategory?.category ?: summary.largestExpense?.merchant.orEmpty().ifBlank { "No expenses yet" }
            ),
            incomeCount = incomeCount,
            expenseCount = expenseCount,
            incomeTrend = AnalyticsService.incomeTrend(financialTransactions, nowMs),
            expenseTrend = AnalyticsService.expenseTrend(financialTransactions, nowMs),
            cashFlowTrend = AnalyticsService.cashFlowTrend(financialTransactions, nowMs),
            incomeBreakdown = AnalyticsService.incomeBreakdown(financialTransactions),
            expenseBreakdown = AnalyticsService.expenseBreakdown(financialTransactions),
            merchantBreakdown = AnalyticsService.merchantStatistics(financialTransactions)
        )
    }

    private fun savingsRate(income: Double, expenses: Double): String {
        if (income <= 0.0) return "0%"
        val rate = ((income - expenses) / income) * 100.0
        return String.format(java.util.Locale.US, "%.1f%%", rate)
    }

    private fun growthText(growthPercent: Double?, positiveIsGood: Boolean): String {
        if (growthPercent == null) return "New this month"
        val direction = when {
            growthPercent > 0.0 -> "+"
            else -> ""
        }
        val label = String.format(java.util.Locale.US, "%s%.1f%% vs last month", direction, growthPercent)
        return if (positiveIsGood || growthPercent <= 0.0) label else label
    }
}
