package com.autoexpense.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.autoexpense.app.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: String,
    val merchantOrRecipient: String,
    val sub: String = "",
    val amount: String,
    val currency: String = "INR",
    val source: String,
    val category: String,
    val note: String = "",
    val status: String,
    val timestamp: Long,
    val confidence: Float = 1.0f,
    val detectionReason: String,
    val safeNotificationExcerpt: String,
    val transactionFingerprint: String,
    val createdAt: Long,
    val updatedAt: Long,
    val rawMerchant: String = ""
) {
    fun toTransaction(): Transaction {
        val dateStr = SimpleDateFormat("d MMM, h:mm a", Locale.US).format(Date(timestamp))

        return Transaction(
            id = id,
            merchant = merchantOrRecipient,
            sub = sub,
            source = source,
            category = category,
            amount = amount,
            date = dateStr,
            status = status,
            notificationExcerpt = safeNotificationExcerpt,
            detectionReason = detectionReason,
            timestamp = timestamp,
            note = note,
            rawMerchant = if (rawMerchant.isNotBlank()) rawMerchant else merchantOrRecipient
        )
    }
}
