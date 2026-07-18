package com.autoexpense.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BillRepository {
    private lateinit var dao: BillDao
    private val _bills = MutableStateFlow<List<BillEntity>>(emptyList())
    val bills: StateFlow<List<BillEntity>> = _bills.asStateFlow()

    fun init(billDao: BillDao) {
        dao = billDao
    }

    suspend fun collectBills() {
        dao.observeAll().collect { _bills.value = it }
    }

    suspend fun insertIfNew(bill: BillEntity): Boolean = dao.insertIgnore(bill) != -1L

    suspend fun markPaid(billId: String, transactionId: String? = null, paidAt: Long = System.currentTimeMillis()) {
        dao.markPaid(billId, transactionId, paidAt)
    }

    suspend fun dismiss(billId: String) {
        dao.dismiss(billId)
    }

    suspend fun markMatchingPaymentPaid(
        transactionId: String,
        merchantOrRecipient: String,
        amount: Double,
        paidAt: Long
    ) {
        val normalizedMerchant = normalize(merchantOrRecipient)
        val match = dao.getOpenBills().firstOrNull { bill ->
            kotlin.math.abs(bill.amount - amount) < 0.01 &&
                (normalize(bill.provider).contains(normalizedMerchant) ||
                    normalizedMerchant.contains(normalize(bill.provider)))
        } ?: return
        dao.markPaid(match.id, transactionId, paidAt)
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("""[^a-z0-9]+"""), "")
}
