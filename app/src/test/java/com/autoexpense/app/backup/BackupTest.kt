package com.autoexpense.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private fun createSampleBackupDto(): AutoExpenseBackupFileDto {
        val txConfirmed = TransactionBackupDto(
            id = "tx_1",
            merchantOrRecipient = "Swiggy",
            sub = "Food Delivery",
            amount = "₹450",
            currency = "INR",
            source = "HDFC Bank",
            category = "Food & Dining",
            note = "Dinner",
            status = "confirmed",
            timestamp = 1721070000000L,
            confidence = 0.95f,
            detectionReason = "Exact Regex Match",
            safeNotificationExcerpt = "Spent Rs.450 on Swiggy",
            transactionFingerprint = "fingerprint_1",
            createdAt = 1721070000000L,
            updatedAt = 1721070000000L,
            rawMerchant = "SWIGGY LIMITED",
            paymentMethod = "UPI"
        )

        val txReview = TransactionBackupDto(
            id = "tx_2",
            merchantOrRecipient = "Localshop739",
            sub = "",
            amount = "₹120",
            currency = "INR",
            source = "SBI UPI",
            category = "Shopping",
            note = "",
            status = "review",
            timestamp = 1721073600000L,
            confidence = 0.60f,
            detectionReason = "Needs Review",
            safeNotificationExcerpt = "Paid Rs.120 to LOCALSHOP739@OKSBI",
            transactionFingerprint = "fingerprint_2",
            createdAt = 1721073600000L,
            updatedAt = 1721073600000L,
            rawMerchant = "LOCALSHOP739@OKSBI"
        )

        val budget = BudgetBackupDto(
            id = 1L,
            category = "Food & Dining",
            categoryKey = "food_dining",
            periodType = "monthly",
            limitAmount = 15000.0,
            warningThreshold = 0.8,
            createdAt = 1720000000000L,
            updatedAt = 1720000000000L
        )

        val customCategory = CustomCategoryBackupDto(
            id = 10,
            name = "Gaming",
            iconName = "sports_esports"
        )

        val merchantMemory = MerchantCategoryBackupDto(
            normalizedMerchant = "swiggy",
            merchantName = "Swiggy",
            category = "Food & Dining",
            updatedAt = 1721070000000L
        )

        val learnedAlias = MerchantAliasBackupDto(
            normalizedRawMerchant = "bundltechnologies",
            rawMerchant = "BUNDL TECHNOLOGIES",
            displayName = "Swiggy",
            updatedAt = 1721070000000L
        )

        val preferences = PreferencesBackupDto(
            userName = "Aarav",
            isOnboardingCompleted = true,
            theme = "dark",
            budgetWarningThreshold = 0.75f,
            isHapticFeedbackEnabled = true,
            isPaymentSetupCompleted = true
        )

        return AutoExpenseBackupFileDto(
            backupFormat = "AutoExpense",
            schemaVersion = 1,
            appVersion = "1.0",
            createdAt = "2026-07-15T21:45:00Z",
            data = BackupPayloadDto(
                transactions = listOf(txConfirmed, txReview),
                budgets = listOf(budget),
                customCategories = listOf(customCategory),
                merchantCategories = listOf(merchantMemory),
                merchantAliases = listOf(learnedAlias),
                preferences = preferences
            )
        )
    }

    @Test
    fun testCreateCompleteBackupDtoAndSerialization() {
        val sample = createSampleBackupDto()
        assertEquals("AutoExpense", sample.backupFormat)
        assertEquals(1, sample.schemaVersion)
        assertEquals(2, sample.data.transactions.size)
        assertEquals("confirmed", sample.data.transactions[0].status)
        assertEquals("review", sample.data.transactions[1].status)
        assertEquals("dark", sample.data.preferences.theme)

        val jsonString = BackupCodec.toJson(sample)
        assertTrue(jsonString.contains("\"backupFormat\": \"AutoExpense\""))
        assertTrue(jsonString.contains("\"schemaVersion\": 1"))
        assertTrue(jsonString.contains("\"Swiggy\""))
        assertTrue(jsonString.contains("\"Localshop739\""))
        assertTrue(jsonString.contains("\"Gaming\""))
        assertTrue(jsonString.contains("\"BUNDL TECHNOLOGIES\""))
        assertTrue(jsonString.contains("\"Aarav\""))
    }

    @Test
    fun testValidRestoreAndParseAndValidate() {
        val sample = createSampleBackupDto()
        val jsonString = BackupCodec.toJson(sample)

        val result = BackupCodec.parseAndValidate(jsonString)
        assertTrue("Result must be Success", result is RestoreValidationResult.Success)
        val restored = (result as RestoreValidationResult.Success).backup

        assertEquals("AutoExpense", restored.backupFormat)
        assertEquals(1, restored.schemaVersion)
        assertEquals(2, restored.data.transactions.size)
        assertEquals("Swiggy", restored.data.transactions[0].merchantOrRecipient)
        assertEquals("UPI", restored.data.transactions[0].paymentMethod)
        assertEquals("Localshop739", restored.data.transactions[1].merchantOrRecipient)
        assertEquals("Food & Dining", restored.data.budgets[0].category)
        assertEquals("Gaming", restored.data.customCategories[0].name)
        assertEquals("Swiggy", restored.data.merchantCategories[0].merchantName)
        assertEquals("Swiggy", restored.data.merchantAliases[0].displayName)
        assertEquals("Aarav", restored.data.preferences.userName)
        assertEquals("dark", restored.data.preferences.theme)
        assertEquals(true, restored.data.preferences.isOnboardingCompleted)
    }

    @Test
    fun testReplaceCurrentDataBehaviorAndNoDuplicateRows() {
        val sample = createSampleBackupDto()
        val txEntity = sample.data.transactions[0].toEntity()
        val fromEntityDto = TransactionBackupDto.fromEntity(txEntity)
        assertEquals(sample.data.transactions[0].id, fromEntityDto.id)
        assertEquals(sample.data.transactions[0].amount, fromEntityDto.amount)
    }

    @Test
    fun testInvalidJson() {
        val result = BackupCodec.parseAndValidate("{ malformed json : ")
        assertTrue(result is RestoreValidationResult.Error)
        assertEquals("This is not a valid AutoExpense backup.", (result as RestoreValidationResult.Error).userMessage)
    }

    @Test
    fun testEmptyBackup() {
        val result1 = BackupCodec.parseAndValidate("")
        val result2 = BackupCodec.parseAndValidate("   ")
        assertTrue(result1 is RestoreValidationResult.Error)
        assertTrue(result2 is RestoreValidationResult.Error)
        assertEquals("This is not a valid AutoExpense backup.", (result1 as RestoreValidationResult.Error).userMessage)
    }

    @Test
    fun testWrongBackupFormat() {
        val sample = createSampleBackupDto()
        val json = BackupCodec.toJson(sample).replace("\"AutoExpense\"", "\"WrongApp\"")
        val result = BackupCodec.parseAndValidate(json)
        assertTrue(result is RestoreValidationResult.Error)
        assertEquals("This is not a valid AutoExpense backup.", (result as RestoreValidationResult.Error).userMessage)
    }

    @Test
    fun testUnsupportedOlderAndNewerSchemaHandling() {
        val sample = createSampleBackupDto()
        val jsonNewer = BackupCodec.toJson(sample).replace("\"schemaVersion\": 1", "\"schemaVersion\": 3")
        val resultNewer = BackupCodec.parseAndValidate(jsonNewer)
        assertTrue(resultNewer is RestoreValidationResult.Error)
        assertEquals("This backup was created by a newer version of AutoExpense. Update the app before restoring it.", (resultNewer as RestoreValidationResult.Error).userMessage)

        val jsonOlder = BackupCodec.toJson(sample).replace("\"schemaVersion\": 1", "\"schemaVersion\": 0")
        val resultOlder = BackupCodec.parseAndValidate(jsonOlder)
        assertTrue(resultOlder is RestoreValidationResult.Error)
        assertEquals("This is not a valid AutoExpense backup.", (resultOlder as RestoreValidationResult.Error).userMessage)
    }

    @Test
    fun testCorruptedBackup() {
        val sample = createSampleBackupDto()
        val jsonMissingData = BackupCodec.toJson(sample).replace("\"data\": {", "\"corrupted_data\": {")
        val result = BackupCodec.parseAndValidate(jsonMissingData)
        assertTrue(result is RestoreValidationResult.Error)
        assertEquals("This is not a valid AutoExpense backup.", (result as RestoreValidationResult.Error).userMessage)
    }

    @Test
    fun testNoCurrentDataDeletionAfterValidationFailure() {
        val corruptedJson = "{\"backupFormat\":\"AutoExpense\",\"schemaVersion\":1,\"data\":{}}"
        val result = BackupCodec.parseAndValidate(corruptedJson)
        assertTrue("Validation must fail cleanly before any restore logic is executed", result is RestoreValidationResult.Error)
        assertEquals("This is not a valid AutoExpense backup.", (result as RestoreValidationResult.Error).userMessage)
    }
}
