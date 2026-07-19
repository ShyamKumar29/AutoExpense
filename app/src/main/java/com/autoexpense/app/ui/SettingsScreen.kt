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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoexpense.app.*
import com.autoexpense.app.data.UserPreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    userPrefs: UserPreferencesRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectedTheme by userPrefs.theme.collectAsState(initial = "system")
    val userName by userPrefs.userName.collectAsState(initial = "User")
    val smartDetection by userPrefs.isSmartPaymentDetectionEnabled.collectAsState(initial = true)
    val smartAutoMatching by userPrefs.isSmartAutoMatchingEnabled.collectAsState(initial = true)
    val smartAutoMarkPaid by userPrefs.isSmartAutoMarkPaidEnabled.collectAsState(initial = true)
    val smartSuggestions by userPrefs.isSmartSuggestionsEnabled.collectAsState(initial = true)
    val smartDashboardWidget by userPrefs.isSmartDashboardWidgetEnabled.collectAsState(initial = true)
    val smartRecurringNotifications by userPrefs.isSmartRecurringNotificationsEnabled.collectAsState(initial = true)
    val smartAutoPaidNotifications by userPrefs.isSmartAutoPaidNotificationsEnabled.collectAsState(initial = true)

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
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = ColorText1
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Settings",
                fontWeight = FontWeight.Bold,
                color = ColorText1,
                fontSize = 30.sp
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {

            // -- APPEARANCE ---------------------------------------------------
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

            // -- SMART PAYMENTS -----------------------------------------------
            Text(
                "SMART PAYMENTS",
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
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Smart Payments", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SmartPaymentSettingRow("Enable Detection", smartDetection) { scope.launch { userPrefs.saveSmartPaymentDetection(it) } }
                    SmartPaymentSettingRow("Enable Auto Matching", smartAutoMatching) { scope.launch { userPrefs.saveSmartAutoMatching(it) } }
                    SmartPaymentSettingRow("Enable Auto Mark Paid", smartAutoMarkPaid) { scope.launch { userPrefs.saveSmartAutoMarkPaid(it) } }
                    SmartPaymentSettingRow("Enable Suggestions", smartSuggestions) { scope.launch { userPrefs.saveSmartSuggestions(it) } }
                    SmartPaymentSettingRow("Enable Dashboard Widget", smartDashboardWidget) { scope.launch { userPrefs.saveSmartDashboardWidget(it) } }
                    SmartPaymentSettingRow("Notify Recurring Detected", smartRecurringNotifications) { scope.launch { userPrefs.saveSmartRecurringNotifications(it) } }
                    SmartPaymentSettingRow("Notify Auto Paid", smartAutoPaidNotifications, showDivider = false) { scope.launch { userPrefs.saveSmartAutoPaidNotifications(it) } }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // -- ABOUT ---------------------------------------------------------
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
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Profile", fontSize = 14.sp, color = ColorText1, modifier = Modifier.weight(1f))
                        Text(if (userName.isBlank()) "User" else userName, fontSize = 13.sp, color = ColorText2)
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ColorBg3)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PrivacyTip, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Privacy Policy", fontSize = 14.sp, color = ColorText1, modifier = Modifier.weight(1f))
                        Text("Local-first", fontSize = 13.sp, color = ColorText2)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ColorBg3)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Support / Help", fontSize = 14.sp, color = ColorText1, modifier = Modifier.weight(1f))
                        Text("Guide", fontSize = 13.sp, color = ColorText2)
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }

}

@Composable
private fun SmartPaymentSettingRow(
    title: String,
    checked: Boolean,
    showDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(title, fontSize = 14.sp, color = ColorText1, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ColorOrange,
                uncheckedThumbColor = ColorText2,
                uncheckedTrackColor = ColorBg1
            )
        )
    }
    if (showDivider) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = ColorBg3)
    }
}
