package com.autoexpense.app.notification

import com.autoexpense.app.DashboardViewModel
import com.autoexpense.app.Transaction
import com.autoexpense.app.data.RecurringPaymentEntity
import java.util.UUID
import kotlin.math.abs

object RecurringPaymentDetector {
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun detect(transactions: List<Transaction>, nowMs: Long = System.currentTimeMillis()): List<RecurringPaymentEntity> {
        val confirmed = DashboardViewModel.computeConfirmedOutgoing(transactions)
        val grouped = confirmed.groupBy { keyFor(it) }
        return grouped.mapNotNull { (_, txns) ->
            if (txns.size < 2) return@mapNotNull null
            val sorted = txns.sortedBy { it.timestamp }
            val intervals = sorted.zipWithNext { a, b -> ((b.timestamp - a.timestamp) / DAY_MS).toInt() }
                .filter { it > 0 }
            if (intervals.isEmpty()) return@mapNotNull null

            val median = intervals.sorted()[intervals.size / 2]
            val frequency = when {
                median in 6..8 -> "WEEKLY"
                median in 27..33 -> "MONTHLY"
                median in 350..380 -> "YEARLY"
                median >= 10 -> "EVERY_${median}_DAYS"
                else -> return@mapNotNull null
            }
            val stableIntervals = intervals.count { abs(it - median) <= 4 }
            val confidence = (0.55f + (stableIntervals.toFloat() / intervals.size.toFloat()) * 0.4f).coerceAtMost(0.95f)
            if (confidence < 0.75f) return@mapNotNull null

            val latest = sorted.last()
            val amount = DashboardViewModel.parseAmount(latest.amount)
            val normalizedMerchant = normalize(latest.merchant)
            val nextExpectedAt = latest.timestamp + median * DAY_MS
            val status = if (nextExpectedAt + 3 * DAY_MS < nowMs) "MISSED" else "ACTIVE"

            RecurringPaymentEntity(
                id = UUID.nameUUIDFromBytes("recurring|$normalizedMerchant|$amount|${latest.source}".toByteArray()).toString(),
                merchant = latest.merchant,
                normalizedMerchant = normalizedMerchant,
                amount = amount,
                currency = "INR",
                frequency = frequency,
                lastPaymentAt = latest.timestamp,
                nextExpectedAt = nextExpectedAt,
                status = status,
                confidence = confidence,
                createdAt = nowMs,
                updatedAt = nowMs
            )
        }.sortedBy { it.nextExpectedAt }
    }

    private fun keyFor(t: Transaction): String {
        val amountBucket = DashboardViewModel.parseAmount(t.amount).let { "%.0f".format(it) }
        return "${normalize(t.merchant)}|$amountBucket|${t.source.lowercase()}"
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("""[^a-z0-9]+"""), "")
}
