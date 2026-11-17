package org.misfinanzas.demo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        CardEntity::class,
        InstallmentPlanEntity::class,
        BudgetEntity::class,
        SubscriptionEntity::class,
        GoalEntity::class,
        DebtEntity::class,
        InvestmentEntity::class,
        RecurringIncomeEntity::class,
        DetectedMovementEntity::class,
        CategoryEntity::class,
        MerchantRuleEntity::class,
        DebtorEntity::class,
        ReceivableEntity::class,
        IgnoredNotificationEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao

    companion object {
        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cards ADD COLUMN unbilledCents INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE transactions ADD COLUMN relatedAccountName TEXT")
                database.execSQL("ALTER TABLE transactions ADD COLUMN installmentPlanId INTEGER")
            }
        }

        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE accounts ADD COLUMN iconKey TEXT NOT NULL DEFAULT 'wallet'")
                database.execSQL("ALTER TABLE detected_movements ADD COLUMN movementType TEXT NOT NULL DEFAULT 'Gasto'")
                database.execSQL("ALTER TABLE detected_movements ADD COLUMN relatedAccountName TEXT")
            }
        }

        private val migration3To4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cards ADD COLUMN iconKey TEXT NOT NULL DEFAULT 'card'")
            }
        }

        private val migration4To5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE installment_plans ADD COLUMN firstDueEpochDay INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE installment_plans ADD COLUMN nextDueEpochDay INTEGER DEFAULT NULL")
                database.execSQL("CREATE TABLE IF NOT EXISTS categories (name TEXT NOT NULL, parentName TEXT, colorHex INTEGER NOT NULL, iconKey TEXT NOT NULL, active INTEGER NOT NULL, PRIMARY KEY(name))")
                database.execSQL("CREATE TABLE IF NOT EXISTS merchant_rules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, keyword TEXT NOT NULL, accountName TEXT, category TEXT NOT NULL, movementType TEXT NOT NULL, merchantName TEXT, enabled INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_merchant_rules_keyword ON merchant_rules(keyword)")
            }
        }

        private val migration5To6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS debtors (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, notes TEXT, colorHex INTEGER NOT NULL, createdAtEpochDay INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS receivables (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, debtorId INTEGER NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, amountCents INTEGER NOT NULL, notes TEXT, dueEpochDay INTEGER, status TEXT NOT NULL, paidEpochDay INTEGER, destinationAccountName TEXT, linkedTransactionId TEXT)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_receivables_debtorId ON receivables(debtorId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_receivables_status ON receivables(status)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_receivables_linkedTransactionId ON receivables(linkedTransactionId)")
            }
        }

        private val migration6To7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS ignored_notifications (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fingerprint TEXT NOT NULL, packageName TEXT NOT NULL, bankName TEXT NOT NULL, title TEXT NOT NULL, text TEXT NOT NULL, reason TEXT NOT NULL, postedAtMillis INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ignored_notifications_fingerprint ON ignored_notifications(fingerprint)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ignored_notifications_postedAtMillis ON ignored_notifications(postedAtMillis)")
            }
        }

        val migration7To8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cards ADD COLUMN creditBalanceCents INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE transactions ADD COLUMN cardEffect TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE transactions ADD COLUMN benefitTarget TEXT NOT NULL DEFAULT 'Saldo actual'")
            }
        }

        @Volatile private var instance: FinanceDatabase? = null

        fun getInstance(context: Context): FinanceDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FinanceDatabase::class.java,
                "mis_finanzas_demo.db"
            ).addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8).build().also { instance = it }
        }
    }
}


