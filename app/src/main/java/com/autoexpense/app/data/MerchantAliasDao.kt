package com.autoexpense.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantAliasDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alias: MerchantAliasEntity)

    @Query("SELECT * FROM merchant_aliases ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MerchantAliasEntity>>

    @Query("SELECT * FROM merchant_aliases ORDER BY updatedAt DESC")
    suspend fun getAllAliases(): List<MerchantAliasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(aliases: List<MerchantAliasEntity>)

    @Query("DELETE FROM merchant_aliases")
    suspend fun deleteAll()

    @Query("SELECT * FROM merchant_aliases WHERE normalizedRawMerchant = :normalized")
    suspend fun getByNormalizedMerchant(normalized: String): MerchantAliasEntity?
}
