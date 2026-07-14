@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.autoexpense.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.TextUnit
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

    fun confirmTransaction(id: String, category: String, onComplete: (() -> Unit)? = null) {
        coroutineScope.launch {
            dao.confirmTransaction(id, category)
            onComplete?.invoke()
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

    fun approveAll(suggestions: Map<String, String>, onComplete: (() -> Unit)? = null) {
        coroutineScope.launch {
            _transactions.value.forEach {
                if (it.status == "review") {
                    val cat = suggestions[it.id] ?: "Personal Transfer"
                    dao.confirmTransaction(it.id, cat)
                }
            }
            onComplete?.invoke()
        }
    }
}

// ── VIEWMODELS ─────────────────────────────────────────────────────────────
data class CashFlowChartData(
    val labels: List<String>,
    val values: List<Float>,
    val totalFormatted: String,
    val counts: List<Int> = emptyList()
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
            val grouped = monthTxns.groupBy { com.autoexpense.app.ui.cleanCategoryName(it.category) }
            val maxEntry = grouped.maxByOrNull { entry -> entry.value.sumOf { parseAmount(it.amount) } }
            if (maxEntry != null) {
                val catName = com.autoexpense.app.ui.cleanCategoryName(maxEntry.key).ifBlank { "Other" }
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
            val counts = mutableListOf<Int>()
            for (i in 0..6) {
                val dayStart = mondayMs + i * 86400000L
                val dayEnd = dayStart + 86400000L - 1L
                val dayTxns = confirmed.filter { it.timestamp in dayStart..dayEnd }
                val daySum = dayTxns.sumOf { parseAmount(it.amount) }.toFloat()
                values.add(daySum)
                counts.add(dayTxns.size)
            }
            val totalSum = values.sum().toDouble()
            val totalFormatted = "₹" + if (totalSum == 0.0) "0" else String.format(java.util.Locale.US, "%,.2f", totalSum)
            return CashFlowChartData(labels, values, totalFormatted, counts)
        }

        fun computeMonthlyChartData(list: List<Transaction>, nowMs: Long = System.currentTimeMillis()): CashFlowChartData {
            val confirmed = computeConfirmedOutgoing(list)
            val labels = listOf("Wk 1", "Wk 2", "Wk 3", "Wk 4", "Wk 5")
            val (monthStart, monthEnd) = getCurrentMonthBounds(nowMs)
            val monthTxns = confirmed.filter { it.timestamp in monthStart..monthEnd }
            val cal = java.util.Calendar.getInstance()
            val sums = FloatArray(5) { 0f }
            val counts = IntArray(5) { 0 }
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
                counts[bucket] += 1
            }
            val values = sums.toList()
            val totalSum = values.sum().toDouble()
            val totalFormatted = "₹" + if (totalSum == 0.0) "0" else String.format(java.util.Locale.US, "%,.2f", totalSum)
            return CashFlowChartData(labels, values, totalFormatted, counts.toList())
        }

        fun formatIndianCurrencyValue(amount: Double): String {
            val localeIN = java.util.Locale("en", "IN")
            val formatter = java.text.NumberFormat.getCurrencyInstance(localeIN)
            var s = formatter.format(amount)
            s = s.replace("Rs.", "₹").replace("INR", "₹").replace(" ", "").trim()
            if (!s.startsWith("₹")) {
                val parts = String.format(java.util.Locale.US, "%.2f", amount).split(".")
                val intPart = parts[0]
                val decPart = parts[1]
                val n = intPart.length
                val formattedInt = if (n <= 3) {
                    intPart
                } else {
                    val lastThree = intPart.substring(n - 3)
                    var rest = intPart.substring(0, n - 3)
                    val res = StringBuilder()
                    while (rest.length > 2) {
                        res.insert(0, "," + rest.substring(rest.length - 2))
                        rest = rest.substring(0, rest.length - 2)
                    }
                    if (rest.isNotEmpty()) {
                        res.insert(0, rest)
                    }
                    res.append(",").append(lastThree).toString()
                }
                return "₹$formattedInt.$decPart"
            }
            return s
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

    fun confirmTransaction(id: String, category: String, onComplete: (() -> Unit)? = null) {
        TransactionRepository.confirmTransaction(id, category, onComplete)
    }

    fun ignoreTransaction(id: String) {
        TransactionRepository.ignoreTransaction(id)
    }

    fun approveAll(onComplete: (() -> Unit)? = null) {
        val suggestions = mapOf(
            "TXN004" to "Personal Transfer",
            "TXN007" to "Groceries",
            "TXN009" to "Personal Transfer"
        )
        TransactionRepository.approveAll(suggestions, onComplete)
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
    val context = LocalContext.current
    val userPrefs = remember { com.autoexpense.app.data.UserPreferencesRepository.getInstance(context) }
    val isOnboardingCompleted by userPrefs.isOnboardingCompleted.collectAsState(initial = false)
    val userName by userPrefs.userName.collectAsState(initial = "")
    val scope = rememberCoroutineScope()

    var activeScreen by remember { mutableStateOf("dashboard") }
    // Session-only: once dismissed the card won't re-appear until next launch.
    var setupCardDismissed by remember { mutableStateOf(false) }

    val reviewCount by reviewViewModel.pendingReviewCount.collectAsState()
    val notificationEnabled by profileViewModel.notificationAccessEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Refresh permission status after login and whenever the active screen changes
    // (covers the case where the user returns from Android Settings).
    LaunchedEffect(activeScreen, isOnboardingCompleted) {
        if (isOnboardingCompleted) profileViewModel.refreshPermissionStatus(context)
    }

    val showNotificationSetupCard = isOnboardingCompleted && !notificationEnabled && !setupCardDismissed

    if (!isOnboardingCompleted) {
        com.autoexpense.app.ui.OnboardingScreen(
            onGetStarted = { name ->
                scope.launch {
                    userPrefs.completeOnboarding(name)
                }
            }
        )
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
                androidx.compose.animation.AnimatedContent(
                    targetState = activeScreen,
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(250)) +
                            androidx.compose.animation.slideInVertically(
                                animationSpec = androidx.compose.animation.core.tween(250),
                                initialOffsetY = { it / 35 }
                            )) togetherWith
                            androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                    },
                    label = "screenTransition"
                ) { targetScreen ->
                    when (targetScreen) {
                        "dashboard" -> DashboardScreen(
                            viewModel = dashboardViewModel,
                            onNavigate = { activeScreen = it },
                            userName = userName,
                            onProfileClick = { activeScreen = "profile" },
                            showNotificationSetupCard = showNotificationSetupCard,
                            onDismissSetupCard = { setupCardDismissed = true }
                        )
                        "receipts" -> ReceiptsLedgerScreen(
                            viewModel = receiptsViewModel,
                            onNavigateToExport = { activeScreen = "export" }
                        )
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

// ── DEPRECATED LOGIN SCREEN (Replaced by OnboardingScreen in Phase 2/3) ───
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    LaunchedEffect(Unit) { onLoginSuccess() }
}

// ── DASHBOARD SCREEN ───────────────────────────────────────────────────────────
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigate: (String) -> Unit,
    userName: String = "",
    onProfileClick: () -> Unit = {},
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
        DashboardTopBar(userName = userName, onProfileClick = onProfileClick)

        // Phase 2: dismissible notification-access setup card.
        if (showNotificationSetupCard) {
            NotificationSetupCard(
                onEnable = { onNavigate("profile") },
                onDismiss = onDismissSetupCard,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(12.dp))

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

            SpendingByCategoryCard(
                transactions = transactions,
                periodType = chartType,
                onPeriodChange = { chartType = it }
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
                shape = RoundedCornerShape(12.dp),
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
fun DashboardTopBar(
    userName: String = "",
    onProfileClick: () -> Unit = {}
) {
    val currentMonth = remember {
        java.text.SimpleDateFormat("MMMM", java.util.Locale.US).format(java.util.Date())
    }
    val greeting = remember(userName) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val timeGreeting = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        val namePart = if (userName.isNotBlank() && userName != "User") ", $userName" else ""
        "$timeGreeting$namePart"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorBg1)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            com.autoexpense.app.ui.AutoExpenseLogo(size = 36.dp, tint = ColorOrange)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(greeting, fontWeight = FontWeight.Bold, color = ColorText1, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Here’s your spending overview for $currentMonth.", color = ColorText2, fontSize = 12.sp)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {}) {
                Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = ColorText2)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ColorOrange)
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                val initials = if (userName.isNotBlank() && userName != "User") {
                    userName.trim().take(2).uppercase()
                } else {
                    "AE"
                }
                Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            val cleanTopCat = com.autoexpense.app.ui.cleanCategoryName(topCatLabel)
            val topCatIcon = if (cleanTopCat != "None" && cleanTopCat != "Other" && cleanTopCat.isNotBlank()) {
                com.autoexpense.app.ui.getCategoryIcon(cleanTopCat)
            } else null
            MetricCard(
                label = "Top Category",
                value = cleanTopCat,
                subText = topCatSubText,
                subTextColor = ColorText2,
                modifier = Modifier.weight(1f),
                icon = topCatIcon
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
fun AnimatedAmountText(
    targetFormatted: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color
) {
    val targetAmount = remember(targetFormatted) { DashboardViewModel.parseAmount(targetFormatted) }
    val animatable = remember { androidx.compose.animation.core.Animatable(0f) }
    var lastAnimateTarget by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(targetAmount) {
        if (lastAnimateTarget == null || kotlin.math.abs(targetAmount - (lastAnimateTarget ?: 0.0)) > 0.01) {
            lastAnimateTarget = targetAmount
            if (targetAmount == 0.0) {
                animatable.snapTo(0f)
            } else {
                animatable.snapTo(0f)
                animatable.animateTo(
                    targetValue = targetAmount.toFloat(),
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 850,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )
            }
        }
    }

    val currentVal = animatable.value.toDouble()
    val isExactTarget = lastAnimateTarget == targetAmount && kotlin.math.abs(currentVal - targetAmount) < 0.01
    val displayStr = if (isExactTarget) {
        targetFormatted
    } else {
        DashboardViewModel.formatIndianCurrencyValue(currentVal)
    }

    Text(
        text = displayStr,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color
    )
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    subText: String,
    subTextColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label.uppercase(), fontSize = 10.sp, color = ColorText3, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ColorText1,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (label.equals("Total Spent (Month)", ignoreCase = true)) {
                    AnimatedAmountText(
                        targetFormatted = value,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorText1
                    )
                } else {
                    Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                }
            }
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
    var selectedBarIdx by remember { mutableStateOf<Int?>(null) }

    val barAnimatables = remember(chartType, values) {
        values.map { androidx.compose.animation.core.Animatable(0f) }
    }

    LaunchedEffect(chartType, values) {
        selectedBarIdx = null
        barAnimatables.forEachIndexed { i, anim ->
            launch {
                kotlinx.coroutines.delay(i * 60L)
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 750,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .pointerInput(Unit) {
                    detectTapGestures { selectedBarIdx = null }
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text("CASH FLOW", fontSize = 10.sp, color = ColorText3, fontWeight = FontWeight.Bold)
                    AnimatedAmountText(
                        targetFormatted = chartData.totalFormatted,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorText1
                    )
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
                            .clickable {
                                selectedBarIdx = null
                                onTypeChange("weekly")
                            }
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
                            .clickable {
                                selectedBarIdx = null
                                onTypeChange("monthly")
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Monthly", fontSize = 11.sp, color = monthlyText, fontWeight = FontWeight.Bold)
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedBarIdx != null,
                enter = fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.95f, animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.95f, animationSpec = tween(150))
            ) {
                val idx = selectedBarIdx
                if (idx != null && idx in labels.indices) {
                    val title = if (chartType == "weekly") {
                        when (labels[idx]) {
                            "Mon" -> "Monday"
                            "Tue" -> "Tuesday"
                            "Wed" -> "Wednesday"
                            "Thu" -> "Thursday"
                            "Fri" -> "Friday"
                            "Sat" -> "Saturday"
                            "Sun" -> "Sunday"
                            else -> labels[idx]
                        }
                    } else {
                        when (labels[idx]) {
                            "Wk 1" -> "Week 1 (1st–7th)"
                            "Wk 2" -> "Week 2 (8th–14th)"
                            "Wk 3" -> "Week 3 (15th–21st)"
                            "Wk 4" -> "Week 4 (22nd–28th)"
                            "Wk 5" -> "Week 5 (29th+)"
                            else -> labels[idx]
                        }
                    }
                    val barSum = values.getOrNull(idx)?.toDouble() ?: 0.0
                    val count = chartData.counts.getOrNull(idx) ?: 0
                    val spentStr = DashboardViewModel.formatIndianCurrencyValue(barSum) + " spent"
                    val countStr = "$count " + if (count == 1) "transaction" else "transactions"

                    Card(
                        colors = CardDefaults.cardColors(containerColor = ColorBg1),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ColorOrange.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorOrange)
                                Text(spentStr, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                            }
                            Text(countStr, fontSize = 12.sp, color = ColorText2)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .pointerInput(chartType, chartData, values) {
                        detectTapGestures { tapOffset ->
                            val W = size.width
                            val padL = 30.dp.toPx()
                            val padR = 10.dp.toPx()
                            val innerW = W - padL - padR
                            val stepX = if (values.isNotEmpty()) innerW / values.size else innerW
                            if (tapOffset.x in padL..(W - padR) && stepX > 0) {
                                val clickedIdx = ((tapOffset.x - padL) / stepX).toInt().coerceIn(0, values.size - 1)
                                selectedBarIdx = if (selectedBarIdx == clickedIdx) null else clickedIdx
                            } else {
                                selectedBarIdx = null
                            }
                        }
                    }
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
                    val progress = barAnimatables.getOrNull(i)?.value ?: 1f
                    val x = padL + (i * stepX) + (stepX - barW) / 2
                    val barH = ((value / max) * innerH) * progress
                    val y = padT + innerH - barH
                    val isHighlighted = if (selectedBarIdx != null) i == selectedBarIdx else i == maxIdx

                    val brush = if (isHighlighted) {
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

                    if (isHighlighted) {
                        drawRoundRect(
                            color = ColorOrange.copy(alpha = 0.25f),
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

// ── SPENDING BY CATEGORY CARD ─────────────────────────────────────────────
data class CategorySpendingItem(
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
    val color: Color
)

@Composable
fun SpendingByCategoryCard(
    transactions: List<Transaction>,
    periodType: String,
    onPeriodChange: (String) -> Unit
) {
    val confirmed = remember(transactions) { DashboardViewModel.computeConfirmedOutgoing(transactions) }
    val bounds = remember(periodType) {
        if (periodType == "weekly") {
            val mon = DashboardViewModel.getMondayOfWeek()
            Pair(mon, mon + 7L * 86400000L - 1L)
        } else {
            DashboardViewModel.getCurrentMonthBounds()
        }
    }
    val periodTxns = remember(confirmed, bounds) {
        confirmed.filter { it.timestamp in bounds.first..bounds.second }
    }
    val (items, totalAmount) = remember(periodTxns) {
        if (periodTxns.isEmpty()) {
            Pair(emptyList<CategorySpendingItem>(), 0.0)
        } else {
            val total = periodTxns.sumOf { DashboardViewModel.parseAmount(it.amount) }
            if (total <= 0.0) {
                Pair(emptyList<CategorySpendingItem>(), 0.0)
            } else {
                val grouped = periodTxns.groupBy { com.autoexpense.app.ui.cleanCategoryName(it.category).ifBlank { "Other" } }
                val palette = listOf(
                    ColorOrange, ColorAmber, ColorGreen, Color(0xFF42A5F5),
                    Color(0xFFAB47BC), Color(0xFF26A69A), Color(0xFFEC407A),
                    Color(0xFF7E57C2), Color(0xFF8D6E63), Color(0xFF78909C)
                )
                val computed = grouped.map { (catName, txList) ->
                    val catSum = txList.sumOf { DashboardViewModel.parseAmount(it.amount) }
                    val pct = if (total > 0) ((catSum / total) * 100).toFloat() else 0f
                    Triple(catName, catSum, pct)
                }.filter { it.second > 0.0 }.sortedByDescending { it.second }

                val finalItems = computed.mapIndexed { idx, triple ->
                    CategorySpendingItem(
                        categoryName = triple.first,
                        amount = triple.second,
                        percentage = triple.third,
                        color = palette[idx % palette.size]
                    )
                }
                Pair(finalItems, total)
            }
        }
    }

    val animProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(items) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 850, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("SPENDING BY CATEGORY", fontSize = 10.sp, color = ColorText3, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    val formatInr = "₹" + if (totalAmount == 0.0) "0" else String.format(java.util.Locale.US, "%,.2f", totalAmount)
                    Text(formatInr, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                }
                Row(
                    modifier = Modifier
                        .background(ColorBg3, RoundedCornerShape(8.dp))
                        .padding(2.dp)
                ) {
                    val weeklyBg = if (periodType == "weekly") ColorOrange else Color.Transparent
                    val weeklyText = if (periodType == "weekly") Color.White else ColorText2
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(weeklyBg)
                            .clickable { onPeriodChange("weekly") }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("Weekly", fontSize = 11.sp, color = weeklyText, fontWeight = FontWeight.Bold)
                    }
                    val monthlyBg = if (periodType == "monthly") ColorOrange else Color.Transparent
                    val monthlyText = if (periodType == "monthly") Color.White else ColorText2
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(monthlyBg)
                            .clickable { onPeriodChange("monthly") }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("Monthly", fontSize = 11.sp, color = monthlyText, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No confirmed spending in this period", color = ColorText2, fontSize = 13.sp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(160.dp)) {
                        val strokeW = 22.dp.toPx()
                        var startAngle = -90f
                        items.forEach { item ->
                            val sweepAngle = (item.percentage / 100f) * 360f * animProgress.value
                            if (sweepAngle > 0f) {
                                drawArc(
                                    color = item.color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    style = Stroke(width = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Butt)
                                )
                                startAngle += (item.percentage / 100f) * 360f
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOTAL", fontSize = 10.sp, color = ColorText3, fontWeight = FontWeight.Bold)
                        val formatInr = "₹" + if (totalAmount == 0.0) "0" else String.format(java.util.Locale.US, "%,.2f", totalAmount)
                        Text(formatInr, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(item.color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = com.autoexpense.app.ui.getCategoryIcon(item.categoryName),
                                    contentDescription = null,
                                    tint = ColorText1,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.categoryName,
                                    fontSize = 13.sp,
                                    color = ColorText1,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.1f", item.percentage)}%",
                                    fontSize = 12.sp,
                                    color = ColorText2,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                val amtFormatted = "₹" + if (item.amount == 0.0) "0" else String.format(java.util.Locale.US, "%,.2f", item.amount)
                                Text(
                                    text = amtFormatted,
                                    fontSize = 13.sp,
                                    color = ColorText1,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
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
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            transactions.forEach { t ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
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
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val cleanCat = com.autoexpense.app.ui.cleanCategoryName(t.category)
                            Icon(
                                imageVector = com.autoexpense.app.ui.getCategoryIcon(cleanCat),
                                contentDescription = null,
                                tint = ColorText3,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val subLabel = if (t.sub.isNotBlank() && t.sub != cleanCat) "${t.sub} · $cleanCat" else cleanCat
                            Text(subLabel, fontSize = 11.sp, color = ColorText3)
                        }
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
fun ReceiptsLedgerScreen(
    viewModel: ReceiptsViewModel,
    onNavigateToExport: () -> Unit = {}
) {
    val confirmedTxns by viewModel.confirmedTransactions.collectAsState()

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
                Text("Receipts Ledger", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                Text(
                    "All your confirmed UPI transactions in one place.",
                    fontSize = 12.sp,
                    color = ColorText2,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            OutlinedButton(
                onClick = onNavigateToExport,
                border = BorderStroke(1.dp, ColorBg3),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorText2),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = ColorText2, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export", fontSize = 12.sp, color = ColorText2)
            }
        }

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
    var showSuccessAnimation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    if (showSuccessAnimation) {
        LaunchedEffect(showSuccessAnimation) {
            delay(1200)
            showSuccessAnimation = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                            viewModel.approveAll {
                                scope.launch {
                                    showSuccessAnimation = true
                                }
                            }
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
                                        viewModel.confirmTransaction(t.id, selectedCat) {
                                            scope.launch {
                                                showSuccessAnimation = true
                                            }
                                        }
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

        androidx.compose.animation.AnimatedVisibility(
            visible = showSuccessAnimation,
            enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.85f, animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ColorBg2),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, ColorOrange),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val checkAnim = remember { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(showSuccessAnimation) {
                        checkAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(ColorOrange.copy(alpha = 0.2f))
                            .border(1.5.dp, ColorOrange, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .scale(checkAnim.value)
                                .alpha(checkAnim.value)
                        )
                    }
                    Text(
                        text = "Expense added",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorText1
                    )
                }
            }
        }
    }
}

@Composable
fun CreateCategoryDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, iconName: String) -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    val iconOptions = listOf(
        "ShoppingBag" to Icons.Outlined.ShoppingBag,
        "SportsEsports" to Icons.Outlined.SportsEsports,
        "School" to Icons.Outlined.School,
        "FitnessCenter" to Icons.Outlined.FitnessCenter,
        "Subscriptions" to Icons.Outlined.Subscriptions,
        "Paid" to Icons.Outlined.Paid,
        "StarOutline" to Icons.Outlined.StarOutline,
        "FavoriteBorder" to Icons.Outlined.FavoriteBorder,
        "WorkOutline" to Icons.Outlined.WorkOutline,
        "Restaurant" to Icons.Outlined.Restaurant,
        "DirectionsCar" to Icons.Outlined.DirectionsCar,
        "Home" to Icons.Outlined.Home,
        "Flight" to Icons.Outlined.Flight,
        "LocalHospital" to Icons.Outlined.LocalHospital,
        "Movie" to Icons.Outlined.Movie,
        "SwapHoriz" to Icons.Outlined.SwapHoriz
    )
    var selectedIconName by remember { mutableStateOf(iconOptions.first().first) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ColorBg2),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, ColorBg3),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Create Custom Category",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorText1
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Examples: Shopping, Gaming, Education, Fitness, Subscriptions.",
                    fontSize = 12.sp,
                    color = ColorText2
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Category Name", color = ColorText3) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorOrange,
                        unfocusedBorderColor = ColorBg3,
                        focusedTextColor = ColorText1,
                        unfocusedTextColor = ColorText1
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Select Icon",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorText2
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    iconOptions.forEach { (name, vector) ->
                        val isSelected = selectedIconName == name
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) ColorOrange else ColorBg3)
                                .clickable { selectedIconName = name },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = vector,
                                contentDescription = name,
                                tint = if (isSelected) Color.White else ColorText1,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = ColorText2)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val clean = categoryName.trim()
                            if (clean.isNotBlank()) {
                                onCreate(clean, selectedIconName)
                                onDismiss()
                            }
                        },
                        enabled = categoryName.trim().isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Create", color = Color.White, fontWeight = FontWeight.Bold)
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
    var showCreateDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
                        val iconVector = when {
                            txn.notificationExcerpt.isNotEmpty() -> Icons.Outlined.Notifications
                            txn.merchant.contains("Rahul") || txn.merchant.contains("Priya") -> Icons.Outlined.Person
                            else -> Icons.Outlined.HelpOutline
                        }
                        Icon(iconVector, contentDescription = null, tint = ColorText1, modifier = Modifier.size(18.dp))
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
                    .background(ColorBg3, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                val hint = when {
                    txn.notificationExcerpt.isNotEmpty() ->
                        "\"${txn.notificationExcerpt}\"\n${txn.detectionReason}"
                    txn.merchant.contains("Rahul") -> "This looks like a person-to-person transfer. What was it for?"
                    txn.merchant.contains("Unknown") -> "We couldn't identify this merchant. Add a note to categorize it."
                    else -> "P2P transfer detected. What was the purpose?"
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
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1.5f)
                )

                Button(
                    onClick = onAiSuggest,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOrangeDim),
                    border = BorderStroke(1.dp, ColorOrange.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isThinking) {
                        CircularProgressIndicator(color = ColorOrange, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI Suggest", fontSize = 11.sp, color = ColorOrange, maxLines = 1)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Categories Chips
            val customCategories by com.autoexpense.app.data.CustomCategoryRepository.customCategories.collectAsState(
                initial = emptyList()
            )
            val baseChips = when {
                txn.merchant.contains("Rahul") -> listOf(
                    "Food & Dining" to "rc1_food",
                    "Entertainment" to "rc1_ent",
                    "Rent / Bills" to "rc1_rent",
                    "Personal Transfer" to "rc1_personal"
                )
                txn.merchant.contains("Unknown") -> listOf(
                    "Groceries" to "rc2_grocery",
                    "Food & Dining" to "rc2_food",
                    "Healthcare" to "rc2_health",
                    "Transport" to "rc2_trans"
                )
                else -> listOf(
                    "Travel" to "rc3_travel",
                    "Food & Dining" to "rc3_food",
                    "Personal Transfer" to "rc3_personal"
                )
            }
            val chips = baseChips + customCategories.map { it.name to "custom_${it.id}" }

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
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) ColorOrange else ColorBg3)
                            .clickable { selectedCategory = chip.first }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = com.autoexpense.app.ui.getCategoryIcon(chip.first),
                            contentDescription = null,
                            tint = if (isSelected) Color.White else ColorText2,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = chip.first,
                            fontSize = 11.sp,
                            color = if (isSelected) Color.White else ColorText2,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ColorBg3)
                        .border(1.dp, ColorOrange, RoundedCornerShape(8.dp))
                        .clickable { showCreateDialog = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = ColorOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "+ Create category",
                        fontSize = 11.sp,
                        color = ColorOrange,
                        fontWeight = FontWeight.Bold
                    )
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

            if (showCreateDialog) {
                CreateCategoryDialog(
                    onDismiss = { showCreateDialog = false },
                    onCreate = { name, iconName ->
                        com.autoexpense.app.data.CustomCategoryRepository.addCategory(name, iconName) {
                            selectedCategory = name
                        }
                    }
                )
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
