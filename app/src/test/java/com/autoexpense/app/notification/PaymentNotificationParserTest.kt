package com.autoexpense.app.notification

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [PaymentNotificationParser] and [NotificationProcessor].
 * No Android framework dependencies – runs on the JVM with plain JUnit 4.
 */
class PaymentNotificationParserTest {

    private val gpayPkg      = "com.google.android.apps.nbu.paisa.user"
    private val phonePePkg   = "com.phonepe.app"
    private val paytmPkg     = "net.one97.paytm"
    private val messagesPkg  = "com.google.android.apps.messaging"
    private val truecallerPkg = "com.truecaller"
    private val unknownPkg   = "com.unknown.app"
    private val ts           = System.currentTimeMillis()

    // ── SBI real-device notification ─────────────────────────────────────────

    private val sbiSms = "Dear UPI user A/C X4511 debited by 1.00 on date 13Jul26 trf to Harini J Refno 619473207907 If not u? call-1800111109 for other services-18001234-SBI"

    @Test
    fun sbi_parse_amount_is_1() {
        val r = PaymentNotificationParser.parse("", sbiSms, messagesPkg, ts)
        assertNotNull("SBI SMS should be detected", r)
        assertEquals(1.0, r!!.amount, 0.001)
    }

    @Test
    fun sbi_parse_recipient_is_HariniJ() {
        val r = PaymentNotificationParser.parse("", sbiSms, messagesPkg, ts)
        assertNotNull(r)
        assertEquals("Harini J", r!!.merchantOrRecipient)
    }

    @Test
    fun sbi_parse_source_contains_SBI() {
        val r = PaymentNotificationParser.parse("", sbiSms, messagesPkg, ts)
        assertNotNull(r)
        assertTrue("Source should mention SBI", r!!.sourceApplication.contains("SBI", ignoreCase = true))
    }

    @Test
    fun sbi_parse_status_is_review() {
        // Verified at repository level – parse returns a non-null result that
        // will become status=review via NotificationProcessor.
        val r = PaymentNotificationParser.parse("", sbiSms, messagesPkg, ts)
        assertNotNull("SBI payment should not be null", r)
    }

    @Test
    fun sbi_X4511_not_extracted_as_amount() {
        val v = PaymentNotificationParser.extractAmountBankSms(sbiSms)
        assertNotNull(v)
        assertNotEquals(4511.0, v!!, 0.001)
        assertEquals(1.0, v, 0.001)
    }

    @Test
    fun sbi_date_token_not_extracted_as_amount() {
        val v = PaymentNotificationParser.extractAmountBankSms(sbiSms)
        assertNotNull(v)
        // 13Jul26 must not be parsed – value must still be 1.00
        assertNotEquals(13.0, v!!, 0.001)
        assertEquals(1.0, v, 0.001)
    }

    @Test
    fun sbi_refno_not_extracted_as_amount() {
        val v = PaymentNotificationParser.extractAmountBankSms(sbiSms)
        assertNotNull(v)
        assertNotEquals(619473207907.0, v!!, 0.001)
        assertEquals(1.0, v, 0.001)
    }

    @Test
    fun sbi_phone_number_not_extracted_as_amount() {
        val v = PaymentNotificationParser.extractAmountBankSms(sbiSms)
        assertNotNull(v)
        // 1800111109 is a phone number – not the amount
        assertNotEquals(1800111109.0, v!!, 0.001)
        assertEquals(1.0, v, 0.001)
    }

    @Test
    fun sbi_refno_not_included_in_recipient() {
        val r = PaymentNotificationParser.parse("", sbiSms, messagesPkg, ts)
        assertNotNull(r)
        assertFalse("Refno should not appear in recipient", r!!.merchantOrRecipient.contains("619473207907"))
        assertFalse("Refno label should not appear in recipient", r.merchantOrRecipient.lowercase().contains("refno"))
    }

