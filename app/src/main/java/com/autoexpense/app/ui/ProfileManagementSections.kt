package com.autoexpense.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.autoexpense.app.ColorAmber
import com.autoexpense.app.ColorBg0
import com.autoexpense.app.ColorBg1
import com.autoexpense.app.ColorBg2
import com.autoexpense.app.ColorBg3
import com.autoexpense.app.ColorGreen
import com.autoexpense.app.ColorOrange
import com.autoexpense.app.ColorRed
import com.autoexpense.app.ColorText1
import com.autoexpense.app.ColorText2
import com.autoexpense.app.ColorText3
import com.autoexpense.app.ProfileViewModel
import com.autoexpense.app.TransactionRepository
import com.autoexpense.app.data.AutoExpenseDatabase
import com.autoexpense.app.data.MerchantAliasRepository
import com.autoexpense.app.data.MerchantCategoryRepository
import com.autoexpense.app.data.UserPreferencesRepository
import com.autoexpense.app.notification.NotificationHealthRepository
import com.autoexpense.app.ui.AppHaptic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PaymentDetectionSection(
    userPrefs: UserPreferencesRepository,
    profileViewModel: ProfileViewModel,
    onOpenPaymentSetup: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val notificationEnabled by profileViewModel.notificationAccessEnabled.collectAsState()
    val setupComplete by userPrefs.isPaymentSetupCompleted.collectAsState(initial = false)
    var batteryExempt by remember { mutableStateOf(NotificationHealthRepository.isIgnoringBatteryOptimizations(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                profileViewModel.refreshPermissionStatus(context)
                batteryExempt = NotificationHealthRepository.isIgnoringBatteryOptimizations(context)
                if (NotificationHealthRepository.isNotificationListenerEnabled(context) && batteryExempt) {
                    scope.launch { userPrefs.completePaymentSetup() }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val status = remember(notificationEnabled, batteryExempt, setupComplete) {
        NotificationHealthRepository.getBackgroundDetectionStatus(context, setupComplete)
    }

    ProfileSectionHeader("PAYMENT DETECTION")
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            StatusRow("Notification Access", if (notificationEnabled) "Enabled" else "Disabled", if (notificationEnabled) ColorGreen else ColorAmber)
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = ColorBg3)
            val background = when (status) {
                NotificationHealthRepository.BackgroundDetectionStatus.COMPLETED -> "Completed" to ColorGreen
                NotificationHealthRepository.BackgroundDetectionStatus.RECOMMENDED -> "Setup recommended" to ColorAmber
                NotificationHealthRepository.BackgroundDetectionStatus.REQUIRED -> "Setup required" to ColorRed
            }
            StatusRow("Background Detection", background.first, background.second)
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = ColorBg3)
            ManagementAction(Icons.Outlined.Security, "Review Payment Detection Setup", ColorOrange, onOpenPaymentSetup)
        }
    }
}

@Composable
fun BudgetPreferencesSection(userPrefs: UserPreferencesRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val threshold by userPrefs.budgetWarningThreshold.collectAsState(initial = 0.7f)
    val hapticsEnabled by userPrefs.isHapticFeedbackEnabled.collectAsState(initial = true)

    ProfileSectionHeader("BUDGET PREFERENCES")
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Tune, null, tint = ColorOrange, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Budget warning threshold", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorText1)
            }
            Spacer(Modifier.height(4.dp))
            Text("Selected threshold for future budget warnings", fontSize = 12.sp, color = ColorText2)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.5f to "50%", 0.6f to "60%", 0.7f to "70%", 0.8f to "80%", 0.9f to "90%").forEach { (value, label) ->
                    val selected = kotlin.math.abs(threshold - value) < 0.02f
                    Box(
                        Modifier.weight(1f).height(34.dp)
                            .background(if (selected) ColorOrange else ColorBg1, RoundedCornerShape(6.dp))
                            .border(1.dp, if (selected) ColorOrange else ColorBg3, RoundedCornerShape(6.dp))
                            .clickable { AppHaptic.trigger(context); scope.launch { userPrefs.saveBudgetWarningThreshold(value) } },
                        contentAlignment = Alignment.Center
                    ) { Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) Color.White else ColorText2) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = ColorBg3)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Vibration, null, tint = ColorOrange, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Haptic Feedback", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                    Text("Subtle vibration on button taps and actions", fontSize = 12.sp, color = ColorText2)
                }
                Switch(
                    checked = hapticsEnabled,
                    onCheckedChange = { enabled ->
                        AppHaptic.isEnabled = enabled
                        if (enabled) AppHaptic.trigger(context)
                        scope.launch { userPrefs.saveHapticFeedback(enabled) }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ColorOrange, uncheckedThumbColor = ColorText2, uncheckedTrackColor = ColorBg1)
                )
            }
        }
    }
}

@Composable
fun DataManagementSection(
    userPrefs: UserPreferencesRepository,
    onOpenExport: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onDataCleared: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf("") }

    ProfileSectionHeader("DATA")
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorBg2),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ColorBg3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            ManagementAction(Icons.Outlined.FileDownload, "Export Transactions", ColorOrange, onOpenExport)
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = ColorBg3)
            ManagementAction(Icons.Outlined.Save, "Backup & Restore", ColorOrange, onOpenBackupRestore)
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = ColorBg3)
            ManagementAction(Icons.Outlined.DeleteForever, "Clear All Local Data", ColorRed) { confirmation = ""; showClearDialog = true }
        }
    }
    if (showClearDialog) {
        val confirmed = confirmation.trim().equals("Delete all data", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = ColorBg2,
            title = { Text("Clear All Local Data?", fontWeight = FontWeight.Bold, color = ColorText1, fontSize = 18.sp) },
            text = {
                Column {
                    Text("This action will permanently delete all your transactions, budgets, custom categories, merchant aliases, and local preferences from this device. This cannot be undone.", fontSize = 13.sp, color = ColorText2, lineHeight = 18.sp)
                    Spacer(Modifier.height(14.dp))
                    Text("Type \"Delete all data\" to confirm:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmation, onValueChange = { confirmation = it }, singleLine = true,
                        placeholder = { Text("Delete all data", color = ColorText3) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ColorText1, unfocusedTextColor = ColorText1, focusedBorderColor = if (confirmed) ColorRed else ColorOrange, unfocusedBorderColor = ColorBg3, focusedContainerColor = ColorBg1, unfocusedContainerColor = ColorBg1),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) { AutoExpenseDatabase.getDatabase(context).clearAllTables() }
                            MerchantAliasRepository.clearMemoryForTest()
                            MerchantCategoryRepository.isUnknownMerchant("reset")
                            TransactionRepository.init(context)
                            userPrefs.clearAllPreferences()
                            onDataCleared()
                        }
                    },
                    enabled = confirmed,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorRed, disabledContainerColor = ColorRed.copy(alpha = 0.3f), contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Delete Everything", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel", color = ColorText2) } }
        )
    }
}

@Composable
private fun ProfileSectionHeader(title: String) {
    Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ColorText3, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun StatusRow(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 14.sp, color = ColorText1, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun ManagementAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { AppHaptic.trigger(context); onClick() }.padding(vertical = 4.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 14.sp, fontWeight = if (color == ColorRed) FontWeight.Bold else FontWeight.SemiBold, color = if (color == ColorRed) ColorRed else ColorText1, modifier = Modifier.weight(1f))
        if (color != ColorRed) Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, null, tint = ColorText2, modifier = Modifier.size(14.dp))
    }
}
