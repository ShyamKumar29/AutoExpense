package com.autoexpense.app.notification

import com.autoexpense.app.data.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentMethodDetectorTest {
    @Test
    fun detectsUpiFromKnownPaymentApp() {
        assertEquals(
            PaymentMethod.UPI,
            PaymentMethodDetector.detect("", "You paid Rs. 450 to Swiggy", "com.google.android.apps.nbu.paisa.user")
        )
    }

    @Test
    fun detectsUpiFromBankSmsRefno() {
        assertEquals(
            PaymentMethod.UPI,
            PaymentMethodDetector.detect("SBI", "A/C X4511 debited by 1.00 trf to Harini Refno 123456789", PaymentIngestion.DIRECT_SMS_PACKAGE)
        )
    }

    @Test
    fun detectsDebitCardFromCardWording() {
        assertEquals(
            PaymentMethod.DEBIT_CARD,
            PaymentMethodDetector.detect("HDFC", "Rs.500 spent on debit card XX1234 at Amazon", "com.google.android.apps.messaging")
        )
    }

    @Test
    fun detectsCreditCardFromStatementWording() {
        assertEquals(
            PaymentMethod.CREDIT_CARD,
            PaymentMethodDetector.detect("ICICI", "Credit card XX4321 debited for Rs.999 at NETFLIX", "com.google.android.apps.messaging")
        )
    }
}