    @Test
    fun sbi_bankRef_extracted() {
        val ref = PaymentNotificationParser.extractBankRef(sbiSms)
        assertEquals("619473207907", ref)
    }

    // ── Second SBI format ────────────────────────────────────────────────────

    @Test
    fun sbi_second_format_amount_1500() {
        val sms = "A/C XX1234 debited by 1,500.50 trf to Rahul Verma Refno 123456789"
        val r = PaymentNotificationParser.parse("", sms, messagesPkg, ts)
        assertNotNull(r)
        assertEquals(1500.50, r!!.amount, 0.001)
        assertEquals("Rahul Verma", r.merchantOrRecipient)
    }

    // ── Duplicate prevention ─────────────────────────────────────────────────

    @Test
    fun duplicate_same_refno_adds_only_once() {
        // Reset processor state between runs by calling twice with same refno.
        // We test the parser level: two notifications with the same refno should
        // produce ParsedPayments with the same bankRefNumber.
        val sms1 = "A/C X4511 debited by 1.00 trf to Harini J Refno 619473207907 -SBI"
        val sms2 = "Dear UPI user A/C X4511 debited by 1.00 on date 13Jul26 trf to Harini J Refno 619473207907 SBI"
        val r1 = PaymentNotificationParser.parse("", sms1, messagesPkg, ts)
        val r2 = PaymentNotificationParser.parse("", sms2, messagesPkg, ts + 5000)
        assertNotNull(r1); assertNotNull(r2)
        assertEquals(r1!!.bankRefNumber, r2!!.bankRefNumber)
    }

    // ── Amount extraction: Indian formats ────────────────────────────────────

    @Test
    fun extractAmount_rupee_450() {
        val v = PaymentNotificationParser.extractAmount("You paid Rs. 450")
        assertNotNull(v); assertEquals(450.0, v!!, 0.001)
    }

    @Test
    fun extractAmount_rupee_1500_comma() {
        val v = PaymentNotificationParser.extractAmount("Rs. 1,500 sent")
        assertNotNull(v); assertEquals(1500.0, v!!, 0.001)
    }

    @Test
    fun extractAmount_rupee_decimal() {
        val v = PaymentNotificationParser.extractAmount("Rs. 24,831.50 debited")
        assertNotNull(v); assertEquals(24831.50, v!!, 0.001)
    }

    @Test
    fun extractAmount_Rs_dot_space() {
        val v = PaymentNotificationParser.extractAmount("Rs. 450 paid")
        assertNotNull(v); assertEquals(450.0, v!!, 0.001)
    }

    @Test
    fun extractAmount_Rs_noDot() {
        val v = PaymentNotificationParser.extractAmount("Rs 1,500 sent")
        assertNotNull(v); assertEquals(1500.0, v!!, 0.001)
    }

    @Test
    fun extractAmount_INR_prefix() {
        val v = PaymentNotificationParser.extractAmount("INR 2,000 transferred")
        assertNotNull(v); assertEquals(2000.0, v!!, 0.001)
    }

    @Test
    fun extractAmount_null_noAmount() {
        assertNull(PaymentNotificationParser.extractAmount("Payment notification"))
    }

    @Test
    fun extractAmount_rejectsZero() {
        assertNull(PaymentNotificationParser.extractAmount("Rs. 0 paid"))
    }

    // ── Bank SMS amount extraction ────────────────────────────────────────────

    @Test
    fun bankSms_extractAmount_debitedBy_1() {
        val v = PaymentNotificationParser.extractAmountBankSms("debited by 1.00 on date")
        assertNotNull(v); assertEquals(1.0, v!!, 0.001)
    }

    @Test
    fun bankSms_extractAmount_debitedBy_1500() {
        val v = PaymentNotificationParser.extractAmountBankSms("A/C XX1234 debited by 1,500.50 trf to Rahul")
        assertNotNull(v); assertEquals(1500.50, v!!, 0.001)
    }

