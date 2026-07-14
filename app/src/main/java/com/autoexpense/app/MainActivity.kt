@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.autoexpense.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import com.autoexpense.app.notification.NotificationProcessor
import com.autoexpense.app.data.AutoExpenseDatabase
import com.autoexpense.app.data.TransactionDao
import com.autoexpense.app.data.TransactionEntity
import com.autoexpense.app.data.PeriodType
import com.autoexpense.app.budget.BudgetLevel
import com.autoexpense.app.budget.BudgetNotificationHelper
import com.autoexpense.app.budget.BudgetRepositorySingleton
import com.autoexpense.app.budget.BudgetViewModel
import com.autoexpense.app.budget.BudgetWithSpending
import com.autoexpense.app.budget.BudgetWarning
import com.autoexpense.app.budget.computeLevel
import com.autoexpense.app.budget.BudgetScreen

// ── COLOR PALETTE ──────────────────────────────────────────────────────────
val ColorBg0 = Color(0xFF0D0D0F)
val ColorBg1 = Color(0xFF13141A)
val ColorBg2 = Color(0xFF1A1B24)
val ColorBg3 = Color(0xFF22232F)
val ColorBg4 = Color(0xFF2A2B3A)
val ColorOrange = Color(0xFFFF6B2C)
val ColorOrangeDim = Color(0x26FF6B2C)
val ColorOrangeDark = Color(0xFFCC4A14)
val ColorText1 = Color(0xFFF0F0F5)
val ColorText2 = Color(0xFF9999B0)
val ColorText3 = Color(0xFF5A5A70)
val ColorGreen = Color(0xFF22C55E)
val ColorRed = Color(0xFFEF4444)
val ColorAmber = Color(0xFFF59E0B)
val ColorBlue = Color(0xFF3B82F6)

// ── CUSTOM COMPOSE THEME ──────────────────────────────────────────────────
@Composable
fun AutoExpenseTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        background = ColorBg0,
        surface = ColorBg2,
        primary = ColorOrange,
        onBackground = ColorText1,
        onSurface = ColorText1,
        secondary = ColorText2
    )
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}

// ── DATA MODELS ───────────────────────────────────────────────────────────
data class Transaction(
    val id: String,
    val merchant: String,
    val sub: String,
    val source: String, // "gpay", "phonepe", "paytm", "bhim"
    var category: String,
    val amount: String,
    val date: String,
    var status: String, // "confirmed", "review", "ignored"
    // Phase 2: populated only for notification-detected transactions.
    val notificationExcerpt: String = "",
    val detectionReason: String = "",
    val timestamp: Long = 0L
)

// ── TRANSACTION REPOSITORY (Single Source of Truth) ─────────────────────────
object TransactionRepository {
    private lateinit var dao: TransactionDao
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    fun init(context: Context) {
        dao = AutoExpenseDatabase.getDatabase(context).transactionDao()
        coroutineScope.launch {
            dao.observeAll().collect { entities ->
                _transactions.value = entities.map { it.toTransaction() }
            }
        }
    }

    suspend fun getConfirmedEntities(context: Context): List<com.autoexpense.app.data.TransactionEntity> {
        return AutoExpenseDatabase.getDatabase(context).transactionDao().getConfirmedTransactions()
    }

    fun confirmTransaction(id: String, category: String) {
        coroutineScope.launch {
            dao.confirmTransaction(id, category)
        }
    }

    fun ignoreTransaction(id: String) {
        coroutineScope.launch {
            dao.ignoreTransaction(id)
        }
    }

    suspend fun existsByFingerprint(fingerprint: String): Boolean {
        return dao.existsByFingerprint(fingerprint)
    }

    fun addTransactionEntity(entity: TransactionEntity) {
        coroutineScope.launch {
            dao.insert(entity)
        }
    }

    /** Insert a newly detected transaction at the top of the list (Phase 2). */
    fun addTransaction(t: Transaction) {
        // Kept for fallback, but NotificationProcessor will use addTransactionEntity
    }

    fun approveAll(suggestions: Map<String, String>) {
        coroutineScope.launch {
            _transactions.value.forEach {
                if (it.status == "review") {
                    val cat = suggestions[it.id] ?: "💸 Personal Transfer"
                    dao.confirmTransaction(it.id, cat)
                }
            }
        }
    }
}

// ── VIEWMODELS ─────────────────────────────────────────────────────────────
data class CashFlowChartData(
    val labels: List<String>,
    val values: List<Float>,
    val totalFormatted: String
)

data class TopCategoryData(
    val label: String,
    val subText: String
)

data class UpiSourcesData(
    val label: String,
    val subText: String
)

class DashboardViewModel : ViewModel() {
    val transactions: StateFlow<List<Transaction>> = TransactionRepository.transactions

