package com.autoexpense.app.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoexpense.app.data.AutoExpenseDatabase
import com.autoexpense.app.data.TransactionEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportViewModel : ViewModel() {

    private val _allConfirmedEntities = MutableStateFlow<List<TransactionEntity>>(emptyList())

    val selectedFormat = MutableStateFlow("PDF Report")
    val selectedDateRange = MutableStateFlow(ExportFilterHelper.DATE_THIS_MONTH)
    val selectedCategory = MutableStateFlow(ExportFilterHelper.CAT_ALL)
    val customStartMs = MutableStateFlow<Long?>(null)
    val customEndMs = MutableStateFlow<Long?>(null)

    val isExporting = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val successMessage = MutableStateFlow<String?>(null)
    val generatedUri = MutableStateFlow<Uri?>(null)
    val generatedFilename = MutableStateFlow<String?>(null)
    val generatedFile = MutableStateFlow<File?>(null)

    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        _allConfirmedEntities,
        selectedDateRange,
        selectedCategory,
        customStartMs,
        customEndMs
    ) { all, dateRange, category, startMs, endMs ->
        ExportFilterHelper.filterTransactions(
            allTransactions = all,
            dateFilter = dateRange,
            categoryFilter = category,
            customStartMs = startMs,
            customEndMs = endMs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun init(context: Context) {
        viewModelScope.launch {
            val dao = AutoExpenseDatabase.getDatabase(context).transactionDao()
            dao.observeConfirmed().collect { list ->
                _allConfirmedEntities.value = list
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
            errorMessage.value = "No confirmed expenses found for this period."
            return
        }

        isExporting.value = true
        errorMessage.value = null
        successMessage.value = null

        viewModelScope.launch {
            try {
                val dateSuffix = SimpleDateFormat("MMMM_yyyy", Locale.US).format(Date())
                val format = selectedFormat.value

                if (format == "PDF Report") {
                    val filename = "AutoExpense_Report_${dateSuffix}.pdf"
                    val result = ExportFileGenerator.generatePdfFile(
                        context = context,
                        filename = filename,
                        transactions = transactions,
                        periodText = selectedDateRange.value
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
