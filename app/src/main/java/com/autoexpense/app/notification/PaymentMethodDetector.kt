package com.autoexpense.app.notification

import com.autoexpense.app.data.PaymentMethod

object PaymentMethodDetector {
    fun detect(title: String, body: String, packageName: String): PaymentMethod {
        val source = SupportedPaymentSources.findSource(packageName)
        val text = "$title $body".lowercase()

        if (source != null && !source.isMessaging) return PaymentMethod.UPI
        if (packageName == PaymentIngestion.DIRECT_SMS_PACKAGE && looksLikeUpi(text)) return PaymentMethod.UPI
        if (SupportedPaymentSources.isMessagingApp(packageName) && looksLikeUpi(text)) return PaymentMethod.UPI

        return when {
            text.contains("credit card") || text.contains("cc ") || text.contains("card xx") && text.contains("credit") ->
                PaymentMethod.CREDIT_CARD
            text.contains("debit card") || text.contains("dc ") || text.contains("pos ") || text.contains("ecom") || text.contains("card") ->
                PaymentMethod.DEBIT_CARD
            text.contains("net banking") || text.contains("netbanking") || text.contains("internet banking") ->
                PaymentMethod.NET_BANKING
            text.contains("wallet") || text.contains("paytm wallet") || text.contains("amazon pay balance") ->
                PaymentMethod.WALLET
            else -> PaymentMethod.UNKNOWN
        }
    }

    private fun looksLikeUpi(text: String): Boolean {
        return text.contains("upi") ||
            text.contains("vpa") ||
            text.contains("@ybl") ||
            text.contains("@oksbi") ||
            text.contains("@okaxis") ||
            text.contains("@okhdfcbank") ||
            text.contains("@paytm") ||
            text.contains("trf to") ||
            text.contains("refno") ||
            text.contains("upi ref")
    }
}
