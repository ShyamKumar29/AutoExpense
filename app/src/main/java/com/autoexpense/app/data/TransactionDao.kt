package com.autoexpense.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = 'confirmed' ORDER BY timestamp DESC")
    fun observeConfirmed(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = 'confirmed' ORDER BY timestamp DESC")
    suspend fun getConfirmedTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE status = 'review' ORDER BY timestamp DESC")
    fun observeNeedsReview(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Query("UPDATE transactions SET category = :category, status = 'confirmed', updatedAt = :updatedAt WHERE id = :id")
    suspend fun confirmTransaction(id: String, category: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET status = 'ignored', updatedAt = :updatedAt WHERE id = :id")
    suspend fun ignoreTransaction(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) > 0 FROM transactions WHERE transactionFingerprint = :fingerprint")
    suspend fun existsByFingerprint(fingerprint: String): Boolean

    @Query("UPDATE transactions SET merchantOrRecipient = :merchant, category = :category, note = :note, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTransactionDetails(id: String, merchant: String, category: String, note: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)
}

