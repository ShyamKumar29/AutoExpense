package com.autoexpense.app.notification

data class ParsedBill(
    val id: String,
    val billType: String,
    val provider: String,
    val amount: Double,
    val currency: String = "INR",
    val dueDate: Long?,
    val generatedAt: Long,
    val source: String,
    val safeExcerpt: String,
    val billFingerprint: String
)
