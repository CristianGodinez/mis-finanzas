package org.misfinanzas.demo.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.misfinanzas.demo.FinanzasApplication
import org.misfinanzas.demo.automation.FinanceAlertManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.YearMonth

data class ProjectionEvent(
    val dateEpochDay: Long,
    val description: String,
    val amountCents: Long,
    val kind: String
)

private data class AutomationData(
    val detections: List<DetectedMovementEntity>,
    val categories: List<CategoryEntity>,
    val rules: List<MerchantRuleEntity>,
    val ignoredNotifications: List<IgnoredNotificationEntity>
)

data class FinanceUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val cards: List<CardEntity> = emptyList(),
    val installmentPlans: List<InstallmentPlanEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val subscriptions: List<SubscriptionEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val debts: List<DebtEntity> = emptyList(),
    val investments: List<InvestmentEntity> = emptyList(),
    val recurringIncome: List<RecurringIncomeEntity> = emptyList(),
    val pendingDetections: List<DetectedMovementEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val merchantRules: List<MerchantRuleEntity> = emptyList(),
    val debtors: List<DebtorEntity> = emptyList(),
    val receivables: List<ReceivableEntity> = emptyList(),
    val ignoredNotifications: List<IgnoredNotificationEntity> = emptyList()
) {
    val liquidMoneyCents: Long get() = accounts.filter { it.includedInAvailable }.sumOf { it.balanceCents }
    val cardDebtCents: Long get() = cards.sumOf { it.balanceCents.coerceAtLeast(0) }
    val netCardCreditCents: Long get() = cards.sumOf { (-it.balanceCents).coerceAtLeast(0) }
    val creditAvailableCents: Long get() = cards.sumOf { it.availableCents }
    val upcomingPaymentsCents: Long get() = cards.filterNot { it.paymentConfirmed }.sumOf { it.statementPaymentCents }
    val reservedSubscriptionsCents: Long
        get() {
            val today = LocalDate.now().toEpochDay()
            val end = YearMonth.now().atEndOfMonth().toEpochDay()
            return subscriptions.filter {
                it.status !in listOf("Pausada", "Cancelada") && it.nextChargeEpochDay != null && it.nextChargeEpochDay in today..end
            }.sumOf { it.amountCents }
        }
    val reservedDebtPaymentsCents: Long
        get() {
            val end = YearMonth.now().atEndOfMonth().toEpochDay()
            return debts.filter { it.status == "Activa" && (it.nextPaymentEpochDay == null || it.nextPaymentEpochDay <= end) }.sumOf { it.monthlyPaymentCents }
        }
    val reservedGoalContributionsCents: Long get() = goals.filter { it.status == "Activa" }.sumOf { it.monthlyContributionCents }
    val fixedCommitmentsCents: Long get() = reservedSubscriptionsCents + reservedDebtPaymentsCents + reservedGoalContributionsCents
    val totalReservedCents: Long get() = upcomingPaymentsCents + fixedCommitmentsCents
    val safeToSpendCents: Long get() = (liquidMoneyCents - totalReservedCents).coerceAtLeast(0)
    val netWorthCents: Long get() = liquidMoneyCents + investments.sumOf { it.currentValueCents } + netCardCreditCents - cardDebtCents - debts.sumOf { it.balanceCents }

    val currentMonthSpentCents: Long
        get() {
            val month = YearMonth.now()
            return transactions.asSequence()
                .filter { it.status == "Confirmado" }
                .filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == month }
                .sumOf { it.budgetImpactCents }
        }

    val expectedMonthlyIncomeCents: Long
        get() = recurringIncome.sumOf {
            when (it.cadence) {
                "Cada lunes" -> it.amountCents * 4
                "Último día del mes" -> it.amountCents
                else -> it.amountCents
            }
        }

    val projectionEvents30: List<ProjectionEvent>
        get() {
            val today = LocalDate.now()
            val end = today.plusDays(30)
            val events = mutableListOf<ProjectionEvent>()

            var date = today.plusDays(1)
            while (!date.isAfter(end)) {
                recurringIncome.filter { it.enabled }.forEach { income ->
                    val isDue = when (income.cadence) {
                        "Cada lunes" -> date.dayOfWeek == DayOfWeek.MONDAY
                        "Último día del mes" -> date.dayOfMonth == date.lengthOfMonth()
                        else -> false
                    }
                    if (isDue) events += ProjectionEvent(date.toEpochDay(), income.description, income.amountCents, "Ingreso")
                }
                date = date.plusDays(1)
            }

            cards.filter { !it.paymentConfirmed && it.statementPaymentCents > 0 }.forEach { card ->
                val due = card.dueEpochDay?.let(LocalDate::ofEpochDay)
                if (due != null && !due.isBefore(today) && !due.isAfter(end)) {
                    events += ProjectionEvent(due.toEpochDay(), "Pago ${card.name}", -card.statementPaymentCents, "Tarjeta")
                }
            }

            subscriptions.filter { it.status !in listOf("Pausada", "Cancelada") }.forEach { subscription ->
                val due = subscription.nextChargeEpochDay?.let(LocalDate::ofEpochDay)
                if (due != null && !due.isBefore(today) && !due.isAfter(end)) {
                    events += ProjectionEvent(due.toEpochDay(), subscription.name, -subscription.amountCents, "Suscripción")
                }
            }

            debts.filter { it.status == "Activa" && it.monthlyPaymentCents > 0 }.forEach { debt ->
                val due = debt.nextPaymentEpochDay?.let(LocalDate::ofEpochDay) ?: end
                if (!due.isBefore(today) && !due.isAfter(end)) {
                    events += ProjectionEvent(due.toEpochDay(), debt.name, -debt.monthlyPaymentCents, "Deuda")
                }
            }

            val goalTotal = goals.filter { it.status == "Activa" }.sumOf { it.monthlyContributionCents }
            if (goalTotal > 0) {
                val goalDate = YearMonth.from(today).atEndOfMonth().coerceAtMost(end)
                events += ProjectionEvent(goalDate.toEpochDay(), "Aportaciones a metas", -goalTotal, "Meta")
            }

            return events.sortedWith(compareBy<ProjectionEvent> { it.dateEpochDay }.thenByDescending { it.amountCents })
        }

    val projectedIncome30Cents: Long get() = projectionEvents30.filter { it.amountCents > 0 }.sumOf { it.amountCents }
    val projectedExpenses30Cents: Long get() = projectionEvents30.filter { it.amountCents < 0 }.sumOf { -it.amountCents }
    val projectedBalance30Cents: Long get() = liquidMoneyCents + projectionEvents30.sumOf { it.amountCents }
    val pendingReceivablesCents: Long get() = receivables.filter { it.status == "Pendiente" }.sumOf { it.amountCents }
}

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as FinanzasApplication).repository

    private val primary = combine(
        repository.transactions,
        repository.accounts,
        repository.cards,
        repository.installmentPlans,
        repository.budgets
    ) { transactions, accounts, cards, plans, budgets ->
        val today = LocalDate.now().toEpochDay()
        FinanceUiState(
            transactions = transactions
                .filter { it.dateEpochDay <= today }
                .sortedWith(compareByDescending<TransactionEntity> { it.dateEpochDay }.thenByDescending { it.createdAtMillis }),
            accounts = accounts,
            cards = cards,
            installmentPlans = plans,
            budgets = budgets
        )
    }

    private val secondary = combine(
        repository.subscriptions,
        repository.goals,
        repository.debts,
        repository.investments,
        repository.recurringIncome
    ) { subscriptions, goals, debts, investments, income ->
        FinanceUiState(
            subscriptions = subscriptions,
            goals = goals,
            debts = debts,
            investments = investments,
            recurringIncome = income
        )
    }

    private val automation = combine(
        repository.pendingDetections,
        repository.categories,
        repository.merchantRules,
        repository.ignoredNotifications
    ) { detections, categories, rules, ignoredNotifications ->
        AutomationData(detections, categories, rules, ignoredNotifications)
    }

    private val receivableData = combine(repository.debtors, repository.receivables) { debtors, receivables ->
        debtors to receivables
    }

    val state = combine(primary, secondary, automation, receivableData) { a, b, automation, receivableData ->
        a.copy(
            subscriptions = b.subscriptions,
            goals = b.goals,
            debts = b.debts,
            investments = b.investments,
            recurringIncome = b.recurringIncome,
            pendingDetections = automation.detections,
            categories = automation.categories,
            merchantRules = automation.rules,
            debtors = receivableData.first,
            receivables = receivableData.second,
            ignoredNotifications = automation.ignoredNotifications
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceUiState())

    init {
        viewModelScope.launch {
            repository.initializeIfNeeded()
            repository.processRecurringIncome()
            repository.processRecurringExpenses()
            repository.processCardCycles()
            FinanceAlertManager.runChecks(application, (application as FinanzasApplication).database.financeDao())
        }
    }

    fun addTransaction(account: String, description: String, type: String, category: String, amountCents: Long) {
        viewModelScope.launch { repository.addTransaction(account, description, type, category, amountCents) }
    }

    fun addMovement(request: MovementRequest, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.addMovement(request) }
                .onSuccess { onResult(null) }
                .onFailure { onResult(it.message ?: "No se pudo guardar el movimiento") }
        }
    }

    fun confirmDetection(item: DetectedMovementEntity, request: MovementRequest, onResult: (String?) -> Unit = {}) =
        runAction({ repository.confirmDetection(item, request) }, onResult)

    fun dismissDetection(item: DetectedMovementEntity) {
        viewModelScope.launch { repository.dismissDetection(item) }
    }

    fun deleteIgnoredNotification(item: IgnoredNotificationEntity) {
        viewModelScope.launch { repository.deleteIgnoredNotification(item) }
    }

    fun clearIgnoredNotifications(onResult: (String?) -> Unit = {}) = runAction({ repository.clearIgnoredNotifications() }, onResult)

    fun addGoal(name: String, targetCents: Long) {
        viewModelScope.launch { repository.addGoal(name, targetCents) }
    }

    private fun runAction(block: suspend () -> Unit, onResult: (String?) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { onResult(null) }
                .onFailure { onResult(it.message ?: "No se pudo completar la operación") }
        }
    }

    fun saveAccount(item: AccountEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.saveAccount(item) }, onResult)
    fun reconcileAccount(item: AccountEntity, actualBalanceCents: Long, reason: String, onResult: (String?) -> Unit = {}) =
        runAction({ repository.reconcileAccount(item, actualBalanceCents, reason) }, onResult)
    fun deleteAccount(item: AccountEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.deleteAccount(item) }, onResult)
    fun saveCard(item: CardEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.saveCard(item) }, onResult)
    fun reconcileCard(
        item: CardEntity,
        statementPaymentCents: Long,
        currentBalanceCents: Long,
        msiBalanceCents: Long,
        availableCents: Long,
        reason: String,
        creditBalanceCents: Long = item.creditBalanceCents,
        onResult: (String?) -> Unit = {}
    ) = runAction({ repository.reconcileCard(item, statementPaymentCents, currentBalanceCents, msiBalanceCents, availableCents, reason, creditBalanceCents) }, onResult)
    fun deleteCard(item: CardEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.deleteCard(item) }, onResult)
    fun saveBudget(category: String, limitCents: Long?, onResult: (String?) -> Unit = {}) = runAction({ repository.saveBudget(category, limitCents) }, onResult)
    fun saveCategory(item: CategoryEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.saveCategory(item) }, onResult)
    fun deleteCategory(item: CategoryEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.deleteCategory(item) }, onResult)
    fun saveMerchantRule(item: MerchantRuleEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.saveMerchantRule(item) }, onResult)
    fun deleteMerchantRule(item: MerchantRuleEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.deleteMerchantRule(item) }, onResult)
    fun saveSubscription(item: SubscriptionEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.saveSubscription(item) }, onResult)
    fun deleteSubscription(item: SubscriptionEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.deleteSubscription(item) }, onResult)
    fun saveGoal(item: GoalEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.saveGoal(item) }, onResult)
    fun deleteGoal(item: GoalEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.deleteGoal(item) }, onResult)
    fun saveDebt(item: DebtEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.saveDebt(item) }, onResult)
    fun deleteDebt(item: DebtEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.deleteDebt(item) }, onResult)
    fun saveInvestment(item: InvestmentEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.saveInvestment(item) }, onResult)
    fun deleteInvestment(item: InvestmentEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.deleteInvestment(item) }, onResult)
    fun saveRecurringIncome(item: RecurringIncomeEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.saveRecurringIncome(item) }, onResult)
    fun deleteRecurringIncome(item: RecurringIncomeEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.deleteRecurringIncome(item) }, onResult)
    fun saveDebtor(item: DebtorEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.saveDebtor(item) }, onResult)
    fun deleteDebtor(item: DebtorEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.deleteDebtor(item) }, onResult)
    fun saveReceivable(item: ReceivableEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.saveReceivable(item) }, onResult)
    fun deleteReceivable(item: ReceivableEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.deleteReceivable(item) }, onResult)
    fun markReceivablePaid(item: ReceivableEntity, destinationAccountName: String?, linkedTransactionId: String?, onResult: (String?) -> Unit = {}) =
        runAction({ repository.markReceivablePaid(item, destinationAccountName, linkedTransactionId) }, onResult)
    fun replaceMovement(item: TransactionEntity, request: MovementRequest, onResult: (String?) -> Unit = {}) =
        runAction({ repository.replaceMovement(item, request) }, onResult)
    fun deleteMovement(item: TransactionEntity, onResult: (String?) -> Unit = {}) = runAction({ repository.deleteMovement(item) }, onResult)
    fun payInstallmentPlan(item: InstallmentPlanEntity, sourceAccount: String, amountCents: Long, onResult: (String?) -> Unit = {}) =
        runAction({ repository.payInstallmentPlan(item, sourceAccount, amountCents) }, onResult)
    fun saveInstallmentPlan(original: InstallmentPlanEntity, updated: InstallmentPlanEntity, preserveCardBalances: Boolean = false, onResult: (String?) -> Unit = {}) =
        runAction({ repository.saveInstallmentPlan(original, updated, preserveCardBalances) }, onResult)
    fun restoreBackup(json: String, onResult: (String?) -> Unit = {}) = runAction({ repository.restoreBackup(json) }, onResult)
}


