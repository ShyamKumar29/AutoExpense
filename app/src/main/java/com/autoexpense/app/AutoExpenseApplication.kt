package com.autoexpense.app

import android.app.Application
import com.autoexpense.app.budget.BudgetNotificationHelper
import com.autoexpense.app.budget.BudgetRepository
import com.autoexpense.app.budget.BudgetRepositorySingleton
import com.autoexpense.app.data.AutoExpenseDatabase
import com.autoexpense.app.notification.SmsPaymentScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AutoExpenseApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        TransactionRepository.init(this)
        val db = AutoExpenseDatabase.getDatabase(this)
        BudgetRepositorySingleton.init(BudgetRepository(db.budgetDao()))
        com.autoexpense.app.data.CustomCategoryRepository.init(db.customCategoryDao())
        com.autoexpense.app.data.MerchantCategoryRepository.init(db.merchantCategoryDao())
        com.autoexpense.app.data.MerchantAliasRepository.init(db.merchantAliasDao())
        BudgetNotificationHelper.createChannel(this)

        applicationScope.launch {
            SmsPaymentScanner.scanRecent(this@AutoExpenseApplication)
        }
    }
}
