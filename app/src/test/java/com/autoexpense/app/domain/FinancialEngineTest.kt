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

    @Test
    fun incomingClassificationUsesIncomeCategoriesAndRejectsAmountTitles() {
        val transfer = TransactionClassificationService.classifyEvent(
            rawText = "Transfer from Harini Rs 500",
            amount = "500",
            merchant = "Rs.500"
        )
        assertEquals(TransactionType.INCOME, transfer.transactionType)
        assertEquals("Personal Transfer", transfer.category)
        assertEquals("Harini", transfer.merchant)

        val salary = TransactionClassificationService.classifyEvent(
            rawText = "Salary credited by TCS INR 50000",
            amount = "50000",
            merchant = "Income"
        )
        assertEquals(TransactionType.INCOME, salary.transactionType)
        assertEquals("Salary", salary.category)
        assertEquals("TCS", salary.merchant)

        val business = TransactionClassificationService.classifyEvent(
            rawText = "Business payment received from Client INR 25000",
            amount = "25000",
            merchant = ""
        )
        assertEquals(TransactionType.INCOME, business.transactionType)
        assertEquals("Business", business.category)
    }

    @Test
    fun categoryProviderReturnsTypeAwareCategoriesAndSuggestions() {
        val expenseCategories = DefaultCategoryProvider.getCategories(TransactionType.EXPENSE).map { it.name }
        assertEquals(true, "Food & Dining" in expenseCategories)
        assertEquals(false, "Salary" in expenseCategories)

        val incomeCategories = DefaultCategoryProvider.getCategories(TransactionType.INCOME).map { it.name }
        assertEquals(true, "Salary" in incomeCategories)
        assertEquals(true, "Personal Transfer" in incomeCategories)
        assertEquals(false, "Food & Dining" in incomeCategories)

        val salary = tx("salary", TransactionType.INCOME, 50000.0).copy(
            title = "TCS",
            merchant = "TCS",
            category = "Salary",
            metadata = mapOf("classificationReason" to "Salary credited by TCS")
        )
        assertEquals("Salary", DefaultCategoryProvider.suggestCategory(salary)?.name)

        val transfer = tx("transfer", TransactionType.INCOME, 500.0).copy(
            title = "Harini",
            merchant = "Harini",
            category = "Personal Transfer",
            metadata = mapOf("classificationReason" to "Transfer from Harini")
        )
        assertEquals("Personal Transfer", DefaultCategoryProvider.suggestCategory(transfer)?.name)

        val cashback = tx("cashback", TransactionType.CASHBACK, 80.0).copy(
            category = "Cashback",
            metadata = mapOf("classificationReason" to "Cashback credited")
        )
        assertEquals("Cashback", DefaultCategoryProvider.suggestCategory(cashback)?.name)
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
