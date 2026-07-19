package com.autoexpense.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.autoexpense.app.*
import com.autoexpense.app.notification.NotificationHealthRepository
import com.autoexpense.app.notification.SmsPaymentScanner
import kotlinx.coroutines.launch

@Composable
fun PaymentDetectionSetupScreen(
    onComplete: () -> Unit,
    isReviewMode: Boolean = false,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val userPrefs = remember { com.autoexpense.app.data.UserPreferencesRepository.getInstance(context) }
    val isPaymentSetupCompleted by userPrefs.isPaymentSetupCompleted.collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    var isNotificationAccessEnabled by remember {
        mutableStateOf(NotificationHealthRepository.isNotificationListenerEnabled(context))
    }
    var isBatteryExempt by remember {
        mutableStateOf(NotificationHealthRepository.isIgnoringBatteryOptimizations(context))
    }
    var isSmsAccessEnabled by remember {
        mutableStateOf(SmsPaymentScanner.hasSmsPermission(context))
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.READ_SMS] == true &&
            permissions[Manifest.permission.RECEIVE_SMS] == true
        isSmsAccessEnabled = granted || SmsPaymentScanner.hasSmsPermission(context)
        if (isSmsAccessEnabled) {
            scope.launch { SmsPaymentScanner.scanRecent(context) }
        }
    }

    // Refresh states when resuming the screen after user returns from Android settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotificationAccessEnabled = NotificationHealthRepository.isNotificationListenerEnabled(context)
                isBatteryExempt = NotificationHealthRepository.isIgnoringBatteryOptimizations(context)
                isSmsAccessEnabled = SmsPaymentScanner.hasSmsPermission(context)
                if (isSmsAccessEnabled) {
                    scope.launch { SmsPaymentScanner.scanRecent(context) }
                }
                if (isNotificationAccessEnabled && isBatteryExempt) {
                    scope.launch { userPrefs.completePaymentSetup() }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val bgStatus = remember(isNotificationAccessEnabled, isBatteryExempt, isPaymentSetupCompleted) {
        NotificationHealthRepository.getBackgroundDetectionStatus(context, isPaymentSetupCompleted)
    }

    // Detect if device requires manufacturer-specific autostart settings
    val manufacturer = remember { Build.MANUFACTURER.lowercase() }
    val needsAutostartStep = remember(manufacturer) {
        manufacturer.contains("xiaomi") ||
        manufacturer.contains("redmi") ||
        manufacturer.contains("poco") ||
        manufacturer.contains("oppo") ||
        manufacturer.contains("realme") ||
        manufacturer.contains("oneplus") ||
        manufacturer.contains("vivo") ||
        manufacturer.contains("iqoo") ||
        manufacturer.contains("huawei") ||
        manufacturer.contains("honor")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg0)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorBg1)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isReviewMode) {
                IconButton(
                    onClick = {
                        AppHaptic.trigger(context)
                        onNavigateBack()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = ColorText1
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(ColorOrangeDim, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = ColorOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            Text(
                text = "Keep payment detection reliable",
                fontWeight = FontWeight.Bold,
                color = ColorText1,
                fontSize = 24.sp
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Zors uses payment notifications first. Optional SMS access can recover bank debit messages when the SMS app or Truecaller does not show a notification.",
                fontSize = 14.sp,
                color = ColorText2,
                modifier = Modifier.padding(bottom = 20.dp),
                lineHeight = 20.sp
            )

            // ── STEP 1: NOTIFICATION ACCESS ──────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = ColorBg2),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isNotificationAccessEnabled) ColorGreen.copy(alpha = 0.5f) else ColorBg3),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = if (isNotificationAccessEnabled) ColorGreen else ColorOrange,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Notification Access",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorText1,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Required to read payment notifications and create expenses.",
                        fontSize = 13.sp,
                        color = ColorText2,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    if (isNotificationAccessEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ColorGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Notification access enabled",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorGreen
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                AppHaptic.trigger(context)
                                try {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                } catch (e: Exception) {
                                    // Fallback
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text(
                                text = "Enable Notification Access",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── OPTIONAL SMS FALLBACK ────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = ColorBg2),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isSmsAccessEnabled) ColorGreen.copy(alpha = 0.5f) else ColorBg3),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = if (isSmsAccessEnabled) ColorGreen else ColorOrange,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "SMS Payment Fallback",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorText1,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Optional. Zors reads bank debit SMS locally to detect payments when no SMS or Truecaller notification appears. It stores only parsed transaction details and masked excerpts.",
                        fontSize = 13.sp,
                        color = ColorText2,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No unrelated SMS content is saved, uploaded, sold, or used for ads.",
                        fontSize = 12.sp,
                        color = ColorText3,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    if (isSmsAccessEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ColorGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SMS fallback enabled",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorGreen
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                AppHaptic.trigger(context)
                                smsPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_SMS,
                                        Manifest.permission.RECEIVE_SMS
                                    )
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorOrange),
                            border = BorderStroke(1.dp, ColorOrange),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text(
                                text = "Enable SMS Fallback",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorOrange
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── STEP 2: BACKGROUND ACTIVITY (BATTERY OPTIMIZATION) ──────────
            Card(
                colors = CardDefaults.cardColors(containerColor = ColorBg2),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isBatteryExempt) ColorGreen.copy(alpha = 0.5f) else ColorBg3),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.BatteryStd,
                            contentDescription = null,
                            tint = if (isBatteryExempt) ColorGreen else ColorOrange,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Background Activity",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorText1,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Allow Zors to continue detecting payments while the app is closed.",
                        fontSize = 13.sp,
                        color = ColorText2,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    if (isBatteryExempt) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ColorGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Background activity allowed",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorGreen
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                AppHaptic.trigger(context)
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e2: Exception) {
                                        // Ignore if neither is reachable
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text(
                                text = "Allow Background Activity",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // ── STEP 3: AUTOSTART (ONLY FOR RELEVANT MANUFACTURERS) ──────────
            if (needsAutostartStep) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = ColorBg2),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ColorBg3),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.RocketLaunch,
                                contentDescription = null,
                                tint = ColorOrange,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Allow Zors to start automatically",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorText1,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Recommended to ensure background detection after restarting your device.",
                            fontSize = 13.sp,
                            color = ColorText2,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .background(ColorAmber.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Recommended — verification unavailable",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorAmber
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                AppHaptic.trigger(context)
                                val opened = tryOpenManufacturerAutostart(context, manufacturer)
                                if (!opened) {
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorOrange),
                            border = BorderStroke(1.dp, ColorOrange),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text(
                                text = "Open Autostart Settings",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorOrange
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── SETUP COMPLETION CARD & PROCEED BUTTON ────────────────────────
            when (bgStatus) {
                NotificationHealthRepository.BackgroundDetectionStatus.COMPLETED -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ColorGreen.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ColorGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Zors is ready",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorGreen
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Payment detection is configured to work while the app is closed.",
                                fontSize = 13.sp,
                                color = ColorText1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                NotificationHealthRepository.BackgroundDetectionStatus.RECOMMENDED -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ColorAmber.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ColorAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Background activity setup recommended",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorAmber,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Allowing background activity ensures payments are detected reliably when the app is closed.",
                                fontSize = 12.sp,
                                color = ColorText2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                NotificationHealthRepository.BackgroundDetectionStatus.REQUIRED -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ColorAmber.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ColorAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Payment detection is currently disabled.",
                                fontSize = 13.sp,
                                color = ColorText1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "You can enable notification access anytime later in Settings or Profile.",
                                fontSize = 12.sp,
                                color = ColorText2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            Button(
                onClick = {
                    AppHaptic.trigger(context)
                    onComplete()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    text = if (isReviewMode) "Done" else "Open Dashboard",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun tryOpenManufacturerAutostart(context: Context, manufacturer: String): Boolean {
    val intentsToTry = mutableListOf<Intent>()

    when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
            intentsToTry.add(Intent().apply {
                component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            })
        }
        manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
            intentsToTry.add(Intent().apply {
                component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            })
            intentsToTry.add(Intent().apply {
                component = ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
            })
        }
        manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
            intentsToTry.add(Intent().apply {
                component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            })
            intentsToTry.add(Intent().apply {
                component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
            })
        }
        manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
            intentsToTry.add(Intent().apply {
                component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            })
            intentsToTry.add(Intent().apply {
                component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            })
        }
    }

    for (intent in intentsToTry) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            continue
        }
    }
    return false
}