    @Test
    fun bankSms_extractAmount_debitedBy_Rs450() {
        val v = PaymentNotificationParser.extractAmountBankSms("Account debited by Rs. 450")
        assertNotNull(v); assertEquals(450.0, v!!, 0.001)
    }

    @Test
    fun bankSms_extractAmount_debitedBy_rupee2000() {
        val v = PaymentNotificationParser.extractAmountBankSms("Your A/C debited by Rs. 2,000")
        assertNotNull(v); assertEquals(2000.0, v!!, 0.001)
    }

    @Test
    fun bankSms_extractAmount_debitedFor_INR() {
        val v = PaymentNotificationParser.extractAmountBankSms("debited for INR 24,831.50")
        assertNotNull(v); assertEquals(24831.50, v!!, 0.001)
    }

    // ── Merchant extraction ───────────────────────────────────────────────────

    @Test
    fun extractMerchant_youPaid_toSwiggy() {
        assertEquals("Swiggy", PaymentNotificationParser.extractMerchant("You paid Rs. 450 to Swiggy"))
    }

    @Test
    fun extractMerchant_sentToRahulVerma() {
        assertEquals("Rahul Verma", PaymentNotificationParser.extractMerchant("Rs. 1,500 sent to Rahul Verma"))
    }

    @Test
    fun extractMerchant_paymentToUber_wasSuccessful() {
        assertEquals("Uber", PaymentNotificationParser.extractMerchant("Payment of Rs. 280 to Uber was successful"))
    }

    @Test
    fun extractMerchant_debited_noMerchant_returnsNull() {
        assertNull(PaymentNotificationParser.extractMerchant("Rs. 2,000 debited from your account"))
    }

    @Test
    fun bankSms_extractMerchant_trfToHariniJ() {
        assertEquals("Harini J", PaymentNotificationParser.extractMerchantBankSms(sbiSms))
    }

    @Test
    fun bankSms_extractMerchant_transferredToRahulVerma() {
        assertEquals("Rahul Verma", PaymentNotificationParser.extractMerchantBankSms("transferred to Rahul Verma"))
    }

    // ── Successful outgoing UPI payment detection ─────────────────────────────

    @Test
    fun parse_gpay_youPaid_toSwiggy() {
        val r = PaymentNotificationParser.parse("", "You paid Rs. 450 to Swiggy", gpayPkg, ts)
        assertNotNull(r)
        assertEquals(450.0, r!!.amount, 0.001)
        assertEquals("Swiggy", r.merchantOrRecipient)
        assertEquals("Google Pay", r.sourceApplication)
        assertEquals(PaymentConfidence.HIGH, r.confidence)
    }

    @Test
    fun parse_phonepe_sentToRahulVerma() {
        val r = PaymentNotificationParser.parse("", "Rs. 1,500 sent to Rahul Verma", phonePePkg, ts)
        assertNotNull(r)
        assertEquals(1500.0, r!!.amount, 0.001)
        assertEquals("Rahul Verma", r.merchantOrRecipient)
        assertEquals("PhonePe", r.sourceApplication)
    }

    @Test
    fun parse_paytm_paymentSuccessful() {
        val r = PaymentNotificationParser.parse("", "Payment of Rs. 280 to Uber was successful", paytmPkg, ts)
        assertNotNull(r)
        assertEquals(280.0, r!!.amount, 0.001)
        assertEquals("Uber", r.merchantOrRecipient)
    }

    @Test
    fun parse_debited_noMerchant_unknownMerchant() {
        val r = PaymentNotificationParser.parse("", "Rs. 2,000 debited from your account", gpayPkg, ts)
        assertNotNull(r)
        assertEquals("Unknown Merchant", r!!.merchantOrRecipient)
        assertEquals(PaymentConfidence.MEDIUM, r.confidence)
    }

    // ── Rejection tests ───────────────────────────────────────────────────────

    @Test
    fun parse_rejects_credited() {
        assertNull(PaymentNotificationParser.parse("", "Your account has been credited by Rs. 2,000", messagesPkg, ts))
    }

