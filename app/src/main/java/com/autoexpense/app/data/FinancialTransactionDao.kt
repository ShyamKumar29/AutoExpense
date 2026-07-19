package com.autoexpense.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transaction: FinancialTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(transactions: List<FinancialTransactionEntity>)

    @Query("SELECT * FROM financial_transactions WHERE isDeleted = 0 ORDER BY date DESC")
    fun observeAll(): Flow<List<FinancialTransactionEntity>>

    @Query("SELECT * FROM financial_transactions WHERE isDeleted = 0 ORDER BY date DESC")
    suspend fun getAll(): List<FinancialTransactionEntity>

    @Query("SELECT * FROM financial_transactions WHERE isDeleted = 0 AND status = 'confirmed' ORDER BY date DESC")
    fun observeConfirmed(): Flow<List<FinancialTransactionEntity>>

    @Query("SELECT * FROM financial_transactions WHERE id = :id")
    suspend fun getById(id: String): FinancialTransactionEntity?

    @Query("SELECT * FROM financial_transactions WHERE isDeleted = 0 AND transactionType = :transactionType ORDER BY date DESC")
    fun observeByType(transactionType: String): Flow<List<FinancialTransactionEntity>>

    @Query("SELECT * FROM financial_transactions WHERE isDeleted = 0 AND transactionType = :transactionType ORDER BY date DESC")
    suspend fun getByType(transactionType: String): List<FinancialTransactionEntity>

    @Query("SELECT * FROM financial_transactions WHERE isDeleted = 0 AND date BETWEEN :startMs AND :endMs ORDER BY date DESC")
    suspend fun getBetween(startMs: Long, endMs: Long): List<FinancialTransactionEntity>

    @Query("SELECT * FROM financial_transactions WHERE isDeleted = 0 AND category = :category ORDER BY date DESC")
    suspend fun getByCategory(category: String): List<FinancialTransactionEntity>

    @Query("SELECT * FROM financial_transactions WHERE isDeleted = 0 AND merchant = :merchant ORDER BY date DESC")
    suspend fun getByMerchant(merchant: String): List<FinancialTransactionEntity>

    @Query("SELECT * FROM financial_transactions WHERE isDeleted = 0 AND paymentMethod = :paymentMethod ORDER BY date DESC")
    suspend fun getByPaymentMethod(paymentMethod: String): List<FinancialTransactionEntity>

    @Query("SELECT * FROM financial_transactions WHERE isDeleted = 0 ORDER BY date DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<FinancialTransactionEntity>

    @Query(
        """
        SELECT * FROM financial_transactions
        WHERE isDeleted = 0
        AND (
            merchant LIKE '%' || :query || '%'
            OR category LIKE '%' || :query || '%'
            OR notes LIKE '%' || :query || '%'
            OR referenceNumber LIKE '%' || :query || '%'
            OR transactionType LIKE '%' || :query || '%'
            OR CAST(amount AS TEXT) LIKE '%' || :query || '%'
        )
        ORDER BY date DESC
        """
    )
    suspend fun search(query: String): List<FinancialTransactionEntity>

    @Query("UPDATE financial_transactions SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM financial_transactions")
    suspend fun deleteAll()
}
