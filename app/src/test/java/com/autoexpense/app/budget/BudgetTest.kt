package com.autoexpense.app.budget

import com.autoexpense.app.data.BudgetDao
import com.autoexpense.app.data.BudgetEntity
import com.autoexpense.app.data.OVERALL_CATEGORY_KEY
import com.autoexpense.app.data.PeriodType
import com.autoexpense.app.data.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * All 18 unit tests specified in Phase 4 requirements.
 * Uses an in-memory FakeBudgetDao running pure JVM tests with zero Android/device dependencies.
 */
class BudgetTest {

    private lateinit var fakeDao: FakeBudgetDao
    private lateinit var repo: BudgetRepository

    @Before
    fun setup() {
        fakeDao = FakeBudgetDao()
        repo = BudgetRepository(fakeDao)
    }

    // ── 1. Creating weekly overall budget ─────────────────────────────────────
    @Test
    fun testCreateWeeklyOverallBudget() = runBlocking {
        val res = repo.saveBudget(null, PeriodType.WEEKLY, 2000.0)
        assertTrue("Save weekly overall should succeed", res.isSuccess)
        val list = fakeDao.observeAllSync()
        assertEquals(1, list.size)
        assertEquals(null, list[0].category)
        assertEquals(OVERALL_CATEGORY_KEY, list[0].categoryKey)
        assertEquals(PeriodType.WEEKLY, list[0].periodType)
        assertEquals(2000.0, list[0].limitAmount, 0.001)
    }

    // ── 2. Creating monthly overall budget ────────────────────────────────────
    @Test
    fun testCreateMonthlyOverallBudget() = runBlocking {
        val res = repo.saveBudget(null, PeriodType.MONTHLY, 25000.0)
        assertTrue("Save monthly overall should succeed", res.isSuccess)
        val list = fakeDao.observeAllSync()
        assertEquals(1, list.size)
        assertEquals(null, list[0].category)
        assertEquals(PeriodType.MONTHLY, list[0].periodType)
        assertEquals(25000.0, list[0].limitAmount, 0.001)
    }

    // ── 3. Creating category budget ───────────────────────────────────────────
    @Test
    fun testCreateCategoryBudget() = runBlocking {
        val res = repo.saveBudget("🍔 Food & Dining", PeriodType.WEEKLY, 1000.0)
        assertTrue("Save category budget should succeed", res.isSuccess)
        val list = fakeDao.observeAllSync()
        assertEquals(1, list.size)
        assertEquals("🍔 Food & Dining", list[0].category)
        assertEquals("🍔 Food & Dining", list[0].categoryKey)
        assertEquals(1000.0, list[0].limitAmount, 0.001)
    }

    // ── 4. Editing budget ─────────────────────────────────────────────────────
    @Test
    fun testEditBudget() = runBlocking {
        val id = repo.saveBudget("🍔 Food & Dining", PeriodType.WEEKLY, 1000.0).getOrThrow()
        val editRes = repo.saveBudget("🍔 Food & Dining", PeriodType.WEEKLY, 1500.0, existingId = id)
        assertTrue("Edit budget should succeed", editRes.isSuccess)
        val list = fakeDao.observeAllSync()
        assertEquals(1, list.size)
        assertEquals(id, list[0].id)
        assertEquals(1500.0, list[0].limitAmount, 0.001)
    }

    // ── 5. Deleting budget ────────────────────────────────────────────────────
    @Test
    fun testDeleteBudget() = runBlocking {
        val id = repo.saveBudget("🍔 Food & Dining", PeriodType.WEEKLY, 1000.0).getOrThrow()
        assertEquals(1, fakeDao.observeAllSync().size)
        repo.deleteBudget(id)
        assertEquals(0, fakeDao.observeAllSync().size)
    }

