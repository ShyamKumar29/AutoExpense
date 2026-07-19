package com.autoexpense.app.domain

import java.text.NumberFormat
import java.util.Locale

data class CashFlowSummary(
    val income: Double,
    val expenses: Double,
    val refunds: Double,
    val cashback: Double,
    val interest: Double,
    val savings: Double,
    val netCashFlow: Double
)

data class CashFlowPeriodSummary(
    val startMs: Long,
    val endMs: Long,
    val income: Double,
    val expenses: Double,
    val netSavings: Double,
    val cashFlow: Double
)

object CashFlowService {
    fun parseAmount(amountStr: String): Double {
        return amountStr
            .replace("\u2212\u20B9", "")
            .replace("-\u20B9", "")
            .replace("\u20B9", "")
            .replace("\u2212", "")
            .replace("-", "")
            .replace(",", "")
            .trim()
            .toDoubleOrNull() ?: 0.0
    }

    fun formatIndianCurrency(amount: Double): String {
        val formatted = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(amount)
        return "\u20B9$formatted"
    }

    fun formatCompactIndianCurrency(amount: Double): String {
        return "\u20B9" + if (amount == 0.0) "0" else String.format(Locale.US, "%,.2f", amount)
    }

    fun confirmedExpenses(transactions: List<FinancialTransaction>): List<FinancialTransaction> {
        return transactions
            .asSequence()
            .filter { it.isConfirmed }
            .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT_CARD_PURCHASE }
            .filterNot { it.isDeleted }
            .distinctBy { it.id }
            .toList()
    }

    fun confirmedIncome(transactions: List<FinancialTransaction>): List<FinancialTransaction> {
        return transactions
            .asSequence()
            .filter { it.isConfirmed }
            .filter { it.transactionType == TransactionType.INCOME }
            .filterNot { it.isDeleted }
            .distinctBy { it.id }
            .toList()
    }

    fun confirmedByType(transactions: List<FinancialTransaction>, type: TransactionType): List<FinancialTransaction> {
        return transactions
            .asSequence()
            .filter { it.isConfirmed }
            .filter { it.transactionType == type }
            .filterNot { it.isDeleted }
            .distinctBy { it.id }
            .toList()
    }

    fun pendingReview(transactions: List<FinancialTransaction>): List<FinancialTransaction> {
        return transactions.filter { it.status.equals("review", ignoreCase = true) }
    }

    fun summarize(transactions: List<FinancialTransaction>): CashFlowSummary {
        val confirmed = transactions.filter { it.isConfirmed && !it.isDeleted }
        val income = confirmed.sumFor(TransactionType.INCOME)
        val expenses = confirmed
            .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT_CARD_PURCHASE }
            .sumOf { it.amount }
        val refunds = confirmed.sumFor(TransactionType.REFUND)
        val cashback = confirmed.sumFor(TransactionType.CASHBACK)
        val interest = confirmed.sumFor(TransactionType.INTEREST)
        val savings = income - expenses
        val cashFlow = income + refunds + cashback + interest - expenses
        return CashFlowSummary(
            income = income,
            expenses = expenses,
            refunds = refunds,
            cashback = cashback,
            interest = interest,
            savings = savings,
            netCashFlow = cashFlow
        )
    }

    fun totalIncome(transactions: List<FinancialTransaction>): Double =
        confirmedIncome(transactions).sumOf { it.amount }

    fun totalExpense(transactions: List<FinancialTransaction>): Double =
        confirmedExpenses(transactions).sumOf { it.amount }

    fun netSavings(transactions: List<FinancialTransaction>): Double =
        totalIncome(transactions) - totalExpense(transactions)

    fun cashFlow(transactions: List<FinancialTransaction>): Double =
        summarize(transactions).netCashFlow

    fun summaryForPeriod(
        transactions: List<FinancialTransaction>,
        startMs: Long,
        endMs: Long
    ): CashFlowPeriodSummary {
        val period = transactions.filter { it.date in startMs..endMs }
        val summary = summarize(period)
        return CashFlowPeriodSummary(
            startMs = startMs,
            endMs = endMs,
            income = summary.income,
            expenses = summary.expenses,
            netSavings = summary.savings,
            cashFlow = summary.netCashFlow
        )
    }

    fun weeklyCashFlow(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): CashFlowPeriodSummary {
        val start = DateRangeService.getMondayOfWeek(nowMs)
        return summaryForPeriod(transactions, start, start + DateRangeService.DAY_MS * 7 - 1L)
    }

    fun monthlyCashFlow(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): CashFlowPeriodSummary {
        val (start, end) = DateRangeService.getCurrentMonthBounds(nowMs)
        return summaryForPeriod(transactions, start, end)
    }

    fun growthPercent(current: Double, previous: Double): Double? {
        if (previous == 0.0) return if (current == 0.0) 0.0 else null
        return ((current - previous) / previous) * 100.0
    }

    fun incomeGrowth(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): Double? {
        val (thisStart, thisEnd) = DateRangeService.getCurrentMonthBounds(nowMs)
        val (lastStart, lastEnd) = DateRangeService.getPreviousMonthBounds(thisStart)
        val income = confirmedIncome(transactions)
        return growthPercent(
            income.filter { it.date in thisStart..thisEnd }.sumOf { it.amount },
            income.filter { it.date in lastStart..lastEnd }.sumOf { it.amount }
        )
    }

    fun expenseGrowth(transactions: List<FinancialTransaction>, nowMs: Long = System.currentTimeMillis()): Double? {
        val (thisStart, thisEnd) = DateRangeService.getCurrentMonthBounds(nowMs)
        val (lastStart, lastEnd) = DateRangeService.getPreviousMonthBounds(thisStart)
        val expenses = confirmedExpenses(transactions)
        return growthPercent(
            expenses.filter { it.date in thisStart..thisEnd }.sumOf { it.amount },
            expenses.filter { it.date in lastStart..lastEnd }.sumOf { it.amount }
        )
    }

    private fun List<FinancialTransaction>.sumFor(type: TransactionType): Double =
        filter { it.transactionType == type }.sumOf { it.amount }
}
