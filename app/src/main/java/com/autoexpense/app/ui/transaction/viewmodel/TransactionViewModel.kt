package com.autoexpense.app.ui.transaction.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoexpense.app.domain.AnalyticsService
import com.autoexpense.app.domain.CashFlowService
import com.autoexpense.app.domain.DashboardService
import com.autoexpense.app.domain.DateRangeService
import com.autoexpense.app.domain.FinancialTransaction
import com.autoexpense.app.domain.FinancialTransactionRepository
import com.autoexpense.app.domain.IncomeAnalyticsService
import com.autoexpense.app.domain.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class TransactionSortOption(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    HIGHEST_AMOUNT("Highest Amount"),
    LOWEST_AMOUNT("Lowest Amount"),
    ALPHABETICAL("Alphabetical"),
    CATEGORY("Category")
}

enum class TransactionDateFilter(val label: String) {
    ALL("All Time"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year")
}

data class TransactionFilterState(
    val query: String = "",
    val category: String? = null,
    val paymentMethod: String? = null,
    val dateFilter: TransactionDateFilter = TransactionDateFilter.ALL,
    val recurring: Boolean? = null,
    val autoDetected: Boolean? = null,
    val sort: TransactionSortOption = TransactionSortOption.NEWEST
) {
    val activeCount: Int
        get() = listOfNotNull(
            category,
            paymentMethod,
            recurring,
            autoDetected,
            dateFilter.takeIf { it != TransactionDateFilter.ALL }
        ).size
}

data class TransactionSummaryUiState(
    val todayAmount: Double = 0.0,
    val monthlyAmount: Double = 0.0,
    val averageMonthlyAmount: Double = 0.0,
    val largestAmount: Double = 0.0,
    val transactionCount: Int = 0,
    val netSavings: Double = 0.0
)

data class TransactionFormState(
    val amount: String = "",
    val category: String = "",
    val source: String = "Manual",
    val merchant: String = "",
    val notes: String = "",
    val paymentMethod: String = "OTHER",
    val isRecurring: Boolean = false,
    val isAutoDetected: Boolean = false,
    val date: Long = System.currentTimeMillis(),
    val validationError: String? = null
)

class TransactionViewModel : ViewModel() {
    private val _filterState = MutableStateFlow(TransactionFilterState())
    val filterState: StateFlow<TransactionFilterState> = _filterState.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val allTransactions: StateFlow<List<FinancialTransaction>> =
        FinancialTransactionRepository.observeAllTransactions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun transactionsFor(type: TransactionType): StateFlow<List<FinancialTransaction>> {
        return combine(allTransactions, filterState) { transactions, filters ->
            applyFilters(transactions.filter { it.transactionType == type && !it.isDeleted }, filters)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun summaryFor(type: TransactionType): StateFlow<TransactionSummaryUiState> {
        return allTransactions.combine(filterState) { transactions, _ ->
            val scoped = transactions.filter { it.transactionType == type && it.isConfirmed && !it.isDeleted }
            val today = DateRangeService.getDayBounds()
            val month = DateRangeService.getCurrentMonthBounds()
            val totalMonths = scoped.map {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.date }
                cal.get(java.util.Calendar.YEAR) to cal.get(java.util.Calendar.MONTH)
            }.distinct().size.coerceAtLeast(1)
            TransactionSummaryUiState(
                todayAmount = scoped.filter { it.date in today.first..today.second }.sumOf { it.amount },
                monthlyAmount = scoped.filter { it.date in month.first..month.second }.sumOf { it.amount },
                averageMonthlyAmount = scoped.sumOf { it.amount } / totalMonths,
                largestAmount = scoped.maxOfOrNull { it.amount } ?: 0.0,
                transactionCount = scoped.size,
                netSavings = DashboardService.dashboardSummary(transactions).netSavings
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionSummaryUiState())
    }

    fun setSearchQuery(query: String) {
        _filterState.update { it.copy(query = query) }
    }

    fun applyFilters(filters: TransactionFilterState) {
        _filterState.value = filters
    }

    fun clearFilters() {
        _filterState.value = TransactionFilterState(query = _filterState.value.query)
    }

    fun saveTransaction(
        type: TransactionType,
        form: TransactionFormState,
        existing: FinancialTransaction? = null,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val amount = form.amount.replace(",", "").trim().toDoubleOrNull()
        val validationError = when {
            amount == null || amount <= 0.0 -> "Enter a valid amount."
            form.category.isBlank() -> "Choose a category."
            form.merchant.isBlank() -> "Enter a source or merchant."
            form.paymentMethod.isBlank() -> "Choose a payment method."
            form.date <= 0L -> "Choose a valid date."
            else -> null
        }
        if (validationError != null) {
            onResult(false, validationError)
            return
        }

        viewModelScope.launch {
            _saving.value = true
            runCatching {
                val now = System.currentTimeMillis()
                val transaction = FinancialTransaction(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    transactionType = type,
                    amount = amount!!,
                    currency = existing?.currency ?: "INR",
                    title = form.merchant.trim(),
                    category = form.category.trim(),
                    merchant = form.merchant.trim(),
                    paymentMethod = form.paymentMethod,
                    referenceNumber = existing?.referenceNumber.orEmpty(),
                    notes = form.notes.trim(),
                    date = form.date,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    isRecurring = form.isRecurring,
                    isAutoDetected = existing?.isAutoDetected ?: form.isAutoDetected,
                    notificationSource = existing?.notificationSource?.takeIf { it.isNotBlank() } ?: form.merchant.trim(),
                    metadata = existing?.metadata ?: emptyMap(),
                    isDeleted = false,
                    status = "confirmed"
                )
                if (existing == null) {
                    FinancialTransactionRepository.insert(transaction)
                } else {
                    FinancialTransactionRepository.update(transaction)
                }
            }.onSuccess {
                onResult(true, null)
            }.onFailure { throwable ->
                _error.value = throwable.message ?: "Unable to save transaction."
                onResult(false, _error.value)
            }.also {
                _saving.value = false
            }
        }
    }

    fun deleteTransaction(transaction: FinancialTransaction, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { FinancialTransactionRepository.delete(transaction.id) }
                .onSuccess { onComplete() }
                .onFailure { _error.value = it.message ?: "Unable to delete transaction." }
        }
    }

    fun restoreTransaction(transaction: FinancialTransaction) {
        viewModelScope.launch {
            runCatching { FinancialTransactionRepository.update(transaction.copy(isDeleted = false)) }
                .onFailure { _error.value = it.message ?: "Unable to restore transaction." }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun applyFilters(
        transactions: List<FinancialTransaction>,
        filters: TransactionFilterState
    ): List<FinancialTransaction> {
        val dateBounds = when (filters.dateFilter) {
            TransactionDateFilter.ALL -> null
            TransactionDateFilter.TODAY -> DateRangeService.getDayBounds()
            TransactionDateFilter.THIS_WEEK -> {
                val start = DateRangeService.getMondayOfWeek()
                start to start + DateRangeService.DAY_MS * 7 - 1L
            }
            TransactionDateFilter.THIS_MONTH -> DateRangeService.getCurrentMonthBounds()
            TransactionDateFilter.THIS_YEAR -> DateRangeService.getYearBounds()
        }

        return transactions.asSequence()
            .filter { it.isConfirmed }
            .filter { tx ->
                filters.query.isBlank() || listOf(
                    tx.merchant,
                    tx.title,
                    tx.category,
                    tx.notes,
                    tx.referenceNumber,
                    tx.amount.toString(),
                    tx.transactionType.name
                ).any { it.contains(filters.query, ignoreCase = true) }
            }
            .filter { filters.category == null || it.category.equals(filters.category, ignoreCase = true) }
            .filter { filters.paymentMethod == null || it.paymentMethod.equals(filters.paymentMethod, ignoreCase = true) }
            .filter { filters.recurring == null || it.isRecurring == filters.recurring }
            .filter { filters.autoDetected == null || it.isAutoDetected == filters.autoDetected }
            .filter { dateBounds == null || it.date in dateBounds.first..dateBounds.second }
            .toList()
            .let { list ->
                when (filters.sort) {
                    TransactionSortOption.NEWEST -> list.sortedByDescending { it.date }
                    TransactionSortOption.OLDEST -> list.sortedBy { it.date }
                    TransactionSortOption.HIGHEST_AMOUNT -> list.sortedByDescending { it.amount }
                    TransactionSortOption.LOWEST_AMOUNT -> list.sortedBy { it.amount }
                    TransactionSortOption.ALPHABETICAL -> list.sortedBy { it.merchant.lowercase() }
                    TransactionSortOption.CATEGORY -> list.sortedBy { it.category.lowercase() }
                }
            }
    }
}