    companion object {
        fun isConfirmedOutgoingExpense(t: Transaction): Boolean {
            if (!t.status.equals("confirmed", ignoreCase = true)) return false
            val lowerStatus = t.status.lowercase(java.util.Locale.US)
            if (lowerStatus == "review" || lowerStatus == "ignored" || lowerStatus == "failed" || lowerStatus == "refund" || lowerStatus == "incoming" || lowerStatus == "duplicate") {
                return false
            }
            val lowerDesc = "${t.merchant} ${t.detectionReason} ${t.notificationExcerpt}".lowercase(java.util.Locale.US)
            if (lowerDesc.contains("refund") || lowerDesc.contains("incoming payment") || lowerDesc.contains("credit received") || lowerDesc.contains("failed") || lowerDesc.contains("duplicate")) {
                return false
            }
            val amtStr = t.amount.trim()
            if (amtStr.startsWith("+") || amtStr.contains("+₹")) {
                return false
            }
            return true
        }

        fun parseAmount(amountStr: String): Double {
            return amountStr.replace("−₹", "")
                .replace("-₹", "")
                .replace("₹", "")
                .replace("−", "")
                .replace("-", "")
                .replace(",", "")
                .trim()
                .toDoubleOrNull() ?: 0.0
        }

        fun formatSourceName(source: String): String {
            return when (source.lowercase(java.util.Locale.US)) {
                "gpay", "googlepay" -> "GPay"
                "phonepe" -> "PhonePe"
                "paytm" -> "Paytm"
                "bhim" -> "BHIM"
                "banksms", "sms" -> "Bank SMS"
                else -> if (source.isNotBlank()) source.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString() } else "Other"
            }
        }

        fun getCurrentMonthBounds(nowMs: Long = System.currentTimeMillis()): Pair<Long, Long> {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.add(java.util.Calendar.MONTH, 1)
            cal.add(java.util.Calendar.MILLISECOND, -1)
            val end = cal.timeInMillis
            return Pair(start, end)
        }

