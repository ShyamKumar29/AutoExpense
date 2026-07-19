package com.autoexpense.app.ui.transaction.screens

import androidx.compose.runtime.Composable
import com.autoexpense.app.domain.TransactionType

@Composable
fun IncomeScreen() {
    TransactionListScreen(
        transactionType = TransactionType.INCOME,
        title = "Income",
        subtitle = "Track salary, business, refunds and credits",
        addLabel = "Add Income"
    )
}
