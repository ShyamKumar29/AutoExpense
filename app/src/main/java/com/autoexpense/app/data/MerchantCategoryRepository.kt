package com.autoexpense.app.data

import com.autoexpense.app.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object MerchantCategoryRepository {
    private var dao: MerchantCategoryDao? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _mappings = MutableStateFlow<Map<String, String>>(emptyMap())
    val mappings = _mappings.asStateFlow()

    fun normalizeMerchant(name: String): String {
        return name.trim().replace("\\s+".toRegex(), " ").lowercase()
    }

    fun init(dao: MerchantCategoryDao) {
        this.dao = dao
        coroutineScope.launch {
            dao.observeAll().collect { list ->
                val map = mutableMapOf<String, String>()
                list.forEach { entity ->
                    map[entity.normalizedMerchant] = entity.category
                }
                _mappings.value = map
            }
        }
    }

    fun isUnknownMerchant(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        val norm = normalizeMerchant(name)
        return norm.isEmpty() || norm == "unknown merchant" || norm == "unknown" || norm == "❓ unknown" || norm.contains("unknown merchant") || norm == "unknown app"
    }

    suspend fun saveMapping(merchantName: String, category: String) {
        if (isUnknownMerchant(merchantName)) return
        val norm = normalizeMerchant(merchantName)
        if (norm.isNotEmpty()) {
            dao?.insert(
                MerchantCategoryMappingEntity(
                    normalizedMerchant = norm,
                    merchantName = merchantName.trim(),
                    category = category,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun getRememberedCategory(merchantName: String, confirmedTransactions: List<Transaction> = emptyList()): String? {
        if (isUnknownMerchant(merchantName)) return null
        val norm = normalizeMerchant(merchantName)
        if (norm.isEmpty()) return null
        val fromMappings = _mappings.value[norm]
        if (fromMappings != null) return fromMappings

        val fromTxn = confirmedTransactions
            .filter {
                it.status.equals("confirmed", ignoreCase = true) &&
                !isUnknownMerchant(it.merchant) &&
                (normalizeMerchant(it.merchant) == norm || (it.rawMerchant.isNotBlank() && !isUnknownMerchant(it.rawMerchant) && normalizeMerchant(it.rawMerchant) == norm))
            }
            .maxByOrNull { it.timestamp }?.category
        return fromTxn
    }
}
