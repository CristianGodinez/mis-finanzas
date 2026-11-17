package org.misfinanzas.demo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, createdAtMillis DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM accounts ORDER BY includedInAvailable DESC, name")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM cards ORDER BY dueEpochDay")
    fun observeCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM installment_plans ORDER BY status, remainingMonths DESC")
    fun observeInstallmentPlans(): Flow<List<InstallmentPlanEntity>>

    @Query("SELECT * FROM budgets ORDER BY category")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY nextChargeEpochDay")
    fun observeSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM goals ORDER BY id")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM debts ORDER BY nextPaymentEpochDay")
    fun observeDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM investments ORDER BY name")
    fun observeInvestments(): Flow<List<InvestmentEntity>>

    @Query("SELECT * FROM recurring_income WHERE enabled = 1 ORDER BY id")
    fun observeRecurringIncome(): Flow<List<RecurringIncomeEntity>>

    @Query("SELECT * FROM detected_movements WHERE status = 'Pendiente' ORDER BY postedAtMillis DESC")
    fun observePendingDetections(): Flow<List<DetectedMovementEntity>>

    @Query("SELECT * FROM categories WHERE active = 1 ORDER BY parentName, name")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM merchant_rules ORDER BY keyword")
    fun observeMerchantRules(): Flow<List<MerchantRuleEntity>>

    @Query("SELECT * FROM debtors ORDER BY name")
    fun observeDebtors(): Flow<List<DebtorEntity>>

    @Query("SELECT * FROM receivables ORDER BY status, dueEpochDay, id DESC")
    fun observeReceivables(): Flow<List<ReceivableEntity>>

    @Query("SELECT * FROM ignored_notifications ORDER BY postedAtMillis DESC")
    fun observeIgnoredNotifications(): Flow<List<IgnoredNotificationEntity>>

    @Query("SELECT * FROM recurring_income WHERE enabled = 1 ORDER BY id")
    suspend fun getEnabledRecurringIncome(): List<RecurringIncomeEntity>

    @Query("SELECT * FROM subscriptions WHERE status NOT IN ('Pausada', 'Cancelada') ORDER BY nextChargeEpochDay")
    suspend fun getActiveSubscriptions(): List<SubscriptionEntity>

    @Query("SELECT * FROM accounts ORDER BY name")
    suspend fun getAccounts(): List<AccountEntity>

    @Query("SELECT * FROM budgets ORDER BY category")
    suspend fun getBudgets(): List<BudgetEntity>

    @Query("SELECT * FROM debts WHERE status = 'Activa' ORDER BY nextPaymentEpochDay")
    suspend fun getActiveDebts(): List<DebtEntity>

    @Query("SELECT * FROM goals WHERE status = 'Activa' ORDER BY id")
    suspend fun getActiveGoals(): List<GoalEntity>

    @Query("SELECT * FROM transactions WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay AND status = 'Confirmado'")
    suspend fun getConfirmedTransactionsBetween(startEpochDay: Long, endEpochDay: Long): List<TransactionEntity>

    @Query("SELECT * FROM detected_movements WHERE accountName = :accountName AND merchant = :merchant AND amountCents = :amountCents AND postedAtMillis >= :sinceMillis LIMIT 1")
    suspend fun findRecentDetection(accountName: String, merchant: String, amountCents: Long, sinceMillis: Long): DetectedMovementEntity?

    @Query("SELECT * FROM detected_movements WHERE amountCents BETWEEN :minimumAmountCents AND :maximumAmountCents AND postedAtMillis >= :sinceMillis AND status = 'Pendiente' ORDER BY ABS(amountCents - :amountCents), postedAtMillis DESC LIMIT 1")
    suspend fun findRecentDetectionNearAmount(amountCents: Long, minimumAmountCents: Long, maximumAmountCents: Long, sinceMillis: Long): DetectedMovementEntity?

    @Query("SELECT * FROM detected_movements WHERE status = 'Confirmado' AND movementType = 'Ingreso' ORDER BY postedAtMillis DESC LIMIT 100")
    suspend fun getConfirmedIncomeDetections(): List<DetectedMovementEntity>

    @Query("SELECT * FROM detected_movements WHERE status = 'Duplicado' AND movementType IN ('Ingreso', 'Transferencia') AND postedAtMillis >= :sinceMillis ORDER BY postedAtMillis DESC")
    suspend fun getRecentDuplicateTransferDetections(sinceMillis: Long): List<DetectedMovementEntity>

    @Query("SELECT * FROM detected_movements WHERE status = 'Pendiente' ORDER BY postedAtMillis DESC")
    suspend fun getPendingDetections(): List<DetectedMovementEntity>

    @Query("SELECT category FROM transactions WHERE LOWER(description) = LOWER(:merchant) AND status = 'Confirmado' ORDER BY dateEpochDay DESC LIMIT 1")
    suspend fun findLearnedCategory(merchant: String): String?

    @Query("SELECT * FROM transactions WHERE description = :description AND accountName = :accountName AND dateEpochDay = :dateEpochDay LIMIT 1")
    suspend fun findTransaction(description: String, accountName: String, dateEpochDay: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE accountName = :accountName AND amountCents = :amountCents AND dateEpochDay = :dateEpochDay AND type = 'Ingreso' AND status = 'Confirmado' ORDER BY createdAtMillis DESC LIMIT 1")
    suspend fun findConfirmedIncomeTransaction(accountName: String, amountCents: Long, dateEpochDay: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransaction(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE accountName = :accountName AND amountCents = :amountCents AND dateEpochDay = :dateEpochDay AND type = :type AND status = 'Confirmado' AND (:type IN ('Pago tarjeta', 'Transferencia') OR LOWER(description) = LOWER(:description)) LIMIT 1")
    suspend fun findEquivalentTransaction(
        accountName: String,
        description: String,
        amountCents: Long,
        dateEpochDay: Long,
        type: String
    ): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun transactionCount(): Int

    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1")
    suspend fun getAccount(name: String): AccountEntity?

    @Query("SELECT * FROM cards WHERE name = :name LIMIT 1")
    suspend fun getCard(name: String): CardEntity?

    @Query("SELECT * FROM cards ORDER BY name")
    suspend fun getCards(): List<CardEntity>

    @Query("SELECT * FROM installment_plans WHERE status = 'Activo' AND cardName = :cardName ORDER BY id")
    suspend fun getActiveInstallmentPlans(cardName: String): List<InstallmentPlanEntity>

    @Query("SELECT * FROM merchant_rules WHERE enabled = 1 ORDER BY LENGTH(keyword) DESC")
    suspend fun getEnabledMerchantRules(): List<MerchantRuleEntity>

    @Query("SELECT * FROM installment_plans WHERE id = :id LIMIT 1")
    suspend fun getInstallmentPlan(id: Long): InstallmentPlanEntity?

    @Query("SELECT * FROM debtors WHERE id = :id LIMIT 1")
    suspend fun getDebtor(id: Long): DebtorEntity?

    @Query("SELECT * FROM receivables WHERE id = :id LIMIT 1")
    suspend fun getReceivable(id: Long): ReceivableEntity?

    @Query("SELECT * FROM installment_plans WHERE cardName = :cardName AND description = :description AND originalAmountCents = :amountCents ORDER BY id DESC LIMIT 1")
    suspend fun findInstallmentPlan(cardName: String, description: String, amountCents: Long): InstallmentPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(items: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(item: TransactionEntity)

    @Update
    suspend fun updateTransaction(item: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(item: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(items: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(item: AccountEntity)

    @Delete
    suspend fun deleteAccount(item: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(items: List<CardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(item: CardEntity)

    @Delete
    suspend fun deleteCard(item: CardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallmentPlans(items: List<InstallmentPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallmentPlan(item: InstallmentPlanEntity): Long

    @Update
    suspend fun updateInstallmentPlan(item: InstallmentPlanEntity)

    @Delete
    suspend fun deleteInstallmentPlan(item: InstallmentPlanEntity)

    @Query("DELETE FROM installment_plans WHERE cardName IN (:cardNames)")
    suspend fun deleteInstallmentPlansForCards(cardNames: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(item: CategoryEntity)

    @Delete
    suspend fun deleteCategory(item: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMerchantRule(item: MerchantRuleEntity)

    @Delete
    suspend fun deleteMerchantRule(item: MerchantRuleEntity)

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM accounts")
    suspend fun clearAccounts()

    @Query("DELETE FROM cards")
    suspend fun clearCards()

    @Query("DELETE FROM installment_plans")
    suspend fun clearInstallmentPlans()

    @Query("DELETE FROM budgets")
    suspend fun clearBudgets()

    @Query("DELETE FROM subscriptions")
    suspend fun clearSubscriptions()

    @Query("DELETE FROM goals")
    suspend fun clearGoals()

    @Query("DELETE FROM debts")
    suspend fun clearDebts()

    @Query("DELETE FROM investments")
    suspend fun clearInvestments()

    @Query("DELETE FROM recurring_income")
    suspend fun clearRecurringIncome()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM merchant_rules")
    suspend fun clearMerchantRules()

    @Query("DELETE FROM receivables")
    suspend fun clearReceivables()

    @Query("DELETE FROM debtors")
    suspend fun clearDebtors()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(items: List<BudgetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(item: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscriptions(items: List<SubscriptionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(item: SubscriptionEntity)

    @Delete
    suspend fun deleteSubscription(item: SubscriptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringIncome(items: List<RecurringIncomeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringIncome(item: RecurringIncomeEntity)

    @Delete
    suspend fun deleteRecurringIncome(item: RecurringIncomeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDetected(item: DetectedMovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoredNotification(item: IgnoredNotificationEntity): Long

    @Delete
    suspend fun deleteIgnoredNotification(item: IgnoredNotificationEntity)

    @Query("DELETE FROM detected_movements")
    suspend fun clearDetectedMovements()

    @Query("DELETE FROM ignored_notifications")
    suspend fun clearIgnoredNotifications()

    @Query("DELETE FROM ignored_notifications WHERE postedAtMillis < :beforeMillis")
    suspend fun pruneIgnoredNotifications(beforeMillis: Long)

    @Query("DELETE FROM ignored_notifications WHERE id NOT IN (SELECT id FROM ignored_notifications ORDER BY postedAtMillis DESC LIMIT :limit)")
    suspend fun trimIgnoredNotifications(limit: Int)

    @Update
    suspend fun updateDetected(item: DetectedMovementEntity)

    @Update
    suspend fun updateAccount(item: AccountEntity)

    @Update
    suspend fun updateCard(item: CardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(item: GoalEntity)

    @Delete
    suspend fun deleteGoal(item: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(item: DebtEntity)

    @Delete
    suspend fun deleteDebt(item: DebtEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvestment(item: InvestmentEntity)

    @Delete
    suspend fun deleteInvestment(item: InvestmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtor(item: DebtorEntity): Long

    @Update
    suspend fun updateDebtor(item: DebtorEntity)

    @Delete
    suspend fun deleteDebtor(item: DebtorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtors(items: List<DebtorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceivable(item: ReceivableEntity): Long

    @Update
    suspend fun updateReceivable(item: ReceivableEntity)

    @Delete
    suspend fun deleteReceivable(item: ReceivableEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceivables(items: List<ReceivableEntity>)

    @Query("DELETE FROM receivables WHERE debtorId = :debtorId")
    suspend fun deleteReceivablesForDebtor(debtorId: Long)
}


