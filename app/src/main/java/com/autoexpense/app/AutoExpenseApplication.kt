package com.autoexpense.app

import android.app.Application

class AutoExpenseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TransactionRepository.init(this)
    }
}