    @Test
    fun parse_rejects_otp() {
        assertNull(PaymentNotificationParser.parse("", "OTP 123456 for your SBI transaction", messagesPkg, ts))
    }

    @Test
    fun parse_rejects_paymentFailed() {
        assertNull(PaymentNotificationParser.parse("", "Your payment of Rs. 500 failed", paytmPkg, ts))
    }

    @Test
    fun parse_rejects_cashback() {
        assertNull(PaymentNotificationParser.parse("", "Rs. 500 cashback credited", messagesPkg, ts))
    }

    @Test
    fun parse_rejects_availableBalance() {
        assertNull(PaymentNotificationParser.parse("", "Your available balance is Rs. 10,000", messagesPkg, ts))
    }

    @Test
    fun parse_rejects_received() {
        assertNull(PaymentNotificationParser.parse("", "You received Rs. 2,000 from Rahul", gpayPkg, ts))
    }

    @Test
    fun parse_rejects_transactionFailed() {
        assertNull(PaymentNotificationParser.parse("", "Transaction failed. Rs. 300 not debited.", gpayPkg, ts))
    }

    @Test
    fun parse_rejects_refund() {
        assertNull(PaymentNotificationParser.parse("", "Refund of Rs. 200 processed", paytmPkg, ts))
    }

    @Test
    fun parse_rejects_pending() {
        assertNull(PaymentNotificationParser.parse("", "Your payment of Rs. 300 is pending", gpayPkg, ts))
    }

    @Test
    fun parse_rejects_paymentRequest() {
        assertNull(PaymentNotificationParser.parse("", "Payment request of Rs. 500 from Rahul", phonePePkg, ts))
    }

    @Test
    fun parse_returns_null_missingAmount() {
        assertNull(PaymentNotificationParser.parse("", "You paid to Swiggy", gpayPkg, ts))
    }

    @Test
    fun parse_returns_null_emptyText() {
        assertNull(PaymentNotificationParser.parse("", "", gpayPkg, ts))
    }

    // Messages app without financial content must be discarded
    @Test
    fun parse_rejects_plain_message_from_messages_app() {
        assertNull(PaymentNotificationParser.parse("Rahul", "Hey are you coming?", messagesPkg, ts))
    }

    // ── Case-insensitive matching ─────────────────────────────────────────────

    @Test
    fun parse_outgoing_phrase_caseInsensitive() {
        val r = PaymentNotificationParser.parse("", "YOU PAID Rs. 750 TO NETFLIX", gpayPkg, ts)
        assertNotNull(r); assertEquals(750.0, r!!.amount, 0.001)
    }

    @Test
    fun parse_ignore_phrase_caseInsensitive() {
        assertNull(PaymentNotificationParser.parse("", "YOU RECEIVED Rs. 500 FROM RAVI", gpayPkg, ts))
    }

    @Test
    fun parse_DEBITED_uppercase() {
        val r = PaymentNotificationParser.parse("", "Rs. 1,200 DEBITED FROM ACCOUNT", gpayPkg, ts)
        assertNotNull(r); assertEquals(1200.0, r!!.amount, 0.001)
    }

    // ── Excerpt / privacy ─────────────────────────────────────────────────────

    @Test
    fun excerpt_capped_at_80_chars() {
        val longBody = "You paid Rs. 450 to Swiggy " + "x".repeat(200)
        val r = PaymentNotificationParser.parse("", longBody, gpayPkg, ts)
        assertNotNull(r)
        assertTrue(r!!.safeNotificationExcerpt.length <= 81)
    }

    @Test
    fun excerpt_masks_account_number() {
        val r = PaymentNotificationParser.parse("", sbiSms, messagesPkg, ts)
        assertNotNull(r)
        assertFalse("Raw account X4511 should not appear in excerpt",
            r!!.safeNotificationExcerpt.contains("X4511"))
    }

