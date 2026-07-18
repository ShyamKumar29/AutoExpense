package com.autoexpense.app.finance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoexpense.app.ColorAmber
import com.autoexpense.app.ColorBg0
import com.autoexpense.app.ColorBg2
import com.autoexpense.app.ColorBg3
import com.autoexpense.app.ColorGreen
import com.autoexpense.app.ColorOrange
import com.autoexpense.app.ColorOrangeDim
import com.autoexpense.app.ColorRed
import com.autoexpense.app.ColorText1
import com.autoexpense.app.ColorText2
import com.autoexpense.app.ColorText3
import com.autoexpense.app.DashboardViewModel
import com.autoexpense.app.Transaction
import com.autoexpense.app.TransactionRepository
import com.autoexpense.app.data.BillEntity
import com.autoexpense.app.data.BillRepository
import com.autoexpense.app.data.PaymentMethod
import com.autoexpense.app.data.RecurringPaymentEntity
import com.autoexpense.app.data.RecurringPaymentRepository
import com.autoexpense.app.data.UserPreferencesRepository
import com.autoexpense.app.notification.RecurringPaymentDetector
import com.autoexpense.app.notification.SmartPaymentsFeedback
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class BillsViewModel : ViewModel() {
    val bills: StateFlow<List<BillEntity>> = BillRepository.bills

    fun markPaid(id: String) {
        viewModelScope.launch { BillRepository.markPaid(id) }
    }

    fun dismiss(id: String) {
        viewModelScope.launch { BillRepository.dismiss(id) }
    }

    fun saveManualBill(
        existing: BillEntity?,
        name: String,
        category: String,
        amount: Double,
        dueDate: Long,
        repeatCycle: String,
        reminder: String,
        autoPay: Boolean,
        notes: String
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val status = if (existing?.status == "PAID") "PAID" else deriveBillStatus(dueDate)
            val safeExcerpt = buildList {
                add(repeatCycle)
                add("Reminder: $reminder")
                add("Auto Pay: ${if (autoPay) "On" else "Off"}")
                if (notes.isNotBlank()) add(notes.trim())
            }.joinToString(" • ")
            BillRepository.upsert(
                BillEntity(
                    id = existing?.id ?: "manual_bill_${UUID.randomUUID()}",
                    billType = normalizeManualKey(category),
                    provider = name.trim(),
                    amount = amount,
                    dueDate = dueDate,
                    status = status,
                    generatedAt = existing?.generatedAt ?: now,
                    paidAt = existing?.paidAt,
                    paidTransactionId = existing?.paidTransactionId,
                    source = "MANUAL",
                    safeExcerpt = safeExcerpt,
                    billFingerprint = existing?.billFingerprint ?: "manual_bill_${UUID.randomUUID()}",
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
            )
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { BillRepository.delete(id) }
    }
}

class RecurringPaymentsViewModel : ViewModel() {
    val recurringPayments: StateFlow<List<RecurringPaymentEntity>> = RecurringPaymentRepository.items
    val transactions = TransactionRepository.transactions

    fun refreshDetected() {
        viewModelScope.launch {
            RecurringPaymentRepository.upsertAll(RecurringPaymentDetector.detect(transactions.value))
        }
    }

    fun updateStatus(id: String, status: String) {
        viewModelScope.launch { RecurringPaymentRepository.updateStatus(id, status) }
    }

    fun saveManualSubscription(
        existing: RecurringPaymentEntity?,
        name: String,
        amount: Double,
        renewalDate: Long,
        billingCycle: String,
        reminder: String,
        autoPay: Boolean
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val normalized = normalizeManualKey(name).lowercase()
            RecurringPaymentRepository.upsert(
                RecurringPaymentEntity(
                    id = existing?.id ?: "manual_subscription_${UUID.randomUUID()}",
                    merchant = name.trim(),
                    normalizedMerchant = normalized,
                    amount = amount,
                    frequency = billingCycle.uppercase(Locale.US),
                    lastPaymentAt = existing?.lastPaymentAt ?: (renewalDate - 30L * DAY_MS),
                    nextExpectedAt = renewalDate,
                    status = existing?.status?.takeIf { it == "PAID" || it == "PAUSED" } ?: "ACTIVE",
                    confidence = existing?.confidence ?: 1f,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
            )
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { RecurringPaymentRepository.delete(id) }
    }
}

data class PaymentMethodBreakdownItem(
    val method: String,
    val label: String,
    val amount: Double,
    val count: Int
)

private data class UpcomingItem(
    val title: String,
    val subtitle: String,
    val amount: Double,
    val timestamp: Long?,
    val icon: String
)

private data class CalendarPaymentItem(
    val id: String,
    val type: String,
    val name: String,
    val amount: Double,
    val dueAt: Long,
    val status: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private data class SmartPaymentSuggestion(
    val id: String,
    val merchant: String,
    val amount: Double,
    val frequency: String,
    val occurrenceCount: Int,
    val nextDueAt: Long,
    val type: String,
    val lastPaymentAt: Long
)

class PaymentMethodStatsViewModel : ViewModel() {
    val breakdown: StateFlow<List<PaymentMethodBreakdownItem>> = TransactionRepository.transactions.map { txns ->
        DashboardViewModel.computeConfirmedOutgoing(txns)
            .groupBy { it.paymentMethod.ifBlank { PaymentMethod.UNKNOWN.name } }
            .map { (method, items) ->
                PaymentMethodBreakdownItem(
                    method = method,
                    label = PaymentMethod.labelFor(method),
                    amount = items.sumOf { DashboardViewModel.parseAmount(it.amount) },
                    count = items.size
                )
            }
            .sortedByDescending { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun PaymentMethodSummaryCard(viewModel: PaymentMethodStatsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val breakdown by viewModel.breakdown.collectAsState()
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PAYMENT METHODS", fontSize = 10.sp, color = ColorText3, fontWeight = FontWeight.Bold)
            if (breakdown.isEmpty()) {
                Text("No confirmed payment methods yet", color = ColorText2, fontSize = 13.sp)
            } else {
                breakdown.take(4).forEach { item ->
                    FinanceMetricRow(
                        title = item.label,
                        sub = "${item.count} ${if (item.count == 1) "txn" else "txns"}",
                        amount = DashboardViewModel.formatIndianCurrencyValue(item.amount)
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentMethodDistributionCard(viewModel: PaymentMethodStatsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val breakdown by viewModel.breakdown.collectAsState()
    val total = breakdown.sumOf { it.amount }
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("PAYMENT METHODS", fontSize = 10.sp, color = ColorText3, fontWeight = FontWeight.Bold)
                    Text(DashboardViewModel.formatIndianCurrencyValue(total), color = ColorText1, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("${breakdown.sumOf { it.count }} txns", color = ColorText2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            if (breakdown.isEmpty() || total <= 0.0) {
                EmptyFinanceState("Payment method trends will appear after confirmed expenses.")
            } else {
                Canvas(modifier = Modifier.fillMaxWidth().height(18.dp)) {
                    var x = 0f
                    breakdown.forEachIndexed { index, item ->
                        val width = (item.amount / total).toFloat() * size.width
                        drawRoundRect(
                            color = methodColor(index),
                            topLeft = Offset(x, 0f),
                            size = Size(width.coerceAtLeast(if (item.amount > 0) 4f else 0f), size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
                        )
                        x += width
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    breakdown.forEachIndexed { index, item ->
                        val pct = if (total > 0) (item.amount / total) * 100 else 0.0
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(methodColor(index)))
                                Spacer(modifier = Modifier.size(10.dp))
                                Column {
                                    Text(item.label, color = ColorText1, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("${item.count} ${if (item.count == 1) "transaction" else "transactions"} - ${String.format(Locale.US, "%.0f", pct)}%", color = ColorText3, fontSize = 11.sp)
                                }
                            }
                            Text(DashboardViewModel.formatIndianCurrencyValue(item.amount), color = ColorText1, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpcomingDashboardCard(onViewAll: () -> Unit) {
    val bills by BillRepository.bills.collectAsState()
    val recurring by RecurringPaymentRepository.items.collectAsState()
    val upcoming = remember(bills, recurring) {
        val billItems = bills
            .filter { it.status != "PAID" && it.status != "DISMISSED" }
            .map {
                UpcomingItem(
                    title = it.provider,
                    subtitle = it.billType.replace('_', ' '),
                    amount = it.amount,
                    timestamp = it.dueDate,
                    icon = "⚡"
                )
            }
        val recurringItems = recurring
            .filter { it.status == "ACTIVE" || it.status == "MISSED" }
            .map {
                UpcomingItem(
                    title = it.merchant,
                    subtitle = it.frequency.replace('_', ' '),
                    amount = it.amount,
                    timestamp = it.nextExpectedAt,
                    icon = "↻"
                )
            }
        (billItems + recurringItems).sortedBy { it.timestamp ?: Long.MAX_VALUE }.take(3)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Upcoming Payments", color = ColorText1, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Bills and subscriptions due soon", color = ColorText3, fontSize = 12.sp)
                }
                TextButton(onClick = onViewAll) {
                    Text("View All", color = ColorOrange, fontWeight = FontWeight.Bold)
                }
            }
            if (upcoming.isEmpty()) {
                EmptyFinanceState("You're all caught up.")
            } else {
                upcoming.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(ColorOrange.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Text(item.icon, fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.size(12.dp))
                            Column {
                                Text(item.title, color = ColorText1, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(relativeDueText(item.timestamp), color = ColorText3, fontSize = 12.sp)
                            }
                        }
                        Text(DashboardViewModel.formatIndianCurrencyValue(item.amount), color = ColorText1, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentsScreen(
    billsViewModel: BillsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    recurringViewModel: RecurringPaymentsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    initialTab: String = "bills",
    focusSuggestionsOnOpen: Boolean = false
) {
    var selectedTab by remember(initialTab) { mutableStateOf(if (initialTab == "subscriptions") "subscriptions" else "bills") }
    var editingBill by remember { mutableStateOf<BillEntity?>(null) }
    var editingSubscription by remember { mutableStateOf<RecurringPaymentEntity?>(null) }
    var showBillSheet by remember { mutableStateOf(false) }
    var showSubscriptionSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val bills by billsViewModel.bills.collectAsState()
    val recurring by recurringViewModel.recurringPayments.collectAsState()
    val transactions by recurringViewModel.transactions.collectAsState()
    val userPrefs = remember(context) { UserPreferencesRepository.getInstance(context) }
    val smartDetectionEnabled by userPrefs.isSmartPaymentDetectionEnabled.collectAsState(initial = true)
    val smartSuggestionsEnabled by userPrefs.isSmartSuggestionsEnabled.collectAsState(initial = true)
    val smartRecurringNotificationsEnabled by userPrefs.isSmartRecurringNotificationsEnabled.collectAsState(initial = true)
    val tabs = listOf("bills" to "Bills", "subscriptions" to "Subscriptions")
    val visibleBills = if (selectedTab == "bills") bills else emptyList()
    val visibleSubscriptions = if (selectedTab == "subscriptions") recurring else emptyList()
    val smartPrefs = remember(context) { context.getSharedPreferences("smart_payments", android.content.Context.MODE_PRIVATE) }
    var ignoredSuggestionIds by remember {
        mutableStateOf(smartPrefs.getStringSet("ignored_suggestions", emptySet()).orEmpty())
    }
    var notifiedSuggestionIds by remember {
        mutableStateOf(smartPrefs.getStringSet("notified_suggestions", emptySet()).orEmpty())
    }
    val suggestions = remember(transactions, bills, recurring, ignoredSuggestionIds, selectedTab, smartDetectionEnabled, smartSuggestionsEnabled) {
        if (!smartDetectionEnabled || !smartSuggestionsEnabled) {
            emptyList()
        } else {
            detectSmartPaymentSuggestions(transactions, bills, recurring, ignoredSuggestionIds)
                .filter { if (selectedTab == "bills") it.type == "BILL" else it.type == "SUBSCRIPTION" }
        }
    }
    val paymentsScrollState = rememberScrollState()

    LaunchedEffect(suggestions, smartRecurringNotificationsEnabled) {
        if (!smartRecurringNotificationsEnabled) return@LaunchedEffect
        val newSuggestion = suggestions.firstOrNull { it.id !in notifiedSuggestionIds } ?: return@LaunchedEffect
        SmartPaymentsFeedback.publishRecurringDetected(context, newSuggestion.merchant, newSuggestion.frequency)
        val updated = notifiedSuggestionIds + newSuggestion.id
        notifiedSuggestionIds = updated
        smartPrefs.edit().putStringSet("notified_suggestions", updated).apply()
    }

    LaunchedEffect(focusSuggestionsOnOpen, suggestions.size) {
        if (focusSuggestionsOnOpen && suggestions.isNotEmpty()) {
            paymentsScrollState.animateScrollTo(620)
        }
    }

    if (showBillSheet) {
        AddBillSheet(
            existing = editingBill,
            onDismiss = {
                showBillSheet = false
                editingBill = null
            },
            onSave = { name, category, amount, dueDate, repeatCycle, reminder, autoPay, notes ->
                billsViewModel.saveManualBill(editingBill, name, category, amount, dueDate, repeatCycle, reminder, autoPay, notes)
                showBillSheet = false
                editingBill = null
            }
        )
    }

    if (showSubscriptionSheet) {
        AddSubscriptionSheet(
            existing = editingSubscription,
            onDismiss = {
                showSubscriptionSheet = false
                editingSubscription = null
            },
            onSave = { name, amount, renewalDate, billingCycle, reminder, autoPay ->
                recurringViewModel.saveManualSubscription(editingSubscription, name, amount, renewalDate, billingCycle, reminder, autoPay)
                showSubscriptionSheet = false
                editingSubscription = null
            }
        )
    }

    Scaffold(
        containerColor = ColorBg0,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == "bills") {
                        editingBill = null
                        showBillSheet = true
                    } else {
                        editingSubscription = null
                        showSubscriptionSheet = true
                    }
                },
                containerColor = ColorOrange,
                contentColor = androidx.compose.ui.graphics.Color.White,
                shape = CircleShape,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Icon(Icons.Default.Add, contentDescription = if (selectedTab == "bills") "Add bill" else "Add subscription")
            }
        }
    ) { innerPadding ->
        FinanceListScreenShell(
            title = "Payments",
            subtitle = "Manage bills and subscriptions",
            modifier = Modifier.padding(innerPadding),
            scrollState = paymentsScrollState
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ColorBg2, RoundedCornerShape(18.dp))
                    .border(1.dp, ColorBg3, RoundedCornerShape(18.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEach { (key, label) ->
                    val selected = selectedTab == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) ColorOrange else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { selectedTab = key }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (selected) androidx.compose.ui.graphics.Color.White else ColorText2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            PaymentsSummaryCard(bills = visibleBills, recurring = visibleSubscriptions)
            ExpectedFixedExpensesCard(
                bills = visibleBills,
                recurring = visibleSubscriptions,
                selectedTab = selectedTab
            )
            SmartUpcomingCalendar(
                bills = visibleBills,
                recurring = visibleSubscriptions,
                onMarkBillPaid = { billsViewModel.markPaid(it) },
                onMarkSubscriptionPaid = { recurringViewModel.updateStatus(it, "PAID") }
            )
            SmartSuggestionsSection(
                suggestions = suggestions,
                onTrack = { suggestion ->
                    if (suggestion.type == "BILL") {
                        editingBill = suggestion.toBillEntity()
                        showBillSheet = true
                    } else {
                        editingSubscription = suggestion.toRecurringPaymentEntity()
                        showSubscriptionSheet = true
                    }
                },
                onIgnore = { suggestion ->
                    val updated = ignoredSuggestionIds + suggestion.id
                    ignoredSuggestionIds = updated
                    smartPrefs.edit().putStringSet("ignored_suggestions", updated).apply()
                }
            )
            if (selectedTab == "bills") {
                BillsContent(
                    viewModel = billsViewModel,
                    transactions = transactions,
                    onAdd = {
                        editingBill = null
                        showBillSheet = true
                    },
                    onEdit = {
                        editingBill = it
                        showBillSheet = true
                    },
                    onDelete = { billsViewModel.delete(it.id) }
                )
            } else {
                SubscriptionsContent(
                    viewModel = recurringViewModel,
                    transactions = transactions,
                    onAdd = {
                        editingSubscription = null
                        showSubscriptionSheet = true
                    },
                    onEdit = {
                        editingSubscription = it
                        showSubscriptionSheet = true
                    },
                    onDelete = { recurringViewModel.delete(it.id) }
                )
            }
        }
    }
}

@Composable
private fun PaymentsSummaryCard(
    bills: List<BillEntity>,
    recurring: List<RecurringPaymentEntity>
) {
    val calendarItems = remember(bills, recurring) { buildCalendarPaymentItems(bills, recurring) }
    val upcoming = calendarItems.filter { it.status != "PAID" && it.status != "DISMISSED" }
    val nextDue = upcoming.minByOrNull { it.dueAt }
    val upcomingAmount = upcoming.sumOf { it.amount }
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Upcoming", color = ColorText1, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(DashboardViewModel.formatIndianCurrencyValue(upcomingAmount), color = ColorText1, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryPill("Bills", bills.count { it.status != "DISMISSED" }.toString(), Modifier.weight(1f))
                SummaryPill("Subscriptions", recurring.count { it.status != "DISMISSED" && it.status != "PAUSED" }.toString(), Modifier.weight(1f))
                SummaryPill("Next Due", relativeDueText(nextDue?.dueAt), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ColorBg3.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, color = ColorText3, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(value, color = ColorText1, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartUpcomingCalendar(
    bills: List<BillEntity>,
    recurring: List<RecurringPaymentEntity>,
    onMarkBillPaid: (String) -> Unit,
    onMarkSubscriptionPaid: (String) -> Unit
) {
    val todayStart = remember { startOfDay(System.currentTimeMillis()) }
    val days = remember(todayStart) { (0 until 10).map { todayStart + it * DAY_MS } }
    var selectedDay by remember(todayStart) { mutableStateOf(todayStart) }
    val payments = remember(bills, recurring) { buildCalendarPaymentItems(bills, recurring) }
    val byDay = remember(payments) { payments.groupBy { startOfDay(it.dueAt) } }
    val selectedPayments = byDay[selectedDay].orEmpty().sortedBy { it.dueAt }
    var showLegendGuide by remember { mutableStateOf(false) }
    val legendSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showLegendGuide) {
        PaymentStatusGuideSheet(
            sheetState = legendSheetState,
            onDismiss = { showLegendGuide = false }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Upcoming Calendar", color = ColorText1, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text(SimpleDateFormat("MMMM", Locale.US).format(Date(todayStart)), color = ColorText3, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                CalendarLegend(onInfoClick = { showLegendGuide = true })
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                days.forEach { dayStart ->
                    val dayPayments = byDay[dayStart].orEmpty()
                    CalendarDateChip(
                        dayStart = dayStart,
                        isToday = dayStart == todayStart,
                        selected = dayStart == selectedDay,
                        payments = dayPayments,
                        onClick = { selectedDay = dayStart }
                    )
                }
            }

            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(180))
            ) {
                DailyPaymentPanel(
                    dayStart = selectedDay,
                    payments = selectedPayments,
                    onMarkBillPaid = onMarkBillPaid,
                    onMarkSubscriptionPaid = onMarkSubscriptionPaid
                )
            }
        }
    }
}

@Composable
private fun CalendarDateChip(
    dayStart: Long,
    isToday: Boolean,
    selected: Boolean,
    payments: List<CalendarPaymentItem>,
    onClick: () -> Unit
) {
    val statusColor = calendarStatusColor(payments)
    val chipBg by animateColorAsState(
        targetValue = if (selected) ColorOrange.copy(alpha = 0.18f) else if (isToday) ColorOrangeDim else ColorBg3.copy(alpha = 0.45f),
        animationSpec = tween(180),
        label = "calendarChipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected || isToday) ColorOrange else ColorBg3,
        animationSpec = tween(180),
        label = "calendarChipBorder"
    )
    Column(
        modifier = Modifier
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(chipBg)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(SimpleDateFormat("EEE", Locale.US).format(Date(dayStart)), color = ColorText3, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(SimpleDateFormat("d", Locale.US).format(Date(dayStart)), color = if (selected) ColorOrange else ColorText1, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(7.dp))
        if (payments.isEmpty()) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(ColorBg3))
        } else if (payments.size == 1) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
        } else {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(statusColor)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(payments.size.toString(), color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DailyPaymentPanel(
    dayStart: Long,
    payments: List<CalendarPaymentItem>,
    onMarkBillPaid: (String) -> Unit,
    onMarkSubscriptionPaid: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ColorBg3.copy(alpha = 0.35f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(SimpleDateFormat("d MMMM", Locale.US).format(Date(dayStart)), color = ColorText1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (payments.isEmpty()) {
            Text("No payments scheduled.", color = ColorText2, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
        } else {
            payments.forEach { item ->
                DailyPaymentRow(
                    item = item,
                    onMarkPaid = {
                        if (item.type == "Bill") onMarkBillPaid(item.id) else onMarkSubscriptionPaid(item.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun DailyPaymentRow(
    item: CalendarPaymentItem,
    onMarkPaid: () -> Unit
) {
    val statusColor = paymentStatusColor(item)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(item.icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(21.dp))
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, color = ColorText1, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${formatDate(item.dueAt)} - ${paymentStatusLabel(item)}", color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(DashboardViewModel.formatIndianCurrencyValue(item.amount), color = ColorText1, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (item.status != "PAID" && item.status != "DISMISSED") {
                TextButton(onClick = onMarkPaid, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text("Paid", color = ColorOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CalendarLegend(onInfoClick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(ColorOrange, ColorAmber, ColorRed, ColorGreen).forEach { color ->
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        }
        IconButton(
            onClick = onInfoClick,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Payment status guide",
                tint = ColorText3,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentStatusGuideSheet(
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ColorBg2,
        contentColor = ColorText1
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Payment Status Guide", color = ColorText1, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            PaymentStatusGuideRow(ColorOrange, "Upcoming", "A payment is scheduled for a future date.")
            PaymentStatusGuideRow(ColorAmber, "Due Soon", "The payment is due within the next 2 days.")
            PaymentStatusGuideRow(ColorRed, "Overdue", "The payment's due date has passed and it has not been marked as Paid.")
            PaymentStatusGuideRow(ColorGreen, "Paid", "All payments scheduled for that date have been completed.")
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PaymentStatusGuideRow(
    color: androidx.compose.ui.graphics.Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ColorBg3.copy(alpha = 0.35f))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ColorText1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(3.dp))
            Text(description, color = ColorText2, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ExpectedFixedExpensesCard(
    bills: List<BillEntity>,
    recurring: List<RecurringPaymentEntity>,
    selectedTab: String
) {
    val activeBills = bills.filter { it.status != "DISMISSED" && it.status != "PAID" }
    val activeSubscriptions = recurring.filter { it.status != "DISMISSED" && it.status != "PAUSED" }
    val items = remember(activeBills, activeSubscriptions) {
        (activeBills.map { it.provider to it.amount } + activeSubscriptions.map { it.merchant to it.amount })
            .sortedByDescending { it.second }
    }

    FinanceCard {
        Text("Expected Fixed Expenses", color = ColorText1, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(DashboardViewModel.formatIndianCurrencyValue(items.sumOf { it.second }), color = ColorText1, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(12.dp))
        if (items.isEmpty()) {
            Text(
                text = if (selectedTab == "bills") "No active bills." else "No active subscriptions.",
                color = ColorText2,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        } else {
            items.take(5).forEach { (name, amount) ->
                FinanceMetricRow(name, "Active recurring payment", DashboardViewModel.formatIndianCurrencyValue(amount))
            }
        }
    }
}

@Composable
private fun SmartSuggestionsSection(
    suggestions: List<SmartPaymentSuggestion>,
    onTrack: (SmartPaymentSuggestion) -> Unit,
    onIgnore: (SmartPaymentSuggestion) -> Unit
) {
    if (suggestions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Suggested Recurring Payments", color = ColorText1, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        suggestions.forEach { suggestion ->
            FinanceCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ColorOrange.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (suggestion.type == "BILL") billCategoryIcon(suggestion.merchant) else Icons.Outlined.Subscriptions,
                            contentDescription = null,
                            tint = ColorOrange,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(suggestion.merchant, color = ColorText1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Detected ${suggestion.frequency.lowercase().replaceFirstChar { it.titlecase(Locale.US) }}", color = ColorText2, fontSize = 12.sp)
                        Text("Found ${suggestion.occurrenceCount} matching payments", color = ColorText3, fontSize = 11.sp)
                    }
                    Text(DashboardViewModel.formatIndianCurrencyValue(suggestion.amount), color = ColorText1, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onTrack(suggestion) },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Track as ${if (suggestion.type == "BILL") "Bill" else "Subscription"}", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onIgnore(suggestion) },
                        border = BorderStroke(1.dp, ColorBg3),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Ignore", color = ColorText2, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBillSheet(
    existing: BillEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Long, String, String, Boolean, String) -> Unit
) {
    val categoryOptions = remember {
        listOf("Electricity", "Water", "WiFi", "Rent", "Insurance", "Credit Card", "Mobile Recharge", "Gas", "Internet", "Others")
    }
    val repeatOptions = remember { listOf("Weekly", "Monthly", "Yearly") }
    val reminderOptions = remember { listOf("None", "1 Day Before", "3 Days Before", "7 Days Before") }
    var name by remember(existing) { mutableStateOf(existing?.provider.orEmpty()) }
    var category by remember(existing) { mutableStateOf(existing?.billType?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.titlecase(Locale.US) } ?: categoryOptions.first()) }
    var amount by remember(existing) { mutableStateOf(existing?.amount?.takeIf { it > 0.0 }?.toString().orEmpty()) }
    var dueDate by remember(existing) { mutableStateOf(existing?.dueDate ?: startOfDay(System.currentTimeMillis())) }
    var repeatCycle by remember(existing) { mutableStateOf(existing?.safeExcerpt?.substringBefore(" • ")?.takeIf { it in repeatOptions } ?: "Monthly") }
    var reminder by remember(existing) { mutableStateOf(reminderOptions.first()) }
    var autoPay by remember(existing) { mutableStateOf(existing?.safeExcerpt?.contains("Auto Pay: On") == true) }
    var notes by remember(existing) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ColorBg2,
        contentColor = ColorText1
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(if (existing == null) "Add Bill" else "Edit Bill", color = ColorText1, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            FinanceTextField(value = name, onValueChange = { name = it }, label = "Bill Name")
            CategoryDropdown(value = category, options = categoryOptions, onValueChange = { category = it })
            FinanceTextField(
                value = amount,
                onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = "Amount",
                keyboardType = KeyboardType.Decimal
            )
            DatePickerField(label = "Due Date", selectedDate = dueDate, onDateSelected = { dueDate = it })
            SelectionChipRow(title = "Repeat Cycle", options = repeatOptions, selected = repeatCycle, onSelected = { repeatCycle = it })
            SelectionChipRow(title = "Reminder", options = reminderOptions, selected = reminder, onSelected = { reminder = it })
            AutoPayRow(checked = autoPay, onCheckedChange = { autoPay = it })
            FinanceTextField(value = notes, onValueChange = { notes = it }, label = "Notes (optional)", singleLine = false)
            error?.let { Text(it, color = ColorRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    border = BorderStroke(1.dp, ColorBg3),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel", color = ColorText2) }
                Button(
                    onClick = {
                        val parsedAmount = amount.toDoubleOrNull()
                        error = when {
                            name.trim().isEmpty() -> "Enter a bill name."
                            parsedAmount == null || parsedAmount <= 0.0 -> "Enter a valid amount."
                            dueDate <= 0L -> "Choose a due date."
                            else -> null
                        }
                        if (error == null) onSave(name.trim(), category, parsedAmount ?: 0.0, dueDate, repeatCycle, reminder, autoPay, notes)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Save Bill", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSubscriptionSheet(
    existing: RecurringPaymentEntity?,
    onDismiss: () -> Unit,
    onSave: (String, Double, Long, String, String, Boolean) -> Unit
) {
    val cycleOptions = remember { listOf("Monthly", "Yearly") }
    val reminderOptions = remember { listOf("None", "1 Day Before", "3 Days Before", "7 Days Before") }
    var name by remember(existing) { mutableStateOf(existing?.merchant.orEmpty()) }
    var price by remember(existing) { mutableStateOf(existing?.amount?.takeIf { it > 0.0 }?.toString().orEmpty()) }
    var renewalDate by remember(existing) { mutableStateOf(existing?.nextExpectedAt ?: startOfDay(System.currentTimeMillis())) }
    var billingCycle by remember(existing) { mutableStateOf(existing?.frequency?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.titlecase(Locale.US) }?.takeIf { it in cycleOptions } ?: "Monthly") }
    var reminder by remember(existing) { mutableStateOf(reminderOptions.first()) }
    var autoPay by remember(existing) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ColorBg2,
        contentColor = ColorText1
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(if (existing == null) "Add Subscription" else "Edit Subscription", color = ColorText1, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            FinanceTextField(value = name, onValueChange = { name = it }, label = "Subscription Name")
            FinanceTextField(
                value = price,
                onValueChange = { price = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = "Price",
                keyboardType = KeyboardType.Decimal
            )
            DatePickerField(label = "Renewal Date", selectedDate = renewalDate, onDateSelected = { renewalDate = it })
            SelectionChipRow(title = "Billing Cycle", options = cycleOptions, selected = billingCycle, onSelected = { billingCycle = it })
            SelectionChipRow(title = "Reminder", options = reminderOptions, selected = reminder, onSelected = { reminder = it })
            AutoPayRow(checked = autoPay, onCheckedChange = { autoPay = it })
            error?.let { Text(it, color = ColorRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    border = BorderStroke(1.dp, ColorBg3),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel", color = ColorText2) }
                Button(
                    onClick = {
                        val parsedPrice = price.toDoubleOrNull()
                        error = when {
                            name.trim().isEmpty() -> "Enter a subscription name."
                            parsedPrice == null || parsedPrice <= 0.0 -> "Enter a valid price."
                            renewalDate <= 0L -> "Choose a renewal date."
                            else -> null
                        }
                        if (error == null) onSave(name.trim(), parsedPrice ?: 0.0, renewalDate, billingCycle, reminder, autoPay)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Save Subscription", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PaymentEmptyPlaceholder(
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 18.dp)
        ) {
            Text(title, color = ColorText2, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = ColorText3, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PaymentBillCard(
    bill: BillEntity,
    history: List<Transaction>,
    onMarkPaid: () -> Unit,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    FinanceCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(amountColor(bill.status).copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(billCategoryIcon(bill.billType), contentDescription = null, tint = amountColor(bill.status), modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(bill.provider, color = ColorText1, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${bill.billType.replace('_', ' ')} - Due ${formatDate(bill.dueDate)}", color = ColorText2, fontSize = 12.sp, maxLines = 1)
                Text("${bill.safeExcerpt.substringBefore(" • ").ifBlank { "Monthly" }} - ${paymentStatusText(bill.status)}", color = amountColor(bill.status), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(DashboardViewModel.formatIndianCurrencyValue(bill.amount), color = ColorText1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                PaymentOverflowMenu(
                    canMarkPaid = bill.status != "PAID" && bill.status != "DISMISSED",
                    onMarkPaid = onMarkPaid,
                    onEdit = onEdit,
                    onDelete = onDelete
                )
            }
        }
        if (bill.status != "PAID" && bill.status != "DISMISSED") {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onMarkPaid,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Mark Paid", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp) }
                OutlinedButton(
                    onClick = onDismiss,
                    border = BorderStroke(1.dp, ColorBg3),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Dismiss", color = ColorText2, fontSize = 12.sp) }
            }
        }
        PaymentHistoryBlock(history)
    }
}

@Composable
private fun SubscriptionPaymentCard(
    item: RecurringPaymentEntity,
    history: List<Transaction>,
    onMarkPaid: () -> Unit,
    onTogglePause: () -> Unit,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusLabel = subscriptionStatusText(item)
    val statusColor = when (statusLabel) {
        "Overdue" -> ColorRed
        "Paid" -> ColorGreen
        else -> ColorOrange
    }
    FinanceCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Subscriptions, contentDescription = null, tint = statusColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.merchant, color = ColorText1, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${item.frequency.replace('_', ' ')} - Renews ${formatDate(item.nextExpectedAt)}", color = ColorText2, fontSize = 12.sp, maxLines = 1)
                Text(statusLabel, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(DashboardViewModel.formatIndianCurrencyValue(item.amount), color = ColorText1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                PaymentOverflowMenu(
                    canMarkPaid = item.status != "PAID" && item.status != "DISMISSED",
                    onMarkPaid = onMarkPaid,
                    onEdit = onEdit,
                    onDelete = onDelete
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onTogglePause,
                border = BorderStroke(1.dp, ColorBg3),
                shape = RoundedCornerShape(10.dp)
            ) { Text(if (item.status == "PAUSED") "Resume" else "Pause", color = ColorText2, fontSize = 12.sp) }
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, ColorBg3),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Dismiss", color = ColorText2, fontSize = 12.sp) }
        }
        PaymentHistoryBlock(history)
    }
}

@Composable
private fun PaymentHistoryBlock(history: List<Transaction>) {
    if (history.isEmpty()) return
    Spacer(modifier = Modifier.height(12.dp))
    Text("Previous Payments", color = ColorText2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(6.dp))
    history.take(3).forEach { txn ->
        FinanceMetricRow(
            title = formatDate(txn.timestamp),
            sub = if (txn.detectionReason.isNotBlank() || txn.notificationExcerpt.isNotBlank()) "Matched Automatically" else "Manual",
            amount = DashboardViewModel.formatIndianCurrencyValue(DashboardViewModel.parseAmount(txn.amount))
        )
    }
}

@Composable
private fun PaymentOverflowMenu(
    canMarkPaid: Boolean,
    onMarkPaid: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Payment actions", tint = ColorText3, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (canMarkPaid) {
                DropdownMenuItem(text = { Text("Mark as Paid") }, onClick = { expanded = false; onMarkPaid() })
            }
            DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }, onClick = { expanded = false; onEdit() })
            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }, onClick = { expanded = false; onDelete() })
        }
    }
}

@Composable
private fun FinanceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ColorText1,
            unfocusedTextColor = ColorText1,
            focusedLabelColor = ColorOrange,
            unfocusedLabelColor = ColorText3,
            focusedBorderColor = ColorOrange,
            unfocusedBorderColor = ColorBg3,
            cursorColor = ColorOrange
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CategoryDropdown(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Category", color = ColorText3, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, ColorBg3, RoundedCornerShape(16.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Text(value, color = ColorText1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = {
                        onValueChange(option)
                        expanded = false
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    selectedDate: Long,
    onDateSelected: (Long) -> Unit
) {
    var openPicker by remember { mutableStateOf(false) }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = ColorText3, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, ColorBg3, RoundedCornerShape(16.dp))
                .clickable { openPicker = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Event, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(19.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(formatDate(selectedDate), color = ColorText1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    if (openPicker) {
        DatePickerDialog(
            onDismissRequest = { openPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDateSelected(startOfDay(it)) }
                    openPicker = false
                }) { Text("Done", color = ColorOrange) }
            },
            dismissButton = {
                TextButton(onClick = { openPicker = false }) { Text("Cancel", color = ColorText2) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SelectionChipRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = ColorText3, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSelected) ColorOrange else ColorBg3.copy(alpha = 0.45f))
                        .border(1.dp, if (isSelected) ColorOrange else ColorBg3, RoundedCornerShape(999.dp))
                        .clickable { onSelected(option) }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(option, color = if (isSelected) androidx.compose.ui.graphics.Color.White else ColorText2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AutoPayRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ColorBg3.copy(alpha = 0.35f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Auto Pay", color = ColorText1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Track whether this payment is automated.", color = ColorText3, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun BillsScreen(viewModel: BillsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    FinanceListScreenShell(
        title = "Bills",
        subtitle = "Pending, paid, and overdue bills"
    ) {
        BillsContent(viewModel)
    }
}

@Composable
private fun BillsContent(
    viewModel: BillsViewModel,
    transactions: List<Transaction> = emptyList(),
    onAdd: () -> Unit = {},
    onEdit: (BillEntity) -> Unit = {},
    onDelete: (BillEntity) -> Unit = {}
) {
    val bills by viewModel.bills.collectAsState()
    val grouped = bills.groupBy { it.status }
    AnimatedVisibility(
        visible = bills.isEmpty(),
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(140))
    ) {
        PaymentEmptyPlaceholder(
            title = "No bills added yet.",
            subtitle = "Tap + to add your first bill."
        )
    }
    AnimatedVisibility(
        visible = bills.isNotEmpty(),
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(140))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("OVERDUE", "DUE_SOON", "UPCOMING", "PAID", "DISMISSED").forEach { status ->
                val items = grouped[status].orEmpty()
                if (items.isNotEmpty()) {
                    Text(status.replace('_', ' '), fontSize = 12.sp, color = ColorText3, fontWeight = FontWeight.Bold)
                    items.forEach { bill ->
                        PaymentBillCard(
                            bill = bill,
                            history = matchedPaymentHistory(bill.provider, bill.amount, transactions),
                            onMarkPaid = { viewModel.markPaid(bill.id) },
                            onDismiss = { viewModel.dismiss(bill.id) },
                            onEdit = { onEdit(bill) },
                            onDelete = { onDelete(bill) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecurringPaymentsScreen(viewModel: RecurringPaymentsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    FinanceListScreenShell(
        title = "Subscriptions",
        subtitle = "Recurring payments detected from your history"
    ) {
        SubscriptionsContent(viewModel)
    }
}

@Composable
private fun SubscriptionsContent(
    viewModel: RecurringPaymentsViewModel,
    transactions: List<Transaction> = emptyList(),
    onAdd: () -> Unit = {},
    onEdit: (RecurringPaymentEntity) -> Unit = {},
    onDelete: (RecurringPaymentEntity) -> Unit = {}
) {
    val recurring by viewModel.recurringPayments.collectAsState()
    AnimatedVisibility(
        visible = recurring.isEmpty(),
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(140))
    ) {
        PaymentEmptyPlaceholder(
            title = "No subscriptions added yet.",
            subtitle = "Tap + to add your first subscription."
        )
    }
    AnimatedVisibility(
        visible = recurring.isNotEmpty(),
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(140))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("ACTIVE", "PAUSED", "MISSED", "DISMISSED", "PAID").forEach { status ->
                val section = recurring.filter { it.status == status }
                if (section.isNotEmpty()) {
                    Text(status, fontSize = 12.sp, color = ColorText3, fontWeight = FontWeight.Bold)
                    section.forEach { item ->
                        SubscriptionPaymentCard(
                            item = item,
                            history = matchedPaymentHistory(item.merchant, item.amount, transactions),
                            onMarkPaid = { viewModel.updateStatus(item.id, "PAID") },
                            onTogglePause = { viewModel.updateStatus(item.id, if (item.status == "PAUSED") "ACTIVE" else "PAUSED") },
                            onDismiss = { viewModel.updateStatus(item.id, "DISMISSED") },
                            onEdit = { onEdit(item) },
                            onDelete = { onDelete(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FinanceListScreenShell(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBg0)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = ColorText1)
        Text(subtitle, fontSize = 16.sp, color = ColorText2)
        content()
    }
}

@Composable
private fun EmptyFinanceState(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text.substringBefore("\n"), color = ColorText2, fontSize = 18.sp)
        }
    }
}

@Composable
private fun FinanceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun FinanceMetricRow(title: String, sub: String, amount: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(ColorOrange))
            Spacer(modifier = Modifier.size(8.dp))
            Column {
                Text(title, color = ColorText1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(sub, color = ColorText3, fontSize = 11.sp)
            }
        }
        Text(amount, color = ColorText1, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun amountColor(status: String) = when (status) {
    "OVERDUE" -> ColorRed
    "DUE_SOON" -> ColorAmber
    "PAID" -> ColorGreen
    else -> ColorText1
}

private fun methodColor(index: Int) = listOf(
    ColorOrange,
    ColorAmber,
    ColorGreen,
    androidx.compose.ui.graphics.Color(0xFF42A5F5),
    androidx.compose.ui.graphics.Color(0xFFAB47BC),
    androidx.compose.ui.graphics.Color(0xFF26A69A)
)[index % 6]

private fun relativeDueText(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "No due date"
    val diffDays = ((timestamp - System.currentTimeMillis()) / (24L * 60L * 60L * 1000L)).toInt()
    return when {
        diffDays < 0 -> "${kotlin.math.abs(diffDays)} days overdue"
        diffDays == 0 -> "Today"
        diffDays == 1 -> "Tomorrow"
        else -> "$diffDays days"
    }
}

private fun formatDate(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "No due date"
    return SimpleDateFormat("d MMM", Locale.US).format(Date(timestamp))
}

private const val DAY_MS = 24L * 60L * 60L * 1000L

private fun buildCalendarPaymentItems(
    bills: List<BillEntity>,
    recurring: List<RecurringPaymentEntity>
): List<CalendarPaymentItem> {
    val billItems = bills
        .filter { it.dueDate != null && it.status != "DISMISSED" }
        .map {
            CalendarPaymentItem(
                id = it.id,
                type = "Bill",
                name = it.provider.ifBlank { it.billType.replace('_', ' ') },
                amount = it.amount,
                dueAt = it.dueDate ?: 0L,
                status = it.status,
                icon = Icons.AutoMirrored.Outlined.ReceiptLong
            )
        }
    val subscriptionItems = recurring
        .filter { it.nextExpectedAt > 0L && it.status != "DISMISSED" && it.status != "PAUSED" }
        .map {
            CalendarPaymentItem(
                id = it.id,
                type = "Subscription",
                name = it.merchant,
                amount = it.amount,
                dueAt = it.nextExpectedAt,
                status = it.status,
                icon = Icons.Outlined.Subscriptions
            )
        }
    return (billItems + subscriptionItems).sortedBy { it.dueAt }
}

private fun startOfDay(timestamp: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun calendarStatusColor(payments: List<CalendarPaymentItem>): androidx.compose.ui.graphics.Color {
    if (payments.isEmpty()) return ColorBg3
    return when {
        payments.all { it.status == "PAID" } -> ColorGreen
        payments.any { paymentStatusLabel(it) == "Overdue" } -> ColorRed
        payments.any { daysUntil(it.dueAt) <= 2 } -> ColorAmber
        else -> ColorOrange
    }
}

private fun paymentStatusColor(item: CalendarPaymentItem): androidx.compose.ui.graphics.Color {
    return when (paymentStatusLabel(item)) {
        "Paid" -> ColorGreen
        "Overdue" -> ColorRed
        "Due Soon" -> ColorAmber
        else -> ColorOrange
    }
}

private fun paymentStatusLabel(item: CalendarPaymentItem): String {
    return when {
        item.status == "PAID" -> "Paid"
        item.status == "MISSED" || item.status == "OVERDUE" || startOfDay(item.dueAt) < startOfDay(System.currentTimeMillis()) -> "Overdue"
        daysUntil(item.dueAt) <= 2 -> "Due Soon"
        else -> "Upcoming"
    }
}

private fun daysUntil(timestamp: Long): Int {
    val today = startOfDay(System.currentTimeMillis())
    val due = startOfDay(timestamp)
    return ((due - today) / DAY_MS).toInt()
}

private fun deriveBillStatus(dueDate: Long): String {
    val days = daysUntil(dueDate)
    return when {
        days < 0 -> "OVERDUE"
        days <= 2 -> "DUE_SOON"
        else -> "UPCOMING"
    }
}

private fun normalizeManualKey(value: String): String =
    value.trim()
        .ifBlank { "Manual" }
        .uppercase(Locale.US)
        .replace(Regex("""[^A-Z0-9]+"""), "_")
        .trim('_')

private fun paymentStatusText(status: String): String = when (status) {
    "OVERDUE" -> "Overdue"
    "DUE_SOON" -> "Due Soon"
    "UPCOMING" -> "Upcoming"
    "PAID" -> "Paid"
    "DISMISSED" -> "Dismissed"
    else -> status.replace('_', ' ')
}

private fun detectSmartPaymentSuggestions(
    transactions: List<Transaction>,
    bills: List<BillEntity>,
    recurring: List<RecurringPaymentEntity>,
    ignoredIds: Set<String>,
    nowMs: Long = System.currentTimeMillis()
): List<SmartPaymentSuggestion> {
    val trackedMerchants = (bills.map { it.provider } + recurring.map { it.merchant })
        .map { normalizeSmartMerchant(it) }
        .filter { it.isNotBlank() }
        .toSet()
    val confirmed = DashboardViewModel.computeConfirmedOutgoing(transactions)
        .filter { it.timestamp > 0L && DashboardViewModel.parseAmount(it.amount) > 0.0 }

    return confirmed
        .groupBy { normalizeSmartMerchant(it.merchant) }
        .mapNotNull { (key, txns) ->
            if (key.isBlank() || key in trackedMerchants || key in ignoredIds || txns.size < 3) return@mapNotNull null
            val sorted = txns.sortedBy { it.timestamp }
            val intervals = sorted.zipWithNext { a, b -> ((b.timestamp - a.timestamp) / DAY_MS).toInt() }
                .filter { it > 0 }
            if (intervals.size < 2) return@mapNotNull null
            val medianInterval = intervals.sorted()[intervals.size / 2]
            val frequency = when {
                medianInterval in 6..8 -> "WEEKLY"
                medianInterval in 27..33 -> "MONTHLY"
                medianInterval in 350..380 -> "YEARLY"
                else -> return@mapNotNull null
            }
            val stableIntervals = intervals.count { kotlin.math.abs(it - medianInterval) <= 4 }
            if (stableIntervals < 2) return@mapNotNull null
            val amounts = sorted.map { DashboardViewModel.parseAmount(it.amount) }
            val averageAmount = amounts.average()
            val amountTolerance = maxOf(25.0, averageAmount * 0.18)
            if (amounts.count { kotlin.math.abs(it - averageAmount) <= amountTolerance } < 3) return@mapNotNull null
            val latest = sorted.last()
            val type = classifySmartPayment(latest.merchant, latest.category, averageAmount)
            val suggestionId = key
            SmartPaymentSuggestion(
                id = suggestionId,
                merchant = latest.merchant,
                amount = averageAmount,
                frequency = frequency,
                occurrenceCount = sorted.size,
                nextDueAt = latest.timestamp + medianInterval * DAY_MS,
                type = type,
                lastPaymentAt = latest.timestamp
            )
        }
        .filter { it.nextDueAt >= nowMs - 45L * DAY_MS }
        .sortedBy { it.nextDueAt }
}

private fun classifySmartPayment(merchant: String, category: String, amount: Double): String {
    val haystack = "$merchant $category".lowercase(Locale.US)
    val billKeywords = listOf("electric", "water", "wifi", "internet", "rent", "insurance", "credit", "mobile", "recharge", "gas", "fuel", "jio", "airtel", "bsnl", "utility")
    val subscriptionKeywords = listOf("netflix", "spotify", "youtube", "google", "apple", "prime", "hotstar", "disney", "subscription", "one")
    return when {
        billKeywords.any { it in haystack } -> "BILL"
        subscriptionKeywords.any { it in haystack } -> "SUBSCRIPTION"
        amount >= 1000.0 -> "BILL"
        else -> "SUBSCRIPTION"
    }
}

private fun SmartPaymentSuggestion.toBillEntity(): BillEntity {
    val now = System.currentTimeMillis()
    return BillEntity(
        id = "suggested_bill_${UUID.nameUUIDFromBytes(id.toByteArray())}",
        billType = normalizeManualKey(merchant),
        provider = merchant,
        amount = amount,
        dueDate = nextDueAt,
        status = deriveBillStatus(nextDueAt),
        generatedAt = now,
        source = "SUGGESTED",
        safeExcerpt = "${frequency.lowercase().replaceFirstChar { it.titlecase(Locale.US) }} • Reminder: None • Auto Pay: Off",
        billFingerprint = "suggested_bill_$id",
        createdAt = now,
        updatedAt = now
    )
}

private fun SmartPaymentSuggestion.toRecurringPaymentEntity(): RecurringPaymentEntity {
    val now = System.currentTimeMillis()
    return RecurringPaymentEntity(
        id = "suggested_subscription_${UUID.nameUUIDFromBytes(id.toByteArray())}",
        merchant = merchant,
        normalizedMerchant = normalizeSmartMerchant(merchant),
        amount = amount,
        frequency = frequency,
        lastPaymentAt = lastPaymentAt,
        nextExpectedAt = nextDueAt,
        status = "ACTIVE",
        confidence = 0.9f,
        createdAt = now,
        updatedAt = now
    )
}

private fun matchedPaymentHistory(
    merchant: String,
    amount: Double,
    transactions: List<Transaction>
): List<Transaction> {
    val normalizedMerchant = normalizeSmartMerchant(merchant)
    if (normalizedMerchant.isBlank()) return emptyList()
    val tolerance = maxOf(25.0, amount * 0.18)
    return DashboardViewModel.computeConfirmedOutgoing(transactions)
        .filter { txn ->
            val txnMerchant = normalizeSmartMerchant(txn.merchant)
            val merchantMatch = txnMerchant.contains(normalizedMerchant) || normalizedMerchant.contains(txnMerchant)
            val amountMatch = kotlin.math.abs(DashboardViewModel.parseAmount(txn.amount) - amount) <= tolerance
            merchantMatch && amountMatch
        }
        .sortedByDescending { it.timestamp }
}

private fun normalizeSmartMerchant(value: String): String =
    value.lowercase(Locale.US).replace(Regex("""[^a-z0-9]+"""), "")

private fun subscriptionStatusText(item: RecurringPaymentEntity): String = when {
    item.status == "PAID" -> "Paid"
    item.status == "MISSED" || startOfDay(item.nextExpectedAt) < startOfDay(System.currentTimeMillis()) -> "Overdue"
    item.status == "DISMISSED" -> "Dismissed"
    item.status == "PAUSED" -> "Paused"
    else -> "Upcoming"
}

private fun billCategoryIcon(category: String): ImageVector {
    val normalized = category.uppercase(Locale.US)
    return when {
        "ELECTRIC" in normalized || "UTILITY" in normalized -> Icons.Outlined.ElectricBolt
        "WATER" in normalized -> Icons.Outlined.WaterDrop
        "WIFI" in normalized || "INTERNET" in normalized -> Icons.Outlined.Wifi
        "RENT" in normalized -> Icons.Outlined.Home
        "INSURANCE" in normalized -> Icons.Outlined.Shield
        "CREDIT" in normalized || "CARD" in normalized -> Icons.Outlined.CreditCard
        "MOBILE" in normalized || "RECHARGE" in normalized -> Icons.Outlined.Smartphone
        "GAS" in normalized || "FUEL" in normalized -> Icons.Outlined.LocalGasStation
        else -> Icons.AutoMirrored.Outlined.ReceiptLong
    }
}
