package com.autoexpense.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recurring_payments",
    indices = [Index(value = ["normalizedMerchant", "amount", "currency"], unique = true)]
)
data class RecurringPaymentEntity(
    @PrimaryKey
    val id: String,
    val merchant: String,
    val normalizedMerchant: String,
    val amount: Double,
    val currency: String = "INR",
    val frequency: String,
    val lastPaymentAt: Long,
    val nextExpectedAt: Long,
    val status: String,
    val confidence: Float,
    val createdAt: Long,
    val updatedAt: Long
)
