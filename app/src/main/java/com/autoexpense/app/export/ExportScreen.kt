package com.autoexpense.app.export

import android.app.DatePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoexpense.app.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: ExportViewModel,
    onBackToDashboard: () -> Unit
) {
    val context = LocalContext.current
    val format by viewModel.selectedFormat.collectAsState()
    val dateRange by viewModel.selectedDateRange.collectAsState()
    val category by viewModel.selectedCategory.collectAsState()
    val transactionType by viewModel.selectedTransactionType.collectAsState()
    val merchant by viewModel.selectedMerchant.collectAsState()
    val paymentMethod by viewModel.selectedPaymentMethod.collectAsState()
    val availableMerchants by viewModel.availableMerchants.collectAsState()
    val availablePaymentMethods by viewModel.availablePaymentMethods.collectAsState()
    val filteredTransactions by viewModel.filteredTransactions.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val generatedUri by viewModel.generatedUri.collectAsState()
    val generatedFilename by viewModel.generatedFilename.collectAsState()
    val customStartMs by viewModel.customStartMs.collectAsState()
    val customEndMs by viewModel.customEndMs.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    val summary = remember(filteredTransactions, dateRange, customStartMs, customEndMs) {
        ExportFilterHelper.calculateSummary(
            filteredTransactions,
            ExportFilterHelper.periodLabel(dateRange, customStartMs, customEndMs)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg0)
            .verticalScroll(rememberScrollState())
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorBg1)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(ColorOrangeDim, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = ColorOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Export Financial Report",
                    fontWeight = FontWeight.Bold,
                    color = ColorText1,
                    fontSize = 24.sp
                )
            }

            TextButton(onClick = onBackToDashboard) {
                Text("← Back", color = ColorOrange)
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {

            // 1. Export Format
            Text("EXPORT FORMAT", fontSize = 11.sp, color = ColorText3, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                val formats = listOf("PDF Report", "CSV Spreadsheet")
                for (f in formats) {
                    val selected = format == f
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) ColorOrangeDim else ColorBg2
                        ),
                        border = BorderStroke(1.dp, if (selected) ColorOrange else ColorBg3),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                viewModel.setFormat(f)
                                viewModel.clearMessages()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    viewModel.setFormat(f)
                                    viewModel.clearMessages()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ColorOrange,
                                    unselectedColor = ColorText3
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(f, color = ColorText1, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Date Range
            Text("DATE RANGE", fontSize = 11.sp, color = ColorText3, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            val ranges = listOf(
                ExportFilterHelper.DATE_THIS_WEEK,
                ExportFilterHelper.DATE_THIS_MONTH,
                ExportFilterHelper.DATE_LAST_MONTH,
                ExportFilterHelper.DATE_ALL_TIME,
                ExportFilterHelper.DATE_CUSTOM
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (r in ranges) {
                    val selected = dateRange == r
                    FilterChip(
                        selected = selected,
                        onClick = {
                            viewModel.setDateRange(r)
                            viewModel.clearMessages()
                        },
                        label = { Text(r, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ColorOrange,
                            selectedLabelColor = Color.White,
                            containerColor = ColorBg2,
                            labelColor = ColorText2
                        )
                    )
                }
            }

            // Custom Date Pickers
            if (dateRange == ExportFilterHelper.DATE_CUSTOM) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                    val startText = if (customStartMs != null && customStartMs!! > 0) dateFormat.format(Date(customStartMs!!)) else "Start Date"
                    val endText = if (customEndMs != null && customEndMs!! > 0 && customEndMs!! < Long.MAX_VALUE) dateFormat.format(Date(customEndMs!!)) else "End Date"

                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance()
                            if (customStartMs != null) cal.timeInMillis = customStartMs!!
                            DatePickerDialog(context, { _, year, month, dayOfMonth ->
                                val selected = Calendar.getInstance().apply {
                                    set(year, month, dayOfMonth, 0, 0, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                                viewModel.setCustomDates(selected, customEndMs)
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, ColorOrange.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorText1)
                    ) {
                        Icon(Icons.Outlined.DateRange, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(startText, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance()
                            if (customEndMs != null && customEndMs!! < Long.MAX_VALUE) cal.timeInMillis = customEndMs!!
                            DatePickerDialog(context, { _, year, month, dayOfMonth ->
                                val selected = Calendar.getInstance().apply {
                                    set(year, month, dayOfMonth, 23, 59, 59)
                                    set(Calendar.MILLISECOND, 999)
                                }.timeInMillis
                                viewModel.setCustomDates(customStartMs, selected)
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, ColorOrange.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorText1)
                    ) {
                        Icon(Icons.Outlined.DateRange, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(endText, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Transaction Type Filter
            Text("TRANSACTION TYPE", fontSize = 11.sp, color = ColorText3, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            val typeFilters = listOf(
                ExportFilterHelper.TYPE_ALL,
                ExportFilterHelper.TYPE_EXPENSES,
                ExportFilterHelper.TYPE_INCOME,
                ExportFilterHelper.TYPE_REFUNDS,
                ExportFilterHelper.TYPE_CASHBACK,
                ExportFilterHelper.TYPE_TRANSFERS
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (type in typeFilters) {
                    val selected = transactionType == type
                    FilterChip(
                        selected = selected,
                        onClick = {
                            viewModel.setTransactionType(type)
                            viewModel.clearMessages()
                        },
                        label = { Text(type, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ColorOrange,
                            selectedLabelColor = Color.White,
                            containerColor = ColorBg2,
                            labelColor = ColorText2
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Optional Category Filter
            Text("OPTIONAL CATEGORY FILTER", fontSize = 11.sp, color = ColorText3, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            val customCategories by com.autoexpense.app.data.CustomCategoryRepository.customCategories.collectAsState(
                initial = emptyList()
            )
            val baseCategories = listOf(
                ExportFilterHelper.CAT_ALL,
                ExportFilterHelper.CAT_FOOD,
                ExportFilterHelper.CAT_TRANSPORT,
                ExportFilterHelper.CAT_SHOPPING,
                ExportFilterHelper.CAT_GROCERIES,
                ExportFilterHelper.CAT_HEALTHCARE,
                ExportFilterHelper.CAT_ENTERTAINMENT,
                ExportFilterHelper.CAT_RENT,
                ExportFilterHelper.CAT_PERSONAL,
                ExportFilterHelper.CAT_OTHER
            )
            val categories = (baseCategories.take(baseCategories.size - 1) + customCategories.map { it.name } + baseCategories.last()).distinct()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (cat in categories) {
                    val selected = category == cat
                    FilterChip(
                        selected = selected,
                        onClick = {
                            viewModel.setCategory(cat)
                            viewModel.clearMessages()
                        },
                        label = { Text(cat, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ColorOrange,
                            selectedLabelColor = Color.White,
                            containerColor = ColorBg2,
                            labelColor = ColorText2
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Optional Merchant and Payment Method Filters
            Text("OPTIONAL MERCHANT FILTER", fontSize = 11.sp, color = ColorText3, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val merchants = listOf(ExportFilterHelper.MERCHANT_ALL) + availableMerchants
                for (m in merchants) {
                    val selected = merchant == m
                    FilterChip(
                        selected = selected,
                        onClick = {
                            viewModel.setMerchant(m)
                            viewModel.clearMessages()
                        },
                        label = { Text(m, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ColorOrange,
                            selectedLabelColor = Color.White,
                            containerColor = ColorBg2,
                            labelColor = ColorText2
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text("OPTIONAL PAYMENT METHOD FILTER", fontSize = 11.sp, color = ColorText3, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val methods = listOf(ExportFilterHelper.PAYMENT_ALL) + availablePaymentMethods
                for (method in methods) {
                    val selected = paymentMethod == method
                    FilterChip(
                        selected = selected,
                        onClick = {
                            viewModel.setPaymentMethod(method)
                            viewModel.clearMessages()
                        },
                        label = { Text(method, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ColorOrange,
                            selectedLabelColor = Color.White,
                            containerColor = ColorBg2,
                            labelColor = ColorText2
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 6. Export Preview Card
            Card(
                colors = CardDefaults.cardColors(containerColor = ColorBg2),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, ColorOrange.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("EXPORT PREVIEW", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorOrange)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Selected date range:", fontSize = 13.sp, color = ColorText2)
                        Text(dateRange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ColorText1)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Selected category:", fontSize = 13.sp, color = ColorText2)
                        Text(category, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ColorText1)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Transaction type:", fontSize = 13.sp, color = ColorText2)
                        Text(transactionType, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ColorText1)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Number of transactions:", fontSize = 13.sp, color = ColorText2)
                        Text("${filteredTransactions.size}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ColorText1)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Income:", fontSize = 13.sp, color = ColorText2)
                        Text(
                            ExportFilterHelper.formatIndianCurrency(summary.income),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Expenses:", fontSize = 13.sp, color = ColorText2)
                        Text(
                            ExportFilterHelper.formatIndianCurrency(summary.expenses),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorOrange
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net savings:", fontSize = 13.sp, color = ColorText2)
                        Text(
                            ExportFilterHelper.formatIndianCurrency(summary.netSavings),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (summary.netSavings >= 0.0) ColorGreen else ColorAmber
                        )
                    }

                    if (filteredTransactions.isEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No confirmed transactions found for this period.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 7. Error Message Display
            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ColorBg2),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ColorAmber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        errorMessage!!,
                        color = ColorAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // 8. Success Display & Share / Open Actions
            if (successMessage != null && generatedUri != null && generatedFilename != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ColorBg2),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ColorGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ColorGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(successMessage!!, fontWeight = FontWeight.Bold, color = ColorText1, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Filename: ${generatedFilename!!}",
                            fontSize = 12.sp,
                            color = ColorText2
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    val mime = if (format == "PDF Report") "application/pdf" else "text/csv"
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = mime
                                        putExtra(Intent.EXTRA_STREAM, generatedUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Financial Report"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", color = Color.White, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val mime = if (format == "PDF Report") "application/pdf" else "text/csv"
                                    val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(generatedUri, mime)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(openIntent, "Open Financial Report"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No app available to open this format.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                border = BorderStroke(1.dp, ColorOrange),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorOrange),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Open", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // 9. Export Trigger Button
            Button(
                onClick = { viewModel.exportReport(context) },
                enabled = !isExporting && filteredTransactions.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generating...", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (format == "PDF Report") "Export PDF Report" else "Export CSV Spreadsheet",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
