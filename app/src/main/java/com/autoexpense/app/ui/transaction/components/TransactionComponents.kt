@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.autoexpense.app.ui.transaction.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoexpense.app.domain.CashFlowService
import com.autoexpense.app.domain.FinancialTransaction
import com.autoexpense.app.domain.TransactionType
import com.autoexpense.app.ui.components.AeButton
import com.autoexpense.app.ui.components.AeCard
import com.autoexpense.app.ui.components.AeEmptyState
import com.autoexpense.app.ui.components.AeFilterChip
import com.autoexpense.app.ui.components.AeLoadingState
import com.autoexpense.app.ui.components.AeMerchantLogo
import com.autoexpense.app.ui.components.AeOutlinedButton
import com.autoexpense.app.ui.components.AeSearchField
import com.autoexpense.app.ui.theme.AeSpacing
import com.autoexpense.app.ui.theme.aePalette
import com.autoexpense.app.ui.theme.categoryColor
import com.autoexpense.app.ui.transaction.viewmodel.TransactionDateFilter
import com.autoexpense.app.ui.transaction.viewmodel.TransactionFilterState
import com.autoexpense.app.ui.transaction.viewmodel.TransactionFormState
import com.autoexpense.app.ui.transaction.viewmodel.TransactionSortOption
import com.autoexpense.app.ui.transaction.viewmodel.TransactionSummaryUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TransactionSummaryCard(
    title: String,
    summary: TransactionSummaryUiState,
    transactionType: TransactionType,
    modifier: Modifier = Modifier
) {
    val palette = aePalette()
    val isIncome = transactionType == TransactionType.INCOME
    val accent = if (isIncome) palette.success else palette.expense
    AeCard(modifier = modifier, elevated = true) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("This month", color = palette.textSecondary, fontSize = 12.sp)
            }
            AeMerchantLogo(label = title, color = accent, icon = Icons.Outlined.AccountBalance, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(AeSpacing.Md))
        AnimatedContent(targetState = summary.monthlyAmount, label = "monthlyAmount") { amount ->
            Text(CashFlowService.formatIndianCurrency(amount), color = accent, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(AeSpacing.Md))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryPill("Today", CashFlowService.formatIndianCurrency(summary.todayAmount), Icons.Outlined.CalendarMonth)
            SummaryPill("Average", CashFlowService.formatIndianCurrency(summary.averageMonthlyAmount), Icons.Outlined.Payments)
            SummaryPill("Largest", CashFlowService.formatIndianCurrency(summary.largestAmount), Icons.AutoMirrored.Outlined.ReceiptLong)
            SummaryPill("Count", summary.transactionCount.toString(), Icons.Outlined.Info)
            SummaryPill("Net Savings", CashFlowService.formatIndianCurrency(summary.netSavings), Icons.Outlined.AccountBalance)
        }
    }
}

