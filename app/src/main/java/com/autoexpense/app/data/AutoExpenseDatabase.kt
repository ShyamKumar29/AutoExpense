package com.autoexpense.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TransactionEntity::class], version = 1, exportSchema = false)
abstract class AutoExpenseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AutoExpenseDatabase? = null

        fun getDatabase(context: Context): AutoExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AutoExpenseDatabase::class.java,
                    "autoexpense_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
