package com.autoexpense.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinancialEngineTest {
    @Test
    fun classificationDetectsSupportedFinancialEvents() {
        assertEquals(TransactionType.INCOME, TransactionClassificationService.classifyNotificationText("Salary credited INR 50000"))
        assertEquals(TransactionType.REFUND, TransactionClassificationService.classifyNotificationText("Amazon refund processed Rs 450"))
        assertEquals(TransactionType.CASHBACK, TransactionClassificationService.classifyNotificationText("Cashback credited Rs 80"))
        assertEquals(TransactionType.INTEREST, TransactionClassificationService.classifyNotificationText("Savings interest credited Rs 22"))
        assertEquals(TransactionType.TRANSFER, TransactionClassificationService.classifyNotificationText("Transfer to self account Rs 1000"))
        assertEquals(TransactionType.EXPENSE, TransactionClassificationService.classifyNotificationText("Rs 220 debited for UPI payment"))
    }

    @Test
    fun cashFlowSummaryCombinesIncomeAndCreditsAgainstExpenses() {
        val transactions = listOf(
            tx("income", TransactionType.INCOME, 50000.0),
            tx("expense", TransactionType.EXPENSE, 1200.0),
            tx("refund", TransactionType.REFUND, 300.0),
            tx("cashback", TransactionType.CASHBACK, 80.0),
            tx("interest", TransactionType.INTEREST, 20.0)
        )

        val summary = CashFlowService.summarize(transactions)

        assertEquals(50000.0, summary.income, 0.01)
        assertEquals(1200.0, summary.expenses, 0.01)
        assertEquals(48800.0, summary.savings, 0.01)
        assertEquals(49200.0, summary.netCashFlow, 0.01)
    }

    @Test
    fun incomeAnalyticsHandlesEmptyAndAverageIncome() {
        assertNull(IncomeAnalyticsService.getLargestIncome(emptyList()))

        val transactions = listOf(
            tx("salary", TransactionType.INCOME, 50000.0),
            tx("rent", TransactionType.INCOME, 10000.0),
            tx("expense", TransactionType.EXPENSE, 2000.0)
        )

        assertEquals(30000.0, IncomeAnalyticsService.getAverageIncome(transactions), 0.01)
        assertEquals(50000.0, IncomeAnalyticsService.getLargestIncome(transactions)?.amount ?: 0.0, 0.01)
    }

    private fun tx(id: String, type: TransactionType, amount: Double): FinancialTransaction {
        return FinancialTransaction(
            id = id,
            transactionType = type,
            amount = amount,
            currency = "INR",
            title = id,
            category = type.name,
            merchant = id,
            date = System.currentTimeMillis(),
            status = "confirmed"
        )
    }
}
