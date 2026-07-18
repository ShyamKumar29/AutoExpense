package com.autoexpense.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringPaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RecurringPaymentEntity>)

    @Query("SELECT * FROM recurring_payments ORDER BY nextExpectedAt ASC")
    fun observeAll(): Flow<List<RecurringPaymentEntity>>

    @Query("SELECT * FROM recurring_payments ORDER BY nextExpectedAt ASC")
    suspend fun getAll(): List<RecurringPaymentEntity>

    @Query("UPDATE recurring_payments SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM recurring_payments")
    suspend fun deleteAll()
}
