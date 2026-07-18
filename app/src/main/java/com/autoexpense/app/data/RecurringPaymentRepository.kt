package com.autoexpense.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

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

    suspend fun markMatchingPaymentPaid(
        merchantOrRecipient: String,
        amount: Double,
        paidAt: Long
    ): RecurringPaymentEntity? {
        val normalizedMerchant = normalize(merchantOrRecipient)
        val match = dao.getActive().firstOrNull { item ->
            val merchantMatch = item.normalizedMerchant.contains(normalizedMerchant) ||
                normalizedMerchant.contains(item.normalizedMerchant)
            val amountTolerance = maxOf(25.0, item.amount * 0.15)
            merchantMatch && abs(item.amount - amount) <= amountTolerance
        } ?: return null
        val nextExpectedAt = nextExpectedDate(match.frequency, paidAt)
        dao.markPaid(match.id, paidAt, nextExpectedAt)
        return match
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    private fun nextExpectedDate(frequency: String, paidAt: Long): Long {
        val days = when {
            frequency.equals("WEEKLY", ignoreCase = true) -> 7
            frequency.equals("YEARLY", ignoreCase = true) -> 365
            frequency.startsWith("EVERY_", ignoreCase = true) -> frequency.substringAfter("EVERY_").substringBefore("_DAYS").toIntOrNull() ?: 30
            else -> 30
        }
        return paidAt + days * 24L * 60L * 60L * 1000L
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("""[^a-z0-9]+"""), "")
}
