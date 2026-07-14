package com.autoexpense.app.dashboard

import androidx.compose.ui.graphics.Color
import com.autoexpense.app.DashboardViewModel
import com.autoexpense.app.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DashboardViewModelTest {

    @Test
    fun testIsConfirmedOutgoingExpenseExclusions() {
        // Confirmed outgoing expense
        val valid = Transaction(
            id = "txn1", merchant = "Swiggy", sub = "Food", source = "gpay",
            category = "🍔 Food & Dining", amount = "₹450.00", date = "14 Jul",
            status = "confirmed", timestamp = 1000L
        )
        assertTrue(DashboardViewModel.isConfirmedOutgoingExpense(valid))

        // Needs Review
        val review = valid.copy(id = "txn2", status = "review")
        assertFalse(DashboardViewModel.isConfirmedOutgoingExpense(review))

        // Ignored
        val ignored = valid.copy(id = "txn3", status = "ignored")
        assertFalse(DashboardViewModel.isConfirmedOutgoingExpense(ignored))

        // Incoming (+)
        val incoming = valid.copy(id = "txn4", amount = "+₹1,000.00")
        assertFalse(DashboardViewModel.isConfirmedOutgoingExpense(incoming))

        // Refund keyword
        val refund = valid.copy(id = "txn5", merchant = "Amazon Refund")
        assertFalse(DashboardViewModel.isConfirmedOutgoingExpense(refund))

        // Failed reason
        val failed = valid.copy(id = "txn6", detectionReason = "payment failed")
        assertFalse(DashboardViewModel.isConfirmedOutgoingExpense(failed))

        // Duplicate reason
        val duplicate = valid.copy(id = "txn7", detectionReason = "duplicate transaction")
        assertFalse(DashboardViewModel.isConfirmedOutgoingExpense(duplicate))
    }

    @Test
    fun testComputeConfirmedOutgoingDeduplication() {
        val t1 = Transaction("txn1", "Store A", "", "gpay", "🛍️ Shopping", "₹100", "14 Jul", "confirmed", timestamp = 1000L)
        val t1Dup = Transaction("txn1", "Store A", "", "gpay", "🛍️ Shopping", "₹100", "14 Jul", "confirmed", timestamp = 1000L)
        val t2 = Transaction("txn2", "Store B", "", "phonepe", "🚗 Transport", "₹200", "14 Jul", "review", timestamp = 2000L)

        val result = DashboardViewModel.computeConfirmedOutgoing(listOf(t1, t1Dup, t2))
        assertEquals(1, result.size)
        assertEquals("txn1", result[0].id)
    }

    @Test
    fun testComputeTotalSpentAndEmptyHandling() {
        val nowMs = System.currentTimeMillis()
        val bounds = DashboardViewModel.getCurrentMonthBounds(nowMs)
        val midMonth = bounds.first + 3600000L // 1 hour into this month

        val t1 = Transaction("1", "A", "", "gpay", "Food", "₹500.50", "14 Jul", "confirmed", timestamp = midMonth)
        val t2 = Transaction("2", "B", "", "gpay", "Food", "₹250.00", "14 Jul", "confirmed", timestamp = midMonth)
        // Outside month
        val tOld = Transaction("3", "C", "", "gpay", "Food", "₹1000.00", "1 Jun", "confirmed", timestamp = bounds.first - 3600000L)

        val total = DashboardViewModel.computeTotalSpent(listOf(t1, t2, tOld), nowMs)
        assertEquals("₹750.50", total)

        val emptyTotal = DashboardViewModel.computeTotalSpent(emptyList(), nowMs)
        assertEquals("₹0", emptyTotal)
    }

    @Test
    fun testComputeTopCategory() {
        val nowMs = System.currentTimeMillis()
        val bounds = DashboardViewModel.getCurrentMonthBounds(nowMs)
        val midMonth = bounds.first + 3600000L

        val t1 = Transaction("1", "Swiggy", "", "gpay", "🍔 Food & Dining", "₹400", "14 Jul", "confirmed", timestamp = midMonth)
        val t2 = Transaction("2", "Zomato", "", "gpay", "🍔 Food & Dining", "₹600", "14 Jul", "confirmed", timestamp = midMonth)
        val t3 = Transaction("3", "Uber", "", "phonepe", "🚗 Transport", "₹350", "14 Jul", "confirmed", timestamp = midMonth)

        val top = DashboardViewModel.computeTopCategory(listOf(t1, t2, t3), nowMs)
        assertEquals("🍔 Food & Dining", top.label)
        assertEquals("₹1,000 · 2 txns", top.subText)

        val emptyTop = DashboardViewModel.computeTopCategory(emptyList(), nowMs)
        assertEquals("None", emptyTop.label)
        assertEquals("₹0 · 0 txns", emptyTop.subText)
    }

    @Test
    fun testComputeUpiSources() {
        val t1 = Transaction("1", "A", "", "gpay", "Food", "₹100", "14 Jul", "confirmed", timestamp = 1000L)
        val t2 = Transaction("2", "B", "", "GooglePay", "Food", "₹200", "14 Jul", "confirmed", timestamp = 2000L)
        val t3 = Transaction("3", "C", "", "phonepe", "Food", "₹300", "14 Jul", "confirmed", timestamp = 3000L)
        val t4 = Transaction("4", "D", "", "paytm", "Food", "₹400", "14 Jul", "confirmed", timestamp = 4000L)

        val upi = DashboardViewModel.computeUpiSources(listOf(t1, t2, t3, t4))
        assertEquals("3 Apps", upi.label)
        assertEquals("GPay · PhonePe · Paytm", upi.subText)

        val emptyUpi = DashboardViewModel.computeUpiSources(emptyList())
        assertEquals("0 Apps", emptyUpi.label)
        assertEquals("None", emptyUpi.subText)
    }

    @Test
    fun testComputeChartDataEmpty() {
        val weekly = DashboardViewModel.computeWeeklyChartData(emptyList())
        assertEquals(7, weekly.labels.size)
        assertEquals("₹0", weekly.totalFormatted)
        assertTrue(weekly.values.all { it == 0f })

        val monthly = DashboardViewModel.computeMonthlyChartData(emptyList())
        assertEquals(5, monthly.labels.size)
        assertEquals("₹0", monthly.totalFormatted)
        assertTrue(monthly.values.all { it == 0f })
    }
}
