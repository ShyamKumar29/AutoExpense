package com.autoexpense.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        BudgetEntity::class,
        CustomCategoryEntity::class,
        MerchantCategoryMappingEntity::class,
        MerchantAliasEntity::class,
        PaymentInstrumentEntity::class,
        BillEntity::class,
        RecurringPaymentEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AutoExpenseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun customCategoryDao(): CustomCategoryDao
    abstract fun merchantCategoryDao(): MerchantCategoryDao
    abstract fun merchantAliasDao(): MerchantAliasDao
    abstract fun paymentInstrumentDao(): PaymentInstrumentDao
    abstract fun billDao(): BillDao
    abstract fun recurringPaymentDao(): RecurringPaymentDao

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

        /** Non-destructive migration: adds the merchant_category_mappings table introduced in version 4. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `merchant_category_mappings` (
                        `normalizedMerchant` TEXT NOT NULL PRIMARY KEY,
                        `merchantName` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Non-destructive migration: adds rawMerchant column to transactions and creates merchant_aliases table in version 5. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `transactions` ADD COLUMN `rawMerchant` TEXT NOT NULL DEFAULT ''")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `merchant_aliases` (
                        `normalizedRawMerchant` TEXT NOT NULL PRIMARY KEY,
                        `rawMerchant` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Adds payment method metadata and the future payment instruments table. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `transactions` ADD COLUMN `paymentMethod` TEXT NOT NULL DEFAULT 'UNKNOWN'")
                database.execSQL("ALTER TABLE `transactions` ADD COLUMN `paymentInstrumentId` TEXT")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `payment_instruments` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `paymentMethod` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `issuer` TEXT NOT NULL DEFAULT '',
                        `maskedLast4` TEXT NOT NULL DEFAULT '',
                        `providerPackage` TEXT NOT NULL DEFAULT '',
                        `isArchived` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Adds bill detection storage in version 7. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bills` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `billType` TEXT NOT NULL,
                        `provider` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `currency` TEXT NOT NULL DEFAULT 'INR',
                        `dueDate` INTEGER,
                        `status` TEXT NOT NULL,
                        `generatedAt` INTEGER NOT NULL,
                        `paidAt` INTEGER,
                        `paidTransactionId` TEXT,
                        `source` TEXT NOT NULL,
                        `safeExcerpt` TEXT NOT NULL,
                        `billFingerprint` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_status_dueDate` ON `bills` (`status`, `dueDate`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bills_billFingerprint` ON `bills` (`billFingerprint`)")
            }
        }

        /** Adds recurring payment storage in version 8. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recurring_payments` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `merchant` TEXT NOT NULL,
                        `normalizedMerchant` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `currency` TEXT NOT NULL DEFAULT 'INR',
                        `frequency` TEXT NOT NULL,
                        `lastPaymentAt` INTEGER NOT NULL,
                        `nextExpectedAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_payments_normalizedMerchant_amount_currency`
                    ON `recurring_payments` (`normalizedMerchant`, `amount`, `currency`)
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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
