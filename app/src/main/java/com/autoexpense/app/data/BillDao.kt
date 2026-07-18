package com.autoexpense.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(bill: BillEntity): Long

    @Query("SELECT * FROM bills ORDER BY COALESCE(dueDate, generatedAt) ASC")
    fun observeAll(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE status IN ('UPCOMING', 'DUE_SOON', 'OVERDUE') ORDER BY COALESCE(dueDate, generatedAt) ASC")
    suspend fun getOpenBills(): List<BillEntity>

    @Query("SELECT * FROM bills ORDER BY COALESCE(dueDate, generatedAt) ASC")
    suspend fun getAll(): List<BillEntity>

    @Query("UPDATE bills SET status = 'PAID', paidAt = :paidAt, paidTransactionId = :transactionId, updatedAt = :updatedAt WHERE id = :billId")
    suspend fun markPaid(billId: String, transactionId: String?, paidAt: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE bills SET status = 'DISMISSED', updatedAt = :updatedAt WHERE id = :billId")
    suspend fun dismiss(billId: String, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bills: List<BillEntity>)

    @Query("DELETE FROM bills")
    suspend fun deleteAll()
}
