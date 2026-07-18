package com.autoexpense.app.notification

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

object BillNotificationParser {
    private val IGNORE = listOf(
        "otp", "cashback", "refund", "credited", "failed", "declined",
        "promotional", "offer", "collect request", "payment request"
    )

    private val BILL_KEYWORDS = listOf(
        "bill", "due date", "amount due", "statement", "minimum amount due",
        "electricity", "water", "gas", "broadband", "internet", "mobile",
        "dth", "insurance", "emi"
    )

    private val AMOUNT = Regex(
        """(?:amount\s+due|bill\s+amount|total\s+due|pay)\s*(?:of|is|:)?\s*(?:Rs\.?\s*|INR\s*)?([\d,]+(?:\.\d{1,2})?)|(?:Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val DUE_DATE = Regex(
        """(?:due\s+(?:date|on|by)|pay\s+by)\s*[:\-]?\s*(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{1,2}\s+[A-Za-z]{3,9}\s+\d{2,4})""",
        RegexOption.IGNORE_CASE
    )

    fun parse(title: String, body: String, packageName: String, timestamp: Long): ParsedBill? {
        val fullText = "$title $body".trim()
        if (fullText.isBlank()) return null
        val lower = fullText.lowercase()
        if (IGNORE.any { lower.contains(it) }) return null
        if (BILL_KEYWORDS.none { lower.contains(it) }) return null

        val amount = extractAmount(fullText) ?: return null
        val billType = detectBillType(fullText)
        val provider = extractProvider(title, body, billType)
        val dueDate = extractDueDate(fullText)
        val source = SupportedPaymentSources.findSource(packageName)?.displayName
            ?: KnownBanks.detect(fullText)?.displayName
            ?: packageName.ifBlank { "Notification" }
        val fingerprint = buildFingerprint(provider, billType, amount, dueDate ?: timestamp)

        return ParsedBill(
            id = UUID.randomUUID().toString(),
            billType = billType,
            provider = provider,
            amount = amount,
            dueDate = dueDate,
            generatedAt = timestamp,
            source = source,
            safeExcerpt = PaymentNotificationParser.maskedExcerpt(fullText),
            billFingerprint = fingerprint
        )
    }

    internal fun extractAmount(text: String): Double? {
        val match = AMOUNT.find(text) ?: return null
        val raw = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return null
        return raw.replace(",", "").toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    internal fun detectBillType(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("credit card") || lower.contains("statement") -> "CREDIT_CARD"
            lower.contains("electricity") || lower.contains("eb bill") -> "ELECTRICITY"
            lower.contains("water") -> "WATER"
            lower.contains("gas") -> "GAS"
            lower.contains("broadband") || lower.contains("internet") || lower.contains("wifi") -> "INTERNET"
            lower.contains("mobile") || lower.contains("postpaid") || lower.contains("prepaid") -> "MOBILE"
            lower.contains("dth") -> "DTH"
            lower.contains("insurance") || lower.contains("premium") -> "INSURANCE"
            lower.contains("emi") -> "EMI"
            else -> "OTHER"
        }
    }

    internal fun extractDueDate(text: String): Long? {
        val raw = DUE_DATE.find(text)?.groupValues?.getOrNull(1)?.trim() ?: return null
        val formats = listOf("dd/MM/yyyy", "dd/MM/yy", "dd-MM-yyyy", "dd-MM-yy", "d MMM yyyy", "d MMM yy", "dd MMM yyyy", "dd MMM yy")
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                return sdf.parse(raw)?.time
            } catch (_: Exception) {}
        }
        return null
    }

    private fun extractProvider(title: String, body: String, billType: String): String {
        val titleProvider = title.trim()
            .replace(Regex("""[^A-Za-z0-9 &._-]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (titleProvider.length in 2..40 && !titleProvider.equals("AutoExpense", ignoreCase = true)) {
            return titleProvider
        }
        val beforeBill = Regex("""([A-Za-z][A-Za-z0-9 &._-]{1,40})\s+(?:bill|statement|premium|emi)""", RegexOption.IGNORE_CASE)
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!beforeBill.isNullOrBlank()) return beforeBill
        return billType.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase(Locale.US) }
    }

    private fun buildFingerprint(provider: String, billType: String, amount: Double, dueOrGeneratedAt: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = dueOrGeneratedAt }
        val monthKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}"
        val normalizedProvider = provider.lowercase().replace(Regex("""[^a-z0-9]+"""), "")
        return "bill|$normalizedProvider|$billType|$amount|$monthKey"
    }
}