@Composable
private fun SummaryPill(label: String, value: String, icon: ImageVector) {
    val palette = aePalette()
    Card(
        colors = CardDefaults.cardColors(containerColor = palette.surfaceAlt),
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = palette.primary, modifier = Modifier.size(14.dp))
            Text("$label: $value", color = palette.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun TransactionSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    activeFilterCount: Int,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AeSearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Search transactions...",
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onFilterClick) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(Icons.Outlined.FilterList, contentDescription = "Open filters", tint = aePalette().primary)
                if (activeFilterCount > 0) {
                    Text(activeFilterCount.toString(), color = aePalette().primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionCard(
    transaction: FinancialTransaction,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = aePalette()
    val isIncomeLike = transaction.transactionType in setOf(TransactionType.INCOME, TransactionType.REFUND, TransactionType.CASHBACK, TransactionType.INTEREST)
    val accent = if (isIncomeLike) palette.success else palette.expense
    AeCard(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress),
        elevated = true
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AeMerchantLogo(
                label = transaction.merchant.ifBlank { transaction.title },
                color = categoryColor(transaction.category).takeUnless { transaction.category.isBlank() } ?: accent,
                icon = if (isIncomeLike) Icons.Outlined.AccountBalance else Icons.AutoMirrored.Outlined.ReceiptLong
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(transaction.merchant.ifBlank { transaction.title }, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${transaction.category} · ${formatDateTime(transaction.date)}", color = palette.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TransactionBadge(transaction.transactionType.name.replace('_', ' '))
                    if (transaction.isRecurring) TransactionBadge("Recurring", Icons.Outlined.Repeat)
                    if (transaction.isAutoDetected) TransactionBadge("Auto", Icons.Outlined.AutoAwesome)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isIncomeLike) "+" else "-"}${CashFlowService.formatIndianCurrency(transaction.amount)}",
                    color = accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(transaction.paymentMethod.ifBlank { transaction.notificationSource.ifBlank { "Manual" } }, color = palette.textTertiary, fontSize = 11.sp)
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = palette.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionBadge(label: String, icon: ImageVector? = null) {
    AssistChip(
        onClick = {},
        label = { Text(label, fontSize = 10.sp, maxLines = 1) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(12.dp)) } },
        colors = AssistChipDefaults.assistChipColors(containerColor = aePalette().surfaceAlt, labelColor = aePalette().textSecondary),
        border = null
    )
}

@Composable
fun TransactionList(
    transactions: List<FinancialTransaction>,
    loading: Boolean,
    transactionType: TransactionType,
    onAdd: () -> Unit,
    onClick: (FinancialTransaction) -> Unit,
    onLongPress: (FinancialTransaction) -> Unit,
    onEdit: (FinancialTransaction) -> Unit,
    onDelete: (FinancialTransaction) -> Unit,
    modifier: Modifier = Modifier
) {
    if (loading) {
        AeLoadingState(visible = true, modifier = modifier)
    } else if (transactions.isEmpty()) {
        AeEmptyState(
            title = if (transactionType == TransactionType.INCOME) "Your income will appear here." else "No transactions yet.",
            description = "Add a transaction or wait for Zors to detect one.",
            icon = Icons.Outlined.SearchOff,
            action = {
                AeButton(
                    text = if (transactionType == TransactionType.INCOME) "Add Income" else "Add Transaction",
                    onClick = onAdd,
                    icon = Icons.Default.Add
                )
            },
            modifier = modifier
        )
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(transactions, key = { it.id }) { transaction ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        when (value) {
                            SwipeToDismissBoxValue.StartToEnd -> onEdit(transaction)
                            SwipeToDismissBoxValue.EndToStart -> onDelete(transaction)
                            SwipeToDismissBoxValue.Settled -> Unit
                        }
                        false
                    }
                )
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        SwipeBackground(dismissValue = dismissState.dismissDirection)
                    },
                    content = {
                        TransactionCard(
                            transaction = transaction,
                            onClick = { onClick(transaction) },
                            onLongPress = { onLongPress(transaction) },
                            onEdit = { onEdit(transaction) },
                            onDelete = { onDelete(transaction) }
                        )
                    }
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun SwipeBackground(dismissValue: SwipeToDismissBoxValue) {
    val palette = aePalette()
    val isDelete = dismissValue == SwipeToDismissBoxValue.EndToStart
    val color = if (isDelete) palette.error else palette.primary
    val icon = if (isDelete) Icons.Outlined.Delete else Icons.Outlined.Edit
    val alignment = if (isDelete) Alignment.CenterEnd else Alignment.CenterStart
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .padding(horizontal = 4.dp),
        contentAlignment = alignment
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFilterSheet(
    filters: TransactionFilterState,
    categories: List<String>,
    paymentMethods: List<String>,
    onApply: (TransactionFilterState) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(filters) { mutableStateOf(filters) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = aePalette().surface) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Filters", color = aePalette().textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Date Range", color = aePalette().textSecondary, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionDateFilter.entries.forEach { option ->
                    AeFilterChip(label = option.label, selected = draft.dateFilter == option, onClick = { draft = draft.copy(dateFilter = option) })
                }
            }
            Text("Sort", color = aePalette().textSecondary, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionSortOption.entries.forEach { option ->
                    AeFilterChip(label = option.label, selected = draft.sort == option, onClick = { draft = draft.copy(sort = option) })
                }
            }
            Text("Category", color = aePalette().textSecondary, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AeFilterChip(label = "All", selected = draft.category == null, onClick = { draft = draft.copy(category = null) })
                categories.forEach { category ->
                    AeFilterChip(label = category, selected = draft.category == category, onClick = { draft = draft.copy(category = category) })
                }
            }
            Text("Payment Method", color = aePalette().textSecondary, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AeFilterChip(label = "All", selected = draft.paymentMethod == null, onClick = { draft = draft.copy(paymentMethod = null) })
                paymentMethods.forEach { method ->
                    AeFilterChip(label = method, selected = draft.paymentMethod == method, onClick = { draft = draft.copy(paymentMethod = method) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = draft.recurring == true, onCheckedChange = { draft = draft.copy(recurring = if (it) true else null) })
                Text("Recurring only", color = aePalette().textPrimary)
                Spacer(Modifier.width(12.dp))
                Checkbox(checked = draft.autoDetected == true, onCheckedChange = { draft = draft.copy(autoDetected = if (it) true else null) })
                Text("Auto detected", color = aePalette().textPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                AeOutlinedButton(text = "Reset", onClick = { draft = TransactionFilterState(query = draft.query) }, modifier = Modifier.weight(1f))
                AeButton(text = "Apply", onClick = { onApply(draft); onDismiss() }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionForm(
    title: String,
    transactionType: TransactionType,
    initial: FinancialTransaction?,
    categories: List<String>,
    paymentMethods: List<String>,
    onSave: (TransactionFormState) -> Unit,
    onDismiss: () -> Unit,
    validationError: String? = null,
    saving: Boolean = false,
    saveButtonText: String = "Save"
) {
    var form by remember(initial) {
        mutableStateOf(
            TransactionFormState(
                amount = initial?.amount?.toString().orEmpty(),
                category = initial?.category ?: categories.firstOrNull().orEmpty(),
                source = initial?.notificationSource?.ifBlank { initial.merchant } ?: "",
                merchant = initial?.merchant ?: "",
                notes = initial?.notes ?: "",
                paymentMethod = initial?.paymentMethod?.ifBlank { "OTHER" } ?: "OTHER",
                isRecurring = initial?.isRecurring ?: false,
                isAutoDetected = initial?.isAutoDetected ?: false,
                date = initial?.date ?: System.currentTimeMillis()
            )
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = aePalette().surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(title, color = aePalette().textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (form.isAutoDetected && initial != null) {
                    Text("Auto detected transaction: only category, notes, and tags should be changed.", color = aePalette().warning, fontSize = 12.sp)
                }
                OutlinedTextField(
                    value = form.amount,
                    onValueChange = { form = form.copy(amount = it) },
                    enabled = initial?.isAutoDetected != true && !saving,
                    label = { Text("Amount") },
                    supportingText = { FieldValidationText(validationError, "amount") },
                    isError = validationError?.contains("amount", ignoreCase = true) == true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = form.merchant,
                    onValueChange = { form = form.copy(merchant = it) },
                    enabled = initial?.isAutoDetected != true && !saving,
                    label = { Text("Source / Merchant") },
                    supportingText = { FieldValidationText(validationError, "source or merchant") },
                    isError = validationError?.contains("merchant", ignoreCase = true) == true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { form = form.copy(notes = it) },
                    enabled = !saving,
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Text("Category", color = aePalette().textSecondary, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category -> FilterChip(selected = form.category == category, enabled = !saving, onClick = { form = form.copy(category = category) }, label = { Text(category) }) }
                }
                FieldValidationText(validationError, "category")
                Text("Payment Method", color = aePalette().textSecondary, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    paymentMethods.forEach { method -> FilterChip(selected = form.paymentMethod == method, enabled = initial?.isAutoDetected != true && !saving, onClick = { form = form.copy(paymentMethod = method) }, label = { Text(method) }) }
                }
                FieldValidationText(validationError, "payment method")
                Text("Date", color = aePalette().textSecondary, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = formatDateOnly(form.date),
                    onValueChange = {},
                    enabled = false,
                    label = { Text("Date") },
                    supportingText = { FieldValidationText(validationError, "date") },
                    isError = validationError?.contains("date", ignoreCase = true) == true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val now = System.currentTimeMillis()
                    FilterChip(selected = isSameDay(form.date, now), enabled = !saving, onClick = { form = form.copy(date = now) }, label = { Text("Today") })
                    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
                    FilterChip(selected = isSameDay(form.date, yesterday), enabled = !saving, onClick = { form = form.copy(date = yesterday) }, label = { Text("Yesterday") })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Recurring", color = aePalette().textPrimary)
                    Switch(checked = form.isRecurring, enabled = !saving, onCheckedChange = { form = form.copy(isRecurring = it) })
                }
                if (validationError != null) Text(validationError, color = aePalette().error, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            AeButton(
                text = saveButtonText,
                onClick = { onSave(form) },
                modifier = Modifier.fillMaxWidth(),
                loading = saving,
                enabled = !saving
            )
            Spacer(Modifier.height(8.dp))
            AeOutlinedButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FieldValidationText(validationError: String?, token: String) {
    if (validationError != null && validationError.contains(token, ignoreCase = true)) {
        Text(validationError, color = aePalette().error, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailsSheet(
    transaction: FinancialTransaction,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = aePalette().surface) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(transaction.merchant.ifBlank { transaction.title }, color = aePalette().textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(CashFlowService.formatIndianCurrency(transaction.amount), color = aePalette().success, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            HorizontalDivider(color = aePalette().border)
            DetailRow("Category", transaction.category)
            DetailRow("Type", transaction.transactionType.name.replace('_', ' '))
            DetailRow("Source", transaction.notificationSource.ifBlank { "Manual" })
            DetailRow("Payment Method", transaction.paymentMethod.ifBlank { "Other" })
            DetailRow("Reference", transaction.referenceNumber.ifBlank { "Not available" })
            DetailRow("Date", formatDateTime(transaction.date))
            DetailRow("Recurring", if (transaction.isRecurring) "Yes" else "No")
            DetailRow("Auto Detected", if (transaction.isAutoDetected) "Yes" else "No")
            if (transaction.notes.isNotBlank()) DetailRow("Notes", transaction.notes)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                AeOutlinedButton("Delete", onClick = onDelete, icon = Icons.Outlined.Delete, modifier = Modifier.weight(1f))
                AeButton("Edit", onClick = onEdit, icon = Icons.Outlined.Edit, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = aePalette().textSecondary, fontSize = 13.sp)
        Text(value, color = aePalette().textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
fun TransactionDeleteDialog(
    label: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $label?", fontWeight = FontWeight.Bold) },
        text = { Text("This removes the transaction from this list. You can undo immediately after deleting.") },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = aePalette().error, contentColor = Color.White)) {
                Text("Delete")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = aePalette().surface
    )
}

@Composable
fun TransactionFab(label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text(label, fontWeight = FontWeight.Bold) },
        containerColor = aePalette().primary,
        contentColor = Color.White
    )
}

fun defaultIncomeCategories(): List<String> = listOf(
    "Salary",
    "Business",
    "Freelancing",
    "Investment",
    "Rental Income",
    "Bonus",
    "Refund",
    "Cashback",
    "Interest",
    "Gift",
    "Transfer Received",
    "Other"
)

fun defaultPaymentMethods(): List<String> = listOf("Bank", "UPI", "Cash", "Cheque", "Wallet", "Card", "Other")

fun formatDateTime(timestamp: Long): String {
    return if (timestamp > 0L) {
        SimpleDateFormat("d MMM, h:mm a", Locale.US).format(Date(timestamp))
    } else {
        "No date"
    }
}

private fun formatDateOnly(timestamp: Long): String {
    return if (timestamp > 0L) {
        SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(timestamp))
    } else {
        "No date"
    }
}

private fun isSameDay(first: Long, second: Long): Boolean {
    val firstCal = Calendar.getInstance().apply { timeInMillis = first }
    val secondCal = Calendar.getInstance().apply { timeInMillis = second }
    return firstCal.get(Calendar.YEAR) == secondCal.get(Calendar.YEAR) &&
        firstCal.get(Calendar.DAY_OF_YEAR) == secondCal.get(Calendar.DAY_OF_YEAR)
}
