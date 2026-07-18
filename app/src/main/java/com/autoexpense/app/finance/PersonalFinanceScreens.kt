package com.autoexpense.app.finance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
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
import com.autoexpense.app.ColorRed
import com.autoexpense.app.ColorText1
import com.autoexpense.app.ColorText2
import com.autoexpense.app.ColorText3
import com.autoexpense.app.DashboardViewModel
import com.autoexpense.app.TransactionRepository
import com.autoexpense.app.data.BillEntity
import com.autoexpense.app.data.BillRepository
import com.autoexpense.app.data.PaymentMethod
import com.autoexpense.app.data.RecurringPaymentEntity
import com.autoexpense.app.data.RecurringPaymentRepository
import com.autoexpense.app.notification.RecurringPaymentDetector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BillsViewModel : ViewModel() {
    val bills: StateFlow<List<BillEntity>> = BillRepository.bills

    fun markPaid(id: String) {
        viewModelScope.launch { BillRepository.markPaid(id) }
    }

    fun dismiss(id: String) {
        viewModelScope.launch { BillRepository.dismiss(id) }
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
        (billItems + recurringItems).sortedBy { it.timestamp ?: Long.MAX_VALUE }.take(4)
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
                    Text("Upcoming", color = ColorText1, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Bills and subscriptions due soon", color = ColorText3, fontSize = 12.sp)
                }
                TextButton(onClick = onViewAll) {
                    Text("View All →", color = ColorOrange, fontWeight = FontWeight.Bold)
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
    recurringViewModel: RecurringPaymentsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var selectedTab by remember { mutableStateOf("bills") }
    val tabs = listOf("bills" to "Bills", "subscriptions" to "Subscriptions")
    FinanceListScreenShell(
        title = "Payments",
        subtitle = "Bills and recurring payments in one place"
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
        if (selectedTab == "bills") {
            BillsContent(billsViewModel)
        } else {
            SubscriptionsContent(recurringViewModel)
        }
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
private fun BillsContent(viewModel: BillsViewModel) {
    val bills by viewModel.bills.collectAsState()
    val grouped = bills.groupBy { it.status }
    if (bills.isEmpty()) {
        EmptyFinanceState("No Bills\nYou're all caught up.")
    } else {
        listOf("OVERDUE", "DUE_SOON", "UPCOMING", "PAID", "DISMISSED").forEach { status ->
            val items = grouped[status].orEmpty()
            if (items.isNotEmpty()) {
                Text(status.replace('_', ' '), fontSize = 12.sp, color = ColorText3, fontWeight = FontWeight.Bold)
                items.forEach { bill ->
                    FinanceCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bill.provider, color = ColorText1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("${bill.billType.replace('_', ' ')} - ${formatDate(bill.dueDate)}", color = ColorText2, fontSize = 12.sp)
                                Text(bill.safeExcerpt, color = ColorText3, fontSize = 11.sp, maxLines = 2)
                            }
                            Text(DashboardViewModel.formatIndianCurrencyValue(bill.amount), color = amountColor(status), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        if (bill.status != "PAID" && bill.status != "DISMISSED") {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.markPaid(bill.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                                    shape = RoundedCornerShape(10.dp)
                                ) { Text("Mark Paid", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp) }
                                OutlinedButton(
                                    onClick = { viewModel.dismiss(bill.id) },
                                    border = BorderStroke(1.dp, ColorBg3),
                                    shape = RoundedCornerShape(10.dp)
                                ) { Text("Dismiss", color = ColorText2, fontSize = 12.sp) }
                            }
                        }
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
private fun SubscriptionsContent(viewModel: RecurringPaymentsViewModel) {
    val recurring by viewModel.recurringPayments.collectAsState()
    val txns by viewModel.transactions.collectAsState()
    LaunchedEffect(txns) { viewModel.refreshDetected() }
    val active = recurring.filter { it.status == "ACTIVE" || it.status == "MISSED" }
    if (recurring.isEmpty()) {
        EmptyFinanceState("No Subscriptions\nRecurring payments will appear here.")
    } else {
        listOf("ACTIVE", "PAUSED", "MISSED", "DISMISSED").forEach { status ->
            val section = recurring.filter { it.status == status }
            if (section.isNotEmpty()) {
                Text(status, fontSize = 12.sp, color = ColorText3, fontWeight = FontWeight.Bold)
                section.forEach { item ->
                    FinanceCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.merchant, color = ColorText1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("${item.frequency.replace('_', ' ')} - next ${formatDate(item.nextExpectedAt)}", color = ColorText2, fontSize = 12.sp)
                                Text("${(item.confidence * 100).toInt()}% confidence - ${item.status}", color = ColorText3, fontSize = 11.sp)
                            }
                            Text(DashboardViewModel.formatIndianCurrencyValue(item.amount), color = if (item.status == "MISSED") ColorRed else ColorText1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.updateStatus(item.id, if (item.status == "PAUSED") "ACTIVE" else "PAUSED") },
                                border = BorderStroke(1.dp, ColorBg3),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text(if (item.status == "PAUSED") "Resume" else "Pause", color = ColorText2, fontSize = 12.sp) }
                            OutlinedButton(
                                onClick = { viewModel.updateStatus(item.id, "DISMISSED") },
                                border = BorderStroke(1.dp, ColorBg3),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Dismiss", color = ColorText2, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinanceListScreenShell(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg0)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ColorText1)
        Text(subtitle, fontSize = 12.sp, color = ColorText2)
        content()
    }
}

@Composable
private fun EmptyFinanceState(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, tint = ColorText3, modifier = Modifier.size(42.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(text, color = ColorText2, fontSize = 13.sp)
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