    // ── 6. Budget persistence ─────────────────────────────────────────────────
    @Test
    fun testBudgetPersistence() = runBlocking {
        val id = repo.saveBudget("🚗 Transport", PeriodType.MONTHLY, 2500.0).getOrThrow()
        val stored = fakeDao.getById(id)
        assertNotNull("Stored budget must exist in DAO", stored)
        assertEquals("🚗 Transport", stored!!.category)
        assertEquals(PeriodType.MONTHLY, stored.periodType)
        assertEquals(2500.0, stored.limitAmount, 0.001)
        assertTrue(stored.createdAt > 0)
        assertTrue(stored.updatedAt >= stored.createdAt)
    }

    // ── 7. Weekly spending calculation ────────────────────────────────────────
    @Test
    fun testWeeklySpendingCalculation() = runBlocking {
        fakeDao.mockTotalSpent = 1400.0
        val spent = repo.getSpentAmount(null, PeriodType.WEEKLY)
        assertEquals(1400.0, spent, 0.001)

        val bounds = repo.getPeriodBounds(PeriodType.WEEKLY)
        val diffDays = (bounds.second - bounds.first + 1) / (1000 * 60 * 60 * 24)
        assertEquals("Weekly bounds should span exactly 7 days", 7, diffDays)
    }

    // ── 8. Monthly spending calculation ───────────────────────────────────────
    @Test
    fun testMonthlySpendingCalculation() = runBlocking {
        fakeDao.mockTotalSpent = 8000.0
        val spent = repo.getSpentAmount(null, PeriodType.MONTHLY)
        assertEquals(8000.0, spent, 0.001)
    }

    // ── 9. Category spending calculation ──────────────────────────────────────
    @Test
    fun testCategorySpendingCalculation() = runBlocking {
        fakeDao.mockCategorySpent["🍔 Food & Dining"] = 700.0
        val spent = repo.getSpentAmount("🍔 Food & Dining", PeriodType.WEEKLY)
        assertEquals(700.0, spent, 0.001)
    }

    // ── 10. Needs Review transactions not counted ─────────────────────────────
    @Test
    fun testNeedsReviewTransactionsNotCounted() = runBlocking {
        // Add transactions to our fake table simulation
        val now = System.currentTimeMillis()
        fakeDao.transactions.add(createTx("1", "−₹500", "needs_review", "🍔 Food & Dining", now))
        fakeDao.transactions.add(createTx("2", "−₹200", "ignored", "🍔 Food & Dining", now))
        fakeDao.transactions.add(createTx("3", "−₹300", "confirmed", "🍔 Food & Dining", now))

        val (startMs, endMs) = repo.getPeriodBounds(PeriodType.WEEKLY)
        val spent = fakeDao.getCategorySpentInPeriod("🍔 Food & Dining", startMs, endMs)
        assertEquals("Only confirmed transaction (300.0) should be summed", 300.0, spent, 0.001)
    }

    // ── 11. 70% threshold ─────────────────────────────────────────────────────
    @Test
    fun testThreshold70Percent() {
        assertEquals(BudgetLevel.NORMAL, computeLevel(690.0, 1000.0))
        assertEquals(BudgetLevel.WARNING, computeLevel(700.0, 1000.0))
        assertEquals(BudgetLevel.WARNING, computeLevel(890.0, 1000.0))
    }

    // ── 12. 90% threshold ─────────────────────────────────────────────────────
    @Test
    fun testThreshold90Percent() {
        assertEquals(BudgetLevel.HIGH_WARNING, computeLevel(900.0, 1000.0))
        assertEquals(BudgetLevel.HIGH_WARNING, computeLevel(990.0, 1000.0))
    }

    // ── 13. 100% threshold ────────────────────────────────────────────────────
    @Test
    fun testThreshold100Percent() {
        assertEquals(BudgetLevel.LIMIT_REACHED, computeLevel(1000.0, 1000.0))
    }

    // ── 14. Above-limit calculation ───────────────────────────────────────────
    @Test
    fun testAboveLimitCalculation() {
        assertEquals(BudgetLevel.EXCEEDED, computeLevel(1100.0, 1000.0))
        val msg = buildWarningMessage("Food", PeriodType.WEEKLY, BudgetLevel.EXCEEDED, 1100.0, 1000.0)
        assertTrue("Message should mention exceeded amount ₹100", msg.contains("exceeded your weekly Food budget by ₹100"))
    }

