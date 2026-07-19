package com.autoexpense.app.notification

import android.content.Context
import com.autoexpense.app.data.TransactionEntity
import com.autoexpense.app.domain.DuplicateDetectionService
import com.autoexpense.app.domain.FinancialEventPipeline

/**
 * Backwards-compatible facade for notification and direct-SMS ingestion.
 *
 * The actual orchestration now lives in FinancialEventPipeline:
 * parser -> classifier -> duplicate detection -> repositories -> derived services.
 */
object PaymentIngestion {
    const val DIRECT_SMS_PACKAGE = "com.autoexpense.sms"

    suspend fun ingestParsedPayment(
        payment: ParsedPayment,
        context: Context?,
        origin: String,
        sourceId: String
    ): Boolean {
        return FinancialEventPipeline.processParsedPayment(payment, context, origin, sourceId).inserted
    }

    internal suspend fun isDuplicate(payment: ParsedPayment): Boolean {
        return DuplicateDetectionService.isDuplicate(payment)
    }

    internal fun fingerprintFor(payment: ParsedPayment): String {
        return DuplicateDetectionService.fingerprintFor(payment)
    }

    internal fun toTransactionEntity(payment: ParsedPayment): TransactionEntity {
        return FinancialEventPipeline.toTransactionEntity(payment)
    }
}
