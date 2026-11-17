package org.misfinanzas.demo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "transactions", indices = [Index("dateEpochDay"), Index("accountName"), Index("category")])
data class TransactionEntity(
    @PrimaryKey val id: String,
    val dateEpochDay: Long,
    val accountName: String,
    val description: String,
    val type: String,
    val category: String,
    val amountCents: Long,
    val budgetImpactCents: Long,
    val isMsi: Boolean,
    val installment: String?,
    val status: String,
    val source: String,
    val notes: String?,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val relatedAccountName: String? = null,
    val installmentPlanId: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val cardEffect: String? = null,
    @ColumnInfo(defaultValue = "'Saldo actual'") val benefitTarget: String = "Saldo actual"
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val name: String,
    val type: String,
    val balanceCents: Long,
    val includedInAvailable: Boolean,
    val updatedAtEpochDay: Long?,
    val colorHex: Long,
    @ColumnInfo(defaultValue = "'wallet'") val iconKey: String = "wallet"
)

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val name: String,
    val institution: String,
    val lastFour: String?,
    val cutoffEpochDay: Long?,
    val dueEpochDay: Long?,
    val creditLimitCents: Long,
    val balanceCents: Long,
    val msiBalanceCents: Long,
    val statementPaymentCents: Long,
    val minimumPaymentCents: Long?,
    val availableCents: Long,
    val paymentConfirmed: Boolean,
    val status: String,
    val colorHex: Long,
    @ColumnInfo(defaultValue = "0") val unbilledCents: Long = 0,
    @ColumnInfo(defaultValue = "'card'") val iconKey: String = "card",
    @ColumnInfo(defaultValue = "0") val creditBalanceCents: Long = 0
)

@Entity(tableName = "installment_plans")
data class InstallmentPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardName: String,
    val description: String,
    val purchaseEpochDay: Long,
    val originalAmountCents: Long,
    val totalMonths: Int,
    val currentPayment: Int,
    val monthlyPaymentCents: Long,
    val pendingBalanceCents: Long,
    val remainingMonths: Int,
    val status: String,
    @ColumnInfo(defaultValue = "NULL") val firstDueEpochDay: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val nextDueEpochDay: Long? = null
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val parentName: String? = null,
    val colorHex: Long = 0xFF64748B,
    val iconKey: String = "tag",
    val active: Boolean = true
)

@Entity(tableName = "merchant_rules", indices = [Index("keyword")])
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val accountName: String? = null,
    val category: String,
    val movementType: String = "Gasto",
    val merchantName: String? = null,
    val enabled: Boolean = true
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val limitCents: Long?
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val name: String,
    val amountCents: Long,
    val frequency: String,
    val nextChargeEpochDay: Long?,
    val accountName: String,
    val status: String
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetCents: Long,
    val savedCents: Long,
    val targetEpochDay: Long?,
    val monthlyContributionCents: Long,
    val priority: String,
    val status: String
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val creditor: String,
    val balanceCents: Long,
    val monthlyPaymentCents: Long,
    val nextPaymentEpochDay: Long?,
    val annualRate: Double?,
    val status: String
)

@Entity(tableName = "investments")
data class InvestmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val institution: String,
    val contributedCents: Long,
    val currentValueCents: Long,
    val updatedAtEpochDay: Long?
)

@Entity(tableName = "recurring_income")
data class RecurringIncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val amountCents: Long,
    val cadence: String,
    val destinationAccount: String,
    val enabled: Boolean
)

@Entity(tableName = "detected_movements", indices = [Index(value = ["fingerprint"], unique = true)])
data class DetectedMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fingerprint: String,
    val packageName: String,
    val accountName: String,
    val merchant: String,
    val amountCents: Long,
    val suggestedCategory: String,
    val postedAtMillis: Long,
    val rawTitle: String,
    val rawText: String,
    val status: String = "Pendiente",
    @ColumnInfo(defaultValue = "'Gasto'") val movementType: String = "Gasto",
    val relatedAccountName: String? = null
)

@Entity(
    tableName = "ignored_notifications",
    indices = [Index(value = ["fingerprint"], unique = true), Index("postedAtMillis")]
)
data class IgnoredNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fingerprint: String,
    val packageName: String,
    val bankName: String,
    val title: String,
    val text: String,
    val reason: String,
    val postedAtMillis: Long
)

@Entity(tableName = "debtors")
data class DebtorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String? = null,
    val colorHex: Long = 0xFF58DCC0,
    val createdAtEpochDay: Long = java.time.LocalDate.now().toEpochDay()
)

@Entity(
    tableName = "receivables",
    indices = [Index("debtorId"), Index("status"), Index(value = ["linkedTransactionId"], unique = true)]
)
data class ReceivableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtorId: Long,
    val name: String,
    val category: String,
    val amountCents: Long,
    val notes: String? = null,
    val dueEpochDay: Long? = null,
    val status: String = "Pendiente",
    val paidEpochDay: Long? = null,
    val destinationAccountName: String? = null,
    val linkedTransactionId: String? = null
)

data class MovementRequest(
    val type: String,
    val sourceName: String,
    val destinationName: String? = null,
    val description: String,
    val category: String,
    val amountCents: Long,
    val notes: String? = null,
    val installmentMonths: Int? = null,
    val dateEpochDay: Long = java.time.LocalDate.now().toEpochDay(),
    val benefitTarget: String = "Saldo actual"
)


