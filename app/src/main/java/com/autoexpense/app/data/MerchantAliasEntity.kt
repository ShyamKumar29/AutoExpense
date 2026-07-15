package com.autoexpense.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchant_aliases")
data class MerchantAliasEntity(
    @PrimaryKey
    val normalizedRawMerchant: String,
    val rawMerchant: String,
    val displayName: String,
    val updatedAt: Long
)
