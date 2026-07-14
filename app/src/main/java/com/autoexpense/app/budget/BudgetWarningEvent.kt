package com.autoexpense.app.budget

/**
 * Warning severity levels for budget thresholds.
 *
 * Ordering: higher ordinal = more severe.
 */
enum class BudgetLevel {
    NORMAL,        // < 70%
    WARNING,       // 70% – 89%
    HIGH_WARNING,  // 90% – 99%
    LIMIT_REACHED, // exactly 100%
    EXCEEDED       // > 100%
}

/**
 * A single budget warning produced after a transaction is confirmed.
 *
 * @param budgetId     The Room id of the affected BudgetEntity.
 * @param category     The display category string, or null for overall budgets.
 * @param periodType   "WEEKLY" or "MONTHLY".
 * @param level        Severity of this warning.
 * @param spent        Amount spent so far in the period (INR).
 * @param limit        Budget limit (INR).
 * @param message      Human-readable warning message.
 */
data class BudgetWarning(
    val budgetId: Long,
    val category: String?,
    val periodType: String,
    val level: BudgetLevel,
    val spent: Double,
    val limit: Double,
    val message: String
)

/** Build a human-readable warning message for the given parameters. */
fun buildWarningMessage(
    category: String?,
    periodType: String,
    level: BudgetLevel,
    spent: Double,
    limit: Double
): String {
    val periodLabel = if (periodType == "WEEKLY") "weekly" else "monthly"
    val catLabel = category ?: ""
    val pct = ((spent / limit) * 100).toInt()
    val excess = spent - limit

    return when (level) {
        BudgetLevel.NORMAL -> ""
        BudgetLevel.WARNING, BudgetLevel.HIGH_WARNING -> {
            if (catLabel.isNotEmpty()) {
                "You've used $pct% of your $periodLabel $catLabel budget."
            } else {
                "You've used $pct% of your $periodLabel budget."
            }
        }
        BudgetLevel.LIMIT_REACHED -> {
            if (catLabel.isNotEmpty()) {
                "You've reached your $periodLabel $catLabel budget."
            } else {
                "You've reached your $periodLabel budget."
            }
        }
        BudgetLevel.EXCEEDED -> {
            val excessStr = formatRupee(excess)
            if (catLabel.isNotEmpty()) {
                "You've exceeded your $periodLabel $catLabel budget by $excessStr."
            } else {
                "You've exceeded your $periodLabel budget by $excessStr."
            }
        }
    }
}

private fun formatRupee(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        "₹${String.format(java.util.Locale.US, "%,.0f", amount)}"
    } else {
        "₹${String.format(java.util.Locale.US, "%,.2f", amount)}"
    }
}

/** Convert a raw (spent, limit) pair into the appropriate [BudgetLevel]. */
fun computeLevel(spent: Double, limit: Double): BudgetLevel {
    if (limit <= 0) return BudgetLevel.NORMAL
    val ratio = spent / limit
    return when {
        ratio > 1.0  -> BudgetLevel.EXCEEDED
        ratio >= 1.0 -> BudgetLevel.LIMIT_REACHED
        ratio >= 0.9 -> BudgetLevel.HIGH_WARNING
        ratio >= 0.7 -> BudgetLevel.WARNING
        else         -> BudgetLevel.NORMAL
    }
}
