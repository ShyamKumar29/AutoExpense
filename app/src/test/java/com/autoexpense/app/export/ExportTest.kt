package com.autoexpense.app.export

import com.autoexpense.app.domain.FinancialTransaction
import com.autoexpense.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ExportTest {

    private fun tx(
        id: String,
        type: TransactionType,
        amount: Double,
        status: String = "confirmed",
        category: String = "Food & Dining",
        timestamp: Long = System.currentTimeMillis(),
        title: String = "Test Merchant",
        merchant: String = title,
        method: String = "UPI",
        notes: String = ""
    ) = FinancialTransaction(
        id = id,
        transactionType = type,
        amount = amount,
        currency = "INR",
        title = title,
        category = category,
        merchant = merchant,
        paymentMethod = method,
        notes = notes,
        date = timestamp,
        createdAt = timestamp,
        updatedAt = timestamp,
        status = status
    )

    @Test
    fun confirmedFinancialTransactionsAreIncludedAcrossTypes() {
        val txList = listOf(
            tx("expense", TransactionType.EXPENSE, 500.0),
            tx("income", TransactionType.INCOME, 3000.0, category = "Salary"),
            tx("review", TransactionType.INCOME, 200.0, status = "review")
        )

        val filtered = ExportFilterHelper.filterTransactions(
            allTransactions = txList,
            dateFilter = ExportFilterHelper.DATE_ALL_TIME,
            categoryFilter = ExportFilterHelper.CAT_ALL
        )

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == "expense" })
        assertTrue(filtered.any { it.id == "income" })
        assertFalse(filtered.any { it.id == "review" })
    }

    @Test
    fun transactionTypeFilterSeparatesExpensesAndIncome() {
        val txList = listOf(
            tx("expense", TransactionType.EXPENSE, 500.0),
            tx("cc", TransactionType.CREDIT_CARD_PURCHASE, 700.0),
            tx("income", TransactionType.INCOME, 3000.0, category = "Salary")
        )

        val expenses = ExportFilterHelper.filterTransactions(
            allTransactions = txList,
            dateFilter = ExportFilterHelper.DATE_ALL_TIME,
            categoryFilter = ExportFilterHelper.CAT_ALL,
            transactionTypeFilter = ExportFilterHelper.TYPE_EXPENSES
        )
        val income = ExportFilterHelper.filterTransactions(
            allTransactions = txList,
            dateFilter = ExportFilterHelper.DATE_ALL_TIME,
            categoryFilter = ExportFilterHelper.CAT_ALL,
            transactionTypeFilter = ExportFilterHelper.TYPE_INCOME
        )

        assertEquals(setOf("cc", "expense"), expenses.map { it.id }.toSet())
        assertEquals(listOf("income"), income.map { it.id })
    }

    @Test
    fun thisWeekFilteringUsesFinancialTransactionDate() {
        val tz = TimeZone.getDefault()
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance(tz).apply { timeInMillis = now }

        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysToMon = (dow - Calendar.MONDAY + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, -daysToMon)
        cal.set(Calendar.HOUR_OF_DAY, 12)
        val thisWeekMs = cal.timeInMillis

        cal.add(Calendar.DAY_OF_MONTH, -14)
        val twoWeeksAgoMs = cal.timeInMillis

        val filtered = ExportFilterHelper.filterTransactions(
            allTransactions = listOf(
                tx("this_week", TransactionType.INCOME, 1000.0, timestamp = thisWeekMs, category = "Salary"),
                tx("old", TransactionType.EXPENSE, 200.0, timestamp = twoWeeksAgoMs)
            ),
            dateFilter = ExportFilterHelper.DATE_THIS_WEEK,
            categoryFilter = ExportFilterHelper.CAT_ALL,
            nowMs = now,
            tz = tz
        )

        assertEquals(1, filtered.size)
        assertEquals("this_week", filtered[0].id)
    }

    @Test
    fun categoryMerchantAndPaymentFiltersAreRespected() {
        val txList = listOf(
            tx("1", TransactionType.EXPENSE, 100.0, category = "Food & Dining", title = "Swiggy", merchant = "Swiggy", method = "UPI"),
            tx("2", TransactionType.INCOME, 2000.0, category = "Salary", title = "TCS", merchant = "TCS", method = "Bank"),
            tx("3", TransactionType.EXPENSE, 300.0, category = "Shopping", title = "Amazon", merchant = "Amazon", method = "Card")
        )

        val filtered = ExportFilterHelper.filterTransactions(
            allTransactions = txList,
            dateFilter = ExportFilterHelper.DATE_ALL_TIME,
            categoryFilter = "Salary",
            merchantFilter = "TCS",
            paymentMethodFilter = "Bank"
        )

        assertEquals(1, filtered.size)
        assertEquals("2", filtered[0].id)
    }

    @Test
    fun summaryCalculatesIncomeExpensesNetAndLargestValues() {
        val txList = listOf(
            tx("salary", TransactionType.INCOME, 45000.0, category = "Salary"),
            tx("refund", TransactionType.REFUND, 250.0, category = "Refund"),
            tx("food", TransactionType.EXPENSE, 350.0),
            tx("rent", TransactionType.EXPENSE, 12000.0, category = "Rent / Bills")
        )

        val summary = ExportFilterHelper.calculateSummary(txList, "1 Jul 2026 - 31 Jul 2026")

        assertEquals(45250.0, summary.income, 0.001)
        assertEquals(12350.0, summary.expenses, 0.001)
        assertEquals(32900.0, summary.netSavings, 0.001)
        assertEquals(45000.0, summary.largestIncome, 0.001)
        assertEquals(12000.0, summary.largestExpense, 0.001)
        assertEquals(4, summary.transactionCount)
    }

    @Test
    fun csvIncludesUnifiedFinancialColumnsAndSignedAmounts() {
        val content = ExportFilterHelper.generateCsvContent(
            listOf(
                tx("salary", TransactionType.INCOME, 45000.0, category = "Salary", title = "TCS", merchant = "TCS"),
                tx("food", TransactionType.EXPENSE, 350.0, title = "Swiggy", merchant = "Swiggy")
            )
        )

        assertTrue(content.startsWith("Date,Time,Transaction Type,Title,Counterparty,Category,Merchant,Payment Method,Amount,Currency,Notes,Reference Number,Auto Detected,Recurring,Status"))
        assertTrue(content.contains("INCOME,TCS,TCS,Salary,TCS,UPI,+45000.00"))
        assertTrue(content.contains("EXPENSE,Swiggy,Swiggy,Food & Dining,Swiggy,UPI,-350.00"))
    }

    @Test
    fun csvEscapingPreservesCommasAndQuotes() {
        val raw = "Uber, Trips \"Private\" \r\n Line2"
        val escaped = ExportFilterHelper.escapeCsv(raw)
        assertEquals("\"Uber, Trips \"\"Private\"\" \r\n Line2\"", escaped)

        assertEquals("SimpleMerchant", ExportFilterHelper.escapeCsv("SimpleMerchant"))
    }

    @Test
    fun emptyResultWhenNoConfirmedTransactionsMatch() {
        val filtered = ExportFilterHelper.filterTransactions(
            allTransactions = listOf(
                tx("1", TransactionType.EXPENSE, 500.0, status = "review"),
                tx("2", TransactionType.INCOME, 300.0, status = "ignored")
            ),
            dateFilter = ExportFilterHelper.DATE_THIS_WEEK,
            categoryFilter = ExportFilterHelper.CAT_ALL
        )

        assertTrue(filtered.isEmpty())
        assertEquals(0.0, ExportFilterHelper.calculateTotalSpent(filtered), 0.0001)
    }
}
