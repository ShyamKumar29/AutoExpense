package com.autoexpense.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NotificationHealthTest {

    @Test
    fun formatLastPaymentTime_zeroOrNegative_returnsNoPaymentsYet() {
        assertEquals("No payments detected yet", NotificationHealthRepository.formatLastPaymentTime(0L))
        assertEquals("No payments detected yet", NotificationHealthRepository.formatLastPaymentTime(-100L))
    }

    @Test
    fun formatLastPaymentTime_today_returnsTodayFormat() {
        val now = System.currentTimeMillis()
        val timeFormatter = SimpleDateFormat("h:mm a", Locale.US)
        val expected = "Today, " + timeFormatter.format(Date(now))
        assertEquals(expected, NotificationHealthRepository.formatLastPaymentTime(now))
    }

    @Test
    fun formatLastPaymentTime_yesterday_returnsYesterdayFormat() {
        val yestCal = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.DAY_OF_MONTH, -1)
        }
        val yestMs = yestCal.timeInMillis
        val timeFormatter = SimpleDateFormat("h:mm a", Locale.US)
        val expected = "Yesterday, " + timeFormatter.format(Date(yestMs))
        assertEquals(expected, NotificationHealthRepository.formatLastPaymentTime(yestMs))
    }

    @Test
    fun formatLastPaymentTime_olderDate_returnsFullDateFormat() {
        val oldCal = Calendar.getInstance().apply {
            set(2025, Calendar.JUNE, 15, 14, 30)
        }
        val oldMs = oldCal.timeInMillis
        val formatted = NotificationHealthRepository.formatLastPaymentTime(oldMs)
        assertTrue("Should contain Jun 15", formatted.contains("Jun 15"))
    }
}
