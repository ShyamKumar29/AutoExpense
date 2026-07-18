package com.autoexpense.app.backup

import org.json.JSONArray
import org.json.JSONObject

sealed class RestoreValidationResult {
    data class Success(val backup: AutoExpenseBackupFileDto) : RestoreValidationResult()
    data class Error(val userMessage: String) : RestoreValidationResult()
}

object BackupCodec {
    fun toJson(backup: AutoExpenseBackupFileDto): String {
        val root = JSONObject()
        root.put("backupFormat", backup.backupFormat)
        root.put("schemaVersion", backup.schemaVersion)
        root.put("appVersion", backup.appVersion)
        root.put("createdAt", backup.createdAt)

        val dataObj = JSONObject()

        val txArray = JSONArray()
        for (tx in backup.data.transactions) {
            val obj = JSONObject()
            obj.put("id", tx.id)
            obj.put("merchantOrRecipient", tx.merchantOrRecipient)
            obj.put("sub", tx.sub)
            obj.put("amount", tx.amount)
            obj.put("currency", tx.currency)
            obj.put("source", tx.source)
            obj.put("category", tx.category)
            obj.put("note", tx.note)
            obj.put("status", tx.status)
            obj.put("timestamp", tx.timestamp)
            obj.put("confidence", tx.confidence.toDouble())
            obj.put("detectionReason", tx.detectionReason)
            obj.put("safeNotificationExcerpt", tx.safeNotificationExcerpt)
            obj.put("transactionFingerprint", tx.transactionFingerprint)
            obj.put("createdAt", tx.createdAt)
            obj.put("updatedAt", tx.updatedAt)
            obj.put("rawMerchant", tx.rawMerchant)
            obj.put("paymentMethod", tx.paymentMethod)
            obj.put("paymentInstrumentId", tx.paymentInstrumentId ?: JSONObject.NULL)
            txArray.put(obj)
        }
        dataObj.put("transactions", txArray)

        val budgetsArray = JSONArray()
        for (b in backup.data.budgets) {
            val obj = JSONObject()
            obj.put("id", b.id)
            obj.put("category", if (b.category != null) b.category else JSONObject.NULL)
            obj.put("categoryKey", b.categoryKey)
            obj.put("periodType", b.periodType)
            obj.put("limitAmount", b.limitAmount)
            obj.put("warningThreshold", b.warningThreshold)
            obj.put("createdAt", b.createdAt)
            obj.put("updatedAt", b.updatedAt)
            budgetsArray.put(obj)
        }
        dataObj.put("budgets", budgetsArray)

        val customCategoriesArray = JSONArray()
        for (c in backup.data.customCategories) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("iconName", c.iconName)
            customCategoriesArray.put(obj)
        }
        dataObj.put("customCategories", customCategoriesArray)

        val mcArray = JSONArray()
        for (mc in backup.data.merchantCategories) {
            val obj = JSONObject()
            obj.put("normalizedMerchant", mc.normalizedMerchant)
            obj.put("merchantName", mc.merchantName)
            obj.put("category", mc.category)
            obj.put("updatedAt", mc.updatedAt)
            mcArray.put(obj)
        }
        dataObj.put("merchantCategories", mcArray)

        val maArray = JSONArray()
        for (ma in backup.data.merchantAliases) {
            val obj = JSONObject()
            obj.put("normalizedRawMerchant", ma.normalizedRawMerchant)
            obj.put("rawMerchant", ma.rawMerchant)
            obj.put("displayName", ma.displayName)
            obj.put("updatedAt", ma.updatedAt)
            maArray.put(obj)
        }
        dataObj.put("merchantAliases", maArray)

        val billsArray = JSONArray()
        for (bill in backup.data.bills) {
            val obj = JSONObject()
            obj.put("id", bill.id)
            obj.put("billType", bill.billType)
            obj.put("provider", bill.provider)
            obj.put("amount", bill.amount)
            obj.put("currency", bill.currency)
            obj.put("dueDate", bill.dueDate ?: JSONObject.NULL)
            obj.put("status", bill.status)
            obj.put("generatedAt", bill.generatedAt)
            obj.put("paidAt", bill.paidAt ?: JSONObject.NULL)
            obj.put("paidTransactionId", bill.paidTransactionId ?: JSONObject.NULL)
            obj.put("source", bill.source)
            obj.put("safeExcerpt", bill.safeExcerpt)
            obj.put("billFingerprint", bill.billFingerprint)
            obj.put("createdAt", bill.createdAt)
            obj.put("updatedAt", bill.updatedAt)
            billsArray.put(obj)
        }
        dataObj.put("bills", billsArray)

