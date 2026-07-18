package com.autoexpense.app.budget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.autoexpense.app.*
import com.autoexpense.app.data.BudgetEntity
import com.autoexpense.app.data.PeriodType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetScreen(viewModel: BudgetViewModel) {
    val budgetsWithSpending by viewModel.budgetsWithSpending.collectAsState()

    var overallDialogPeriod by remember { mutableStateOf<String?>(null) }
    var editOverallTarget by remember { mutableStateOf<BudgetEntity?>(null) }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var editCategoryTarget by remember { mutableStateOf<BudgetEntity?>(null) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val weeklyOverall = budgetsWithSpending.find { it.budget.category == null && it.budget.periodType == PeriodType.WEEKLY }
    val monthlyOverall = budgetsWithSpending.find { it.budget.category == null && it.budget.periodType == PeriodType.MONTHLY }
    val categoryBudgets = budgetsWithSpending.filter { it.budget.category != null }

    if (budgetsWithSpending.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ColorBg0)
                .padding(16.dp)
        ) {
            Text(
                "Budget",
                fontWeight = FontWeight.Bold,
                color = ColorText1,
                fontSize = 30.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Track your spending limits",
                fontSize = 16.sp,
                color = ColorText2
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        "No Budgets Yet",
                        color = ColorText1,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Set budgets for different categories to track\nyour spending and stay on target.",
                        color = ColorText2,
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg0)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorBg1)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Budget",
                fontWeight = FontWeight.Bold,
                color = ColorText1,
                fontSize = 30.sp
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Track your spending limits",
                fontSize = 16.sp,
                color = ColorText2,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Text(
                "BUDGET OVERVIEW",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ColorText3,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Weekly Overall Card
            if (weeklyOverall != null) {
                OverallBudgetCard(
                    title = "Weekly Budget",
                    item = weeklyOverall,
                    onEdit = {
                        overallDialogPeriod = PeriodType.WEEKLY
                        editOverallTarget = weeklyOverall.budget
                    },
                    onDelete = { viewModel.deleteBudget(weeklyOverall.budget.id) }
                )
            } else {
                EmptyOverallCard(
                    title = "Weekly Budget",
                    subtitle = "No overall weekly limit set.",
                    buttonText = "+ Set weekly budget",
                    onClick = {
                        overallDialogPeriod = PeriodType.WEEKLY
                        editOverallTarget = null
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Monthly Overall Card
            if (monthlyOverall != null) {
                OverallBudgetCard(
                    title = "Monthly Budget",
                    item = monthlyOverall,
                    onEdit = {
                        overallDialogPeriod = PeriodType.MONTHLY
                        editOverallTarget = monthlyOverall.budget
                    },
                    onDelete = { viewModel.deleteBudget(monthlyOverall.budget.id) }
                )
            } else {
                EmptyOverallCard(
                    title = "Monthly Budget",
                    subtitle = "No overall monthly limit set.",
                    buttonText = "+ Set monthly budget",
                    onClick = {
                        overallDialogPeriod = PeriodType.MONTHLY
                        editOverallTarget = null
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Category Budgets Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CATEGORY BUDGETS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorText3
                )
                Button(
                    onClick = {
                        editCategoryTarget = null
                        showCategoryDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOrangeDim),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.heightIn(min = 32.dp)
                ) {
                    Text("+ Add Category Budget", fontSize = 11.sp, color = ColorOrange, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (categoryBudgets.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ColorBg2),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ColorBg3),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No category budgets set.",
                            color = ColorText1,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Create weekly or monthly limits for Food & Dining, Transport, Shopping, etc.",
                            color = ColorText2,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    categoryBudgets.forEach { item ->
                        CategoryBudgetCard(
                            item = item,
                            onEdit = {
                                editCategoryTarget = item.budget
                                showCategoryDialog = true
                            },
                            onDelete = { viewModel.deleteBudget(item.budget.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Set/Edit Overall Budget Dialog
    if (overallDialogPeriod != null) {
        val period = overallDialogPeriod!!
        SetOverallBudgetDialog(
            periodType = period,
            existingBudget = editOverallTarget,
            onDismiss = {
                overallDialogPeriod = null
                editOverallTarget = null
            },
            onSave = { amount ->
                viewModel.saveBudget(
                    category = null,
                    periodType = period,
                    limitAmount = amount,
                    existingId = editOverallTarget?.id
                ) { result ->
                    if (result.isFailure) {
                        errorMessage = result.exceptionOrNull()?.message ?: "Error saving budget"
                    } else {
                        overallDialogPeriod = null
                        editOverallTarget = null
                    }
                }
            }
        )
    }

    // Add/Edit Category Budget Dialog
    if (showCategoryDialog) {
        CategoryBudgetDialog(
            existingBudget = editCategoryTarget,
            allBudgets = budgetsWithSpending.map { it.budget },
            onDismiss = {
                showCategoryDialog = false
                editCategoryTarget = null
                errorMessage = null
            },
            onSave = { category, periodType, amount ->
                viewModel.saveBudget(
                    category = category,
                    periodType = periodType,
                    limitAmount = amount,
                    existingId = editCategoryTarget?.id
                ) { result ->
                    if (result.isFailure) {
                        errorMessage = result.exceptionOrNull()?.message ?: "Error saving budget"
                    } else {
                        showCategoryDialog = false
                        editCategoryTarget = null
                        errorMessage = null
                    }
                }
            },
            externalError = errorMessage
        )
    }
}

@Composable
fun OverallBudgetCard(
    title: String,
    item: BudgetWithSpending,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                StatusChip(level = item.level, pct = (item.percentage * 100).toInt())
            }

            Spacer(modifier = Modifier.height(12.dp))

            val spentStr = formatRupee(item.spent)
            val limitStr = formatRupee(item.budget.limitAmount)
            Text(
                text = "$spentStr spent of $limitStr",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ColorText1
            )

            val remaining = item.budget.limitAmount - item.spent
            val subtext = if (remaining >= 0) {
                "${formatRupee(remaining)} remaining"
            } else {
                "${formatRupee(-remaining)} over limit"
            }
            Text(
                text = subtext,
                fontSize = 12.sp,
                color = if (remaining >= 0) ColorText2 else ColorRed,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            val barColor = when (item.level) {
                BudgetLevel.EXCEEDED -> ColorRed
                BudgetLevel.LIMIT_REACHED -> ColorOrange
                BudgetLevel.HIGH_WARNING -> ColorOrange
                BudgetLevel.NORMAL -> ColorGreen
                BudgetLevel.WARNING -> ColorAmber
            }

            LinearProgressIndicator(
                progress = { item.percentage.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = barColor,
                trackColor = ColorBg3
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    border = BorderStroke(1.dp, ColorBg3),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorText2),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Edit", fontSize = 12.sp)
                }
                TextButton(onClick = onDelete, modifier = Modifier.height(36.dp)) {
                    Text("Remove", fontSize = 12.sp, color = ColorRed)
                }
            }
        }
    }
}

@Composable
fun EmptyOverallCard(
    title: String,
    subtitle: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorText1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, fontSize = 12.sp, color = ColorText2)
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text(buttonText, fontSize = 12.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun CategoryBudgetCard(
    item: BudgetWithSpending,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = com.autoexpense.app.ui.getCategoryIcon(item.budget.category ?: "Unknown"),
                        contentDescription = null,
                        tint = ColorOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        com.autoexpense.app.ui.cleanCategoryName(item.budget.category ?: "Unknown"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorText1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ColorBg3)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (item.budget.periodType == PeriodType.WEEKLY) "Weekly" else "Monthly",
                            fontSize = 10.sp,
                            color = ColorText2,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                StatusChip(level = item.level, pct = (item.percentage * 100).toInt())
            }

            Spacer(modifier = Modifier.height(12.dp))

            val spentStr = formatRupee(item.spent)
            val limitStr = formatRupee(item.budget.limitAmount)
            Text(
                text = "$spentStr spent of $limitStr",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ColorText1
            )

            val remaining = item.budget.limitAmount - item.spent
            val subtext = if (remaining >= 0) {
                "${formatRupee(remaining)} remaining"
            } else {
                "${formatRupee(-remaining)} over limit"
            }
            Text(
                text = subtext,
                fontSize = 11.sp,
                color = if (remaining >= 0) ColorText2 else ColorRed,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            val barColor = when (item.level) {
                BudgetLevel.EXCEEDED -> ColorRed
                BudgetLevel.LIMIT_REACHED -> ColorOrange
                BudgetLevel.HIGH_WARNING -> ColorOrange
                BudgetLevel.NORMAL -> ColorGreen
                BudgetLevel.WARNING -> ColorAmber
            }

            LinearProgressIndicator(
                progress = { item.percentage.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = barColor,
                trackColor = ColorBg3
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    border = BorderStroke(1.dp, ColorBg3),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorText2),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Edit", fontSize = 11.sp)
                }
                TextButton(onClick = onDelete, modifier = Modifier.height(34.dp)) {
                    Text("Remove", fontSize = 11.sp, color = ColorRed)
                }
            }
        }
    }
}

@Composable
fun StatusChip(level: BudgetLevel, pct: Int) {
    val (bg, fg, text) = when (level) {
        BudgetLevel.EXCEEDED -> Triple(ColorRed.copy(alpha = 0.15f), ColorRed, "$pct% · Exceeded")
        BudgetLevel.LIMIT_REACHED -> Triple(ColorOrange.copy(alpha = 0.15f), ColorOrange, "$pct% · Limit Reached")
        BudgetLevel.HIGH_WARNING -> Triple(ColorOrange.copy(alpha = 0.15f), ColorOrange, "$pct% · High")
        BudgetLevel.NORMAL -> Triple(ColorGreen.copy(alpha = 0.15f), ColorGreen, "$pct% · On Track")
        BudgetLevel.WARNING -> Triple(ColorAmber.copy(alpha = 0.15f), ColorAmber, "$pct% · Warning")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 10.sp, color = fg, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SetOverallBudgetDialog(
    periodType: String,
    existingBudget: BudgetEntity?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var amountText by remember {
        mutableStateOf(if (existingBudget != null) existingBudget.limitAmount.toLong().toString() else "")
    }
    var localError by remember { mutableStateOf<String?>(null) }

    val periodLabel = if (periodType == PeriodType.WEEKLY) "Weekly" else "Monthly"
    val titleText = if (existingBudget != null) "Edit $periodLabel Budget" else "Set $periodLabel Budget"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ColorBg2),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, ColorBg3),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(titleText, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        localError = null
                    },
                    label = { Text("Limit Amount (₹)") },
                    placeholder = { Text("e.g. 2000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorOrange,
                        unfocusedBorderColor = ColorBg3,
                        focusedLabelColor = ColorOrange,
                        unfocusedLabelColor = ColorText2,
                        focusedTextColor = ColorText1,
                        unfocusedTextColor = ColorText1
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (localError != null) {
                    Text(
                        text = localError!!,
                        color = ColorRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = ColorText3)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val valAmt = amountText.toDoubleOrNull()
                            if (valAmt == null || valAmt <= 0) {
                                localError = "Amount must be greater than zero."
                            } else {
                                onSave(valAmt)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Save", color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryBudgetDialog(
    existingBudget: BudgetEntity?,
    allBudgets: List<BudgetEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, Double) -> Unit,
    externalError: String? = null
) {
    val customCategories by com.autoexpense.app.data.CustomCategoryRepository.customCategories.collectAsState(
        initial = emptyList()
    )
    val baseCategories = listOf(
        "Food & Dining",
        "Transport",
        "Groceries",
        "Shopping",
        "Entertainment",
        "Healthcare",
        "Rent / Bills",
        "Travel",
        "Personal Transfer"
    )
    val categories = (baseCategories + customCategories.map { it.name }).distinct()

    var selectedCategory by remember {
        mutableStateOf(existingBudget?.category ?: categories.first())
    }
    var selectedPeriod by remember {
        mutableStateOf(existingBudget?.periodType ?: PeriodType.WEEKLY)
    }
    var amountText by remember {
        mutableStateOf(if (existingBudget != null) existingBudget.limitAmount.toLong().toString() else "")
    }
    var localError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ColorBg2),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, ColorBg3),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    if (existingBudget != null) "Edit Category Budget" else "Add Category Budget",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorText1
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text("Category", fontSize = 12.sp, color = ColorText2, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) ColorOrangeDim else ColorBg3)
                                .border(1.dp, if (isSelected) ColorOrange else ColorBg3, CircleShape)
                                .clickable {
                                    selectedCategory = cat
                                    localError = null
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = com.autoexpense.app.ui.getCategoryIcon(cat),
                                    contentDescription = null,
                                    tint = if (isSelected) ColorOrange else ColorText2,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(cat, fontSize = 11.sp, color = if (isSelected) ColorOrange else ColorText2)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Period", fontSize = 12.sp, color = ColorText2, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isWeekly = selectedPeriod == PeriodType.WEEKLY
                    Button(
                        onClick = {
                            selectedPeriod = PeriodType.WEEKLY
                            localError = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isWeekly) ColorOrange else ColorBg3
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Weekly", color = if (isWeekly) Color.White else ColorText2, fontSize = 12.sp)
                    }

                    val isMonthly = selectedPeriod == PeriodType.MONTHLY
                    Button(
                        onClick = {
                            selectedPeriod = PeriodType.MONTHLY
                            localError = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMonthly) ColorOrange else ColorBg3
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Monthly", color = if (isMonthly) Color.White else ColorText2, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        localError = null
                    },
                    label = { Text("Limit Amount (₹)") },
                    placeholder = { Text("e.g. 1000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorOrange,
                        unfocusedBorderColor = ColorBg3,
                        focusedLabelColor = ColorOrange,
                        unfocusedLabelColor = ColorText2,
                        focusedTextColor = ColorText1,
                        unfocusedTextColor = ColorText1
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (localError != null || externalError != null) {
                    Text(
                        text = localError ?: externalError ?: "",
                        color = ColorRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = ColorText3)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val valAmt = amountText.toDoubleOrNull()
                            if (valAmt == null || valAmt <= 0) {
                                localError = "Amount must be greater than zero."
                            } else {
                                // Check duplicate before calling onSave
                                val duplicate = allBudgets.any {
                                    it.category == selectedCategory &&
                                    it.periodType == selectedPeriod &&
                                    it.id != (existingBudget?.id ?: 0L)
                                }
                                if (duplicate) {
                                    localError = "A budget for this category and period already exists. Edit the existing budget instead."
                                } else {
                                    onSave(selectedCategory, selectedPeriod, valAmt)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Save", color = Color.White)
                    }
                }
            }
        }
    }
}

private fun formatRupee(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        "₹${String.format(java.util.Locale.US, "%,.0f", amount)}"
    } else {
        "₹${String.format(java.util.Locale.US, "%,.2f", amount)}"
    }
}
