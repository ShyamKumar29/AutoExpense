package com.autoexpense.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bills",
    indices = [
        Index(value = ["status", "dueDate"]),
        Index(value = ["billFingerprint"], unique = true)
    ]
)
data class BillEntity(
    @PrimaryKey
    val id: String,
    val billType: String,
    val provider: String,
    val amount: Double,
    val currency: String = "INR",
    val dueDate: Long?,
    val status: String,
    val generatedAt: Long,
    val paidAt: Long? = null,
    val paidTransactionId: String? = null,
    val source: String,
    val safeExcerpt: String,
    val billFingerprint: String,
    val createdAt: Long,
    val updatedAt: Long
)
