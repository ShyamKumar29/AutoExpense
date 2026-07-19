package com.autoexpense.app.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoexpense.app.domain.FinancialTransaction
import com.autoexpense.app.domain.FinancialTransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ExportFilterSelection(
    val dateRange: String,
    val category: String,
    val transactionType: String,
    val merchant: String,
    val paymentMethod: String
)

class ExportViewModel : ViewModel() {

    private val _allTransactions = MutableStateFlow<List<FinancialTransaction>>(emptyList())

    val selectedFormat = MutableStateFlow("PDF Report")
    val selectedDateRange = MutableStateFlow(ExportFilterHelper.DATE_THIS_MONTH)
    val selectedCategory = MutableStateFlow(ExportFilterHelper.CAT_ALL)
    val selectedTransactionType = MutableStateFlow(ExportFilterHelper.TYPE_ALL)
    val selectedMerchant = MutableStateFlow(ExportFilterHelper.MERCHANT_ALL)
    val selectedPaymentMethod = MutableStateFlow(ExportFilterHelper.PAYMENT_ALL)
    val customStartMs = MutableStateFlow<Long?>(null)
    val customEndMs = MutableStateFlow<Long?>(null)

    val isExporting = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val successMessage = MutableStateFlow<String?>(null)
    val generatedUri = MutableStateFlow<Uri?>(null)
    val generatedFilename = MutableStateFlow<String?>(null)
    val generatedFile = MutableStateFlow<File?>(null)

    private val filterSelection = combine(
        selectedDateRange,
        selectedCategory,
        selectedTransactionType,
        selectedMerchant,
        selectedPaymentMethod
    ) { dateRange, category, transactionType, merchant, paymentMethod ->
        ExportFilterSelection(dateRange, category, transactionType, merchant, paymentMethod)
    }

    val filteredTransactions: StateFlow<List<FinancialTransaction>> = combine(
        _allTransactions,
        filterSelection,
        customStartMs,
        customEndMs
    ) { all, filters, startMs, endMs ->
        ExportFilterHelper.filterTransactions(
            allTransactions = all,
            dateFilter = filters.dateRange,
            categoryFilter = filters.category,
            transactionTypeFilter = filters.transactionType,
            merchantFilter = filters.merchant,
            paymentMethodFilter = filters.paymentMethod,
            customStartMs = startMs,
            customEndMs = endMs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableMerchants: StateFlow<List<String>> = _allTransactions.map { list ->
        list.asSequence()
            .filter { it.isConfirmed && !it.isDeleted }
            .map { it.merchant.ifBlank { it.title } }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availablePaymentMethods: StateFlow<List<String>> = _allTransactions.map { list ->
        list.asSequence()
            .filter { it.isConfirmed && !it.isDeleted }
            .map { ExportFilterHelper.paymentMethodLabel(it.paymentMethod) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun init(context: Context) {
        viewModelScope.launch {
            FinancialTransactionRepository.observeAllTransactions().collect { list ->
                _allTransactions.value = list
            }
        }
    }

    fun setFormat(format: String) {
        selectedFormat.value = format
    }

    fun setDateRange(range: String) {
        selectedDateRange.value = range
    }

    fun setCategory(category: String) {
        selectedCategory.value = category
    }

    fun setTransactionType(type: String) {
        selectedTransactionType.value = type
    }

    fun setMerchant(merchant: String) {
        selectedMerchant.value = merchant
    }

    fun setPaymentMethod(paymentMethod: String) {
        selectedPaymentMethod.value = paymentMethod
    }

    fun setCustomDates(startMs: Long?, endMs: Long?) {
        customStartMs.value = startMs
        customEndMs.value = endMs
    }

    fun clearMessages() {
        errorMessage.value = null
        successMessage.value = null
    }

    fun exportReport(context: Context) {
        val transactions = filteredTransactions.value
        if (transactions.isEmpty()) {
            errorMessage.value = "No confirmed transactions found for this period."
            return
        }

        isExporting.value = true
        errorMessage.value = null
        successMessage.value = null

        viewModelScope.launch {
            try {
                val dateSuffix = SimpleDateFormat("MMMM_yyyy", Locale.US).format(Date())
                val periodLabel = ExportFilterHelper.periodLabel(
                    selectedDateRange.value,
                    customStartMs.value,
                    customEndMs.value
                )
                val format = selectedFormat.value

                if (format == "PDF Report") {
                    val filename = "AutoExpense_Report_${dateSuffix}.pdf"
                    val result = ExportFileGenerator.generatePdfFile(
                        context = context,
                        filename = filename,
                        transactions = transactions,
                        periodText = periodLabel
                    )
                    generatedFile.value = result.first
                    generatedUri.value = result.second
                    generatedFilename.value = filename
                    successMessage.value = "Report generated successfully!"
                } else {
                    val filename = "AutoExpense_Transactions_${dateSuffix}.csv"
                    val result = ExportFileGenerator.generateCsvFile(
                        context = context,
                        filename = filename,
                        transactions = transactions
                    )
                    generatedFile.value = result.first
                    generatedUri.value = result.second
                    generatedFilename.value = filename
                    successMessage.value = "CSV generated successfully!"
                }
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Failed to generate export file."
            } finally {
                isExporting.value = false
            }
        }
    }
}