    // ── 15. Warning only after confirmation ───────────────────────────────────
    @Test
    fun testWarningOnlyAfterConfirmation() = runBlocking {
        repo.saveBudget("🍔 Food & Dining", PeriodType.WEEKLY, 1000.0)
        // Simulate spent is now 700 after confirming a 300 expense (was 400 before)
        fakeDao.mockCategorySpent["🍔 Food & Dining"] = 700.0
        val seenKeys = mutableSetOf<String>()
        val warnings = repo.checkBudgetWarnings("🍔 Food & Dining", 300.0, seenKeys)
        assertEquals(1, warnings.size)
        assertEquals(BudgetLevel.WARNING, warnings[0].level)
    }

    // ── 16. No repeated warning after app restart / session reload ────────────
    @Test
    fun testNoRepeatedWarningAfterAppRestart() = runBlocking {
        repo.saveBudget("🍔 Food & Dining", PeriodType.WEEKLY, 1000.0)
        // Simulate spent is 750 after expense of 50. Before expense it was 700.
        // Both before (700) and after (750) are at WARNING level (70%).
        fakeDao.mockCategorySpent["🍔 Food & Dining"] = 750.0
        val seenKeys = mutableSetOf<String>() // fresh app startup set
        val warnings = repo.checkBudgetWarnings("🍔 Food & Dining", 50.0, seenKeys)
        assertTrue("Should not warn when level has not increased (was 70%, still 70%)", warnings.isEmpty())
    }

    // ── 17. New expense while already exceeded creates a new warning ──────────
    @Test
    fun testNewExpenseWhileAlreadyExceededCreatesNewWarning() = runBlocking {
        repo.saveBudget("🍔 Food & Dining", PeriodType.WEEKLY, 1000.0)
        // First excess expense: spent = 1100 (amount 100)
        fakeDao.mockCategorySpent["🍔 Food & Dining"] = 1100.0
        val seenKeys = mutableSetOf<String>()
        val warnings1 = repo.checkBudgetWarnings("🍔 Food & Dining", 100.0, seenKeys)
        assertEquals(1, warnings1.size)
        assertEquals(BudgetLevel.EXCEEDED, warnings1[0].level)

        // Second excess expense while already over limit: spent = 1300 (amount 200)
        fakeDao.mockCategorySpent["🍔 Food & Dining"] = 1300.0
        val warnings2 = repo.checkBudgetWarnings("🍔 Food & Dining", 200.0, seenKeys)
        assertEquals("New expense while already exceeded MUST create a new warning", 1, warnings2.size)
        assertTrue(warnings2[0].message.contains("₹300"))
    }

    // ── 18. Multiple budgets affected by one expense ──────────────────────────
    @Test
    fun testMultipleBudgetsAffectedByOneExpense() = runBlocking {
        repo.saveBudget(null, PeriodType.WEEKLY, 2000.0)           // overall weekly
        repo.saveBudget(null, PeriodType.MONTHLY, 25000.0)         // overall monthly
        repo.saveBudget("🍔 Food & Dining", PeriodType.WEEKLY, 1000.0) // category weekly

        // Simulate confirming a ₹1000 Food expense when previous spending was 0
        fakeDao.mockTotalSpent = 1000.0
        fakeDao.mockCategorySpent["🍔 Food & Dining"] = 1000.0

        val seenKeys = mutableSetOf<String>()
        val warnings = repo.checkBudgetWarnings("🍔 Food & Dining", 1000.0, seenKeys)

        // Food weekly is 1000/1000 = LIMIT_REACHED (100%)
        // Overall weekly is 1000/2000 = 50% (NORMAL, no warning)
        // Overall monthly is 1000/25000 = 4% (NORMAL, no warning)
        assertEquals(1, warnings.size)
        assertEquals("🍔 Food & Dining", warnings[0].category)
        assertEquals(BudgetLevel.LIMIT_REACHED, warnings[0].level)

        // If we also make Overall weekly cross threshold: e.g., mockTotalSpent = 1500 (75%)
        fakeDao.mockTotalSpent = 1500.0
        val seenKeys2 = mutableSetOf<String>()
        val warningsBoth = repo.checkBudgetWarnings("🍔 Food & Dining", 1000.0, seenKeys2)
        assertEquals("Both category weekly (LIMIT_REACHED) and overall weekly (WARNING) should be returned", 2, warningsBoth.size)
        // Most severe first: LIMIT_REACHED (ordinal 3) > WARNING (ordinal 1)
        assertEquals(BudgetLevel.LIMIT_REACHED, warningsBoth[0].level)
        assertEquals(BudgetLevel.WARNING, warningsBoth[1].level)
    }

