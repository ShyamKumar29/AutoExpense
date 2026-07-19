package com.autoexpense.app.ui.transaction.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoexpense.app.domain.FinancialTransaction
import com.autoexpense.app.domain.TransactionType
import com.autoexpense.app.ui.components.AeScreenHeader
import com.autoexpense.app.ui.theme.aePalette
import com.autoexpense.app.ui.transaction.components.TransactionDeleteDialog
import com.autoexpense.app.ui.transaction.components.TransactionDetailsSheet
import com.autoexpense.app.ui.transaction.components.TransactionFab
import com.autoexpense.app.ui.transaction.components.TransactionFilterSheet
import com.autoexpense.app.ui.transaction.components.TransactionForm
import com.autoexpense.app.ui.transaction.components.TransactionList
import com.autoexpense.app.ui.transaction.components.TransactionSearchBar
import com.autoexpense.app.ui.transaction.components.TransactionSummaryCard
import com.autoexpense.app.ui.transaction.components.defaultIncomeCategories
import com.autoexpense.app.ui.transaction.components.defaultPaymentMethods
import com.autoexpense.app.ui.transaction.viewmodel.TransactionViewModel
import kotlinx.coroutines.launch

@Composable
fun TransactionListScreen(
    transactionType: TransactionType,
    title: String,
    subtitle: String,
    addLabel: String,
    modifier: Modifier = Modifier,
    viewModel: TransactionViewModel = viewModel()
) {
    val transactions by remember(transactionType) { viewModel.transactionsFor(transactionType) }.collectAsState()
    val summary by remember(transactionType) { viewModel.summaryFor(transactionType) }.collectAsState()
    val filters by viewModel.filterState.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val categories = defaultIncomeCategories()
    val paymentMethods = defaultPaymentMethods()

    var showFilters by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<FinancialTransaction?>(null) }
    var editing by remember { mutableStateOf<FinancialTransaction?>(null) }
    var deleting by remember { mutableStateOf<FinancialTransaction?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error!!)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { TransactionFab(label = addLabel, onClick = { validationError = null; editing = null; showForm = true }) },
        containerColor = aePalette().background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(aePalette().background)
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AeScreenHeader(title = title, subtitle = subtitle, modifier = Modifier.padding(horizontal = 0.dp, vertical = 4.dp))
            TransactionSummaryCard(title = "$title Summary", summary = summary, transactionType = transactionType)
            TransactionSearchBar(
                query = filters.query,
                onQueryChange = viewModel::setSearchQuery,
                onFilterClick = { showFilters = true },
                activeFilterCount = filters.activeCount
            )
            if (filters.activeCount > 0) {
                Text("${filters.activeCount} filter${if (filters.activeCount == 1) "" else "s"} active", color = aePalette().primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(modifier = Modifier.weight(1f)) {
                TransactionList(
                    transactions = transactions,
                    loading = loading,
                    transactionType = transactionType,
                    onAdd = { validationError = null; editing = null; showForm = true },
                    onClick = { selected = it },
                    onLongPress = { selected = it },
                    onEdit = { validationError = null; editing = it; showForm = true },
                    onDelete = { deleting = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showFilters) {
        TransactionFilterSheet(
            filters = filters,
            categories = categories,
            paymentMethods = paymentMethods,
            onApply = viewModel::applyFilters,
            onDismiss = { showFilters = false }
        )
    }

    if (showForm) {
        TransactionForm(
            title = if (editing == null) addLabel else "Edit ${title.removeSuffix("s")}",
            transactionType = transactionType,
            initial = editing,
            categories = categories,
            paymentMethods = paymentMethods,
            validationError = validationError,
            saving = saving,
            saveButtonText = if (transactionType == TransactionType.INCOME) "Save Income" else "Save Transaction",
            onSave = { form ->
                viewModel.saveTransaction(transactionType, form, editing) { success, message ->
                    if (success) {
                        validationError = null
                        showForm = false
                        editing = null
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = if (transactionType == TransactionType.INCOME) "Income saved." else "Transaction saved.",
                                duration = SnackbarDuration.Short
                            )
                        }
                    } else {
                        validationError = message
                    }
                }
            },
            onDismiss = { showForm = false; editing = null; validationError = null }
        )
    }

    selected?.let { transaction ->
        TransactionDetailsSheet(
            transaction = transaction,
            onDismiss = { selected = null },
            onEdit = { editing = transaction; selected = null; showForm = true },
            onDelete = { deleting = transaction; selected = null }
        )
    }

    deleting?.let { transaction ->
        TransactionDeleteDialog(
            label = title.removeSuffix("s").lowercase(),
            onConfirm = {
                deleting = null
                viewModel.deleteTransaction(transaction) {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "${title.removeSuffix("s")} deleted.",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            viewModel.restoreTransaction(transaction)
                        }
                    }
                }
            },
            onDismiss = { deleting = null }
        )
    }
}
