package com.autoexpense.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.autoexpense.app.*
import com.autoexpense.app.data.AutoExpenseDatabase
import com.autoexpense.app.data.MerchantAliasRepository
import com.autoexpense.app.data.MerchantCategoryRepository
import com.autoexpense.app.data.UserPreferencesRepository
import com.autoexpense.app.notification.NotificationHealthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    userPrefs: UserPreferencesRepository,
    profileViewModel: ProfileViewModel,
    onNavigateBack: () -> Unit,
    onOpenPaymentSetup: () -> Unit,
    onOpenExport: () -> Unit,
    onDataCleared: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val selectedTheme by userPrefs.theme.collectAsState(initial = "system")
    val budgetThreshold by userPrefs.budgetWarningThreshold.collectAsState(initial = 0.7f)
    val isHapticEnabled by userPrefs.isHapticFeedbackEnabled.collectAsState(initial = true)
    val notificationEnabled by profileViewModel.notificationAccessEnabled.collectAsState()
    val isPaymentSetupCompleted by userPrefs.isPaymentSetupCompleted.collectAsState(initial = false)

    var isBatteryExempt by remember {
        mutableStateOf(NotificationHealthRepository.isIgnoringBatteryOptimizations(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                profileViewModel.refreshPermissionStatus(context)
                isBatteryExempt = NotificationHealthRepository.isIgnoringBatteryOptimizations(context)
                if (NotificationHealthRepository.isNotificationListenerEnabled(context) && isBatteryExempt) {
                    scope.launch { userPrefs.completePaymentSetup() }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val bgStatus = remember(notificationEnabled, isBatteryExempt, isPaymentSetupCompleted) {
        NotificationHealthRepository.getBackgroundDetectionStatus(context, isPaymentSetupCompleted)
    }

    var showClearDialog by remember { mutableStateOf(false) }
    var clearInputText by remember { mutableStateOf("") }

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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    AppHaptic.trigger(context)
                    onNavigateBack()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = ColorText1
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Settings",
                fontWeight = FontWeight.Bold,
                color = ColorText1,
                fontSize = 18.sp
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {

            // ── APPEARANCE ───────────────────────────────────────────────────
            Text(
                "APPEARANCE",
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Palette, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Theme", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themes = listOf(
                            Pair("system", "System Default"),
                            Pair("light", "Light"),
                            Pair("dark", "Dark")
                        )
                        for ((key, label) in themes) {
                            val isSelected = selectedTheme == key
                            val bgColor = if (isSelected) ColorOrange.copy(alpha = 0.15f) else ColorBg0
                            val borderColor = if (isSelected) ColorOrange else ColorBg3
                            val textColor = if (isSelected) ColorOrange else ColorText2

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(bgColor, RoundedCornerShape(8.dp))
                                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        AppHaptic.trigger(context)
                                        scope.launch { userPrefs.saveTheme(key) }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── PAYMENT DETECTION ─────────────────────────────────────────────
            Text(
                "PAYMENT DETECTION",
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Notification Access", fontSize = 14.sp, color = ColorText1, modifier = Modifier.weight(1f))
                        if (notificationEnabled) {
                            Text("Enabled", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorGreen)
                        } else {
                            Text("Disabled", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorAmber)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ColorBg3)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Background Detection", fontSize = 14.sp, color = ColorText1, modifier = Modifier.weight(1f))
                        when (bgStatus) {
                            NotificationHealthRepository.BackgroundDetectionStatus.COMPLETED -> {
                                Text("Completed", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorGreen)
                            }
                            NotificationHealthRepository.BackgroundDetectionStatus.RECOMMENDED -> {
                                Text("Setup recommended", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorAmber)
                            }
                            NotificationHealthRepository.BackgroundDetectionStatus.REQUIRED -> {
                                Text("Setup required", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorRed)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ColorBg3)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppHaptic.trigger(context)
                                onOpenPaymentSetup()
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Outlined.Security, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Review Payment Detection Setup",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ColorText1,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Outlined.ArrowForwardIos, contentDescription = null, tint = ColorText2, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── PREFERENCES ───────────────────────────────────────────────────
            Text(
                "PREFERENCES",
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Budget warning threshold", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Selected threshold for future budget warnings", fontSize = 12.sp, color = ColorText2)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val thresholds = listOf(0.5f to "50%", 0.6f to "60%", 0.7f to "70%", 0.8f to "80%", 0.9f to "90%")
                        thresholds.forEach { (value, label) ->
                            val isSelected = kotlin.math.abs(budgetThreshold - value) < 0.02f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .background(if (isSelected) ColorOrange else ColorBg1, RoundedCornerShape(6.dp))
                                    .border(1.dp, if (isSelected) ColorOrange else ColorBg3, RoundedCornerShape(6.dp))
                                    .clickable {
                                        AppHaptic.trigger(context)
                                        scope.launch { userPrefs.saveBudgetWarningThreshold(value) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else ColorText2
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = ColorBg3)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Vibration, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Haptic Feedback", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                            Text("Subtle vibration on button taps and actions", fontSize = 12.sp, color = ColorText2)
                        }
                        Switch(
                            checked = isHapticEnabled,
                            onCheckedChange = { checked ->
                                AppHaptic.isEnabled = checked
                                if (checked) AppHaptic.trigger(context)
                                scope.launch { userPrefs.saveHapticFeedback(checked) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ColorOrange,
                                uncheckedThumbColor = ColorText2,
                                uncheckedTrackColor = ColorBg1
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── DATA ──────────────────────────────────────────────────────────
            Text(
                "DATA",
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppHaptic.trigger(context)
                                onOpenExport()
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Export Transactions",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ColorText1,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Outlined.ArrowForwardIos, contentDescription = null, tint = ColorText2, modifier = Modifier.size(14.dp))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ColorBg3)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppHaptic.trigger(context)
                                clearInputText = ""
                                showClearDialog = true
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = ColorRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Clear All Local Data",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── ABOUT ─────────────────────────────────────────────────────────
            Text(
                "ABOUT",
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("App version", fontSize = 14.sp, color = ColorText1, modifier = Modifier.weight(1f))
                        Text("Version ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = ColorText2)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ColorBg3)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PrivacyTip, contentDescription = null, tint = ColorGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "All data stays strictly local on your device.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }

    // ── CLEAR ALL DATA CONFIRMATION DIALOG ────────────────────────────────────
    if (showClearDialog) {
        val isConfirmed = clearInputText.trim().equals("Delete all data", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = ColorBg2,
            title = {
                Text(
                    text = "Clear All Local Data?",
                    fontWeight = FontWeight.Bold,
                    color = ColorText1,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "This action will permanently delete all your transactions, budgets, custom categories, merchant aliases, and local preferences from this device. This cannot be undone.",
                        fontSize = 13.sp,
                        color = ColorText2,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Type \"Delete all data\" to confirm:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorText1
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = clearInputText,
                        onValueChange = { clearInputText = it },
                        placeholder = { Text("Delete all data", color = ColorText3) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ColorText1,
                            unfocusedTextColor = ColorText1,
                            focusedBorderColor = if (isConfirmed) ColorRed else ColorOrange,
                            unfocusedBorderColor = ColorBg3,
                            focusedContainerColor = ColorBg1,
                            unfocusedContainerColor = ColorBg1
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isConfirmed) {
                            AppHaptic.trigger(context)
                            showClearDialog = false
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val db = AutoExpenseDatabase.getDatabase(context)
                                    db.clearAllTables()
                                }
                                MerchantAliasRepository.clearMemoryForTest()
                                com.autoexpense.app.data.MerchantCategoryRepository.isUnknownMerchant("reset")
                                TransactionRepository.init(context)
                                userPrefs.clearAllPreferences()
                                onDataCleared()
                            }
                        }
                    },
                    enabled = isConfirmed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorRed,
                        disabledContainerColor = ColorRed.copy(alpha = 0.3f),
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete Everything", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false }
                ) {
                    Text("Cancel", color = ColorText2)
                }
            }
        )
    }
}