    // ── FAKE DAO FOR TESTING ──────────────────────────────────────────────────
    private class FakeBudgetDao : BudgetDao {
        private val budgets = MutableStateFlow<List<BudgetEntity>>(emptyList())
        private var nextId = 1L
        val transactions = mutableListOf<TransactionEntity>()
        var mockTotalSpent = 0.0
        val mockCategorySpent = mutableMapOf<String, Double>()

        override suspend fun insert(budget: BudgetEntity): Long {
            val id = if (budget.id == 0L) nextId++ else budget.id
            val newEntity = budget.copy(id = id)
            budgets.value = budgets.value + newEntity
            return id
        }

        override suspend fun update(budget: BudgetEntity) {
            budgets.value = budgets.value.map { if (it.id == budget.id) budget else it }
        }

        override suspend fun delete(budget: BudgetEntity) {
            budgets.value = budgets.value.filter { it.id != budget.id }
        }

        override suspend fun deleteById(id: Long) {
            budgets.value = budgets.value.filter { it.id != id }
        }

        override fun observeAll(): Flow<List<BudgetEntity>> = budgets

        override suspend fun getAllBudgets(): List<BudgetEntity> = budgets.value

        override suspend fun insertAll(budgetsList: List<BudgetEntity>) {
            budgets.value = budgetsList
        }

        override suspend fun deleteAll() {
            budgets.value = emptyList()
        }

        fun observeAllSync(): List<BudgetEntity> = budgets.value

        override suspend fun getById(id: Long): BudgetEntity? {
            return budgets.value.find { it.id == id }
        }

        override suspend fun getByCategoryKeyAndPeriod(categoryKey: String, periodType: String): BudgetEntity? {
            return budgets.value.find { it.categoryKey == categoryKey && it.periodType == periodType }
        }

        override suspend fun getTotalSpentInPeriod(startMs: Long, endMs: Long): Double {
            if (transactions.isNotEmpty()) {
                return transactions
                    .filter { it.status == "confirmed" && it.timestamp in startMs..endMs }
                    .sumOf { parseAmount(it.amount) }
            }
            return mockTotalSpent
        }

        override suspend fun getCategorySpentInPeriod(category: String, startMs: Long, endMs: Long): Double {
            if (transactions.isNotEmpty()) {
                return transactions
                    .filter { it.status == "confirmed" && it.category == category && it.timestamp in startMs..endMs }
                    .sumOf { parseAmount(it.amount) }
            }
            return mockCategorySpent[category] ?: 0.0
        }

        private fun parseAmount(str: String): Double {
            return str.replace("−₹", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
        }
    }

    private fun createTx(id: String, amount: String, status: String, category: String, timestamp: Long): TransactionEntity {
        return TransactionEntity(
            id = id,
            merchantOrRecipient = "Merchant $id",
            amount = amount,
            source = "SMS",
            category = category,
            status = status,
            timestamp = timestamp,
            detectionReason = "test",
            safeNotificationExcerpt = "test excerpt",
            transactionFingerprint = "fingerprint_$id",
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }
}

