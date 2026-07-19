package com.autoexpense.app.budget

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoexpense.app.ColorAmber
import com.autoexpense.app.ColorBg0
import com.autoexpense.app.ColorBg2
import com.autoexpense.app.ColorBg3
import com.autoexpense.app.ColorOrange
import com.autoexpense.app.ColorOrangeDim
import com.autoexpense.app.ColorRed
import com.autoexpense.app.ColorText1
import com.autoexpense.app.ColorText2
import com.autoexpense.app.ColorText3
import com.autoexpense.app.data.BudgetEntity
import com.autoexpense.app.data.OVERALL_CATEGORY_KEY
import com.autoexpense.app.data.PeriodType

private data class BudgetSummaryUi(
    val totalBudget: Double,
    val spent: Double,
    val sourceLabel: String
) {
    val remaining: Double = totalBudget - spent
    val percentage: Float = if (totalBudget > 0) (spent / totalBudget).toFloat() else 0f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(viewModel: BudgetViewModel) {
    val budgetsWithSpending by viewModel.budgetsWithSpending.collectAsState()

    var selectedPeriod by remember { mutableStateOf(PeriodType.WEEKLY) }
    var editingBudget by remember { mutableStateOf<BudgetEntity?>(null) }
    var showBudgetSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<BudgetEntity?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val periodBudgets = remember(budgetsWithSpending, selectedPeriod) {
        budgetsWithSpending
            .filter { it.budget.periodType == selectedPeriod }
            .sortedWith(compareBy<BudgetWithSpending> { it.budget.category != null }.thenBy { it.budget.category ?: "" })
    }
    val overallBudget = periodBudgets.find { it.budget.category == null }
    val categoryBudgets = periodBudgets.filter { it.budget.category != null }
    val summary = remember(periodBudgets, overallBudget, categoryBudgets) {
        if (overallBudget != null) {
            BudgetSummaryUi(
                totalBudget = overallBudget.budget.limitAmount,
                spent = overallBudget.spent,
                sourceLabel = "Overall ${periodLabel(selectedPeriod).lowercase()} budget"
            )
        } else {
            BudgetSummaryUi(
                totalBudget = categoryBudgets.sumOf { it.budget.limitAmount },
                spent = categoryBudgets.sumOf { it.spent },
                sourceLabel = "Category budgets"
            )
        }
    }

    Scaffold(
        containerColor = ColorBg0,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingBudget = null
                    errorMessage = null
                    showBudgetSheet = true
                },
                containerColor = ColorOrange,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Budget")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ColorBg0)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Text("Budget", color = ColorText1, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Track your spending limits", color = ColorText2, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(18.dp))

            BudgetPeriodTabs(
                selectedPeriod = selectedPeriod,
                onPeriodChange = { selectedPeriod = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(targetState = selectedPeriod, label = "budgetPeriod") {
                Column {
                    BudgetOverviewCard(summary = summary)

                    Spacer(modifier = Modifier.height(18.dp))

                    if (periodBudgets.isEmpty()) {
                        BudgetEmptyState(
                            onCreate = {
                                editingBudget = null
                                errorMessage = null
                                showBudgetSheet = true
                            }
                        )
                    } else {
                        BudgetSectionHeader("Category Budgets")
                        Spacer(modifier = Modifier.height(10.dp))

                        if (categoryBudgets.isEmpty()) {
                            EmptyCategoryBudgetCard(
                                onCreate = {
                                    editingBudget = null
                                    errorMessage = null
                                    showBudgetSheet = true
                                }
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                categoryBudgets.forEach { item ->
                                    BudgetCategoryCard(
                                        item = item,
                                        onEdit = {
                                            editingBudget = item.budget
                                            errorMessage = null
                                            showBudgetSheet = true
                                        },
                                        onDelete = { deleteTarget = item.budget }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        BudgetInsightsCard(items = categoryBudgets)
                    }

                    Spacer(modifier = Modifier.height(84.dp))
                }
            }
        }
    }

    if (showBudgetSheet) {
        BudgetEditorSheet(
            existingBudget = editingBudget,
            allBudgets = budgetsWithSpending.map { it.budget },
            defaultPeriod = selectedPeriod,
            externalError = errorMessage,
            onDismiss = {
                showBudgetSheet = false
                editingBudget = null
                errorMessage = null
            },
            onSave = { category, periodType, amount ->
                viewModel.saveBudget(
                    category = category,
                    periodType = periodType,
                    limitAmount = amount,
                    existingId = editingBudget?.id
                ) { result ->
                    if (result.isSuccess) {
                        showBudgetSheet = false
                        editingBudget = null
                        errorMessage = null
                    } else {
                        errorMessage = result.exceptionOrNull()?.message ?: "Error saving budget"
                    }
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = ColorBg2,
            titleContentColor = ColorText1,
            textContentColor = ColorText2,
            title = { Text("Delete Budget?", fontWeight = FontWeight.Bold) },
            text = { Text("This budget limit will be removed. Your transactions and spending history will remain unchanged.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBudget(target.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = ColorText2)
                }
            }
        )
    }
}

@Composable
private fun BudgetPeriodTabs(
    selectedPeriod: String,
    onPeriodChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ColorBg2)
            .border(1.dp, ColorBg3, RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(PeriodType.WEEKLY to "Weekly", PeriodType.MONTHLY to "Monthly").forEach { (period, label) ->
            val selected = selectedPeriod == period
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) ColorOrange else Color.Transparent)
                    .clickable { onPeriodChange(period) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) Color.White else ColorText2,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BudgetOverviewCard(summary: BudgetSummaryUi) {
    val progress by animateFloatAsState(
        targetValue = summary.percentage.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 750),
        label = "budgetOverviewProgress"
    )
    val progressColor = budgetProgressColor(summary.percentage)

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Budget Overview", color = ColorText1, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(summary.sourceLabel, color = ColorText3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BudgetStatusChip(percentage = summary.percentage)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("Total Budget", color = ColorText2, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(formatRupee(summary.totalBudget), color = ColorText1, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)

            Spacer(modifier = Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BudgetOverviewMetric("Spent", formatRupee(summary.spent), Modifier.weight(1f))
                BudgetOverviewMetric(
                    if (summary.remaining >= 0) "Remaining" else "Over Limit",
                    formatRupee(kotlin.math.abs(summary.remaining)),
                    Modifier.weight(1f),
                    valueColor = if (summary.remaining >= 0) ColorText1 else ColorRed
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(10.dp)),
                color = progressColor,
                trackColor = ColorBg3
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "${(summary.percentage * 100).toInt().coerceAtLeast(0)}% Used",
                color = progressColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BudgetOverviewMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = ColorText1
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ColorBg3.copy(alpha = 0.45f))
            .padding(14.dp)
    ) {
        Text(label, color = ColorText2, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = valueColor, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun BudgetSectionHeader(title: String) {
    Text(title, color = ColorText1, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
}

@Composable
private fun BudgetCategoryCard(
    item: BudgetWithSpending,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val remaining = item.budget.limitAmount - item.spent
    val progress by animateFloatAsState(
        targetValue = item.percentage.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "budgetCategoryProgress"
    )
    val color = budgetProgressColor(item.percentage)
    val category = com.autoexpense.app.ui.cleanCategoryName(item.budget.category ?: "Overall")

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BudgetIconBubble(
                    icon = com.autoexpense.app.ui.getCategoryIcon(category),
                    tint = ColorOrange
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(category, color = ColorText1, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(periodLabel(item.budget.periodType), color = ColorText3, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Budget options", tint = ColorText2)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = ColorBg2
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit", color = ColorText1) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = ColorOrange) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = ColorRed) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ColorRed) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(
                    "${formatRupee(item.spent)} / ${formatRupee(item.budget.limitAmount)}",
                    color = ColorText1,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
                Text("${(item.percentage * 100).toInt().coerceAtLeast(0)}%", color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = color,
                trackColor = ColorBg3
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (remaining >= 0) "Remaining ${formatRupee(remaining)}" else "Budget Exceeded by ${formatRupee(-remaining)}",
                    color = if (remaining >= 0) ColorText2 else ColorRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                BudgetStatusChip(percentage = item.percentage)
            }
        }
    }
}

@Composable
private fun BudgetIconBubble(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(25.dp))
    }
}

@Composable
private fun BudgetStatusChip(percentage: Float) {
    val color = budgetProgressColor(percentage)
    val text = when {
        percentage > 1f -> "Budget Exceeded"
        percentage >= 0.9f -> "Near Limit"
        percentage >= 0.7f -> "Warning"
        else -> "On Track"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BudgetEmptyState(onCreate: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BudgetIconBubble(icon = Icons.Outlined.AccountBalanceWallet, tint = ColorOrange)
            Spacer(modifier = Modifier.height(18.dp))
            Text("No Budgets Yet", color = ColorText1, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Create your first weekly or monthly budget to start tracking your spending.",
                color = ColorText2,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyCategoryBudgetCard(onCreate: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BudgetIconBubble(icon = Icons.Outlined.Category, tint = ColorOrange)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("No category budgets", color = ColorText1, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(3.dp))
                Text("Add limits for food, shopping, travel, bills and more.", color = ColorText2, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun BudgetInsightsCard(items: List<BudgetWithSpending>) {
    if (items.isEmpty()) return

    val bestManaged = items.minByOrNull { it.percentage }
    val highestSpending = items.maxByOrNull { it.spent }
    val closestToLimit = items.maxByOrNull { it.percentage }

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Insights", color = ColorText1, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.height(14.dp))
            InsightRow("Best Managed Budget", bestManaged)
            InsightRow("Highest Spending", highestSpending)
            InsightRow("Closest To Limit", closestToLimit)
        }
    }
}

@Composable
private fun InsightRow(label: String, item: BudgetWithSpending?) {
    if (item == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = com.autoexpense.app.ui.getCategoryIcon(item.budget.category ?: "Budget"),
            contentDescription = null,
            tint = ColorOrange,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = ColorText3, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                com.autoexpense.app.ui.cleanCategoryName(item.budget.category ?: "Overall Budget"),
                color = ColorText1,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text("${(item.percentage * 100).toInt().coerceAtLeast(0)}% Used", color = budgetProgressColor(item.percentage), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BudgetEditorSheet(
    existingBudget: BudgetEntity?,
    allBudgets: List<BudgetEntity>,
    defaultPeriod: String,
    externalError: String?,
    onDismiss: () -> Unit,
    onSave: (String?, String, Double) -> Unit
) {
    val customCategories by com.autoexpense.app.data.CustomCategoryRepository.customCategories.collectAsState(initial = emptyList())
    val categories = remember(customCategories) {
        listOf(
            "Overall Budget",
            "Food & Dining",
            "Shopping",
            "Transportation",
            "Entertainment",
            "Bills",
            "Travel",
            "Fuel",
            "Healthcare",
            "Education",
            "Recharge",
            "Games",
            "Utilities",
            "Others"
        ).plus(customCategories.map { it.name }).distinct()
    }

    var selectedPeriod by remember(existingBudget, defaultPeriod) { mutableStateOf(existingBudget?.periodType ?: defaultPeriod) }
    var selectedCategory by remember(existingBudget) { mutableStateOf(existingBudget?.category ?: "Overall Budget") }
    var amountText by remember(existingBudget) { mutableStateOf(existingBudget?.limitAmount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var localError by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ColorBg2,
        contentColor = ColorText1
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(if (existingBudget == null) "Create Budget" else "Edit Budget", color = ColorText1, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Set a spending limit and Zors will track progress automatically.", color = ColorText2, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(20.dp))

            Text("Budget Type", color = ColorText2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            BudgetPeriodTabs(selectedPeriod = selectedPeriod, onPeriodChange = {
                selectedPeriod = it
                localError = null
            })

            Spacer(modifier = Modifier.height(18.dp))

            Text("Category", color = ColorText2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    val selected = selectedCategory == category
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) ColorOrangeDim else ColorBg3.copy(alpha = 0.65f))
                            .border(1.dp, if (selected) ColorOrange else ColorBg3, CircleShape)
                            .clickable {
                                selectedCategory = category
                                localError = null
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (category == "Overall Budget") Icons.Default.AccountBalanceWallet else com.autoexpense.app.ui.getCategoryIcon(category),
                            contentDescription = null,
                            tint = if (selected) ColorOrange else ColorText2,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(category, color = if (selected) ColorOrange else ColorText2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it.filter { ch -> ch.isDigit() || ch == '.' }
                    localError = null
                },
                label = { Text("Budget Amount") },
                prefix = { Text("₹", color = ColorText2) },
                placeholder = { Text("5000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = budgetTextFieldColors(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            val visibleError = localError ?: externalError
            if (!visibleError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(visibleError, color = ColorRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Cancel", color = ColorText2, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull()
                        val category = if (selectedCategory == "Overall Budget") null else selectedCategory
                        val categoryKey = category ?: OVERALL_CATEGORY_KEY
                        val duplicate = allBudgets.any {
                            it.categoryKey == categoryKey &&
                                it.periodType == selectedPeriod &&
                                it.id != (existingBudget?.id ?: 0L)
                        }
                        when {
                            amount == null || amount <= 0.0 -> localError = "Amount must be greater than zero."
                            duplicate -> localError = "A budget for this category and period already exists."
                            else -> onSave(category, selectedPeriod, amount)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun budgetTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ColorOrange,
    unfocusedBorderColor = ColorBg3,
    focusedLabelColor = ColorOrange,
    unfocusedLabelColor = ColorText2,
    focusedTextColor = ColorText1,
    unfocusedTextColor = ColorText1,
    focusedContainerColor = ColorBg2,
    unfocusedContainerColor = ColorBg2
)

private fun budgetProgressColor(percentage: Float): Color {
    return when {
        percentage >= 0.9f -> ColorRed
        percentage >= 0.7f -> ColorAmber
        else -> ColorOrange
    }
}

private fun periodLabel(periodType: String): String {
    return if (periodType == PeriodType.WEEKLY) "Weekly" else "Monthly"
}

private fun formatRupee(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        "₹${String.format(java.util.Locale.US, "%,.0f", amount)}"
    } else {
        "₹${String.format(java.util.Locale.US, "%,.2f", amount)}"
    }
}
