package com.autoexpense.app.notification

import java.util.UUID
import com.autoexpense.app.domain.TransactionClassificationService
import com.autoexpense.app.domain.TransactionType

/**
 * Stateless parser for payment notifications.
 *
 * Two detection paths:
 * 1. UPI app path  – known payment app packages (GPay, PhonePe, Paytm, BHIM).
 * 2. Bank SMS path – messaging app packages; only accepted when strong bank-debit
 *                    patterns are present. Immediately discards unrelated messages.
 *
 * Privacy: ≤80-char masked excerpt retained; full text discarded after parsing.
 * No Android framework dependencies – fully testable with plain JUnit.
 */
object PaymentNotificationParser {

    // ─────────────────────────────────────────────────────────────────────────
    // IGNORE PHRASES – checked first; always win regardless of path.
    // ─────────────────────────────────────────────────────────────────────────
    private val IGNORE_PHRASES = listOf(
        "credited by",
        "credited with",
        "amount credited",
        "cashback received",
        "amount received",
        "you received",
        "payment failed",
        "transaction failed",
        "payment declined",
        "transaction declined",
        "payment reversed",
        "transaction reversed",
        "reversal",
        "cashback",
        "promotional offer",
        "loan offer",
        "payment request",
        "collect request",
        "payment reminder",
        "available balance",
        "low balance",
        "minimum balance",
        "otp",
        "one-time password",
        // Single-word fallbacks listed last so multi-word matches above take priority
        "received",
        "credited",
        "refund",
        "reversed",
        "pending",
        "processing",
        "reminder",
        "failed",
        "declined"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // UPI APP PATH – outgoing payment phrases
    // ─────────────────────────────────────────────────────────────────────────
    private val UPI_OUTGOING_PHRASES = listOf(
        "payment successful",
        "transaction successful",
        "you paid",
        "you sent",
        "was successful",
        "debited",
        "transferred",
        "paid",
        "sent",
        "successful"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // BANK SMS PATH – strong debit phrases required for messaging-app acceptance
    // ─────────────────────────────────────────────────────────────────────────
    private val BANK_DEBIT_PHRASES = listOf(
        "debited by",
        "debited for",
        "debited with",
        "a/c debited",
        "acct debited",
        "account debited",
        "ac debited",
        "debited from",
        "has been debited",
        "is debited",
        "debit of",
        "trf to",
        "transferred to",
        "transfer to",
        "paid to",
        "spent",
        "spent on",
        "spent at",
        "spent using",
        "purchase of",
        "transaction of",
        "txn of"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // AMOUNT PATTERNS
    // ─────────────────────────────────────────────────────────────────────────

    // Currency-prefixed: ₹450 | Rs. 1,500 | Rs 450 | INR 2,000
    // Group 1: rupee-prefixed   Group 2: INR-prefixed
    private val AMOUNT_WITH_CURRENCY = Regex(
        """(?:₹\s*|Rs\.?\s*)([\d,]+(?:\.\d{1,2})?)|INR\s+([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // Contextual: "debited by 1.00" – plain number after debit phrase (no currency symbol needed).
    // Limits to 10 digit-or-comma characters to exclude long reference/phone numbers.
    // Note: {N}? is a valid lazy quantifier in Java/Kotlin regex; avoid {N?} (invalid).
    private val DEBIT_PHRASE_AMOUNT = Regex(
        """debited\s+(?:by|for|with)\s+(?:₹\s*|Rs\.?\s*|INR\s*)?([\d,]{1,10}(?:\.\d{1,2})?)(?!\d)""",
        RegexOption.IGNORE_CASE
    )

    private val BANK_ACTION_AMOUNT = Regex(
        """(?:debited\s+from|has\s+been\s+debited|is\s+debited|debit\s+of|spent(?:\s+(?:on|at|using))?|purchase\s+of|transaction\s+of|txn\s+of)\s+(?:[A-Za-z/.*Xx\d\s-]{0,30}?\s+)?(?:by\s+|for\s+|of\s+)?(?:₹\s*|Rs\.?\s*|INR\s*)?([\d,]{1,10}(?:\.\d{1,2})?)(?!\d)""",
        RegexOption.IGNORE_CASE
    )

    // ─────────────────────────────────────────────────────────────────────────
    // MERCHANT / RECIPIENT PATTERNS
    // ─────────────────────────────────────────────────────────────────────────

    // UPI pattern A: "to X was/is/has" – lazy stop before auxiliary verb
    private val MERCHANT_TO_WAS = Regex(
        """to\s+([A-Za-z0-9][A-Za-z0-9\s.\-_@]{0,49}?)\s+(?:was|is|has)\b""",
        RegexOption.IGNORE_CASE
    )

    // UPI pattern B: "to X" at end-of-string
    private val MERCHANT_TO_END = Regex(
        """to\s+([A-Za-z0-9][A-Za-z0-9\s.\-_@]{0,49})\s*$""",
        RegexOption.IGNORE_CASE
    )

    // Bank SMS: "trf to NAME" or "transferred to NAME", stopping before stop-words/ref-numbers.
    // Uses positive lookahead so the stop-word is not consumed and not included in capture.
    private val BANK_TRF_RECIPIENT = Regex(
        """(?:trf|transfer(?:red)?)\s+to\s+([A-Za-z0-9][A-Za-z0-9\s.\-_@]{0,49}?)""" +
        """(?=\s*(?:refno|ref\s*no|reference|utr|upi\s*ref|txn\s*id|transaction\s*id""" +
        """|on\s+date|if\s+not|call\b|for\b)|\s*\d{6,}|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    private val BANK_TO_RECIPIENT = Regex(
        """(?:paid\s+to|sent\s+to|to)\s+([A-Za-z0-9][A-Za-z0-9\s.\-_@]{0,49}?)""" +
        """(?=\s*(?:refno|ref\s*no|reference|utr|upi\s*ref|txn\s*id|transaction\s*id""" +
        """|on\s+date|if\s+not|call\b|for\b|upi\b)|\s*\d{6,}|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    private val INCOMING_COUNTERPARTY_PATTERNS = listOf(
        Regex(
            """(?:received|you\s+received)\s+(?:Rs\.?\s*|INR\s*|\u20B9\s*)?[\d,]+(?:\.\d{1,2})?\s+from\s+([A-Za-z0-9][A-Za-z0-9\s.\-_@]{0,49}?)""" +
                """(?=\s+(?:refno|ref\s*no|reference|utr|upi\s*ref|txn\s*id|transaction\s*id|on\b|via\b|to\b|for\b|rs\.?\b|inr\b)|\s*\u20B9|\s*\d{6,}|\s*$)""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """(?:received\s+from|money\s+received\s+from|amount\s+received\s+from|transfer\s+(?:received\s+)?from|upi\s+(?:received\s+)?from|neft\s+(?:received\s+)?from|imps\s+(?:received\s+)?from|rtgs\s+(?:received\s+)?from)\s+([A-Za-z0-9][A-Za-z0-9\s.\-_@]{0,49}?)""" +
                """(?=\s+(?:refno|ref\s*no|reference|utr|upi\s*ref|txn\s*id|transaction\s*id|on\b|via\b|to\b|for\b|rs\.?\b|inr\b)|\s*\u20B9|\s*\d{6,}|\s*$)""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """(?:salary\s+credited\s+(?:by|from)|salary\s+deposited\s+(?:by|from)|credited\s+(?:by|from)|amount\s+credited\s+(?:by|from))\s+([A-Za-z0-9][A-Za-z0-9\s.\-_@]{0,49}?)""" +
                """(?=\s+(?:refno|ref\s*no|reference|utr|upi\s*ref|txn\s*id|transaction\s*id|on\b|via\b|to\b|for\b|rs\.?\b|inr\b)|\s*\u20B9|\s*\d{6,}|\s*$)""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """refund\s+from\s+([A-Za-z0-9][A-Za-z0-9\s.\-_@]{0,49}?)""" +
                """(?=\s+(?:refno|ref\s*no|reference|utr|upi\s*ref|txn\s*id|transaction\s*id|on\b|via\b|to\b|for\b|rs\.?\b|inr\b)|\s*\u20B9|\s*\d{6,}|\s*$)""",
            RegexOption.IGNORE_CASE
        )
    )

    // Bank SMS reference number: "Refno 619473207907" / "UTR 123456"
    private val BANK_REF_NO = Regex(
        """(?:refno|ref\s*no|reference\s*no|utr|upi\s*ref)\s*[:\-]?\s*(\d{6,20})""",
        RegexOption.IGNORE_CASE
    )

    // Account mask: X4511 – must not be parsed as an amount
    private val ACCOUNT_MASK = Regex("""[Xx]\d{3,6}""")

    private val INVALID_MERCHANT_TOKENS = setOf(
        "your", "a", "the", "my", "his", "her", "its", "our", "you", "upi", "user", "rs", "rs.", "inr"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC ENTRY POINT
    // ─────────────────────────────────────────────────────────────────────────

    fun parse(
        title: String,
        body: String,
        packageName: String,
        timestamp: Long
    ): ParsedPayment? {
        val fullText = buildString {
            if (title.isNotBlank()) { append(title.trim()); append(" ") }
            if (body.isNotBlank()) append(body.trim())
        }.trim()
        if (fullText.isBlank()) return null

        val lower = fullText.lowercase()
        val classifiedType = TransactionClassificationService.classifyNotificationText(fullText)
        if (classifiedType != TransactionType.UNKNOWN &&
            classifiedType != TransactionType.EXPENSE &&
            classifiedType != TransactionType.CREDIT_CARD_PURCHASE
        ) {
            return parseClassifiedFinancialEvent(fullText, packageName, timestamp, classifiedType)
        }

        // Step 1 – global ignore list (always wins)
        if (IGNORE_PHRASES.any { lower.contains(it) }) return null

        val isMessaging = SupportedPaymentSources.isMessagingApp(packageName)
        val knownSource = SupportedPaymentSources.findSource(packageName)

        return if (isMessaging) {
            parseBankSms(fullText, lower, packageName, timestamp)
        } else {
            parseUpiApp(fullText, lower, packageName, knownSource, timestamp)
        }
    }

    private fun parseClassifiedFinancialEvent(
        fullText: String,
        packageName: String,
        timestamp: Long,
        transactionType: TransactionType
    ): ParsedPayment? {
        val amount = extractAmountBankSms(fullText) ?: extractAmount(fullText) ?: return null
        if (amount <= 0.0) return null
        val bank = KnownBanks.detect(fullText)
        val source = SupportedPaymentSources.findSource(packageName)
        val counterparty = extractIncomingCounterparty(fullText)
            ?: extractMerchantBankSms(fullText)
            ?: extractMerchant(fullText)
            ?: when (transactionType) {
                TransactionType.INCOME -> "Income"
                TransactionType.REFUND -> "Refund"
                TransactionType.CASHBACK -> "Cashback"
                TransactionType.INTEREST -> "Interest"
                TransactionType.TRANSFER -> "Transfer"
                else -> "Unknown Merchant"
            }
        val sourceDisplay = bank?.let { "${it.displayName} Bank SMS" }
            ?: source?.displayName
            ?: if (packageName.isNotBlank()) packageName else "Unknown Source"
        return ParsedPayment(
            id = UUID.randomUUID().toString(),
            amount = amount,
            currency = "INR",
            merchantOrRecipient = counterparty,
            sourceApplication = sourceDisplay,
            sourcePackage = packageName,
            timestamp = timestamp,
            safeNotificationExcerpt = maskedExcerpt(fullText),
            confidence = if (bank != null || source != null) PaymentConfidence.HIGH else PaymentConfidence.MEDIUM,
            detectionReason = "Financial event classified as ${transactionType.name}",
            bankRefNumber = extractBankRef(fullText),
            paymentMethod = PaymentMethodDetector.detect("", fullText, packageName),
            transactionType = transactionType
        )
    }

    internal fun rejectionReason(
        title: String,
        body: String,
        packageName: String
    ): String {
        val fullText = buildString {
            if (title.isNotBlank()) { append(title.trim()); append(" ") }
            if (body.isNotBlank()) append(body.trim())
        }.trim()
        if (fullText.isBlank()) return "blank_text"

        val lower = fullText.lowercase()
        IGNORE_PHRASES.firstOrNull { lower.contains(it) }?.let {
            return "ignored_${reasonToken(it)}"
        }

        return if (SupportedPaymentSources.isMessagingApp(packageName)) {
            if (BANK_DEBIT_PHRASES.none { lower.contains(it) }) {
                "bank_sms_no_debit_phrase"
            } else if (extractAmountBankSms(fullText) == null) {
                "bank_sms_no_amount"
            } else {
                "bank_sms_unknown_rejection"
            }
        } else {
            if (UPI_OUTGOING_PHRASES.none { lower.contains(it) }) {
                "upi_no_outgoing_phrase"
            } else if (extractAmount(fullText) == null) {
                "upi_no_amount"
            } else {
                "upi_unknown_rejection"
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPI APP PATH
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseUpiApp(
        fullText: String,
        lower: String,
        packageName: String,
        source: PaymentSource?,
        timestamp: Long
    ): ParsedPayment? {
        val matchedPhrase = UPI_OUTGOING_PHRASES.firstOrNull { lower.contains(it) } ?: return null
        val amount = extractAmount(fullText) ?: return null
        if (amount <= 0.0) return null

        val merchant = extractMerchantUpi(fullText) ?: "Unknown Merchant"
        val sourceDisplay = source?.displayName
            ?: if (packageName.isNotBlank()) packageName else "Unknown App"

        val confidence = when {
            source != null && merchant != "Unknown Merchant" -> PaymentConfidence.HIGH
            source != null                                    -> PaymentConfidence.MEDIUM
            else                                              -> PaymentConfidence.LOW
        }

        return ParsedPayment(
            id = UUID.randomUUID().toString(),
            amount = amount,
            currency = "INR",
            merchantOrRecipient = merchant,
            sourceApplication = sourceDisplay,
            sourcePackage = packageName,
            timestamp = timestamp,
            safeNotificationExcerpt = maskedExcerpt(fullText),
            confidence = confidence,
            detectionReason = "Outgoing payment phrase detected: \"$matchedPhrase\"",
            paymentMethod = PaymentMethodDetector.detect("", fullText, packageName),
            transactionType = TransactionClassificationService.classifyNotificationText(fullText)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BANK SMS PATH
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseBankSms(
        fullText: String,
        lower: String,
        packageName: String,
        timestamp: Long
    ): ParsedPayment? {
        // Require at least one strong bank-debit phrase
        val matchedPhrase = BANK_DEBIT_PHRASES.firstOrNull { lower.contains(it) } ?: return null

        val amount = extractAmountBankSms(fullText) ?: return null
        if (amount <= 0.0) return null

        val recipient = extractMerchantBankSms(fullText) ?: "Unknown Merchant"
        val bank = KnownBanks.detect(fullText)
        val bankRef = extractBankRef(fullText)

        val sourceDisplay = if (bank != null) "${bank.displayName} Bank SMS" else "Bank SMS"

        val confidence = when {
            bank != null && recipient != "Unknown Merchant" -> PaymentConfidence.HIGH
            bank != null                                     -> PaymentConfidence.MEDIUM
            else                                             -> PaymentConfidence.LOW
        }

        return ParsedPayment(
            id = UUID.randomUUID().toString(),
            amount = amount,
            currency = "INR",
            merchantOrRecipient = recipient,
            sourceApplication = sourceDisplay,
            sourcePackage = packageName,
            timestamp = timestamp,
            safeNotificationExcerpt = maskedExcerpt(fullText),
            confidence = confidence,
            detectionReason = "Outgoing bank debit notification detected",
            bankRefNumber = bankRef,
            paymentMethod = PaymentMethodDetector.detect("", fullText, packageName),
            transactionType = TransactionClassificationService.classifyNotificationText(fullText)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AMOUNT EXTRACTION  (internal visibility for unit tests)
    // ─────────────────────────────────────────────────────────────────────────

    /** UPI app: currency-prefixed amounts only. */
    internal fun extractAmount(text: String): Double? {
        val m = AMOUNT_WITH_CURRENCY.find(text) ?: return null
        val raw = m.groupValues[1].ifEmpty { m.groupValues[2] }
        if (raw.isEmpty()) return null
        return raw.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }
    }

    /**
     * Bank SMS: tries contextual debit-phrase amount first, then currency-prefixed.
     *
     * Guards against extracting:
     * - account masks  (X4511)
     * - date tokens    (13Jul26 – non-digit chars prevent `[\d,]` from matching)
     * - reference nums (>10 digits – the `{1,10}` limit in DEBIT_PHRASE_AMOUNT blocks them)
     * - phone numbers  (same digit-count guard)
     */
    internal fun extractAmountBankSms(text: String): Double? {
        // Priority 1 – "debited by/for/with X.XX"
        DEBIT_PHRASE_AMOUNT.find(text)?.let { m ->
            val raw = m.groupValues[1].replace(",", "")
            val value = raw.toDoubleOrNull()
            if (value != null && value > 0) return value
        }

        // Priority 2 – alternate bank debit/spend wording with a nearby amount
        BANK_ACTION_AMOUNT.find(text)?.let { m ->
            val raw = m.groupValues[1].replace(",", "")
            val value = raw.toDoubleOrNull()
            if (value != null && value > 0) return value
        }

        // Priority 3 – currency-prefixed amount anywhere in the text
        AMOUNT_WITH_CURRENCY.find(text)?.let { m ->
            val raw = m.groupValues[1].ifEmpty { m.groupValues[2] }
            if (raw.isNotEmpty()) {
                val value = raw.replace(",", "").toDoubleOrNull()
                if (value != null && value > 0) return value
            }
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MERCHANT EXTRACTION  (internal visibility for unit tests)
    // ─────────────────────────────────────────────────────────────────────────

    /** UPI app merchant extraction (also used by the test helper). */
    internal fun extractMerchant(text: String): String? = extractMerchantUpi(text)

    private fun extractMerchantUpi(text: String): String? {
        MERCHANT_TO_WAS.find(text)?.let { m ->
            val c = m.groupValues[1].trim()
            if (isValidMerchantName(c)) return c
        }
        MERCHANT_TO_END.find(text)?.let { m ->
            val c = m.groupValues[1].trim()
            if (isValidMerchantName(c)) return c
        }
        return null
    }

    /** Bank SMS recipient extraction. */
    internal fun extractMerchantBankSms(text: String): String? {
        BANK_TRF_RECIPIENT.find(text)?.let { m ->
            val c = m.groupValues[1].trim()
            if (isValidMerchantName(c)) return c
        }
        BANK_TO_RECIPIENT.find(text)?.let { m ->
            val c = m.groupValues[1].trim()
            if (isValidMerchantName(c)) return c
        }
        return null
    }

    internal fun extractIncomingCounterparty(text: String): String? {
        return INCOMING_COUNTERPARTY_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.getOrNull(1)?.let { raw ->
                cleanupCounterparty(raw).takeIf { isValidMerchantName(it) }
            }
        }
    }

    private fun cleanupCounterparty(value: String): String {
        return value
            .replace(
                Regex("""(?i)\s+(?:refno|ref\s*no|reference|utr|upi\s*ref|txn\s*id|transaction\s*id|on\b|via\b|to\b|for\b|a/c|account|bank)\b.*$"""),
                ""
            )
            .trim(' ', '.', '-', ':')
    }

    private fun isValidMerchantName(name: String): Boolean {
        if (name.isBlank() || name.length > 50) return false
        return name.lowercase().trim() !in INVALID_MERCHANT_TOKENS
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BANK REF / PRIVACY HELPERS  (internal for tests)
    // ─────────────────────────────────────────────────────────────────────────

    internal fun extractBankRef(text: String): String? =
        BANK_REF_NO.find(text)?.groupValues?.get(1)

    private fun reasonToken(phrase: String): String =
        phrase.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), "_")
            .trim('_')

    /** ≤80-char excerpt with account masks and long numbers replaced. */
    internal fun maskedExcerpt(text: String): String {
        val masked = text
            .replace(ACCOUNT_MASK, "X****")
            .replace(Regex("""\b\d{10,}\b"""), "**ref**")
        return if (masked.length > 80) masked.take(80) + "…" else masked
    }
}
