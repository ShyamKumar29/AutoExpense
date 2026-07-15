package com.autoexpense.app

import android.app.Application
import com.autoexpense.app.budget.BudgetNotificationHelper
import com.autoexpense.app.budget.BudgetRepository
import com.autoexpense.app.budget.BudgetRepositorySingleton
import com.autoexpense.app.data.AutoExpenseDatabase

class AutoExpenseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TransactionRepository.init(this)
        val db = AutoExpenseDatabase.getDatabase(this)
        BudgetRepositorySingleton.init(BudgetRepository(db.budgetDao()))
        com.autoexpense.app.data.CustomCategoryRepository.init(db.customCategoryDao())
        com.autoexpense.app.data.MerchantCategoryRepository.init(db.merchantCategoryDao())
        BudgetNotificationHelper.createChannel(this)
    }
}
