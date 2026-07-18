package com.autoexpense.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payment_instruments")
data class PaymentInstrumentEntity(
    @PrimaryKey
    val id: String,
    val paymentMethod: String,
    val displayName: String,
    val issuer: String = "",
    val maskedLast4: String = "",
    val providerPackage: String = "",
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
