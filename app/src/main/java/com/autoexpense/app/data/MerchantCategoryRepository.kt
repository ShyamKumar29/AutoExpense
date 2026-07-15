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

    suspend fun saveMapping(merchantName: String, category: String) {
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
        val norm = normalizeMerchant(merchantName)
        if (norm.isEmpty()) return null
        val fromMappings = _mappings.value[norm]
        if (fromMappings != null) return fromMappings

        val fromTxn = confirmedTransactions
            .filter { it.status.equals("confirmed", ignoreCase = true) && normalizeMerchant(it.merchant) == norm }
            .maxByOrNull { it.timestamp }?.category
        return fromTxn
    }
}