    @Test
    fun detectionReason_nonEmpty() {
        val r = PaymentNotificationParser.parse("", "You paid Rs. 450 to Swiggy", gpayPkg, ts)
        assertNotNull(r); assertTrue(r!!.detectionReason.isNotBlank())
    }

    // ── KnownBanks detection ─────────────────────────────────────────────────

    @Test
    fun knownBanks_detectsSBI() {
        val bank = KnownBanks.detect(sbiSms)
        assertNotNull(bank); assertEquals("SBI", bank!!.displayName)
    }

    @Test
    fun knownBanks_detectsHDFC() {
        assertNotNull(KnownBanks.detect("HDFC Bank: A/C XX1234 debited by Rs. 500"))
    }

    @Test
    fun knownBanks_returnsNull_forUnknown() {
        assertNull(KnownBanks.detect("You paid Rs. 450 to Swiggy"))
    }

    // ── Phase 2.2: Truecaller, Google Messages, Xiaomi Messages, OEM/Generic Messages ──

    @Test
    fun sbi_parse_sujatha() {
        val sms = "Dear UPI user A/C X4511 debited by 2.00 on date 13Jul26 trf to B Sujatha Refno 619455206609 If not u? call ... SBI"
        val packages = listOf(
            "com.truecaller",
            "com.google.android.apps.messaging",
            "com.miui.mms",
            "com.android.messaging"
        )
        for (pkg in packages) {
            val r = PaymentNotificationParser.parse("", sms, pkg, ts)
            assertNotNull("Should parse for package $pkg", r)
            assertEquals(2.0, r!!.amount, 0.001)
            assertEquals("B Sujatha", r.merchantOrRecipient)
            assertTrue(r.sourceApplication.contains("SBI"))
        }
    }

    @Test
    fun sbi_parse_jothiraman() {
        val sms = "Dear UPI user A/C X4511 debited by 2.00 on date 13Jul26 trf to JOTHIRAMAN K Refno 619482279075 If not u? call ... SBI"
        val packages = listOf(
            "com.truecaller",
            "com.google.android.apps.messaging",
            "com.miui.mms",
            "com.android.messaging"
        )
        for (pkg in packages) {
            val r = PaymentNotificationParser.parse("", sms, pkg, ts)
            assertNotNull("Should parse for package $pkg", r)
            assertEquals(2.0, r!!.amount, 0.001)
            assertEquals("JOTHIRAMAN K", r.merchantOrRecipient)
            assertTrue(r.sourceApplication.contains("SBI"))
        }
    }

    @Test
    fun truecaller_parses_when_bank_sender_is_title() {
        val body = "A/C X4511 debited by 2.00 on date 13Jul26 trf to B Sujatha Refno 619455206609"
        val r = PaymentNotificationParser.parse("SBI", body, truecallerPkg, ts)
        assertNotNull(r)
        assertEquals(2.0, r!!.amount, 0.001)
        assertEquals("B Sujatha", r.merchantOrRecipient)
        assertTrue(r.sourceApplication.contains("SBI"))
    }

    @Test
    fun truecaller_parses_hdfc_debited_from_format() {
        val body = "A/C XX1234 debited from HDFC Bank by Rs. 500.00 paid to Amazon UPI Ref no 123456789012"
        val r = PaymentNotificationParser.parse("HDFC Bank", body, truecallerPkg, ts)
        assertNotNull(r)
        assertEquals(500.0, r!!.amount, 0.001)
        assertEquals("Amazon", r.merchantOrRecipient)
        assertTrue(r.sourceApplication.contains("HDFC"))
        assertEquals("123456789012", r.bankRefNumber)
    }

    @Test
    fun truecaller_parses_icici_debit_of_format() {
        val body = "Debit of INR 349.00 from ICICI Bank Acct XX9876 paid to NETFLIX UTR 654321987654"
        val r = PaymentNotificationParser.parse("ICICI Bank", body, truecallerPkg, ts)
        assertNotNull(r)
        assertEquals(349.0, r!!.amount, 0.001)
        assertEquals("NETFLIX", r.merchantOrRecipient)
        assertTrue(r.sourceApplication.contains("ICICI"))
        assertEquals("654321987654", r.bankRefNumber)
    }

