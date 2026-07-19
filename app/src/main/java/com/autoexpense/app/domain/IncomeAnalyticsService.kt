package com.autoexpense.app.domain

object IncomeAnalyticsService {
    fun getTodayIncome(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): Double {
        val (start, end) = DateRangeService.getDayBounds(nowMs)
        return incomeBetween(transactions, start, end).sumOf { it.amount }
    }

    fun getWeeklyIncome(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): Double {
        val start = DateRangeService.getMondayOfWeek(nowMs)
        return incomeBetween(transactions, start, start + DateRangeService.DAY_MS * 7 - 1L).sumOf { it.amount }
    }

    fun getMonthlyIncome(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): Double {
        val (start, end) = DateRangeService.getCurrentMonthBounds(nowMs)
        return incomeBetween(transactions, start, end).sumOf { it.amount }
    }

    fun getYearlyIncome(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): Double {
        val (start, end) = DateRangeService.getYearBounds(nowMs)
        return incomeBetween(transactions, start, end).sumOf { it.amount }
    }

    fun getLargestIncome(transactions: List<FinancialTransaction>): FinancialTransaction? {
        return CashFlowService.confirmedIncome(transactions).maxByOrNull { it.amount }
    }

    fun getAverageIncome(transactions: List<FinancialTransaction>): Double {
        val income = CashFlowService.confirmedIncome(transactions)
        return if (income.isEmpty()) 0.0 else income.sumOf { it.amount } / income.size
    }

    fun getIncomeTrend(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): List<TimeBucketSummary> {
        return AnalyticsService.incomeTrend(transactions, nowMs)
    }

    fun getIncomeByCategory(transactions: List<FinancialTransaction>): List<CategorySummary> {
        return AnalyticsService.incomeByCategory(transactions)
    }

    fun getIncomeBySource(transactions: List<FinancialTransaction>): List<CategorySummary> {
        return AnalyticsService.incomeBySource(transactions)
    }

    private fun incomeBetween(transactions: List<FinancialTransaction>, startMs: Long, endMs: Long): List<FinancialTransaction> {
        return CashFlowService.confirmedIncome(transactions).filter { it.date in startMs..endMs }
    }
}
