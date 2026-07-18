package com.autoexpense.app.notification

import com.autoexpense.app.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringPaymentDetectorTest {
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun detectsMonthlySubscription() {
        val base = 1780000000000L
        val txns = listOf(
            txn("1", "Netflix", "-499", base),
            txn("2", "Netflix", "-499", base + 30 * day),
            txn("3", "Netflix", "-499", base + 60 * day)
        )

        val result = RecurringPaymentDetector.detect(txns, base + 65 * day)

        assertEquals(1, result.size)
        assertEquals("MONTHLY", result[0].frequency)
        assertEquals("ACTIVE", result[0].status)
    }

    @Test
    fun ignoresOneOffPayments() {
        val result = RecurringPaymentDetector.detect(
            listOf(txn("1", "Cafe", "-250", 1780000000000L)),
            1781000000000L
        )

        assertTrue(result.isEmpty())
    }

    private fun txn(id: String, merchant: String, amount: String, timestamp: Long): Transaction =
        Transaction(
            id = id,
            merchant = merchant,
            sub = "Google Pay",
            source = "gpay",
            category = "Entertainment",
            amount = amount,
            date = "",
            status = "confirmed",
            timestamp = timestamp
        )
}
