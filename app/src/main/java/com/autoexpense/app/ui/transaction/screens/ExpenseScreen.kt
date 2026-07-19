package com.autoexpense.app.ui.transaction.screens

import androidx.compose.runtime.Composable
import com.autoexpense.app.domain.TransactionType

@Composable
fun ExpenseScreen() {
    TransactionListScreen(
        transactionType = TransactionType.EXPENSE,
        title = "Expenses",
        subtitle = "Reusable transaction module for spending",
        addLabel = "Add Expense"
    )
}
