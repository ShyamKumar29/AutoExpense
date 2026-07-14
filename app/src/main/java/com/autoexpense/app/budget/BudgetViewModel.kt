package com.autoexpense.app.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoexpense.app.data.BudgetEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** UI model combining a budget with its current period spending. */
data class BudgetWithSpending(
    val budget: BudgetEntity,
    val spent: Double,
    val percentage: Float,   // 0f – 1f+
    val level: BudgetLevel
)

class BudgetViewModel : ViewModel() {

    private val repo = BudgetRepositorySingleton.instance

    // ── Raw budget list ──────────────────────────────────────────────────────

    val budgets: StateFlow<List<BudgetEntity>> = repo
        .observeAllBudgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Budgets with computed spending ────────────────────────────────────────

    private val _refreshTrigger = MutableStateFlow(0)

    val budgetsWithSpending: StateFlow<List<BudgetWithSpending>> = combine(
        budgets,
        com.autoexpense.app.TransactionRepository.transactions,
        _refreshTrigger
    ) { budgetList, _, _ ->
        budgetList.map { budget ->
            val spent = repo.getSpentAmount(budget.category, budget.periodType)
            val pct   = if (budget.limitAmount > 0) (spent / budget.limitAmount).toFloat() else 0f
            val level = computeLevel(spent, budget.limitAmount)
            BudgetWithSpending(
                budget     = budget,
                spent      = spent,
                percentage = pct,
                level      = level
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Manually re-trigger spending refresh (called after a transaction is confirmed). */
    fun refreshSpending() {
        _refreshTrigger.value += 1
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    fun saveBudget(
        category: String?,
        periodType: String,
        limitAmount: Double,
        existingId: Long? = null,
        onResult: (Result<Long>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repo.saveBudget(
                category    = category,
                periodType  = periodType,
                limitAmount = limitAmount,
                existingId  = existingId
            )
            onResult(result)
        }
    }

    fun deleteBudget(id: Long) {
        viewModelScope.launch { repo.deleteBudget(id) }
    }
}

/**
 * Singleton holder so BudgetRepository is accessible from both BudgetViewModel
 * and the NeedsReview confirmation flow without Android DI.
 */
object BudgetRepositorySingleton {
    lateinit var instance: BudgetRepository

    fun init(repo: BudgetRepository) {
        instance = repo
    }
}
