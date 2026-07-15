package com.autoexpense.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object MerchantAliasRepository {
    private var dao: MerchantAliasDao? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _aliases = MutableStateFlow<Map<String, String>>(emptyMap())
    val aliases = _aliases.asStateFlow()

    fun normalizeForAlias(raw: String): String {
        return raw.trim().replace("\\s+".toRegex(), " ").lowercase()
    }

    fun init(dao: MerchantAliasDao) {
        this.dao = dao
        coroutineScope.launch {
            dao.observeAll().collect { list ->
                val map = mutableMapOf<String, String>()
                list.forEach { entity ->
                    map[entity.normalizedRawMerchant] = entity.displayName
                }
                _aliases.value = map
            }
        }
    }

    /** Synchronously update memory map for immediate lookup or unit test usage without needing database observe flow. */
    fun setAliasInMemory(rawMerchant: String, displayName: String) {
        val norm = normalizeForAlias(rawMerchant)
        if (norm.isNotEmpty() && displayName.isNotBlank()) {
            val updated = _aliases.value.toMutableMap()
            updated[norm] = displayName.trim()
            _aliases.value = updated
        }
    }

    suspend fun saveAlias(rawMerchant: String, displayName: String) {
        val norm = normalizeForAlias(rawMerchant)
        if (norm.isNotEmpty() && displayName.isNotBlank()) {
            setAliasInMemory(rawMerchant, displayName)
            dao?.insert(
                MerchantAliasEntity(
                    normalizedRawMerchant = norm,
                    rawMerchant = rawMerchant.trim(),
                    displayName = displayName.trim(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun getAlias(rawMerchant: String): String? {
        val norm = normalizeForAlias(rawMerchant)
        if (norm.isEmpty()) return null
        return _aliases.value[norm]
    }

    fun clearMemoryForTest() {
        _aliases.value = emptyMap()
    }
}
