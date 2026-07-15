package com.autoexpense.app.backup

import com.autoexpense.app.data.*

data class TransactionBackupDto(
    val id: String,
    val merchantOrRecipient: String,
    val sub: String,
    val amount: String,
    val currency: String,
    val source: String,
    val category: String,
    val note: String,
    val status: String,
    val timestamp: Long,
    val confidence: Float,
    val detectionReason: String,
    val safeNotificationExcerpt: String,
    val transactionFingerprint: String,
    val createdAt: Long,
    val updatedAt: Long,
    val rawMerchant: String
) {
    fun toEntity(): TransactionEntity = TransactionEntity(
        id = id,
        merchantOrRecipient = merchantOrRecipient,
        sub = sub,
        amount = amount,
        currency = currency,
        source = source,
        category = category,
        note = note,
        status = status,
        timestamp = timestamp,
        confidence = confidence,
        detectionReason = detectionReason,
        safeNotificationExcerpt = safeNotificationExcerpt,
        transactionFingerprint = transactionFingerprint,
        createdAt = createdAt,
        updatedAt = updatedAt,
        rawMerchant = rawMerchant
    )

    companion object {
        fun fromEntity(entity: TransactionEntity): TransactionBackupDto = TransactionBackupDto(
            id = entity.id,
            merchantOrRecipient = entity.merchantOrRecipient,
            sub = entity.sub,
            amount = entity.amount,
            currency = entity.currency,
            source = entity.source,
            category = entity.category,
            note = entity.note,
            status = entity.status,
            timestamp = entity.timestamp,
            confidence = entity.confidence,
            detectionReason = entity.detectionReason,
            safeNotificationExcerpt = entity.safeNotificationExcerpt,
            transactionFingerprint = entity.transactionFingerprint,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            rawMerchant = entity.rawMerchant
        )
    }
}

data class BudgetBackupDto(
    val id: Long,
    val category: String?,
    val categoryKey: String,
    val periodType: String,
    val limitAmount: Double,
    val warningThreshold: Double,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toEntity(): BudgetEntity = BudgetEntity(
        id = id,
        category = category,
        categoryKey = categoryKey,
        periodType = periodType,
        limitAmount = limitAmount,
        warningThreshold = warningThreshold,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromEntity(entity: BudgetEntity): BudgetBackupDto = BudgetBackupDto(
            id = entity.id,
            category = entity.category,
            categoryKey = entity.categoryKey,
            periodType = entity.periodType,
            limitAmount = entity.limitAmount,
            warningThreshold = entity.warningThreshold,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}

data class CustomCategoryBackupDto(
    val id: Int,
    val name: String,
    val iconName: String
) {
    fun toEntity(): CustomCategoryEntity = CustomCategoryEntity(
        id = id,
        name = name,
        iconName = iconName
    )

    companion object {
        fun fromEntity(entity: CustomCategoryEntity): CustomCategoryBackupDto = CustomCategoryBackupDto(
            id = entity.id,
            name = entity.name,
            iconName = entity.iconName
        )
    }
}

data class MerchantCategoryBackupDto(
    val normalizedMerchant: String,
    val merchantName: String,
    val category: String,
    val updatedAt: Long
) {
    fun toEntity(): MerchantCategoryMappingEntity = MerchantCategoryMappingEntity(
        normalizedMerchant = normalizedMerchant,
        merchantName = merchantName,
        category = category,
        updatedAt = updatedAt
    )

    companion object {
        fun fromEntity(entity: MerchantCategoryMappingEntity): MerchantCategoryBackupDto = MerchantCategoryBackupDto(
            normalizedMerchant = entity.normalizedMerchant,
            merchantName = entity.merchantName,
            category = entity.category,
            updatedAt = entity.updatedAt
        )
    }
}

data class MerchantAliasBackupDto(
    val normalizedRawMerchant: String,
    val rawMerchant: String,
    val displayName: String,
    val updatedAt: Long
) {
    fun toEntity(): MerchantAliasEntity = MerchantAliasEntity(
        normalizedRawMerchant = normalizedRawMerchant,
        rawMerchant = rawMerchant,
        displayName = displayName,
        updatedAt = updatedAt
    )

    companion object {
        fun fromEntity(entity: MerchantAliasEntity): MerchantAliasBackupDto = MerchantAliasBackupDto(
            normalizedRawMerchant = entity.normalizedRawMerchant,
            rawMerchant = entity.rawMerchant,
            displayName = entity.displayName,
            updatedAt = entity.updatedAt
        )
    }
}

data class PreferencesBackupDto(
    val userName: String,
    val isOnboardingCompleted: Boolean,
    val theme: String,
    val budgetWarningThreshold: Float,
    val isHapticFeedbackEnabled: Boolean,
    val isPaymentSetupCompleted: Boolean
) {
    fun toSnapshot(): PreferencesSnapshot = PreferencesSnapshot(
        userName = userName,
        isOnboardingCompleted = isOnboardingCompleted,
        theme = theme,
        budgetWarningThreshold = budgetWarningThreshold,
        isHapticFeedbackEnabled = isHapticFeedbackEnabled,
        isPaymentSetupCompleted = isPaymentSetupCompleted
    )

    companion object {
        fun fromSnapshot(snapshot: PreferencesSnapshot): PreferencesBackupDto = PreferencesBackupDto(
            userName = snapshot.userName,
            isOnboardingCompleted = snapshot.isOnboardingCompleted,
            theme = snapshot.theme,
            budgetWarningThreshold = snapshot.budgetWarningThreshold,
            isHapticFeedbackEnabled = snapshot.isHapticFeedbackEnabled,
            isPaymentSetupCompleted = snapshot.isPaymentSetupCompleted
        )
    }
}

data class BackupPayloadDto(
    val transactions: List<TransactionBackupDto>,
    val budgets: List<BudgetBackupDto>,
    val customCategories: List<CustomCategoryBackupDto>,
    val merchantCategories: List<MerchantCategoryBackupDto>,
    val merchantAliases: List<MerchantAliasBackupDto>,
    val preferences: PreferencesBackupDto
)

data class AutoExpenseBackupFileDto(
    val backupFormat: String = "AutoExpense",
    val schemaVersion: Int = 1,
    val appVersion: String = "1.0",
    val createdAt: String,
    val data: BackupPayloadDto
)
