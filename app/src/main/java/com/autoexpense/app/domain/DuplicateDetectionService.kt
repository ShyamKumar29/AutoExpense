package com.autoexpense.app.domain

import com.autoexpense.app.TransactionRepository
import com.autoexpense.app.notification.KnownBanks
import com.autoexpense.app.notification.ParsedPayment

object DuplicateDetectionService {
    private const val DEDUP_WINDOW_MS = 5 * 60 * 1000L

    private val fingerprintMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

    suspend fun isDuplicate(payment: ParsedPayment): Boolean {
        val now = System.currentTimeMillis()
        val primary = payment.bankRefNumber
            ?.takeIf { it.isNotBlank() }
            ?.let { "ref|$it" }

        if (primary != null) {
            return isDuplicateFingerprint(primary, now)
        }

        return isDuplicateFingerprint(fallbackFingerprintFor(payment), now)
    }

    fun fingerprintFor(payment: ParsedPayment): String {
        if (!payment.bankRefNumber.isNullOrBlank()) {
            return "ref|${payment.bankRefNumber}"
        }
        return fallbackFingerprintFor(payment)
    }

    private suspend fun isDuplicateFingerprint(fingerprint: String, now: Long): Boolean {
        val lastSeen = fingerprintMap[fingerprint]
        if (lastSeen != null && (now - lastSeen) < DEDUP_WINDOW_MS) return true
        fingerprintMap[fingerprint] = now
        val exists = TransactionRepository.existsByFingerprint(fingerprint)
        fingerprintMap.entries.removeIf { (_, value) -> (now - value) > DEDUP_WINDOW_MS }
        return exists
    }

    private fun fallbackFingerprintFor(payment: ParsedPayment): String {
        val normalizedRecipient = payment.merchantOrRecipient.lowercase().replace(Regex("\\s+"), "")
        val bank = KnownBanks.detect(payment.sourceApplication)?.shortName
            ?: payment.sourcePackage.take(8)
        return "t2|${payment.amount}|$normalizedRecipient|$bank"
    }
}
