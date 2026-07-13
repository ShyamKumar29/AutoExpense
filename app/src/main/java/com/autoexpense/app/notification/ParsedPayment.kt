package com.autoexpense.app.notification

enum class PaymentConfidence { HIGH, MEDIUM, LOW }

/**
 * Minimal, privacy-safe representation of a detected outgoing payment notification.
 *
 * Only fields required to populate a Transaction and display in the Needs Review UI are
 * kept. Raw notification body, actions, and unrelated personal content are discarded.
 *
 * @param bankRefNumber  Optional bank reference/UTR number extracted from bank SMS alerts.
 *                       Used as the strongest deduplication key when present.
 */
data class ParsedPayment(
    val id: String,
    val amount: Double,
    val currency: String = "INR",
    val merchantOrRecipient: String,
    val sourceApplication: String,
    val sourcePackage: String,
    val timestamp: Long,
    val safeNotificationExcerpt: String,
    val confidence: PaymentConfidence,
    val detectionReason: String,
    val bankRefNumber: String? = null   // Phase 2.1: used for dedup
)
