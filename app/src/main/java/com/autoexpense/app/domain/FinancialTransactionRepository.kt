package com.autoexpense.app.domain

import com.autoexpense.app.data.FinancialTransactionDao
import com.autoexpense.app.data.FinancialTransactionEntity
import com.autoexpense.app.Transaction
import com.autoexpense.app.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * Domain-facing adapter over the existing repository.
 *
 * The repository still owns persistence. Domain services consume this adapter so
 * screens can ask what the user's money data means without duplicating rules.
 */
object FinancialTransactionRepository {
    private var dao: FinancialTransactionDao? = null

    val rawTransactions: StateFlow<List<Transaction>> = TransactionRepository.transactions

    val transactions = TransactionRepository.transactions.map { items ->
        with(FinancialTransactionMapper) { items.toFinancialTransactions() }
    }

    fun init(financialTransactionDao: FinancialTransactionDao) {
        dao = financialTransactionDao
    }

    suspend fun insert(transaction: FinancialTransaction) {
        requireDao().upsert(FinancialTransactionEntity.fromDomain(transaction))
    }

    suspend fun update(transaction: FinancialTransaction) {
        requireDao().upsert(FinancialTransactionEntity.fromDomain(transaction.copy(updatedAt = System.currentTimeMillis())))
    }

    suspend fun delete(id: String) {
        requireDao().softDelete(id)
    }

    fun observeAllTransactions(): Flow<List<FinancialTransaction>> {
        return requireDao().observeAll().map { list -> list.map { it.toDomain() } }
    }

    suspend fun search(query: String): List<FinancialTransaction> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return getAllTransactions()
        return requireDao().search(trimmed).map { it.toDomain() }
    }

    suspend fun getAllTransactions(): List<FinancialTransaction> {
        return requireDao().getAll().map { it.toDomain() }
    }

    suspend fun getTransactionsByType(type: TransactionType): List<FinancialTransaction> =
        requireDao().getByType(type.name).map { it.toDomain() }

    suspend fun getExpenses(): List<FinancialTransaction> =
        getTransactionsByType(TransactionType.EXPENSE) + getTransactionsByType(TransactionType.CREDIT_CARD_PURCHASE)

    suspend fun getIncome(): List<FinancialTransaction> =
        getTransactionsByType(TransactionType.INCOME)

    suspend fun getRefunds(): List<FinancialTransaction> =
        getTransactionsByType(TransactionType.REFUND)

    suspend fun getCashback(): List<FinancialTransaction> =
        getTransactionsByType(TransactionType.CASHBACK)

    suspend fun getInterest(): List<FinancialTransaction> =
        getTransactionsByType(TransactionType.INTEREST)

    suspend fun getTransfers(): List<FinancialTransaction> =
        getTransactionsByType(TransactionType.TRANSFER)

    suspend fun getTransactionsBetweenDates(startMs: Long, endMs: Long): List<FinancialTransaction> =
        requireDao().getBetween(startMs, endMs).map { it.toDomain() }

    suspend fun getTransactionsByCategory(category: String): List<FinancialTransaction> =
        requireDao().getByCategory(category).map { it.toDomain() }

    suspend fun getTransactionsByMerchant(merchant: String): List<FinancialTransaction> =
        requireDao().getByMerchant(merchant).map { it.toDomain() }

    suspend fun getTransactionsByPaymentMethod(paymentMethod: String): List<FinancialTransaction> =
        requireDao().getByPaymentMethod(paymentMethod).map { it.toDomain() }

    suspend fun getRecentTransactions(limit: Int = 20): List<FinancialTransaction> =
        requireDao().getRecent(limit.coerceAtLeast(1)).map { it.toDomain() }

    fun confirmExpense(id: String, category: String, onComplete: (() -> Unit)? = null) {
        TransactionRepository.confirmTransaction(id, category, onComplete)
    }

    fun updateTransaction(
        id: String,
        merchantName: String,
        category: String,
        note: String,
        updateMemory: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        TransactionRepository.updateTransaction(id, merchantName, category, note, updateMemory, onComplete)
    }

    fun ignoreTransaction(id: String) {
        TransactionRepository.ignoreTransaction(id)
    }

    fun deleteTransaction(id: String, onComplete: (() -> Unit)? = null) {
        TransactionRepository.deleteTransaction(id, onComplete)
    }

    private fun requireDao(): FinancialTransactionDao {
        return dao ?: error("FinancialTransactionRepository.init() must be called before using Room-backed APIs")
    }
}
