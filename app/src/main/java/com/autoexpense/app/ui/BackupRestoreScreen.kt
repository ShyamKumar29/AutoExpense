package com.autoexpense.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoexpense.app.*
import com.autoexpense.app.backup.AutoExpenseBackupFileDto
import com.autoexpense.app.backup.BackupRestoreManager
import com.autoexpense.app.backup.RestoreValidationResult
import com.autoexpense.app.data.UserPreferencesRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BackupRestoreScreen(
    onNavigateBack: () -> Unit,
    onRestoreCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userPrefs = remember { UserPreferencesRepository.getInstance(context) }
    val lastBackupTimestamp by userPrefs.lastBackupTimestamp.collectAsState(initial = 0L)

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingRestoreBackup by remember { mutableStateOf<AutoExpenseBackupFileDto?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            errorMessage = null
            scope.launch {
                try {
                    BackupRestoreManager.writeBackupToFile(context, uri)
                    Toast.makeText(context, "Backup saved successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    errorMessage = "Could not create backup file."
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            errorMessage = null
            scope.launch {
                try {
                    when (val result = BackupRestoreManager.validateBackupFile(context, uri)) {
                        is RestoreValidationResult.Success -> {
                            pendingRestoreBackup = result.backup
                        }
                        is RestoreValidationResult.Error -> {
                            errorMessage = result.userMessage
                        }
                    }
                } catch (e: Exception) {
                    errorMessage = "This is not a valid AutoExpense backup."
                } finally {
                    isLoading = false
                }
            }
        }
    }

    fun formatIsoToReadable(isoTime: String): String {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(isoTime)
            if (date != null) {
                val formatter = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US)
                return formatter.format(date)
            }
        } catch (e: Exception) {
            // ignore
        }
        return if (isoTime.isNotBlank()) isoTime else "Unknown"
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    AppHaptic.trigger(context)
                    onNavigateBack()
                },
                modifier = Modifier.size(32.dp),
                enabled = !isLoading
            ) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = ColorText1
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Backup & Restore",
                fontWeight = FontWeight.Bold,
                color = ColorText1,
                fontSize = 18.sp
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ColorOrange)
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {

            // ── LOCAL BACKUP CARD ─────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = ColorBg2),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ColorBg3),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Save, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Local Backup", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Save a complete copy of your AutoExpense data to a file on this device.",
                        fontSize = 13.sp,
                        color = ColorText2
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            AppHaptic.trigger(context)
                            createBackupLauncher.launch(BackupRestoreManager.getSuggestedBackupFileName())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorOrange, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Backup", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Last backup: ${BackupRestoreManager.formatLastBackupTime(lastBackupTimestamp)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorText3
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── RESTORE BACKUP CARD ───────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = ColorBg2),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ColorBg3),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = ColorOrange, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Restore Backup", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorText1)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Restore transactions, budgets, categories, merchant memories, aliases, and app preferences from an AutoExpense backup.",
                        fontSize = 13.sp,
                        color = ColorText2
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            AppHaptic.trigger(context)
                            openBackupLauncher.launch(arrayOf("*/*"))
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorOrange),
                        border = BorderStroke(1.dp, ColorOrange),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose Backup File", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── PRIVACY NOTE CARD ─────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = ColorBg1),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ColorBg3),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.PrivacyTip, contentDescription = null, tint = ColorText3, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Backup files may contain financial information. Store them somewhere private.",
                        fontSize = 12.sp,
                        color = ColorText2,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = {
                Text(text = "Backup Error", fontWeight = FontWeight.Bold, color = ColorText1)
            },
            text = {
                Text(text = errorMessage!!, color = ColorText2)
            },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK", color = ColorOrange, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = ColorBg2
        )
    }

    if (pendingRestoreBackup != null) {
        val backup = pendingRestoreBackup!!
        val txCount = backup.data.transactions.size
        val pendingCount = backup.data.transactions.count { it.status == "review" }
        val budgetsCount = backup.data.budgets.size
        val categoriesCount = backup.data.customCategories.size

        AlertDialog(
            onDismissRequest = { if (!isLoading) pendingRestoreBackup = null },
            title = {
                Text(text = "Restore AutoExpense backup?", fontWeight = FontWeight.Bold, color = ColorText1)
            },
            text = {
                Column {
                    Text(
                        text = "Backup created:\n${formatIsoToReadable(backup.createdAt)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorText1
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Transactions: $txCount", fontSize = 13.sp, color = ColorText2)
                    Text("Pending reviews: $pendingCount", fontSize = 13.sp, color = ColorText2)
                    Text("Budgets: $budgetsCount", fontSize = 13.sp, color = ColorText2)
                    Text("Custom categories: $categoriesCount", fontSize = 13.sp, color = ColorText2)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "This will replace the AutoExpense data currently stored on this device. This action cannot be undone.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorRed
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        AppHaptic.trigger(context)
                        val restoreBackup = pendingRestoreBackup
                        pendingRestoreBackup = null
                        if (restoreBackup != null) {
                            isLoading = true
                            scope.launch {
                                try {
                                    val success = BackupRestoreManager.performRestore(context, restoreBackup)
                                    if (success) {
                                        Toast.makeText(context, "Backup restored successfully", Toast.LENGTH_LONG).show()
                                        onRestoreCompleted()
                                    } else {
                                        errorMessage = "Could not restore backup."
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Could not restore backup."
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorRed),
                    enabled = !isLoading
                ) {
                    Text("Restore Data", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRestoreBackup = null },
                    enabled = !isLoading
                ) {
                    Text("Cancel", color = ColorText2, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = ColorBg2
        )
    }
}
