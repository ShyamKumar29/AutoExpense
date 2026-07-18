package com.autoexpense.app.notification

import android.util.Log
import com.autoexpense.app.BuildConfig
import com.autoexpense.app.data.BillEntity
import com.autoexpense.app.data.BillRepository

object BillIngestion {
    private const val TAG = "AutoExpenseBillIngestion"

    suspend fun ingestParsedBill(bill: ParsedBill, origin: String, sourceId: String): Boolean {
        val now = System.currentTimeMillis()
        val status = bill.dueDate?.let { due ->
            when {
                due < now -> "OVERDUE"
                due - now <= 3L * 24L * 60L * 60L * 1000L -> "DUE_SOON"
                else -> "UPCOMING"
            }
        } ?: "UPCOMING"

        val inserted = BillRepository.insertIfNew(
            BillEntity(
                id = bill.id,
                billType = bill.billType,
                provider = bill.provider,
                amount = bill.amount,
                currency = bill.currency,
                dueDate = bill.dueDate,
                status = status,
                generatedAt = bill.generatedAt,
                source = bill.source,
                safeExcerpt = bill.safeExcerpt,
                billFingerprint = bill.billFingerprint,
                createdAt = now,
                updatedAt = now
            )
        )
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "bill_${if (inserted) "inserted" else "duplicate"} origin=$origin sourceId=$sourceId type=${bill.billType}")
        }
        return inserted
    }
}
