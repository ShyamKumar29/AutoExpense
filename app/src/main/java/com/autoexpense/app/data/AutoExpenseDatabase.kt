package com.autoexpense.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class, BudgetEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AutoExpenseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        @Volatile
        private var INSTANCE: AutoExpenseDatabase? = null

        /** Non-destructive migration: adds the budgets table introduced in version 2. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budgets` (
                        `id`               INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `category`         TEXT,
                        `categoryKey`      TEXT NOT NULL,
                        `periodType`       TEXT NOT NULL,
                        `limitAmount`      REAL NOT NULL,
                        `warningThreshold` REAL NOT NULL DEFAULT 0.7,
                        `createdAt`        INTEGER NOT NULL,
                        `updatedAt`        INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryKey_periodType`
                    ON `budgets` (`categoryKey`, `periodType`)
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AutoExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AutoExpenseDatabase::class.java,
                    "autoexpense_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
