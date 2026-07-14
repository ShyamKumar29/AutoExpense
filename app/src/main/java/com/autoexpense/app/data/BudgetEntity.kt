package com.autoexpense.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single budget rule.
 *
 * - category = null  → overall budget (weekly or monthly total)
 * - category = "🍔 Food & Dining"  → category-specific budget
 *
 * A unique index on (category, periodType) ensures there is at most one
 * budget per category+period combination.  The NULL category is stored as
 * the sentinel string "OVERALL" in the index because SQLite does not
 * consider two NULLs to be equal for unique-index purposes, which would
 * allow unlimited overall budgets to be inserted.  The application layer
 * always converts null ↔ "OVERALL" when reading/writing.
 */
@Entity(
    tableName = "budgets",
    indices = [Index(value = ["categoryKey", "periodType"], unique = true)]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Human-readable category string (e.g. "🍔 Food & Dining").
     *  Null for an overall (all-category) budget. */
    val category: String?,

    /** Internal key used for the unique index.
     *  Equals "OVERALL" when category is null, otherwise equals category. */
    val categoryKey: String,

    /** "WEEKLY" or "MONTHLY" */
    val periodType: String,

    /** Spending limit in INR (must be > 0). */
    val limitAmount: Double,

    /** Fraction of limit at which a warning is first issued (default 0.70). */
    val warningThreshold: Double = 0.70,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Canonical period type identifiers. */
object PeriodType {
    const val WEEKLY  = "WEEKLY"
    const val MONTHLY = "MONTHLY"
}

/** Sentinel used as categoryKey when the budget is an overall budget. */
const val OVERALL_CATEGORY_KEY = "OVERALL"
