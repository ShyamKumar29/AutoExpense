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
    val rawMerchant: String,
    val paymentMethod: String = "UNKNOWN",
    val paymentInstrumentId: String? = null
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
        rawMerchant = rawMerchant,
        paymentMethod = paymentMethod,
        paymentInstrumentId = paymentInstrumentId
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
            rawMerchant = entity.rawMerchant,
            paymentMethod = entity.paymentMethod,
            paymentInstrumentId = entity.paymentInstrumentId
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

data class BillBackupDto(
    val id: String,
    val billType: String,
    val provider: String,
    val amount: Double,
    val currency: String,
    val dueDate: Long?,
    val status: String,
    val generatedAt: Long,
    val paidAt: Long?,
    val paidTransactionId: String?,
    val source: String,
    val safeExcerpt: String,
    val billFingerprint: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toEntity(): BillEntity = BillEntity(
        id = id,
        billType = billType,
        provider = provider,
        amount = amount,
        currency = currency,
        dueDate = dueDate,
        status = status,
        generatedAt = generatedAt,
        paidAt = paidAt,
        paidTransactionId = paidTransactionId,
        source = source,
        safeExcerpt = safeExcerpt,
        billFingerprint = billFingerprint,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromEntity(entity: BillEntity): BillBackupDto = BillBackupDto(
            id = entity.id,
            billType = entity.billType,
            provider = entity.provider,
            amount = entity.amount,
            currency = entity.currency,
            dueDate = entity.dueDate,
            status = entity.status,
            generatedAt = entity.generatedAt,
            paidAt = entity.paidAt,
            paidTransactionId = entity.paidTransactionId,
            source = entity.source,
            safeExcerpt = entity.safeExcerpt,
            billFingerprint = entity.billFingerprint,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}

data class RecurringPaymentBackupDto(
    val id: String,
    val merchant: String,
    val normalizedMerchant: String,
    val amount: Double,
    val currency: String,
    val frequency: String,
    val lastPaymentAt: Long,
    val nextExpectedAt: Long,
    val status: String,
    val confidence: Float,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toEntity(): RecurringPaymentEntity = RecurringPaymentEntity(
        id = id,
        merchant = merchant,
        normalizedMerchant = normalizedMerchant,
        amount = amount,
        currency = currency,
        frequency = frequency,
        lastPaymentAt = lastPaymentAt,
        nextExpectedAt = nextExpectedAt,
        status = status,
        confidence = confidence,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromEntity(entity: RecurringPaymentEntity): RecurringPaymentBackupDto = RecurringPaymentBackupDto(
            id = entity.id,
            merchant = entity.merchant,
            normalizedMerchant = entity.normalizedMerchant,
            amount = entity.amount,
            currency = entity.currency,
            frequency = entity.frequency,
            lastPaymentAt = entity.lastPaymentAt,
            nextExpectedAt = entity.nextExpectedAt,
            status = entity.status,
            confidence = entity.confidence,
            createdAt = entity.createdAt,
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
    val isPaymentSetupCompleted: Boolean,
    val isSmartPaymentDetectionEnabled: Boolean = true,
    val isSmartAutoMatchingEnabled: Boolean = true,
    val isSmartAutoMarkPaidEnabled: Boolean = true,
    val isSmartSuggestionsEnabled: Boolean = true,
    val isSmartDashboardWidgetEnabled: Boolean = true
) {
    fun toSnapshot(): PreferencesSnapshot = PreferencesSnapshot(
        userName = userName,
        isOnboardingCompleted = isOnboardingCompleted,
        theme = theme,
        budgetWarningThreshold = budgetWarningThreshold,
        isHapticFeedbackEnabled = isHapticFeedbackEnabled,
        isPaymentSetupCompleted = isPaymentSetupCompleted,
        isSmartPaymentDetectionEnabled = isSmartPaymentDetectionEnabled,
        isSmartAutoMatchingEnabled = isSmartAutoMatchingEnabled,
        isSmartAutoMarkPaidEnabled = isSmartAutoMarkPaidEnabled,
        isSmartSuggestionsEnabled = isSmartSuggestionsEnabled,
        isSmartDashboardWidgetEnabled = isSmartDashboardWidgetEnabled
    )

    companion object {
        fun fromSnapshot(snapshot: PreferencesSnapshot): PreferencesBackupDto = PreferencesBackupDto(
            userName = snapshot.userName,
            isOnboardingCompleted = snapshot.isOnboardingCompleted,
            theme = snapshot.theme,
            budgetWarningThreshold = snapshot.budgetWarningThreshold,
            isHapticFeedbackEnabled = snapshot.isHapticFeedbackEnabled,
            isPaymentSetupCompleted = snapshot.isPaymentSetupCompleted,
            isSmartPaymentDetectionEnabled = snapshot.isSmartPaymentDetectionEnabled,
            isSmartAutoMatchingEnabled = snapshot.isSmartAutoMatchingEnabled,
            isSmartAutoMarkPaidEnabled = snapshot.isSmartAutoMarkPaidEnabled,
            isSmartSuggestionsEnabled = snapshot.isSmartSuggestionsEnabled,
            isSmartDashboardWidgetEnabled = snapshot.isSmartDashboardWidgetEnabled
        )
    }
}

data class BackupPayloadDto(
    val transactions: List<TransactionBackupDto>,
    val budgets: List<BudgetBackupDto>,
    val customCategories: List<CustomCategoryBackupDto>,
    val merchantCategories: List<MerchantCategoryBackupDto>,
    val merchantAliases: List<MerchantAliasBackupDto>,
    val bills: List<BillBackupDto> = emptyList(),
    val recurringPayments: List<RecurringPaymentBackupDto> = emptyList(),
    val preferences: PreferencesBackupDto
)

data class AutoExpenseBackupFileDto(
    val backupFormat: String = "AutoExpense",
    val schemaVersion: Int = 2,
    val appVersion: String = "1.0",
    val createdAt: String,
    val data: BackupPayloadDto
)