        val recurringArray = JSONArray()
        for (item in backup.data.recurringPayments) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("merchant", item.merchant)
            obj.put("normalizedMerchant", item.normalizedMerchant)
            obj.put("amount", item.amount)
            obj.put("currency", item.currency)
            obj.put("frequency", item.frequency)
            obj.put("lastPaymentAt", item.lastPaymentAt)
            obj.put("nextExpectedAt", item.nextExpectedAt)
            obj.put("status", item.status)
            obj.put("confidence", item.confidence.toDouble())
            obj.put("createdAt", item.createdAt)
            obj.put("updatedAt", item.updatedAt)
            recurringArray.put(obj)
        }
        dataObj.put("recurringPayments", recurringArray)

        val prefsObj = JSONObject()
        prefsObj.put("userName", backup.data.preferences.userName)
        prefsObj.put("isOnboardingCompleted", backup.data.preferences.isOnboardingCompleted)
        prefsObj.put("theme", backup.data.preferences.theme)
        prefsObj.put("budgetWarningThreshold", backup.data.preferences.budgetWarningThreshold.toDouble())
        prefsObj.put("isHapticFeedbackEnabled", backup.data.preferences.isHapticFeedbackEnabled)
        prefsObj.put("isPaymentSetupCompleted", backup.data.preferences.isPaymentSetupCompleted)
        dataObj.put("preferences", prefsObj)

        root.put("data", dataObj)
        return root.toString(2)
    }

    fun parseAndValidate(jsonString: String): RestoreValidationResult {
        if (jsonString.isBlank()) {
            return RestoreValidationResult.Error("This is not a valid AutoExpense backup.")
        }
        val root = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            return RestoreValidationResult.Error("This is not a valid AutoExpense backup.")
        }

        if (!root.has("backupFormat") || root.isNull("backupFormat") || root.optString("backupFormat") != "AutoExpense") {
            return RestoreValidationResult.Error("This is not a valid AutoExpense backup.")
        }

        if (!root.has("schemaVersion") || root.isNull("schemaVersion")) {
            return RestoreValidationResult.Error("This is not a valid AutoExpense backup.")
        }

        val schemaVersion = try {
            root.getInt("schemaVersion")
        } catch (e: Exception) {
            return RestoreValidationResult.Error("This is not a valid AutoExpense backup.")
        }

        if (schemaVersion > 2) {
            return RestoreValidationResult.Error("This backup was created by a newer version of AutoExpense. Update the app before restoring it.")
        }
        if (schemaVersion < 1) {
            return RestoreValidationResult.Error("This is not a valid AutoExpense backup.")
        }

        val appVersion = root.optString("appVersion", "1.0")
        val createdAt = root.optString("createdAt", "")

        if (!root.has("data") || root.isNull("data")) {
            return RestoreValidationResult.Error("This is not a valid AutoExpense backup.")
        }

        val dataObj = try {
            root.getJSONObject("data")
        } catch (e: Exception) {
            return RestoreValidationResult.Error("This is not a valid AutoExpense backup.")
        }

        val requiredKeys = listOf("transactions", "budgets", "customCategories", "merchantCategories", "merchantAliases", "preferences")
        for (key in requiredKeys) {
            if (!dataObj.has(key) || dataObj.isNull(key)) {
                return RestoreValidationResult.Error("This is not a valid AutoExpense backup.")
            }
        }

        try {
            val txArray = dataObj.getJSONArray("transactions")
            val txList = mutableListOf<TransactionBackupDto>()
            for (i in 0 until txArray.length()) {
                val obj = txArray.getJSONObject(i)
                val id = obj.getString("id")
                val merchantOrRecipient = obj.getString("merchantOrRecipient")
                val amount = obj.getString("amount")
                val source = obj.getString("source")
                val category = obj.getString("category")
                val status = obj.getString("status")
                val timestamp = obj.getLong("timestamp")
                if (id.isBlank() || amount.isBlank() || source.isBlank() || category.isBlank() || status.isBlank()) {
                    return RestoreValidationResult.Error("This is not a valid AutoExpense backup.")
                }
                txList.add(
                    TransactionBackupDto(
                        id = id,
                        merchantOrRecipient = merchantOrRecipient,
                        sub = obj.optString("sub", ""),
                        amount = amount,
                        currency = obj.optString("currency", "INR"),
                        source = source,
                        category = category,
                        note = obj.optString("note", ""),
                        status = status,
                        timestamp = timestamp,
                        confidence = obj.optDouble("confidence", 1.0).toFloat(),
                        detectionReason = obj.optString("detectionReason", "Local Backup"),
                        safeNotificationExcerpt = obj.optString("safeNotificationExcerpt", ""),
                        transactionFingerprint = obj.optString("transactionFingerprint", ""),
                        createdAt = obj.optLong("createdAt", timestamp),
                        updatedAt = obj.optLong("updatedAt", timestamp),
                        rawMerchant = obj.optString("rawMerchant", ""),
                        paymentMethod = obj.optString("paymentMethod", "UNKNOWN"),
                        paymentInstrumentId = if (obj.isNull("paymentInstrumentId")) null else obj.optString("paymentInstrumentId", "").ifBlank { null }
                    )
                )
            }

            val budgetsArray = dataObj.getJSONArray("budgets")
            val budgetsList = mutableListOf<BudgetBackupDto>()
            for (i in 0 until budgetsArray.length()) {
                val obj = budgetsArray.getJSONObject(i)
                val categoryKey = obj.getString("categoryKey")
                val periodType = obj.getString("periodType")
                val limitAmount = obj.getDouble("limitAmount")
                if (categoryKey.isBlank() || periodType.isBlank() || limitAmount <= 0) {
                    return RestoreValidationResult.Error("This is not a valid AutoExpense backup.")
                }
                budgetsList.add(
                    BudgetBackupDto(
                        id = obj.optLong("id", 0L),
                        category = if (obj.isNull("category")) null else obj.getString("category"),
                        categoryKey = categoryKey,
                        periodType = periodType,
                        limitAmount = limitAmount,
                        warningThreshold = obj.optDouble("warningThreshold", 0.7),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            val customCategoriesArray = dataObj.getJSONArray("customCategories")
            val customCategoriesList = mutableListOf<CustomCategoryBackupDto>()
            for (i in 0 until customCategoriesArray.length()) {
                val obj = customCategoriesArray.getJSONObject(i)
                val name = obj.getString("name")
                val iconName = obj.getString("iconName")
                if (name.isBlank() || iconName.isBlank()) {
                    return RestoreValidationResult.Error("This is not a valid AutoExpense backup.")
                }
                customCategoriesList.add(
                    CustomCategoryBackupDto(
                        id = obj.optInt("id", 0),
                        name = name,
                        iconName = iconName
                    )
                )
            }

            val mcArray = dataObj.getJSONArray("merchantCategories")
            val mcList = mutableListOf<MerchantCategoryBackupDto>()
            for (i in 0 until mcArray.length()) {
                val obj = mcArray.getJSONObject(i)
                val normalizedMerchant = obj.getString("normalizedMerchant")
                val merchantName = obj.getString("merchantName")
                val category = obj.getString("category")
                if (normalizedMerchant.isBlank() || merchantName.isBlank() || category.isBlank()) {
                    return RestoreValidationResult.Error("This is not a valid AutoExpense backup.")
                }
                mcList.add(
                    MerchantCategoryBackupDto(
                        normalizedMerchant = normalizedMerchant,
                        merchantName = merchantName,
                        category = category,
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            val maArray = dataObj.getJSONArray("merchantAliases")
            val maList = mutableListOf<MerchantAliasBackupDto>()
            for (i in 0 until maArray.length()) {
                val obj = maArray.getJSONObject(i)
                val normalizedRawMerchant = obj.getString("normalizedRawMerchant")
                val rawMerchant = obj.getString("rawMerchant")
                val displayName = obj.getString("displayName")
                if (normalizedRawMerchant.isBlank() || rawMerchant.isBlank() || displayName.isBlank()) {
                    return RestoreValidationResult.Error("This is not a valid AutoExpense backup.")
                }
                maList.add(
                    MerchantAliasBackupDto(
                        normalizedRawMerchant = normalizedRawMerchant,
                        rawMerchant = rawMerchant,
                        displayName = displayName,
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            val billsList = mutableListOf<BillBackupDto>()
            if (dataObj.has("bills") && !dataObj.isNull("bills")) {
                val billsArray = dataObj.getJSONArray("bills")
                for (i in 0 until billsArray.length()) {
                    val obj = billsArray.getJSONObject(i)
                    billsList.add(
                        BillBackupDto(
                            id = obj.getString("id"),
                            billType = obj.getString("billType"),
                            provider = obj.getString("provider"),
                            amount = obj.getDouble("amount"),
                            currency = obj.optString("currency", "INR"),
                            dueDate = if (obj.isNull("dueDate")) null else obj.getLong("dueDate"),
                            status = obj.getString("status"),
                            generatedAt = obj.getLong("generatedAt"),
                            paidAt = if (obj.isNull("paidAt")) null else obj.getLong("paidAt"),
                            paidTransactionId = if (obj.isNull("paidTransactionId")) null else obj.getString("paidTransactionId"),
                            source = obj.optString("source", ""),
                            safeExcerpt = obj.optString("safeExcerpt", ""),
                            billFingerprint = obj.getString("billFingerprint"),
                            createdAt = obj.optLong("createdAt", obj.getLong("generatedAt")),
                            updatedAt = obj.optLong("updatedAt", obj.getLong("generatedAt"))
                        )
                    )
                }
            }

            val recurringList = mutableListOf<RecurringPaymentBackupDto>()
            if (dataObj.has("recurringPayments") && !dataObj.isNull("recurringPayments")) {
                val recurringArray = dataObj.getJSONArray("recurringPayments")
                for (i in 0 until recurringArray.length()) {
                    val obj = recurringArray.getJSONObject(i)
                    recurringList.add(
                        RecurringPaymentBackupDto(
                            id = obj.getString("id"),
                            merchant = obj.getString("merchant"),
                            normalizedMerchant = obj.getString("normalizedMerchant"),
                            amount = obj.getDouble("amount"),
                            currency = obj.optString("currency", "INR"),
                            frequency = obj.getString("frequency"),
                            lastPaymentAt = obj.getLong("lastPaymentAt"),
                            nextExpectedAt = obj.getLong("nextExpectedAt"),
                            status = obj.getString("status"),
                            confidence = obj.optDouble("confidence", 0.8).toFloat(),
                            createdAt = obj.optLong("createdAt", obj.getLong("lastPaymentAt")),
                            updatedAt = obj.optLong("updatedAt", obj.getLong("lastPaymentAt"))
                        )
                    )
                }
            }

            val prefsObj = dataObj.getJSONObject("preferences")
            val preferencesDto = PreferencesBackupDto(
                userName = prefsObj.optString("userName", ""),
                isOnboardingCompleted = prefsObj.optBoolean("isOnboardingCompleted", false),
                theme = prefsObj.optString("theme", "system"),
                budgetWarningThreshold = prefsObj.optDouble("budgetWarningThreshold", 0.7).toFloat(),
                isHapticFeedbackEnabled = prefsObj.optBoolean("isHapticFeedbackEnabled", true),
                isPaymentSetupCompleted = prefsObj.optBoolean("isPaymentSetupCompleted", false)
            )

            val payload = BackupPayloadDto(
                transactions = txList,
                budgets = budgetsList,
                customCategories = customCategoriesList,
                merchantCategories = mcList,
                merchantAliases = maList,
                bills = billsList,
                recurringPayments = recurringList,
                preferences = preferencesDto
            )

            val backup = AutoExpenseBackupFileDto(
                backupFormat = "AutoExpense",
                schemaVersion = schemaVersion,
                appVersion = appVersion,
                createdAt = createdAt,
                data = payload
            )

            return RestoreValidationResult.Success(backup)
        } catch (e: Exception) {
            return RestoreValidationResult.Error("This is not a valid AutoExpense backup.")
        }
    }
}
