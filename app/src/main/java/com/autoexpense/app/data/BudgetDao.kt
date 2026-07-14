package com.autoexpense.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    // ── Write operations ──────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ── Read operations ───────────────────────────────────────────────────────

    @Query("SELECT * FROM budgets ORDER BY periodType ASC, categoryKey ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): BudgetEntity?

    /** Returns the existing budget for a given (categoryKey, periodType) pair, if any. */
    @Query("SELECT * FROM budgets WHERE categoryKey = :categoryKey AND periodType = :periodType LIMIT 1")
    suspend fun getByCategoryKeyAndPeriod(categoryKey: String, periodType: String): BudgetEntity?

    // ── Spending queries (confirmed transactions only) ────────────────────────

    /**
     * Sum of confirmed outgoing transactions within [startMs, endMs].
     * Ignores: review, ignored, incoming, failed, refund statuses.
     * Amount strings are stored as "−₹1,234.56" — we strip the prefix and
     * commas before summing with SQLite's CAST.
     *
     * Returns 0.0 when there are no matching rows (SUM of empty set → NULL,
     * COALESCE converts to 0.0).
     */
    @Query("""
        SELECT COALESCE(SUM(
            CAST(REPLACE(REPLACE(REPLACE(amount, '−₹', ''), ',', ''), ' ', '') AS REAL)
        ), 0.0)
        FROM transactions
        WHERE status = 'confirmed'
          AND timestamp >= :startMs
          AND timestamp <= :endMs
    """)
    suspend fun getTotalSpentInPeriod(startMs: Long, endMs: Long): Double

    /**
     * Sum of confirmed outgoing transactions for a specific category
     * within [startMs, endMs].
     */
    @Query("""
        SELECT COALESCE(SUM(
            CAST(REPLACE(REPLACE(REPLACE(amount, '−₹', ''), ',', ''), ' ', '') AS REAL)
        ), 0.0)
        FROM transactions
        WHERE status = 'confirmed'
          AND category = :category
          AND timestamp >= :startMs
          AND timestamp <= :endMs
    """)
    suspend fun getCategorySpentInPeriod(category: String, startMs: Long, endMs: Long): Double
}