        fun getPreviousMonthBounds(currentMonthStartMs: Long): Pair<Long, Long> {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = currentMonthStartMs
                add(java.util.Calendar.MILLISECOND, -1)
            }
            val end = cal.timeInMillis
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            return Pair(start, end)
        }

        fun getMondayOfWeek(nowMs: Long = System.currentTimeMillis()): Long {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = nowMs
                firstDayOfWeek = java.util.Calendar.MONDAY
            }
            val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
            val daysFromMon = (dow - java.util.Calendar.MONDAY + 7) % 7
            cal.add(java.util.Calendar.DAY_OF_MONTH, -daysFromMon)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun computeConfirmedOutgoing(list: List<Transaction>): List<Transaction> {
            return list.filter { isConfirmedOutgoingExpense(it) }.distinctBy { it.id }
        }

        fun computeTotalSpent(list: List<Transaction>, nowMs: Long = System.currentTimeMillis()): String {
            val confirmed = computeConfirmedOutgoing(list)
            val (start, end) = getCurrentMonthBounds(nowMs)
            val sum = confirmed.filter { it.timestamp in start..end }.sumOf { parseAmount(it.amount) }
            return "₹" + if (sum == 0.0) "0" else String.format(java.util.Locale.US, "%,.2f", sum)
        }

        fun computeVsLastMonth(list: List<Transaction>, nowMs: Long = System.currentTimeMillis()): Pair<String, Color> {
            val confirmed = computeConfirmedOutgoing(list)
            val (thisStart, thisEnd) = getCurrentMonthBounds(nowMs)
            val (lastStart, lastEnd) = getPreviousMonthBounds(thisStart)
            val thisSum = confirmed.filter { it.timestamp in thisStart..thisEnd }.sumOf { parseAmount(it.amount) }
            val lastSum = confirmed.filter { it.timestamp in lastStart..lastEnd }.sumOf { parseAmount(it.amount) }

            return if (lastSum == 0.0) {
                if (thisSum == 0.0) {
                    Pair("0% vs last month", ColorText2)
                } else {
                    Pair("+100% vs last month", ColorAmber)
                }
            } else {
                val pct = ((thisSum - lastSum) / lastSum) * 100.0
                val text = if (pct >= 0) String.format(java.util.Locale.US, "+%.0f%% vs last month", pct) else String.format(java.util.Locale.US, "%.0f%% vs last month", pct)
                val color = if (pct <= 0) ColorGreen else ColorAmber
                Pair(text, color)
            }
        }

        fun computeTopCategory(list: List<Transaction>, nowMs: Long = System.currentTimeMillis()): TopCategoryData {
            val confirmed = computeConfirmedOutgoing(list)
            val (start, end) = getCurrentMonthBounds(nowMs)
            val monthTxns = confirmed.filter { it.timestamp in start..end }
            if (monthTxns.isEmpty()) {
                return TopCategoryData("None", "₹0 · 0 txns")
            }
            val grouped = monthTxns.groupBy { it.category }
            val maxEntry = grouped.maxByOrNull { entry -> entry.value.sumOf { parseAmount(it.amount) } }
            if (maxEntry != null) {
                val catName = maxEntry.key.ifBlank { "Other" }
                val catSum = maxEntry.value.sumOf { parseAmount(it.amount) }
                val count = maxEntry.value.size
                val sumStr = "₹" + if (catSum == 0.0) "0" else String.format(java.util.Locale.US, "%,.0f", catSum)
                return TopCategoryData(catName, "$sumStr · $count " + if (count == 1) "txn" else "txns")
            }
            return TopCategoryData("None", "₹0 · 0 txns")
        }

        fun computeUpiSources(list: List<Transaction>): UpiSourcesData {
            val confirmed = computeConfirmedOutgoing(list)
            val sources = confirmed.map { formatSourceName(it.source) }
                .filter { it.isNotBlank() && !it.equals("other", ignoreCase = true) }
                .distinct()
            if (sources.isEmpty()) {
                return UpiSourcesData("0 Apps", "None")
            }
            val countLabel = if (sources.size == 1) "1 App" else "${sources.size} Apps"
            val namesLabel = sources.joinToString(" · ")
            return UpiSourcesData(countLabel, namesLabel)
        }

        fun computeWeeklyChartData(list: List<Transaction>, nowMs: Long = System.currentTimeMillis()): CashFlowChartData {
            val confirmed = computeConfirmedOutgoing(list)
            val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val mondayMs = getMondayOfWeek(nowMs)
            val values = mutableListOf<Float>()
            for (i in 0..6) {
                val dayStart = mondayMs + i * 86400000L
                val dayEnd = dayStart + 86400000L - 1L
                val daySum = confirmed.filter { it.timestamp in dayStart..dayEnd }.sumOf { parseAmount(it.amount) }.toFloat()
                values.add(daySum)
            }
            val totalSum = values.sum().toDouble()
            val totalFormatted = "₹" + if (totalSum == 0.0) "0" else String.format(java.util.Locale.US, "%,.2f", totalSum)
            return CashFlowChartData(labels, values, totalFormatted)
        }

        fun computeMonthlyChartData(list: List<Transaction>, nowMs: Long = System.currentTimeMillis()): CashFlowChartData {
            val confirmed = computeConfirmedOutgoing(list)
            val labels = listOf("Wk 1", "Wk 2", "Wk 3", "Wk 4", "Wk 5")
            val (monthStart, monthEnd) = getCurrentMonthBounds(nowMs)
            val monthTxns = confirmed.filter { it.timestamp in monthStart..monthEnd }
            val cal = java.util.Calendar.getInstance()
            val sums = FloatArray(5) { 0f }
            monthTxns.forEach { t ->
                cal.timeInMillis = t.timestamp
                val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
                val bucket = when {
                    day <= 7 -> 0
                    day <= 14 -> 1
                    day <= 21 -> 2
                    day <= 28 -> 3
                    else -> 4
                }
                sums[bucket] += parseAmount(t.amount).toFloat()
            }
            val values = sums.toList()
            val totalSum = values.sum().toDouble()
            val totalFormatted = "₹" + if (totalSum == 0.0) "0" else String.format(java.util.Locale.US, "%,.2f", totalSum)
            return CashFlowChartData(labels, values, totalFormatted)
        }
    }

    val totalSpent: StateFlow<String> = transactions.map { list ->
        computeTotalSpent(list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "₹0")

    val vsLastMonth: StateFlow<Pair<String, Color>> = transactions.map { list ->
        computeVsLastMonth(list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair("0% vs last month", ColorText2))

    val topCategory: StateFlow<TopCategoryData> = transactions.map { list ->
        computeTopCategory(list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TopCategoryData("None", "₹0 · 0 txns"))

    val upiSources: StateFlow<UpiSourcesData> = transactions.map { list ->
        computeUpiSources(list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UpiSourcesData("0 Apps", "None"))

    val weeklyChartData: StateFlow<CashFlowChartData> = transactions.map { list ->
        computeWeeklyChartData(list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CashFlowChartData(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f), "₹0"))

    val monthlyChartData: StateFlow<CashFlowChartData> = transactions.map { list ->
        computeMonthlyChartData(list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CashFlowChartData(listOf("Wk 1", "Wk 2", "Wk 3", "Wk 4", "Wk 5"), listOf(0f, 0f, 0f, 0f, 0f), "₹0"))

    val needsReviewAmount: StateFlow<String> = transactions.map { list ->
        val sum = list.filter { !it.status.equals("confirmed", ignoreCase = true) && !it.status.equals("ignored", ignoreCase = true) && it.status.equals("review", ignoreCase = true) }.sumOf { parseAmount(it.amount) }
        "₹" + if (sum == 0.0) "0" else String.format(java.util.Locale.US, "%,.2f", sum)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "₹0")

    val pendingReviewCount: StateFlow<Int> = transactions.map { list ->
        list.count { it.status.equals("review", ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}

class ReceiptsViewModel : ViewModel() {
    val confirmedTransactions: StateFlow<List<Transaction>> = TransactionRepository.transactions.map { list ->
        list.filter { it.status == "confirmed" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class ReviewViewModel : ViewModel() {
    val pendingTransactions: StateFlow<List<Transaction>> = TransactionRepository.transactions.map { list ->
        list.filter { it.status == "review" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingReviewCount: StateFlow<Int> = TransactionRepository.transactions.map { list ->
        list.count { it.status == "review" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun confirmTransaction(id: String, category: String) {
        TransactionRepository.confirmTransaction(id, category)
    }

    fun ignoreTransaction(id: String) {
        TransactionRepository.ignoreTransaction(id)
    }

    fun approveAll() {
        val suggestions = mapOf(
            "TXN004" to "💸 Personal Transfer",
            "TXN007" to "🛒 Groceries",
            "TXN009" to "💸 Personal Transfer"
        )
        TransactionRepository.approveAll(suggestions)
    }
}

// ── PROFILE VIEWMODEL (Phase 2) ───────────────────────────────────────────
class ProfileViewModel : ViewModel() {
    private val _notificationAccessEnabled = MutableStateFlow(false)
    val notificationAccessEnabled: StateFlow<Boolean> = _notificationAccessEnabled.asStateFlow()

    fun refreshPermissionStatus(context: Context) {
        _notificationAccessEnabled.value = isNotificationListenerEnabled(context)
    }

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val target = "${context.packageName}/com.autoexpense.app.notification.AutoExpenseNotificationListener"
        return enabledListeners.contains(target)
    }
}

// ── MAIN ACTIVITY ────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {

    // Request POST_NOTIFICATIONS for Android 13+
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled silently; warnings will retry */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            AutoExpenseTheme {
                MainAppContainer()
            }
        }
    }
}

@Composable
fun MainAppContainer(
    dashboardViewModel: DashboardViewModel = viewModel(),
    receiptsViewModel: ReceiptsViewModel = viewModel(),
    reviewViewModel: ReviewViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel(),
    budgetViewModel: BudgetViewModel = viewModel(),
    exportViewModel: com.autoexpense.app.export.ExportViewModel = viewModel()
) {
    var isUserLoggedIn by remember { mutableStateOf(false) }
    var activeScreen by remember { mutableStateOf("dashboard") }
    // Session-only: once dismissed the card won't re-appear until next launch.
    var setupCardDismissed by remember { mutableStateOf(false) }

    val reviewCount by reviewViewModel.pendingReviewCount.collectAsState()
    val notificationEnabled by profileViewModel.notificationAccessEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Refresh permission status after login and whenever the active screen changes
    // (covers the case where the user returns from Android Settings).
    LaunchedEffect(activeScreen, isUserLoggedIn) {
        if (isUserLoggedIn) profileViewModel.refreshPermissionStatus(context)
    }

    val showNotificationSetupCard = isUserLoggedIn && !notificationEnabled && !setupCardDismissed

    if (!isUserLoggedIn) {
        LoginScreen(onLoginSuccess = { isUserLoggedIn = true })
    } else {
        Scaffold(
            bottomBar = {
                AppBottomBar(
                    activeScreen = activeScreen,
                    onNavigate = { activeScreen = it },
                    reviewCount = reviewCount
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = ColorBg0
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (activeScreen) {
                    "dashboard" -> DashboardScreen(
                        viewModel = dashboardViewModel,
                        onNavigate = { activeScreen = it },
                        showNotificationSetupCard = showNotificationSetupCard,
                        onDismissSetupCard = { setupCardDismissed = true }
                    )
                    "receipts" -> ReceiptsLedgerScreen(viewModel = receiptsViewModel)
                    "review" -> NeedsReviewScreen(
                        viewModel = reviewViewModel,
                        budgetViewModel = budgetViewModel,
                        snackbarHostState = snackbarHostState,
                        onBackToDashboard = { activeScreen = "dashboard" }
                    )
                    "budget" -> BudgetScreen(viewModel = budgetViewModel)
                    "profile" -> ProfileScreen(
                        viewModel = profileViewModel,
                        onNavigateBack = { activeScreen = "dashboard" }
                    )
                    "export" -> com.autoexpense.app.export.ExportScreen(
                        viewModel = exportViewModel,
                        onBackToDashboard = { activeScreen = "dashboard" }
                    )
                }
            }
        }
    }
}

// ── APP BOTTOM BAR ────────────────────────────────────────────────────────
@Composable
fun AppBottomBar(
    activeScreen: String,
    onNavigate: (String) -> Unit,
    reviewCount: Int
) {
    NavigationBar(
        containerColor = ColorBg1,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = activeScreen == "dashboard",
            onClick = { onNavigate("dashboard") },
            icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
            label = { Text("Dashboard") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ColorOrange,
                selectedTextColor = ColorOrange,
                unselectedIconColor = ColorText2,
                unselectedTextColor = ColorText2,
                indicatorColor = ColorOrangeDim
            )
        )
        NavigationBarItem(
            selected = activeScreen == "receipts",
            onClick = { onNavigate("receipts") },
            icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Receipts") },
            label = { Text("Receipts") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ColorOrange,
                selectedTextColor = ColorOrange,
                unselectedIconColor = ColorText2,
                unselectedTextColor = ColorText2,
                indicatorColor = ColorOrangeDim
            )
        )
        NavigationBarItem(
            selected = activeScreen == "review",
            onClick = { onNavigate("review") },
            icon = {
                BadgedBox(
                    badge = {
                        if (reviewCount > 0) {
                            Badge(containerColor = ColorOrange) {
                                Text(reviewCount.toString(), color = Color.White)
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.RateReview, contentDescription = "Needs Review")
                }
            },
            label = { Text("Review") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ColorOrange,
                selectedTextColor = ColorOrange,
                unselectedIconColor = ColorText2,
                unselectedTextColor = ColorText2,
                indicatorColor = ColorOrangeDim
            )
        )
        NavigationBarItem(
            selected = activeScreen == "budget",
            onClick = { onNavigate("budget") },
            icon = { Icon(Icons.Default.AccountBalance, contentDescription = "Budget") },
            label = { Text("Budget") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ColorOrange,
                selectedTextColor = ColorOrange,
                unselectedIconColor = ColorText2,
                unselectedTextColor = ColorText2,
                indicatorColor = ColorOrangeDim
            )
        )
        NavigationBarItem(
            selected = activeScreen == "profile",
            onClick = { onNavigate("profile") },
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
            label = { Text("Profile") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ColorOrange,
                selectedTextColor = ColorOrange,
                unselectedIconColor = ColorText2,
                unselectedTextColor = ColorText2,
                indicatorColor = ColorOrangeDim
            )
        )
    }
}

// ── LOGIN SCREEN ──────────────────────────────────────────────────────────
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("demo@autoexpense.in") }
    var password by remember { mutableStateOf("password") }
    var isSigningIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg0)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(ColorOrangeDim, RoundedCornerShape(14.dp))
                    .border(1.dp, ColorOrange.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Logo",
                    tint = ColorOrange,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "AutoExpense",
                color = ColorText1,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = "Your UPI spends, tracked automatically.",
                color = ColorText2,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = ColorBg1),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ColorBg3),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Welcome back",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorText1
                    )
                    Text(
                        text = "Sign in to your expense dashboard",
                        fontSize = 12.sp,
                        color = ColorText2,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email address") },
                        placeholder = { Text("you@example.com") },
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

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        placeholder = { Text("••••••••") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            isSigningIn = true
                            scope.launch {
                                delay(900)
                                isSigningIn = false
                                onLoginSuccess()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !isSigningIn
                    ) {
                        if (isSigningIn) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text("Sign In", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { onLoginSuccess() },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Or continue with Demo Mode", color = ColorOrange)
                    }
                }
            }
        }
    }
}

// ── DASHBOARD SCREEN ───────────────────────────────────────────────────────────
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigate: (String) -> Unit,
    showNotificationSetupCard: Boolean = false,
    onDismissSetupCard: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var chartType by remember { mutableStateOf("weekly") } // "weekly" or "monthly"

    val transactions by viewModel.transactions.collectAsState()
    val totalSpent by viewModel.totalSpent.collectAsState()
    val vsLastMonth by viewModel.vsLastMonth.collectAsState()
    val topCategory by viewModel.topCategory.collectAsState()
    val upiSources by viewModel.upiSources.collectAsState()
    val weeklyChartData by viewModel.weeklyChartData.collectAsState()
    val monthlyChartData by viewModel.monthlyChartData.collectAsState()
    val needsReviewAmount by viewModel.needsReviewAmount.collectAsState()
    val pendingReviewCount by viewModel.pendingReviewCount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg0)
            .verticalScroll(rememberScrollState())
    ) {
        DashboardTopBar()

        // Phase 2: dismissible notification-access setup card.
        if (showNotificationSetupCard) {
            NotificationSetupCard(
                onEnable = { onNavigate("profile") },
                onDismiss = onDismissSetupCard,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // ACTION ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onNavigate("budget") },
                    border = BorderStroke(1.dp, ColorOrange.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorOrange),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Budget", maxLines = 1, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { onNavigate("export") },
                    border = BorderStroke(1.dp, ColorBg3),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorText2),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = ColorText2, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", maxLines = 1, fontSize = 12.sp)
                }

                Button(
                    onClick = { onNavigate("review") },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(Icons.Default.RateReview, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Review", maxLines = 1, color = Color.White, fontSize = 12.sp)
                }
            }

            // METRICS GRID
            MetricsRow(
                totalSpent = totalSpent,
                vsLastMonthText = vsLastMonth.first,
                vsLastMonthColor = vsLastMonth.second,
                needsReviewAmount = needsReviewAmount,
                reviewCount = pendingReviewCount,
                topCatLabel = topCategory.label,
                topCatSubText = topCategory.subText,
                upiCountLabel = upiSources.label,
                upiNamesLabel = upiSources.subText
            )

            Spacer(modifier = Modifier.height(16.dp))

            // CHART CARD
            val currentChartData = if (chartType == "weekly") weeklyChartData else monthlyChartData
            ChartCard(
                chartData = currentChartData,
                chartType = chartType,
                onTypeChange = { chartType = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // LIVE SEARCH & TRANSACTIONS TABLE
            Text(
                text = "Recent Transactions",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ColorText1,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search transactions...", color = ColorText3) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = ColorText3) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ColorOrange,
                    unfocusedBorderColor = ColorBg3,
                    focusedTextColor = ColorText1,
                    unfocusedTextColor = ColorText1
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            val filteredTransactions = transactions.filter {
                !it.status.equals("ignored", ignoreCase = true) && !it.status.equals("duplicate", ignoreCase = true) && (
                    it.merchant.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true) ||
                    it.source.contains(searchQuery, ignoreCase = true)
                )
            }.sortedByDescending { it.timestamp }

            TransactionTable(filteredTransactions)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DashboardTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorBg1)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(ColorOrangeDim, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("AutoExpense", fontWeight = FontWeight.Bold, color = ColorText1, fontSize = 18.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {}) {
                Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = ColorText2)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ColorOrange),
                contentAlignment = Alignment.Center
            ) {
                Text("AE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun MetricsRow(
    totalSpent: String,
    vsLastMonthText: String,
    vsLastMonthColor: Color,
    needsReviewAmount: String,
    reviewCount: Int,
    topCatLabel: String,
    topCatSubText: String,
    upiCountLabel: String,
    upiNamesLabel: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                label = "Total Spent (Month)",
                value = totalSpent,
                subText = vsLastMonthText,
                subTextColor = vsLastMonthColor,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Needs Review",
                value = needsReviewAmount,
                subText = "$reviewCount pending",
                subTextColor = ColorAmber,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                label = "Top Category",
                value = topCatLabel,
                subText = topCatSubText,
                subTextColor = ColorText2,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "UPI Sources",
                value = upiCountLabel,
                subText = upiNamesLabel,
                subTextColor = ColorText2,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    subText: String,
    subTextColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label.uppercase(), fontSize = 10.sp, color = ColorText3, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ColorText1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subText, fontSize = 11.sp, color = subTextColor)
        }
    }
}

// ── CUSTOM CANVAS CHART CARD ──────────────────────────────────────────────
@Composable
fun ChartCard(
    chartData: CashFlowChartData,
    chartType: String,
    onTypeChange: (String) -> Unit
) {
    val labels = chartData.labels
    val values = chartData.values

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text("CASH FLOW", fontSize = 10.sp, color = ColorText3, fontWeight = FontWeight.Bold)
                    Text(chartData.totalFormatted, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                }

                Row(
                    modifier = Modifier
                        .background(ColorBg3, RoundedCornerShape(6.dp))
                        .padding(2.dp)
                ) {
                    val weeklyBg = if (chartType == "weekly") ColorOrange else Color.Transparent
                    val weeklyText = if (chartType == "weekly") Color.White else ColorText2
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(weeklyBg)
                            .clickable { onTypeChange("weekly") }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Weekly", fontSize = 11.sp, color = weeklyText, fontWeight = FontWeight.Bold)
                    }

                    val monthlyBg = if (chartType == "monthly") ColorOrange else Color.Transparent
                    val monthlyText = if (chartType == "monthly") Color.White else ColorText2
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(monthlyBg)
                            .clickable { onTypeChange("monthly") }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Monthly", fontSize = 11.sp, color = monthlyText, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val W = size.width
                val H = size.height
                val padL = 30.dp.toPx()
                val padR = 10.dp.toPx()
                val padT = 20.dp.toPx()
                val padB = 25.dp.toPx()
                val innerW = W - padL - padR
                val innerH = H - padT - padB

                val gridLineFractions = listOf(0.25f, 0.5f, 0.75f, 1f)
                gridLineFractions.forEach { fraction ->
                    val y = padT + innerH - (fraction * innerH)
                    drawLine(
                        color = Color.White.copy(alpha = 0.05f),
                        start = Offset(padL, y),
                        end = Offset(W - padR, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val maxVal = values.maxOrNull() ?: 0f
                val max = if (maxVal > 0f) maxVal else 1f
                val maxIdx = if (maxVal > 0f) values.indexOf(maxVal) else -1
                val stepX = if (values.isNotEmpty()) innerW / values.size else innerW
                val barW = stepX * 0.5f

                values.forEachIndexed { i, value ->
                    val x = padL + (i * stepX) + (stepX - barW) / 2
                    val barH = (value / max) * innerH
                    val y = padT + innerH - barH
                    val isMax = i == maxIdx

                    val brush = if (isMax) {
                        Brush.verticalGradient(
                            colors = listOf(ColorOrange, ColorOrange.copy(alpha = 0.3f)),
                            startY = y,
                            endY = padT + innerH
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.03f)),
                            startY = y,
                            endY = padT + innerH
                        )
                    }

                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(x, y),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )

                    if (isMax) {
                        drawRoundRect(
                            color = ColorOrange.copy(alpha = 0.15f),
                            topLeft = Offset(x - 2.dp.toPx(), y - 2.dp.toPx()),
                            size = Size(barW + 4.dp.toPx(), barH + 4.dp.toPx()),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 30.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        color = ColorText3,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── TRANSACTION TABLE ─────────────────────────────────────────────────────
@Composable
fun TransactionTable(transactions: List<Transaction>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            transactions.forEach { t ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .drawBehind {
                            drawLine(
                                color = ColorBg3,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(t.merchant, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                        Text(t.sub, fontSize = 11.sp, color = ColorText3)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        val sourceColor = when (t.source) {
                            "gpay" -> Color(0xFF4285F4)
                            "phonepe" -> Color(0xFF5F259F)
                            "paytm" -> Color(0xFF00BAF2)
                            else -> ColorOrange
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(sourceColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(t.source.uppercase(), fontSize = 10.sp, color = ColorText2, fontWeight = FontWeight.Bold)
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1.2f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(t.amount, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                        Text(t.date, fontSize = 10.sp, color = ColorText3)
                    }
                }
            }
        }
    }
}

// ── RECEIPTS LEDGER SCREEN ────────────────────────────────────────────────
@Composable
fun ReceiptsLedgerScreen(viewModel: ReceiptsViewModel) {
    val confirmedTxns by viewModel.confirmedTransactions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg0)
            .padding(16.dp)
    ) {
        Text("Receipts Ledger", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ColorText1)
        Text(
            "All your confirmed UPI transactions in one place.",
            fontSize = 12.sp,
            color = ColorText2,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (confirmedTxns.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(48.dp), tint = ColorText3)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No confirmed transactions yet", color = ColorText2, fontSize = 14.sp)
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TransactionTable(confirmedTxns)
                }
            }
        }
    }
}

// ── NEEDS REVIEW SCREEN ──────────────────────────────────────────────────
@Composable
fun NeedsReviewScreen(
    viewModel: ReviewViewModel,
    budgetViewModel: BudgetViewModel,
    snackbarHostState: SnackbarHostState,
    onBackToDashboard: () -> Unit
) {
    // Session-local set for dedup (cleared when process dies)
    val seenWarningKeys = remember { mutableSetOf<String>() }
    val reviewTxns by viewModel.pendingTransactions.collectAsState()
    var currentSuggestionId by remember { mutableStateOf("") }
    var thinkingForCardId by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg0)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Needs Review", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                Text(
                    "${reviewTxns.size} transactions need attention",
                    fontSize = 12.sp,
                    color = ColorText2
                )
            }

            if (reviewTxns.isNotEmpty()) {
                Button(
                    onClick = {
                        viewModel.approveAll()
                        scope.launch {
                            snackbarHostState.showSnackbar("All expenses categorized successfully")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Approve All", fontSize = 12.sp, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (reviewTxns.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(56.dp), tint = ColorGreen)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("All transactions caught up!", color = ColorText1, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onBackToDashboard) {
                        Text("← Back to Dashboard", color = ColorOrange)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    reviewTxns.forEach { t ->
                        key(t.id) {
                            ReviewCard(
                                txn = t,
                                isThinking = thinkingForCardId == t.id,
                                suggestedChipId = currentSuggestionId,
                                onAiSuggest = {
                                    thinkingForCardId = t.id
                                    scope.launch {
                                        delay(1200)
                                        thinkingForCardId = ""
                                        val lv = t.merchant.toLowerCase()
                                        currentSuggestionId = when {
                                            lv.contains("rahul") -> "rc1_food"
                                            lv.contains("unknown") -> "rc2_grocery"
                                            lv.contains("priya") -> "rc3_personal"
                                            else -> ""
                                        }
                                    }
                                },
                                onConfirm = { selectedCat ->
                                    viewModel.confirmTransaction(t.id, selectedCat)
                                    scope.launch {
                                        // Parse raw amount from stored string e.g. "−₹450" -> 450.0
                                        val rawAmt = t.amount
                                            .replace("−₹", "")
                                            .replace(",", "")
                                            .trim()
                                            .toDoubleOrNull() ?: 0.0

                                        // Small delay so Room flush completes
                                        delay(300)
                                        budgetViewModel.refreshSpending()

                                        val warnings = withContext(Dispatchers.IO) {
                                            BudgetRepositorySingleton.instance.checkBudgetWarnings(
                                                category = selectedCat,
                                                amount   = rawAmt,
                                                seenWarningKeys = seenWarningKeys
                                            )
                                        }

                                        if (warnings.isEmpty()) {
                                            snackbarHostState.showSnackbar("Expense categorized successfully")
                                        } else {
                                            // Show most-severe warning first
                                            val primary = warnings.first()
                                            snackbarHostState.showSnackbar(
                                                message     = primary.message,
                                                actionLabel = if (warnings.size > 1) "More" else null,
                                                duration    = SnackbarDuration.Long
                                            )

                                            // Send Android notification for each warning
                                            warnings.forEachIndexed { idx, w ->
                                                BudgetNotificationHelper.sendWarning(
                                                    context        = context,
                                                    warning        = w,
                                                    notificationId = (w.budgetId * 10 + idx).toInt()
                                                )
                                            }
                                        }
                                    }
                                },
                                onIgnore = {
                                    viewModel.ignoreTransaction(t.id)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Transaction ignored")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewCard(
    txn: Transaction,
    isThinking: Boolean,
    suggestedChipId: String,
    onAiSuggest: () -> Unit,
    onConfirm: (String) -> Unit,
    onIgnore: () -> Unit
) {
    var noteText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(ColorBg3, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                txn.notificationExcerpt.isNotEmpty() -> "🔔"
                                txn.merchant.contains("Rahul") || txn.merchant.contains("Priya") -> "👤"
                                else -> "❓"
                            },
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(txn.merchant, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                        Text("${txn.source.uppercase()} · ${txn.date}", fontSize = 11.sp, color = ColorText3)
                    }
                }
                Text(txn.amount, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorAmber)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hint
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ColorBg3, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                val hint = when {
                    txn.notificationExcerpt.isNotEmpty() ->
                        "📱 \"${txn.notificationExcerpt}\"\n${txn.detectionReason}"
                    txn.merchant.contains("Rahul") -> "💡 This looks like a person-to-person transfer. What was it for?"
                    txn.merchant.contains("Unknown") -> "💡 We couldn't identify this merchant. Add a note to categorize it."
                    else -> "💡 P2P transfer detected. What was the purpose?"
                }
                Text(hint, color = ColorText2, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Input and AI suggestion
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Describe this (e.g. dinner)", color = ColorText3, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorOrange,
                        unfocusedBorderColor = ColorBg3,
                        focusedTextColor = ColorText1,
                        unfocusedTextColor = ColorText1
                    ),
                    modifier = Modifier.weight(1.5f)
                )

                Button(
                    onClick = onAiSuggest,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOrangeDim),
                    border = BorderStroke(1.dp, ColorOrange.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isThinking) {
                        CircularProgressIndicator(color = ColorOrange, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("🤖 AI Suggest", fontSize = 11.sp, color = ColorOrange, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Categories Chips
            val chips = when {
                txn.merchant.contains("Rahul") -> listOf(
                    "🍔 Food & Dining" to "rc1_food",
                    "🎉 Entertainment" to "rc1_ent",
                    "🏠 Rent / Bills" to "rc1_rent",
                    "💸 Personal Transfer" to "rc1_personal"
                )
                txn.merchant.contains("Unknown") -> listOf(
                    "🛒 Groceries" to "rc2_grocery",
                    "🍔 Food & Dining" to "rc2_food",
                    "💊 Healthcare" to "rc2_health",
                    "🚗 Transport" to "rc2_trans"
                )
                else -> listOf(
                    "✈️ Travel" to "rc3_travel",
                    "🍔 Food & Dining" to "rc3_food",
                    "💸 Personal Transfer" to "rc3_personal"
                )
            }

            // Pre-select AI suggestion automatically if ready
            LaunchedEffect(suggestedChipId) {
                val matchingChip = chips.find { it.second == suggestedChipId }
                if (matchingChip != null) {
                    selectedCategory = matchingChip.first
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chips.forEach { chip ->
                    val isSelected = selectedCategory == chip.first
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) ColorOrangeDim else ColorBg3)
                            .border(1.dp, if (isSelected) ColorOrange else ColorBg3, CircleShape)
                            .clickable { selectedCategory = chip.first }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(chip.first, fontSize = 11.sp, color = if (isSelected) ColorOrange else ColorText2)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Confirm/Ignore Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onIgnore,
                    enabled = !isConfirming,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Ignore", color = ColorText3)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (selectedCategory.isNotEmpty() && !isConfirming) {
                            isConfirming = true
                            scope.launch {
                                delay(600)
                                onConfirm(selectedCategory)
                                isConfirming = false
                            }
                        }
                    },
                    enabled = selectedCategory.isNotEmpty() && !isConfirming,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    if (isConfirming) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Confirm Category", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable FlowRowScope.() -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = content
    )
}


// ── NOTIFICATION SETUP CARD (Phase 2) ──────────────────────────────────────
@Composable
fun NotificationSetupCard(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ColorOrange.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = ColorOrange,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Enable automatic expense detection",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorText1
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Allow AutoExpense to detect payment notifications from supported payment applications.",
                fontSize = 12.sp,
                color = ColorText2
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onEnable,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Enable Access", fontSize = 12.sp, color = Color.White)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Not Now", fontSize = 12.sp, color = ColorText3)
                }
            }
        }
    }
}

// ── PROFILE SCREEN (Phase 2) ──────────────────────────────────────────────────
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationEnabled by viewModel.notificationAccessEnabled.collectAsState()

    // Refresh permission status every time this screen resumes (after returning from Settings).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(ColorOrangeDim, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = ColorOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Profile",
                fontWeight = FontWeight.Bold,
                color = ColorText1,
                fontSize = 18.sp
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {

            // ── Notification Access section ────────────────────────────────
            Text(
                "NOTIFICATION ACCESS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ColorText3,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = ColorBg2),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ColorBg3),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    // Status row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (notificationEnabled) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ColorGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Enabled",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorGreen
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsOff,
                                contentDescription = null,
                                tint = ColorAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Not Enabled",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorAmber
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        if (notificationEnabled)
                            "Automatic expense detection is active."
                        else
                            "Allow AutoExpense to detect payment notifications and organize your expenses automatically.",
                        fontSize = 12.sp,
                        color = ColorText2
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (notificationEnabled) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                )
                            },
                            border = BorderStroke(1.dp, ColorBg3),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorText2),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Manage Access", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                context.startActivity(
                                    android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "Enable Notification Access",
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Debug-only notification simulator (stripped in release) ──────
            if (BuildConfig.DEBUG) {
                Text(
                    "DEBUG - NOTIFICATION SIMULATOR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorText3,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = ColorBg2),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ColorBlue.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = ColorBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Simulate payment notifications",
                                fontSize = 12.sp,
                                color = ColorBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Inject test transactions into Needs Review without a real notification.",
                            fontSize = 11.sp,
                            color = ColorText3
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Test case 1 – GPay, outgoing, should enter Needs Review
                        DebugTestButton(
                            label = "GPay: Rs. 450 to Swiggy  [ADDS]",
                            isIgnored = false,
                            onClick = {
                                NotificationProcessor.simulateNotification(
                                    title = "",
                                    body = "You paid Rs. 450 to Swiggy",
                                    packageName = "com.google.android.apps.nbu.paisa.user"
                                )
                            }
                        )
                        // Test case 2 – PhonePe, outgoing, should enter Needs Review
                        DebugTestButton(
                            label = "PhonePe: Rs. 1,500 sent to Rahul Verma  [ADDS]",
                            isIgnored = false,
                            onClick = {
                                NotificationProcessor.simulateNotification(
                                    title = "",
                                    body = "Rs. 1,500 sent to Rahul Verma",
                                    packageName = "com.phonepe.app"
                                )
                            }
                        )
                        // Test case 3 – Paytm, payment successful, should enter Needs Review
                        DebugTestButton(
                            label = "Paytm: Rs. 280 to Uber was successful  [ADDS]",
                            isIgnored = false,
                            onClick = {
                                NotificationProcessor.simulateNotification(
                                    title = "",
                                    body = "Payment of Rs. 280 to Uber was successful",
                                    packageName = "net.one97.paytm"
                                )
                            }
                        )
                        // Test case 4 – received, should be ignored
                        DebugTestButton(
                            label = "X  You received Rs. 2,000 from Rahul  [IGNORE]",
                            isIgnored = true,
                            onClick = {
                                NotificationProcessor.simulateNotification(
                                    title = "",
                                    body = "You received Rs. 2,000 from Rahul",
                                    packageName = "com.google.android.apps.nbu.paisa.user"
                                )
                            }
                        )
                        // Test case 5 – payment failed, should be ignored
                        DebugTestButton(
                            label = "X  Payment of Rs. 500 failed  [IGNORE]",
                            isIgnored = true,
                            onClick = {
                                NotificationProcessor.simulateNotification(
                                    title = "",
                                    body = "Payment of Rs. 500 failed",
                                    packageName = "net.one97.paytm"
                                )
                            }
                        )
                        // Test case 6 – cashback promo, should be ignored
                        DebugTestButton(
                            label = "X  Cashback promo (no outgoing phrase)  [IGNORE]",
                            isIgnored = true,
                            onClick = {
                                NotificationProcessor.simulateNotification(
                                    title = "",
                                    body = "Get Rs. 100 cashback on your next payment",
                                    packageName = "com.phonepe.app"
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugTestButton(
    label: String,
    isIgnored: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(
            1.dp,
            if (isIgnored) ColorBg4 else ColorOrange.copy(alpha = 0.5f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isIgnored) ColorText3 else ColorOrange
        ),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(label, fontSize = 11.sp)
    }
}
