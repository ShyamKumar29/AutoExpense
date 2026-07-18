package com.autoexpense.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BillNotificationParserTest {
    private val ts = 1784300000000L

    @Test
    fun parsesElectricityBill() {
        val bill = BillNotificationParser.parse(
            title = "TNEB",
            body = "Your electricity bill amount due Rs. 1,240.50 due date 25/07/2026",
            packageName = "com.google.android.apps.messaging",
            timestamp = ts
        )

        assertNotNull(bill)
        assertEquals("ELECTRICITY", bill!!.billType)
        assertEquals("TNEB", bill.provider)
        assertEquals(1240.50, bill.amount, 0.001)
        assertNotNull(bill.dueDate)
    }

    @Test
    fun parsesCreditCardStatement() {
        val bill = BillNotificationParser.parse(
            title = "HDFC Bank",
            body = "Your credit card statement is generated. Total due Rs. 5,600 due on 31/07/2026",
            packageName = "com.google.android.apps.messaging",
            timestamp = ts
        )

        assertNotNull(bill)
        assertEquals("CREDIT_CARD", bill!!.billType)
        assertEquals(5600.0, bill.amount, 0.001)
    }

    @Test
    fun rejectsOtp() {
        assertNull(
            BillNotificationParser.parse(
                title = "BESCOM",
                body = "OTP 123456 for bill payment",
                packageName = "com.google.android.apps.messaging",
                timestamp = ts
            )
        )
    }
}
