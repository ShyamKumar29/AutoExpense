package com.autoexpense.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantCategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: MerchantCategoryMappingEntity)

    @Query("SELECT * FROM merchant_category_mappings ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MerchantCategoryMappingEntity>>

    @Query("SELECT * FROM merchant_category_mappings WHERE normalizedMerchant = :normalizedMerchant")
    suspend fun getByNormalizedMerchant(normalizedMerchant: String): MerchantCategoryMappingEntity?
}
