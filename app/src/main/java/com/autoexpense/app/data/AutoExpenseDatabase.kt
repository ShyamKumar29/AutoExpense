package com.autoexpense.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class, BudgetEntity::class, CustomCategoryEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AutoExpenseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun customCategoryDao(): CustomCategoryDao

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

        /** Non-destructive migration: adds the custom_categories table introduced in version 3. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `iconName` TEXT NOT NULL
                    )
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
