package com.autoexpense.app.export

import com.autoexpense.app.data.TransactionEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * All required Phase 5 Expense Export unit tests:
 * 1. This-week filtering
 * 2. This-month filtering
 * 3. Last-month filtering
 * 4. Custom date filtering
 * 5. Category filtering
 * 6. Confirmed transactions included
 * 7. Needs Review transactions excluded
 * 8. Ignored transactions excluded
 * 9. CSV escaping
 * 10. Empty export result
 * 11. Correct total calculation
 */
class ExportTest {

    private fun createTx(
        id: String,
        amount: String,
        status: String,
        category: String = "🍔 Food & Dining",
        timestamp: Long = System.currentTimeMillis(),
        merchant: String = "Test Merchant",
        note: String = ""
    ) = TransactionEntity(
        id = id,
        merchantOrRecipient = merchant,
        sub = "com.test.bank",
        amount = amount,
        currency = "INR",
        source = "banksms",
        category = category,
        status = status,
        timestamp = timestamp,
        confidence = 1.0f,
        detectionReason = "SMS regex match",
        safeNotificationExcerpt = "Payment of $amount to $merchant",
        transactionFingerprint = "fp_$id",
        createdAt = timestamp,
        updatedAt = timestamp,
        note = note
    )

    @Test
    fun testConfirmedTransactionsIncluded() {
        val txList = listOf(
            createTx("1", "−₹500.00", "confirmed"),
            createTx("2", "−₹300.00", "review")
        )
        val filtered = ExportFilterHelper.filterTransactions(
            allTransactions = txList,
            dateFilter = ExportFilterHelper.DATE_ALL_TIME,
            categoryFilter = ExportFilterHelper.CAT_ALL
        )
        assertEquals(1, filtered.size)
        assertEquals("1", filtered[0].id)
    }

    @Test
    fun testNeedsReviewTransactionsExcluded() {
        val txList = listOf(
            createTx("1", "−₹200.00", "review"),
            createTx("2", "−₹100.00", "confirmed")
        )
        val filtered = ExportFilterHelper.filterTransactions(
            allTransactions = txList,
            dateFilter = ExportFilterHelper.DATE_ALL_TIME,
            categoryFilter = ExportFilterHelper.CAT_ALL
        )
        assertEquals(1, filtered.size)
        assertFalse(filtered.any { it.status == "review" })
    }

    @Test
    fun testIgnoredTransactionsExcluded() {
        val txList = listOf(
            createTx("1", "−₹400.00", "ignored"),
            createTx("2", "−₹600.00", "confirmed")
        )
        val filtered = ExportFilterHelper.filterTransactions(
            allTransactions = txList,
            dateFilter = ExportFilterHelper.DATE_ALL_TIME,
            categoryFilter = ExportFilterHelper.CAT_ALL
        )
        assertEquals(1, filtered.size)
        assertFalse(filtered.any { it.status == "ignored" })
    }

    @Test
    fun testThisWeekFiltering() {
        val tz = TimeZone.getDefault()
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance(tz).apply { timeInMillis = now }

        // Find Monday of this week
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysToMon = (dow - Calendar.MONDAY + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, -daysToMon)
        cal.set(Calendar.HOUR_OF_DAY, 12)
        val thisWeekMs = cal.timeInMillis

        // 2 weeks ago
        cal.add(Calendar.DAY_OF_MONTH, -14)
        val twoWeeksAgoMs = cal.timeInMillis

        val txList = listOf(
            createTx("tx_this_week", "−₹100", "confirmed", timestamp = thisWeekMs),
            createTx("tx_old", "−₹200", "confirmed", timestamp = twoWeeksAgoMs)
        )

        val filtered = ExportFilterHelper.filterTransactions(
            allTransactions = txList,
            dateFilter = ExportFilterHelper.DATE_THIS_WEEK,
            categoryFilter = ExportFilterHelper.CAT_ALL,
            nowMs = now,
            tz = tz
        )

        assertEquals(1, filtered.size)
        assertEquals("tx_this_week", filtered[0].id)
    }

