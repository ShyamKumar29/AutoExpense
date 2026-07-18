package com.autoexpense.app.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.autoexpense.app.data.AutoExpenseDatabase
import com.autoexpense.app.data.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object BackupRestoreManager {

    fun getSuggestedBackupFileName(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
        return "AutoExpense_Backup_${formatter.format(Date())}.aexbackup"
    }

    fun formatLastBackupTime(timestamp: Long): String {
        if (timestamp <= 0L) return "Never"
        val formatter = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US)
        return formatter.format(Date(timestamp))
    }

    suspend fun createBackupDto(context: Context): AutoExpenseBackupFileDto = withContext(Dispatchers.IO) {
        val db = AutoExpenseDatabase.getDatabase(context)
        val userPrefs = UserPreferencesRepository.getInstance(context)

        val transactions = db.transactionDao().getAllTransactions().map { TransactionBackupDto.fromEntity(it) }
        val budgets = db.budgetDao().getAllBudgets().map { BudgetBackupDto.fromEntity(it) }
        val customCategories = db.customCategoryDao().getAll().map { CustomCategoryBackupDto.fromEntity(it) }
        val merchantCategories = db.merchantCategoryDao().getAllMappings().map { MerchantCategoryBackupDto.fromEntity(it) }
        val merchantAliases = db.merchantAliasDao().getAllAliases().map { MerchantAliasBackupDto.fromEntity(it) }
        val bills = db.billDao().getAll().map { BillBackupDto.fromEntity(it) }
        val recurringPayments = db.recurringPaymentDao().getAll().map { RecurringPaymentBackupDto.fromEntity(it) }
        val prefsSnapshot = userPrefs.getPreferencesSnapshot()

        val timeFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        timeFormatter.timeZone = TimeZone.getTimeZone("UTC")
        val createdAt = timeFormatter.format(Date())

        AutoExpenseBackupFileDto(
            backupFormat = "AutoExpense",
            schemaVersion = 2,
            appVersion = "1.0",
            createdAt = createdAt,
            data = BackupPayloadDto(
                transactions = transactions,
                budgets = budgets,
                customCategories = customCategories,
                merchantCategories = merchantCategories,
                merchantAliases = merchantAliases,
                bills = bills,
                recurringPayments = recurringPayments,
                preferences = PreferencesBackupDto.fromSnapshot(prefsSnapshot)
            )
        )
    }

    suspend fun writeBackupToFile(context: Context, outputUri: Uri): Long = withContext(Dispatchers.IO) {
        val backupDto = createBackupDto(context)
        val jsonStr = BackupCodec.toJson(backupDto)
        context.contentResolver.openOutputStream(outputUri, "wt")?.use { stream ->
            stream.write(jsonStr.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Could not open output stream")

        val timestamp = System.currentTimeMillis()
        UserPreferencesRepository.getInstance(context).saveLastBackupTimestamp(timestamp)
        timestamp
    }

    suspend fun validateBackupFile(context: Context, inputUri: Uri): RestoreValidationResult = withContext(Dispatchers.IO) {
        try {
            val jsonStr = context.contentResolver.openInputStream(inputUri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
            if (jsonStr == null) {
                RestoreValidationResult.Error("Could not read backup file.")
            } else {
                BackupCodec.parseAndValidate(jsonStr)
            }
        } catch (e: Exception) {
            RestoreValidationResult.Error("Could not read backup file.")
        }
    }

    suspend fun performRestore(context: Context, backupDto: AutoExpenseBackupFileDto): Boolean = withContext(Dispatchers.IO) {
        val db = AutoExpenseDatabase.getDatabase(context)
        val userPrefs = UserPreferencesRepository.getInstance(context)

        try {
            db.withTransaction {
                db.transactionDao().deleteAll()
                db.budgetDao().deleteAll()
                db.customCategoryDao().deleteAll()
                db.merchantCategoryDao().deleteAll()
                db.merchantAliasDao().deleteAll()
                db.billDao().deleteAll()
                db.recurringPaymentDao().deleteAll()

                db.transactionDao().insertAll(backupDto.data.transactions.map { it.toEntity() })
                db.budgetDao().insertAll(backupDto.data.budgets.map { it.toEntity() })
                db.customCategoryDao().insertAll(backupDto.data.customCategories.map { it.toEntity() })
                db.merchantCategoryDao().insertAll(backupDto.data.merchantCategories.map { it.toEntity() })
                db.merchantAliasDao().insertAll(backupDto.data.merchantAliases.map { it.toEntity() })
                db.billDao().insertAll(backupDto.data.bills.map { it.toEntity() })
                db.recurringPaymentDao().upsertAll(backupDto.data.recurringPayments.map { it.toEntity() })
            }
            userPrefs.restorePreferencesSnapshot(backupDto.data.preferences.toSnapshot())
            true
        } catch (e: Exception) {
            false
        }
    }
}
