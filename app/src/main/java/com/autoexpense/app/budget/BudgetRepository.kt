package com.autoexpense.app.budget

import com.autoexpense.app.data.BudgetDao
import com.autoexpense.app.data.BudgetEntity
import com.autoexpense.app.data.OVERALL_CATEGORY_KEY
import com.autoexpense.app.data.PeriodType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.TimeZone

/**
 * Single source of truth for all budget operations.
 *
 * Spending calculations use the device's local timezone so that weekly and
 * monthly boundaries reflect what the user expects.
 */
class BudgetRepository(private val dao: BudgetDao) {

    // ── Observe ───────────────────────────────────────────────────────────────

    fun observeAllBudgets(): Flow<List<BudgetEntity>> = dao.observeAll()

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Insert or update a budget.
     *
     * Returns Result.success(id) on success, Result.failure if a duplicate
     * (same categoryKey+periodType) exists and [existingId] is null.
     */
    suspend fun saveBudget(
        category: String?,
        periodType: String,
        limitAmount: Double,
        warningThreshold: Double = 0.70,
        existingId: Long? = null
    ): Result<Long> {
        if (limitAmount <= 0) {
            return Result.failure(IllegalArgumentException("Amount must be greater than zero."))
        }

        val categoryKey = category ?: OVERALL_CATEGORY_KEY

        if (existingId == null) {
            val existing = dao.getByCategoryKeyAndPeriod(categoryKey, periodType)
            if (existing != null) {
                return Result.failure(
                    IllegalStateException("A budget for this category and period already exists.")
                )
            }
        }

        val now = System.currentTimeMillis()
        val originalCreatedAt = existingId?.let { dao.getById(it)?.createdAt } ?: now

        val entity = BudgetEntity(
            id               = existingId ?: 0,
            category         = category,
            categoryKey      = categoryKey,
            periodType       = periodType,
            limitAmount      = limitAmount,
            warningThreshold = warningThreshold,
            createdAt        = originalCreatedAt,
            updatedAt        = now
        )

        return if (existingId != null) {
            dao.update(entity)
            Result.success(existingId)
        } else {
            val id = dao.insert(entity)
            Result.success(id)
        }
    }

    suspend fun deleteBudget(id: Long) = dao.deleteById(id)

    // ── Spending calculations ─────────────────────────────────────────────────

    /**
     * Total confirmed spending for [periodType] in the current period.
     * category = null → overall spending.
     */
    suspend fun getSpentAmount(category: String?, periodType: String): Double {
        val (startMs, endMs) = getPeriodBounds(periodType)
        return if (category == null) {
            dao.getTotalSpentInPeriod(startMs, endMs)
        } else {
            dao.getCategorySpentInPeriod(category, startMs, endMs)
        }
    }

    // ── Warning check (called after each confirmation) ────────────────────────

    /**
     * After a transaction is confirmed with [category] and [amount], evaluates
     * all applicable budgets and returns warnings sorted by severity (most
     * severe first).
     *
     * [seenWarningKeys] is an in-memory Set that persists for the app's
     * session. It prevents duplicate warnings during a session without
     * preventing new warnings on new expenses.
     */
    suspend fun checkBudgetWarnings(
        category: String,
        amount: Double,
        seenWarningKeys: MutableSet<String>
    ): List<BudgetWarning> {
        val allBudgets = dao.observeAll().first()
        val warnings   = mutableListOf<BudgetWarning>()

        for (budget in allBudgets) {
            val budgetApplies = budget.category == null || budget.category == category
            if (!budgetApplies) continue

            val (startMs, endMs) = getPeriodBounds(budget.periodType)

            val spentAfter = if (budget.category == null) {
                dao.getTotalSpentInPeriod(startMs, endMs)
            } else {
                dao.getCategorySpentInPeriod(budget.category, startMs, endMs)
            }

            // Before = after minus this expense (floor at 0)
            val spentBefore = maxOf(0.0, spentAfter - amount)

            val levelAfter  = computeLevel(spentAfter,  budget.limitAmount)
            val levelBefore = computeLevel(spentBefore, budget.limitAmount)

            val shouldWarn = when {
                levelAfter == BudgetLevel.EXCEEDED -> true  // always warn for every new excess
                levelAfter > levelBefore && levelAfter >= BudgetLevel.WARNING -> true
                else -> false
            }
            if (!shouldWarn) continue

            // Session dedup key
            val warningKey = if (levelAfter == BudgetLevel.EXCEEDED) {
                "${budget.id}|EXCEEDED|${spentAfter.toLong()}"
            } else {
                "${budget.id}|${levelAfter.name}"
            }
            if (warningKey in seenWarningKeys) continue
            seenWarningKeys.add(warningKey)

            val message = buildWarningMessage(
                category   = budget.category,
                periodType = budget.periodType,
                level      = levelAfter,
                spent      = spentAfter,
                limit      = budget.limitAmount
            )

            warnings.add(
                BudgetWarning(
                    budgetId   = budget.id,
                    category   = budget.category,
                    periodType = budget.periodType,
                    level      = levelAfter,
                    spent      = spentAfter,
                    limit      = budget.limitAmount,
                    message    = message
                )
            )
        }

        return warnings.sortedByDescending { it.level.ordinal }
    }

    // ── Date helpers ──────────────────────────────────────────────────────────

    /**
     * Returns (startMs, endMs) for the current calendar week (Mon–Sun) or
     * calendar month, in the device's default timezone.
     */
    fun getPeriodBounds(periodType: String): Pair<Long, Long> {
        val tz  = TimeZone.getDefault()
        val cal = Calendar.getInstance(tz)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return if (periodType == PeriodType.WEEKLY) {
            // Roll back to Monday
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val daysToMon = (dow - Calendar.MONDAY + 7) % 7
            cal.add(Calendar.DAY_OF_MONTH, -daysToMon)
            val startMs = cal.timeInMillis

            cal.add(Calendar.DAY_OF_MONTH, 7)
            cal.add(Calendar.MILLISECOND, -1)
            Pair(startMs, cal.timeInMillis)
        } else {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val startMs = cal.timeInMillis

            cal.add(Calendar.MONTH, 1)
            cal.add(Calendar.MILLISECOND, -1)
            Pair(startMs, cal.timeInMillis)
        }
    }
}
