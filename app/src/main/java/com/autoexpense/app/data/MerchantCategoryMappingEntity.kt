package com.autoexpense.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchant_category_mappings")
data class MerchantCategoryMappingEntity(
    @PrimaryKey
    val normalizedMerchant: String,
    val merchantName: String,
    val category: String,
    val updatedAt: Long = System.currentTimeMillis()
)