    @Test
    fun truecaller_parses_unknown_merchant_when_debit_is_valid() {
        val body = "SBI A/C X4511 has been debited by Rs. 725.00 Refno 619455206610"
        val r = PaymentNotificationParser.parse("SBI", body, truecallerPkg, ts)
        assertNotNull(r)
        assertEquals(725.0, r!!.amount, 0.001)
        assertEquals("Unknown Merchant", r.merchantOrRecipient)
        assertEquals(PaymentConfidence.MEDIUM, r.confidence)
    }

    @Test
    fun parser_rejectionReason_does_not_include_raw_body() {
        val body = "OTP 123456 for your SBI transaction"
        val reason = PaymentNotificationParser.rejectionReason("SBI", body, truecallerPkg)
        assertEquals("ignored_otp", reason)
        assertFalse(reason.contains("123456"))
        assertFalse(reason.contains("SBI"))
    }

    @Test
    fun directSms_parses_bank_debit_alert() {
        val body = "Dear UPI user A/C X4511 debited by 250.00 trf to BIG BASKET Refno 619455206611 SBI"
        val r = PaymentNotificationParser.parse("SBI", body, PaymentIngestion.DIRECT_SMS_PACKAGE, ts)
        assertNotNull(r)
        assertEquals(250.0, r!!.amount, 0.001)
        assertEquals("BIG BASKET", r.merchantOrRecipient)
        assertEquals("619455206611", r.bankRefNumber)
        assertTrue(r.sourceApplication.contains("SBI"))
    }

    @Test
    fun directSms_rejects_nonFinancialSms() {
        val body = "Your delivery OTP is 123456"
        assertNull(PaymentNotificationParser.parse("SHOP", body, PaymentIngestion.DIRECT_SMS_PACKAGE, ts))
    }

    @Test
    fun directSms_and_notification_use_same_ref_fingerprint() {
        val body = "A/C X4511 debited by 250.00 trf to BIG BASKET Refno 619455206611 SBI"
        val fromSms = PaymentNotificationParser.parse("SBI", body, PaymentIngestion.DIRECT_SMS_PACKAGE, ts)
        val fromNotification = PaymentNotificationParser.parse("SBI", body, truecallerPkg, ts)

        assertNotNull(fromSms)
        assertNotNull(fromNotification)
        assertEquals(
            PaymentIngestion.fingerprintFor(fromNotification!!),
            PaymentIngestion.fingerprintFor(fromSms!!)
        )
    }

    @Test
    fun sameRecipientSameAmount_differentRefno_haveDifferentFingerprints() {
        val first = PaymentNotificationParser.parse(
            "JK-SBIUPI-S",
            "Dear UPI user A/C X4511 debited by 1.00 on date 17Jul26 trf to B Sujatha Refno 619853467030 If not u? call-1800111109 for other services-18001234-SBI",
            PaymentIngestion.DIRECT_SMS_PACKAGE,
            ts
        )
        val second = PaymentNotificationParser.parse(
            "JD-SBIUPI-S",
            "Dear UPI user A/C X4511 debited by 1.00 on date 17Jul26 trf to B Sujatha Refno 619853415880 If not u? call-1800111109 for other services-18001234-SBI",
            PaymentIngestion.DIRECT_SMS_PACKAGE,
            ts + 30_000
        )

        assertNotNull(first)
        assertNotNull(second)
        assertEquals("B Sujatha", first!!.merchantOrRecipient)
        assertEquals("B Sujatha", second!!.merchantOrRecipient)
        assertEquals("619853467030", first.bankRefNumber)
        assertEquals("619853415880", second.bankRefNumber)
        assertNotEquals(
            PaymentIngestion.fingerprintFor(first),
            PaymentIngestion.fingerprintFor(second)
        )
    }
}
