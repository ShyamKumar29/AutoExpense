package com.autoexpense.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RecurringPaymentRepository {
    private lateinit var dao: RecurringPaymentDao
    private val _items = MutableStateFlow<List<RecurringPaymentEntity>>(emptyList())
    val items: StateFlow<List<RecurringPaymentEntity>> = _items.asStateFlow()

    fun init(recurringPaymentDao: RecurringPaymentDao) {
        dao = recurringPaymentDao
    }

    suspend fun collectItems() {
        dao.observeAll().collect { _items.value = it }
    }

    suspend fun upsertAll(items: List<RecurringPaymentEntity>) {
        if (items.isNotEmpty()) dao.upsertAll(items)
    }

    suspend fun upsert(item: RecurringPaymentEntity) {
        dao.upsert(item)
    }

    suspend fun updateStatus(id: String, status: String) {
        dao.updateStatus(id, status)
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}