    @Test
    fun testThisMonthFiltering() {
        val tz = TimeZone.getDefault()
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 15)
            set(Calendar.HOUR_OF_DAY, 12)
        }
        val thisMonthMs = cal.timeInMillis

        cal.add(Calendar.MONTH, -2)
        val twoMonthsAgoMs = cal.timeInMillis

        val txList = listOf(
            createTx("tx_this_month", "−₹500", "confirmed", timestamp = thisMonthMs),
            createTx("tx_old_month", "−₹300", "confirmed", timestamp = twoMonthsAgoMs)
        )

        val filtered = ExportFilterHelper.filterTransactions(
            allTransactions = txList,
            dateFilter = ExportFilterHelper.DATE_THIS_MONTH,
            categoryFilter = ExportFilterHelper.CAT_ALL,
            nowMs = now,
            tz = tz
        )

        assertEquals(1, filtered.size)
        assertEquals("tx_this_month", filtered[0].id)
    }

    @Test
    fun testLastMonthFiltering() {
        val tz = TimeZone.getDefault()
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 15)
            set(Calendar.HOUR_OF_DAY, 12)
            add(Calendar.MONTH, -1)
        }
        val lastMonthMs = cal.timeInMillis

        cal.add(Calendar.MONTH, -1)
        val twoMonthsAgoMs = cal.timeInMillis

        val txList = listOf(
            createTx("tx_last_month", "−₹800", "confirmed", timestamp = lastMonthMs),
            createTx("tx_two_months_ago", "−₹400", "confirmed", timestamp = twoMonthsAgoMs),
            createTx("tx_now", "−₹100", "confirmed", timestamp = now)
        )

        val filtered = ExportFilterHelper.filterTransactions(
            allTransactions = txList,
            dateFilter = ExportFilterHelper.DATE_LAST_MONTH,
            categoryFilter = ExportFilterHelper.CAT_ALL,
            nowMs = now,
            tz = tz
        )

        assertEquals(1, filtered.size)
        assertEquals("tx_last_month", filtered[0].id)
    }

    @Test
    fun testCustomDateFiltering() {
        val startMs = 1700000000000L
        val endMs = 1700100000000L
        val insideMs = 1700050000000L
        val outsideMs = 1700200000000L

        val txList = listOf(
            createTx("in", "−₹100", "confirmed", timestamp = insideMs),
            createTx("out", "−₹200", "confirmed", timestamp = outsideMs)
        )

        val filtered = ExportFilterHelper.filterTransactions(
            allTransactions = txList,
            dateFilter = ExportFilterHelper.DATE_CUSTOM,
            categoryFilter = ExportFilterHelper.CAT_ALL,
            customStartMs = startMs,
            customEndMs = endMs
        )

        assertEquals(1, filtered.size)
        assertEquals("in", filtered[0].id)
    }

    @Test
    fun testCategoryFiltering() {
        val txList = listOf(
            createTx("1", "−₹100", "confirmed", category = "🍔 Food & Dining"),
            createTx("2", "−₹200", "confirmed", category = "🚗 Transport"),
            createTx("3", "−₹300", "confirmed", category = "❓ Unknown")
        )

        val foodFiltered = ExportFilterHelper.filterTransactions(
            allTransactions = txList,
            dateFilter = ExportFilterHelper.DATE_ALL_TIME,
            categoryFilter = ExportFilterHelper.CAT_FOOD
        )
        assertEquals(1, foodFiltered.size)
        assertEquals("1", foodFiltered[0].id)

        val transportFiltered = ExportFilterHelper.filterTransactions(
            allTransactions = txList,
            dateFilter = ExportFilterHelper.DATE_ALL_TIME,
            categoryFilter = ExportFilterHelper.CAT_TRANSPORT
        )
        assertEquals(1, transportFiltered.size)
        assertEquals("2", transportFiltered[0].id)

        val otherFiltered = ExportFilterHelper.filterTransactions(
            allTransactions = txList,
            dateFilter = ExportFilterHelper.DATE_ALL_TIME,
            categoryFilter = ExportFilterHelper.CAT_OTHER
        )
        assertEquals(1, otherFiltered.size)
        assertEquals("3", otherFiltered[0].id)
    }

    @Test
    fun testCorrectTotalCalculation() {
        val txList = listOf(
            createTx("1", "−₹1,500.50", "confirmed"),
            createTx("2", "−₹2,499.50", "confirmed"),
            createTx("3", "−₹500.00", "review") // should not be included when computing on confirmed filtered
        )
        val filtered = ExportFilterHelper.filterTransactions(
            allTransactions = txList,
            dateFilter = ExportFilterHelper.DATE_ALL_TIME,
            categoryFilter = ExportFilterHelper.CAT_ALL
        )
        val total = ExportFilterHelper.calculateTotalSpent(filtered)
        assertEquals(4000.0, total, 0.001)
        assertEquals("₹4,000.00", ExportFilterHelper.formatIndianCurrency(total))
    }

    @Test
    fun testCsvEscaping() {
        val raw = "Uber, Trips \"Private\" \r\n Line2"
        val escaped = ExportFilterHelper.escapeCsv(raw)
        assertEquals("\"Uber, Trips \"\"Private\"\" \r\n Line2\"", escaped)

        val simple = "SimpleMerchant"
        assertEquals("SimpleMerchant", ExportFilterHelper.escapeCsv(simple))
    }

    @Test
    fun testEmptyExportResult() {
        val txList = listOf(
            createTx("1", "−₹500", "review"), // only review exists
            createTx("2", "−₹300", "ignored") // only ignored exists
        )
        val filtered = ExportFilterHelper.filterTransactions(
            allTransactions = txList,
            dateFilter = ExportFilterHelper.DATE_THIS_WEEK,
            categoryFilter = ExportFilterHelper.CAT_ALL
        )
        assertTrue("Filtered transactions should be empty when no confirmed matches exist", filtered.isEmpty())
        val total = ExportFilterHelper.calculateTotalSpent(filtered)
        assertEquals(0.0, total, 0.0001)
    }
}
