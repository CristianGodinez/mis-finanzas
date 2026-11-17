package org.misfinanzas.demo.data

import android.content.Context
import android.app.backup.BackupManager
import androidx.room.withTransaction
import org.misfinanzas.demo.notifications.NotificationParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.json.JSONObject

class FinanceRepository(
    private val context: Context,
    private val database: FinanceDatabase
) {
    private val dao = database.financeDao()

    val transactions: Flow<List<TransactionEntity>> = dao.observeTransactions()
    val accounts: Flow<List<AccountEntity>> = dao.observeAccounts()
    val cards: Flow<List<CardEntity>> = dao.observeCards()
    val installmentPlans: Flow<List<InstallmentPlanEntity>> = dao.observeInstallmentPlans()
    val budgets: Flow<List<BudgetEntity>> = dao.observeBudgets()
    val subscriptions: Flow<List<SubscriptionEntity>> = dao.observeSubscriptions()
    val goals: Flow<List<GoalEntity>> = dao.observeGoals()
    val debts: Flow<List<DebtEntity>> = dao.observeDebts()
    val investments: Flow<List<InvestmentEntity>> = dao.observeInvestments()
    val recurringIncome: Flow<List<RecurringIncomeEntity>> = dao.observeRecurringIncome()
    val pendingDetections: Flow<List<DetectedMovementEntity>> = dao.observePendingDetections()
    val categories: Flow<List<CategoryEntity>> = dao.observeCategories()
    val merchantRules: Flow<List<MerchantRuleEntity>> = dao.observeMerchantRules()
    val debtors: Flow<List<DebtorEntity>> = dao.observeDebtors()
    val receivables: Flow<List<ReceivableEntity>> = dao.observeReceivables()
    val ignoredNotifications: Flow<List<IgnoredNotificationEntity>> = dao.observeIgnoredNotifications()

    suspend fun initializeIfNeeded() {
        val preferences = context.getSharedPreferences("public_demo", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("initialized", false)) {
            database.withTransaction {
                if (dao.getAccounts().isEmpty() && dao.getCards().isEmpty() && dao.observeTransactions().first().isEmpty()) {
                    dao.insertAccounts(SeedData.accounts)
                    dao.insertCards(SeedData.cards)
                    dao.insertInstallmentPlans(SeedData.installmentPlans)
                    dao.insertBudgets(SeedData.categories.map { BudgetEntity(it, null) })
                    dao.insertCategories(SeedData.categories.map { CategoryEntity(it) })
                    dao.insertTransactions(SeedData.transactions)
                }
            }
            preferences.edit().putBoolean("initialized", true).apply()
        }
        recoverRecentTransferDuplicates()
        reclassifyPendingDetections()
        repairPendingTransferPairs()
        repairConfirmedIncomingTransfers()
    }

    private suspend fun reclassifyPendingDetections() {
        dao.getPendingDetections().forEach { item ->
            val parsed = NotificationParser.parse(item.packageName, item.rawTitle, item.rawText, item.postedAtMillis)
            if (parsed == null) {
                dao.updateDetected(item.copy(status = "Descartado"))
            } else {
                dao.updateDetected(
                    item.copy(
                        accountName = parsed.accountName,
                        merchant = parsed.merchant,
                        amountCents = parsed.amountCents,
                        suggestedCategory = parsed.suggestedCategory,
                        movementType = parsed.movementType,
                        relatedAccountName = parsed.relatedAccountName
                    )
                )
            }
        }
    }

    private suspend fun recoverRecentTransferDuplicates() {
        val since = System.currentTimeMillis() - 24 * 60 * 60_000L
        dao.getRecentDuplicateTransferDetections(since).forEach {
            dao.updateDetected(it.copy(status = "Pendiente"))
        }
    }

    private fun requestSystemBackup() {
        BackupManager(context).dataChanged()
    }

    private suspend fun repairPendingTransferPairs() {
        val pending = dao.getPendingDetections()
        val used = mutableSetOf<Long>()
        pending.filter {
            it.movementType == "Transferencia" && it.relatedAccountName != null && it.relatedAccountName == it.accountName
        }.forEach {
            dao.updateDetected(it.copy(status = "Fusionado"))
            used += it.id
        }
        pending.forEach { outgoing ->
            if (outgoing.id in used) return@forEach
            val outgoingAccount = when {
                outgoing.movementType == "Transferencia" -> outgoing.accountName
                outgoing.movementType == "Gasto" -> NotificationParser.liquidDebitNoticeAccount(outgoing.packageName, outgoing.rawTitle, outgoing.rawText)
                else -> null
            } ?: return@forEach
            val incoming = pending.firstOrNull { candidate ->
                candidate.id != outgoing.id && candidate.id !in used &&
                    NotificationParser.amountsCanPair(candidate.amountCents, outgoing.amountCents) &&
                    kotlin.math.abs(candidate.postedAtMillis - outgoing.postedAtMillis) <= 10 * 60_000L &&
                    (candidate.movementType == "Ingreso" ||
                        (outgoing.movementType == "Transferencia" && candidate.movementType == "Gasto" &&
                            NotificationParser.liquidAccountForPackage(candidate.packageName) != null))
            } ?: return@forEach
            val destination = if (incoming.movementType == "Ingreso") incoming.accountName
            else NotificationParser.liquidAccountForPackage(incoming.packageName) ?: return@forEach
            if (destination == outgoingAccount ||
                !NotificationParser.canPairDestination(outgoing.relatedAccountName, destination, outgoing.rawTitle, outgoing.rawText)) return@forEach
            dao.updateDetected(
                outgoing.copy(
                    accountName = outgoingAccount,
                    merchant = "Transferencia a $destination",
                    amountCents = incoming.amountCents,
                    suggestedCategory = "Transferencias",
                    movementType = "Transferencia",
                    relatedAccountName = destination,
                    rawTitle = "${outgoing.rawTitle} · ${incoming.rawTitle}",
                    rawText = "${outgoing.rawText} · ${incoming.rawText}",
                    postedAtMillis = maxOf(outgoing.postedAtMillis, incoming.postedAtMillis)
                )
            )
            dao.updateDetected(incoming.copy(status = "Fusionado"))
            used += outgoing.id
            used += incoming.id
        }
    }

    private suspend fun repairConfirmedIncomingTransfers() {
        val confirmedIncome = dao.getConfirmedIncomeDetections()
        var changed = false
        dao.getPendingDetections().forEach { outgoing ->
            val source = when {
                outgoing.movementType == "Transferencia" -> outgoing.accountName
                outgoing.movementType == "Gasto" -> NotificationParser.liquidDebitNoticeAccount(outgoing.packageName, outgoing.rawTitle, outgoing.rawText)
                else -> null
            } ?: return@forEach
            val incoming = confirmedIncome.firstOrNull { candidate ->
                candidate.accountName != source &&
                    NotificationParser.amountsCanPair(candidate.amountCents, outgoing.amountCents) &&
                    kotlin.math.abs(candidate.postedAtMillis - outgoing.postedAtMillis) <= 10 * 60_000L &&
                    NotificationParser.canPairDestination(outgoing.relatedAccountName, candidate.accountName, outgoing.rawTitle, outgoing.rawText)
            } ?: return@forEach
            val date = java.time.Instant.ofEpochMilli(incoming.postedAtMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .toEpochDay()
            val transaction = dao.findConfirmedIncomeTransaction(incoming.accountName, incoming.amountCents, date) ?: return@forEach
            database.withTransaction {
                val sourceAccount = dao.getAccount(source) ?: return@withTransaction
                dao.updateAccount(sourceAccount.copy(balanceCents = sourceAccount.balanceCents - incoming.amountCents))
                dao.updateTransaction(
                    transaction.copy(
                        accountName = source,
                        description = "Transferencia a ${incoming.accountName}",
                        type = "Transferencia",
                        category = "Transferencias",
                        budgetImpactCents = 0,
                        relatedAccountName = incoming.accountName
                    )
                )
                dao.updateDetected(
                    outgoing.copy(
                        status = "Confirmado",
                        accountName = source,
                        merchant = "Transferencia a ${incoming.accountName}",
                        amountCents = incoming.amountCents,
                        suggestedCategory = "Transferencias",
                        movementType = "Transferencia",
                        relatedAccountName = incoming.accountName
                    )
                )
                dao.updateDetected(incoming.copy(status = "Fusionado"))
            }
            changed = true
        }
        if (changed) requestSystemBackup()
    }

    suspend fun processRecurringIncome() {
        val preferences = context.getSharedPreferences("finance_automation", Context.MODE_PRIVATE)
        val today = LocalDate.now()
        val lastEpochDay = preferences.getLong("last_income_check", Long.MIN_VALUE)
        val incomes = dao.getEnabledRecurringIncome()

        if (lastEpochDay == Long.MIN_VALUE) {
            incomes.forEach { income ->
                val existing = dao.findTransaction(income.description, income.destinationAccount, today.toEpochDay())
                if (existing?.status == "Pendiente") dao.updateTransaction(existing.copy(status = "Confirmado", source = "Ingreso programado"))
            }
            preferences.edit().putLong("last_income_check", today.toEpochDay()).apply()
            return
        }

        val firstDate = LocalDate.ofEpochDay(lastEpochDay).plusDays(1)
        if (firstDate.isAfter(today)) return
        var date = firstDate
        while (!date.isAfter(today)) {
            incomes.filter { it.isDueOn(date) }.forEach { postRecurringIncome(it, date) }
            date = date.plusDays(1)
        }
        preferences.edit().putLong("last_income_check", today.toEpochDay()).apply()
    }

    suspend fun processRecurringExpenses() {
        val preferences = context.getSharedPreferences("finance_automation", Context.MODE_PRIVATE)
        val today = LocalDate.now()
        val lastEpochDay = preferences.getLong("last_expense_check", Long.MIN_VALUE)
        val subscriptions = dao.getActiveSubscriptions()

        subscriptions.forEach { subscription ->
            var chargeDate = subscription.nextChargeEpochDay?.let(LocalDate::ofEpochDay) ?: return@forEach
            var updated = subscription
            var guard = 0
            while (!chargeDate.isAfter(today) && guard++ < 24) {
                val shouldPost = lastEpochDay != Long.MIN_VALUE || !chargeDate.isBefore(today.minusDays(7))
                if (shouldPost) {
                    val existing = dao.findTransaction(subscription.name, subscription.accountName, chargeDate.toEpochDay())
                    if (existing == null) {
                        val posted = runCatching {
                            addMovement(
                                MovementRequest(
                                    type = "Gasto",
                                    sourceName = subscription.accountName,
                                    description = subscription.name,
                                    category = "Suscripciones",
                                    amountCents = subscription.amountCents,
                                    notes = "Cargo recurrente automático · ${subscription.frequency}",
                                    dateEpochDay = chargeDate.toEpochDay()
                                )
                            )
                        }.isSuccess
                        if (!posted) break
                    }
                }
                chargeDate = chargeDate.advance(subscription.frequency)
                updated = updated.copy(nextChargeEpochDay = chargeDate.toEpochDay(), status = "Activa")
            }
            if (updated != subscription) dao.insertSubscription(updated)
        }

        preferences.edit().putLong("last_expense_check", today.toEpochDay()).apply()
    }

    private fun LocalDate.advance(frequency: String): LocalDate = when (frequency) {
        "Semanal" -> plusWeeks(1)
        "Quincenal" -> plusDays(15)
        "Anual" -> plusYears(1)
        else -> plusMonths(1)
    }

    suspend fun processCardCycles() {
        val preferences = context.getSharedPreferences("finance_automation", Context.MODE_PRIVATE)
        val today = LocalDate.now()
        dao.getCards().forEach { initialCard ->
            val cutoffDay = initialCard.cutoffEpochDay?.let { LocalDate.ofEpochDay(it).dayOfMonth } ?: return@forEach
            val key = "last_card_cycle_${initialCard.name}"
            val stored = preferences.getLong(key, Long.MIN_VALUE)
            if (stored == Long.MIN_VALUE) {
                preferences.edit().putLong(key, latestCutoffOnOrBefore(today, cutoffDay).toEpochDay()).apply()
                return@forEach
            }
            var cutoff = LocalDate.ofEpochDay(stored).plusMonths(1).withSafeDay(cutoffDay)
            while (!cutoff.isAfter(today)) {
                closeCardCycle(initialCard.name, cutoff)
                preferences.edit().putLong(key, cutoff.toEpochDay()).apply()
                cutoff = cutoff.plusMonths(1).withSafeDay(cutoffDay)
            }
        }
    }

    private fun latestCutoffOnOrBefore(date: LocalDate, day: Int): LocalDate {
        val candidate = YearMonth.from(date).atDay(day.coerceAtMost(date.lengthOfMonth()))
        return if (candidate.isAfter(date)) candidate.minusMonths(1).withSafeDay(day) else candidate
    }

    private fun LocalDate.withSafeDay(day: Int): LocalDate = YearMonth.from(this).atDay(day.coerceAtMost(lengthOfMonth()))

    private suspend fun closeCardCycle(cardName: String, cutoff: LocalDate) {
        database.withTransaction {
            val card = dao.getCard(cardName) ?: return@withTransaction
            val plans = dao.getActiveInstallmentPlans(cardName)
            val dueGap = card.cutoffEpochDay?.let { oldCutoff ->
                card.dueEpochDay?.let { ChronoUnit.DAYS.between(LocalDate.ofEpochDay(oldCutoff), LocalDate.ofEpochDay(it)).toInt() }
            }?.coerceIn(5, 35) ?: 20
            val cycleDue = cutoff.plusDays(dueGap.toLong())
            var installmentTotal = 0L
            plans.forEach { plan ->
                val nextDue = plan.nextDueEpochDay?.let(LocalDate::ofEpochDay)
                if (nextDue != null && nextDue.isAfter(cycleDue)) return@forEach
                val payment = plan.monthlyPaymentCents.coerceAtMost(plan.pendingBalanceCents)
                if (payment > 0) {
                    installmentTotal += payment
                    val pending = (plan.pendingBalanceCents - payment).coerceAtLeast(0)
                    val current = (plan.currentPayment + 1).coerceAtMost(plan.totalMonths)
                    dao.updateInstallmentPlan(
                        plan.copy(
                            currentPayment = current,
                            pendingBalanceCents = pending,
                            remainingMonths = (plan.totalMonths - current).coerceAtLeast(0),
                            status = if (pending == 0L) "Pagado" else "Activo",
                            nextDueEpochDay = if (pending == 0L) null else (nextDue ?: cycleDue).plusMonths(1).toEpochDay()
                        )
                    )
                }
            }
            val closed = card.breakdown().closeCycle(installmentTotal)
            val statement = closed.statementCents
            dao.updateCard(
                card.withBreakdown(closed).copy(
                    cutoffEpochDay = cutoff.toEpochDay(),
                    dueEpochDay = cycleDue.toEpochDay(),
                    statementPaymentCents = statement,
                    paymentConfirmed = statement == 0L,
                    status = if (statement == 0L) "Sin pago requerido" else "Pendiente"
                )
            )
        }
    }

    private fun RecurringIncomeEntity.isDueOn(date: LocalDate): Boolean = when (cadence) {
        "Cada lunes" -> date.dayOfWeek == DayOfWeek.MONDAY
        "Último día del mes" -> date.dayOfMonth == date.lengthOfMonth()
        else -> false
    }

    private suspend fun postRecurringIncome(income: RecurringIncomeEntity, date: LocalDate) {
        val existing = dao.findTransaction(income.description, income.destinationAccount, date.toEpochDay())
        if (existing?.status == "Confirmado") return
        val account = dao.getAccount(income.destinationAccount) ?: return
        database.withTransaction {
            dao.updateAccount(account.copy(balanceCents = account.balanceCents + income.amountCents))
            if (existing != null) {
                dao.updateTransaction(existing.copy(status = "Confirmado", source = "Ingreso programado"))
            } else {
                dao.insertTransaction(
                    TransactionEntity(
                        id = "AUTO-INCOME-${income.id}-${date.toEpochDay()}",
                        dateEpochDay = date.toEpochDay(),
                        accountName = income.destinationAccount,
                        description = income.description,
                        type = "Ingreso",
                        category = "Ingresos",
                        amountCents = income.amountCents,
                        budgetImpactCents = 0,
                        isMsi = false,
                        installment = null,
                        status = "Confirmado",
                        source = "Ingreso programado",
                        notes = income.cadence
                    )
                )
            }
        }
    }

    suspend fun addTransaction(
        accountName: String,
        description: String,
        type: String,
        category: String,
        amountCents: Long,
        isMsi: Boolean = false,
        installment: String? = null,
        source: String = "Registro manual"
    ) {
        val impact = when (type) {
            "Gasto", "Comisión", "Mensualidad MSI" -> amountCents
            "Devolución" -> -amountCents
            else -> 0L
        }
        dao.insertTransaction(
            TransactionEntity(
                id = "APP-${UUID.randomUUID()}",
                dateEpochDay = LocalDate.now().toEpochDay(),
                accountName = accountName,
                description = description,
                type = type,
                category = category,
                amountCents = amountCents,
                budgetImpactCents = impact,
                isMsi = isMsi,
                installment = installment,
                status = "Confirmado",
                source = source,
                notes = null
            )
        )
    }

    suspend fun addMovement(request: MovementRequest) {
        require(request.amountCents > 0) { "Escribe un importe mayor a cero" }
        require(request.sourceName.isNotBlank()) { "Selecciona una cuenta o tarjeta" }

        database.withTransaction {
            when (request.type) {
                "Gasto" -> registerExpense(request)
                "Ingreso" -> registerIncome(request)
                "Devolución" -> registerRefund(request)
                "Pago con puntos", "Bonificación" -> registerCardBenefit(request)
                "Transferencia" -> registerTransfer(request)
                "Pago tarjeta" -> registerCardPayment(request)
                "Compra a MSI" -> registerInstallmentPurchase(request)
                else -> error("Tipo de movimiento no compatible")
            }
        }
        requestSystemBackup()
    }

    private suspend fun registerExpense(request: MovementRequest) {
        val account = dao.getAccount(request.sourceName)
        val card = dao.getCard(request.sourceName)
        require(account != null || card != null) { "No se encontró la cuenta seleccionada" }
        account?.let { dao.updateAccount(it.copy(balanceCents = it.balanceCents - request.amountCents)) }
        val updated = card?.withBreakdown(card.breakdown().charge(request.amountCents))?.withPaymentStatus()
        updated?.let { dao.updateCard(it) }
        insertMovementTransaction(request, budgetImpactCents = request.amountCents, cardEffect = card?.effectTo(updated!!))
    }

    private suspend fun registerIncome(request: MovementRequest) {
        val account = dao.getAccount(request.sourceName)
            ?: error("Los ingresos deben depositarse en una cuenta")
        dao.updateAccount(account.copy(balanceCents = account.balanceCents + request.amountCents))
        insertMovementTransaction(request, budgetImpactCents = 0)
    }

    private suspend fun registerRefund(request: MovementRequest) {
        val account = dao.getAccount(request.sourceName)
        val card = dao.getCard(request.sourceName)
        require(account != null || card != null) { "No se encontró la cuenta seleccionada" }
        account?.let { dao.updateAccount(it.copy(balanceCents = it.balanceCents + request.amountCents)) }
        val updated = card?.withBreakdown(card.breakdown().reduce(
            request.amountCents, listOf(CardBalancePart.Current, CardBalancePart.Statement)
        ))?.withPaymentStatus()
        updated?.let { dao.updateCard(it) }
        insertMovementTransaction(request, budgetImpactCents = -request.amountCents, cardEffect = card?.effectTo(updated!!))
    }

    private suspend fun registerCardBenefit(request: MovementRequest) {
        val card = dao.getCard(request.sourceName) ?: error("Selecciona una tarjeta de crédito")
        val part = when (request.benefitTarget) {
            "Saldo actual" -> CardBalancePart.Current
            "Saldo a pagar" -> CardBalancePart.Statement
            else -> error("Selecciona dónde aplicó el banco el beneficio")
        }
        val updated = card.withBreakdown(card.breakdown().reduce(
            request.amountCents, listOf(part)
        )).withPaymentStatus()
        dao.updateCard(updated)
        insertMovementTransaction(request, budgetImpactCents = 0, cardEffect = card.effectTo(updated))
    }

    private fun CardEntity.withPaymentStatus(): CardEntity = copy(
        paymentConfirmed = statementPaymentCents == 0L,
        status = if (statementPaymentCents == 0L) "Sin pago requerido" else "Pendiente"
    )

    private suspend fun registerTransfer(request: MovementRequest) {
        val destinationName = request.destinationName.orEmpty()
        require(destinationName.isNotBlank()) { "Selecciona la cuenta de destino" }
        require(destinationName != request.sourceName) { "El origen y el destino deben ser distintos" }
        val source = dao.getAccount(request.sourceName) ?: error("Selecciona una cuenta de origen")
        val destination = dao.getAccount(destinationName) ?: error("Selecciona una cuenta de destino")
        dao.updateAccount(source.copy(balanceCents = source.balanceCents - request.amountCents))
        dao.updateAccount(destination.copy(balanceCents = destination.balanceCents + request.amountCents))
        insertMovementTransaction(request.copy(notes = request.notes), budgetImpactCents = 0, relatedAccountName = destinationName)
    }

    private suspend fun registerCardPayment(request: MovementRequest) {
        val destinationName = request.destinationName.orEmpty()
        require(destinationName.isNotBlank()) { "Selecciona la tarjeta que pagarás" }
        val source = dao.getAccount(request.sourceName) ?: error("Selecciona una cuenta para pagar")
        val card = dao.getCard(destinationName) ?: error("Selecciona una tarjeta válida")
        dao.updateAccount(source.copy(balanceCents = source.balanceCents - request.amountCents))
        val updated = card.breakdown().reduce(
            request.amountCents,
            listOf(CardBalancePart.Statement, CardBalancePart.Current, CardBalancePart.Installment)
        )
        val updatedCard = card.withBreakdown(updated).withPaymentStatus()
        dao.updateCard(updatedCard)
        insertMovementTransaction(
            request,
            budgetImpactCents = 0,
            accountName = destinationName,
            relatedAccountName = request.sourceName,
            cardEffect = card.effectTo(updatedCard)
        )
    }

    private suspend fun registerInstallmentPurchase(request: MovementRequest) {
        val months = request.installmentMonths ?: error("Selecciona el número de meses")
        require(months > 1) { "Selecciona un plazo válido" }
        val card = dao.getCard(request.sourceName) ?: error("Las compras a MSI requieren una tarjeta de crédito")
        val monthly = (request.amountCents + months - 1) / months
        val purchaseDate = LocalDate.ofEpochDay(request.dateEpochDay)
        val firstDue = firstInstallmentDueDate(card, purchaseDate)
        val updated = card.breakdown().copy(installmentCents = card.msiBalanceCents + request.amountCents)
        dao.updateCard(
            card.withBreakdown(updated).copy(paymentConfirmed = false)
        )
        val planId = dao.insertInstallmentPlan(
            InstallmentPlanEntity(
                cardName = request.sourceName,
                description = request.description,
                purchaseEpochDay = request.dateEpochDay,
                originalAmountCents = request.amountCents,
                totalMonths = months,
                currentPayment = 0,
                monthlyPaymentCents = monthly,
                pendingBalanceCents = request.amountCents,
                remainingMonths = months,
                status = "Activo",
                firstDueEpochDay = firstDue.toEpochDay(),
                nextDueEpochDay = firstDue.toEpochDay()
            )
        )
        insertMovementTransaction(
            request,
            budgetImpactCents = monthly,
            isMsi = true,
            installment = "1/$months",
            installmentPlanId = planId
        )
    }

    private fun firstInstallmentDueDate(card: CardEntity, purchaseDate: LocalDate): LocalDate {
        val cutoffDay = card.cutoffEpochDay?.let { LocalDate.ofEpochDay(it).dayOfMonth } ?: purchaseDate.dayOfMonth
        val currentMonthCutoff = YearMonth.from(purchaseDate).atDay(cutoffDay.coerceAtMost(purchaseDate.lengthOfMonth()))
        val firstCutoff = if (purchaseDate.isAfter(currentMonthCutoff)) {
            currentMonthCutoff.plusMonths(1).withSafeDay(cutoffDay)
        } else {
            currentMonthCutoff
        }
        val dueGap = card.cutoffEpochDay?.let { cutoff ->
            card.dueEpochDay?.let { due -> ChronoUnit.DAYS.between(LocalDate.ofEpochDay(cutoff), LocalDate.ofEpochDay(due)).toInt() }
        }?.coerceIn(5, 35) ?: 20
        return firstCutoff.plusDays(dueGap.toLong())
    }

    private suspend fun insertMovementTransaction(
        request: MovementRequest,
        budgetImpactCents: Long,
        isMsi: Boolean = false,
        installment: String? = null,
        accountName: String = request.sourceName,
        relatedAccountName: String? = request.destinationName,
        installmentPlanId: Long? = null,
        cardEffect: String? = null
    ) {
        dao.insertTransaction(
            TransactionEntity(
                id = "APP-${UUID.randomUUID()}",
                dateEpochDay = request.dateEpochDay,
                accountName = accountName,
                description = request.description.ifBlank { "Movimiento" },
                type = request.type,
                category = request.category,
                amountCents = request.amountCents,
                budgetImpactCents = budgetImpactCents,
                isMsi = isMsi,
                installment = installment,
                status = "Confirmado",
                source = "Registro manual",
                notes = request.notes,
                relatedAccountName = relatedAccountName,
                installmentPlanId = installmentPlanId,
                cardEffect = cardEffect,
                benefitTarget = request.benefitTarget
            )
        )
    }

    suspend fun confirmDetection(item: DetectedMovementEntity, editedRequest: MovementRequest? = null) {
        val date = java.time.Instant.ofEpochMilli(item.postedAtMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        val request = editedRequest ?: MovementRequest(
                sourceName = item.accountName,
                destinationName = item.relatedAccountName,
                description = item.merchant,
                type = item.movementType,
                category = item.suggestedCategory,
                amountCents = item.amountCents,
                notes = "Detectado desde notificación",
                dateEpochDay = date.toEpochDay()
            )
        addMovement(request)
        dao.updateDetected(item.copy(status = "Confirmado"))
    }

    suspend fun dismissDetection(item: DetectedMovementEntity) {
        dao.updateDetected(item.copy(status = "Descartado"))
    }

    suspend fun deleteIgnoredNotification(item: IgnoredNotificationEntity) {
        dao.deleteIgnoredNotification(item)
    }

    suspend fun clearIgnoredNotifications() {
        dao.clearIgnoredNotifications()
    }

    suspend fun addGoal(name: String, targetCents: Long) {
        dao.insertGoal(
            GoalEntity(
                name = name,
                targetCents = targetCents,
                savedCents = 0,
                targetEpochDay = null,
                monthlyContributionCents = 0,
                priority = "Media",
                status = "Activa"
            )
        )
    }

    suspend fun saveAccount(item: AccountEntity) { dao.insertAccount(item); requestSystemBackup() }

    suspend fun reconcileAccount(item: AccountEntity, actualBalanceCents: Long, reason: String) {
        database.withTransaction {
            val stored = dao.getAccount(item.name) ?: error("No se encontró la cuenta")
            val difference = actualBalanceCents - stored.balanceCents
            dao.updateAccount(stored.copy(balanceCents = actualBalanceCents, updatedAtEpochDay = LocalDate.now().toEpochDay()))
            if (difference != 0L) {
                dao.insertTransaction(
                    TransactionEntity(
                        id = "RECON-${UUID.randomUUID()}",
                        dateEpochDay = LocalDate.now().toEpochDay(),
                        accountName = stored.name,
                        description = "Ajuste de saldo · ${reason.ifBlank { "Conciliación" }}",
                        type = if (difference > 0) "Ingreso" else "Gasto",
                        category = "Ajustes",
                        amountCents = kotlin.math.abs(difference),
                        budgetImpactCents = 0,
                        isMsi = false,
                        installment = null,
                        status = "Confirmado",
                        source = "Conciliación manual",
                        notes = reconciliationNotes(reason, false, stored.reconciliationValues(), mapOf("Saldo" to actualBalanceCents))
                    )
                )
            }
        }
        requestSystemBackup()
    }

    suspend fun deleteAccount(item: AccountEntity) { dao.deleteAccount(item); requestSystemBackup() }

    suspend fun saveCard(item: CardEntity) {
        require(item.creditLimitCents >= 0 && item.availableCents >= 0) { "El límite y el disponible no pueden ser negativos" }
        require(listOf(item.statementPaymentCents, item.unbilledCents, item.msiBalanceCents, item.creditBalanceCents).all { it >= 0 }) {
            "Los importes del desglose no pueden ser negativos"
        }
        require(item.balanceCheck().isConsistent) {
            "El utilizado debe ser la suma del desglose menos saldo a favor; el disponible debe coincidir con el límite menos utilizado"
        }
        dao.insertCard(item)
        requestSystemBackup()
    }

    suspend fun reconcileCard(
        item: CardEntity,
        statementPaymentCents: Long,
        currentBalanceCents: Long,
        msiBalanceCents: Long,
        availableCents: Long,
        reason: String,
        creditBalanceCents: Long = item.creditBalanceCents
    ) {
        require(statementPaymentCents >= 0 && currentBalanceCents >= 0 && msiBalanceCents >= 0 && availableCents >= 0 && creditBalanceCents >= 0) {
            "Los importes no pueden ser negativos"
        }
        val actualDebtCents = statementPaymentCents + currentBalanceCents + msiBalanceCents - creditBalanceCents
        database.withTransaction {
            val stored = dao.getCard(item.name) ?: error("No se encontró la tarjeta")
            val difference = actualDebtCents - stored.balanceCents
            dao.updateCard(
                stored.copy(
                    balanceCents = actualDebtCents,
                    creditBalanceCents = creditBalanceCents,
                    msiBalanceCents = msiBalanceCents,
                    statementPaymentCents = statementPaymentCents,
                    unbilledCents = currentBalanceCents,
                    availableCents = availableCents,
                    paymentConfirmed = statementPaymentCents == 0L,
                    status = if (statementPaymentCents == 0L) "Sin pago requerido" else "Pendiente"
                )
            )
            if (difference != 0L || stored.breakdown() != CardBreakdown(statementPaymentCents, currentBalanceCents, msiBalanceCents, creditBalanceCents) || stored.availableCents != availableCents) {
                dao.insertTransaction(
                    TransactionEntity(
                        id = "RECON-${UUID.randomUUID()}",
                        dateEpochDay = LocalDate.now().toEpochDay(),
                        accountName = stored.name,
                        description = "Ajuste de deuda · ${reason.ifBlank { "Conciliación" }}",
                        type = if (difference > 0) "Gasto" else "Devolución",
                        category = "Ajustes",
                        amountCents = kotlin.math.abs(difference),
                        budgetImpactCents = 0,
                        isMsi = false,
                        installment = null,
                        status = "Confirmado",
                        source = "Conciliación manual",
                        notes = reconciliationNotes(
                            reason, true, stored.reconciliationValues(), stored.copy(
                                balanceCents = actualDebtCents, statementPaymentCents = statementPaymentCents,
                                unbilledCents = currentBalanceCents, msiBalanceCents = msiBalanceCents,
                                creditBalanceCents = creditBalanceCents, availableCents = availableCents
                            ).reconciliationValues()
                        )
                    )
                )
            }
        }
        requestSystemBackup()
    }

    suspend fun deleteCard(item: CardEntity) { dao.deleteCard(item); requestSystemBackup() }

    suspend fun saveBudget(category: String, limitCents: Long?) { dao.insertBudget(BudgetEntity(category, limitCents)); requestSystemBackup() }

    suspend fun saveCategory(item: CategoryEntity) { dao.insertCategory(item); requestSystemBackup() }

    suspend fun deleteCategory(item: CategoryEntity) { dao.deleteCategory(item); requestSystemBackup() }

    suspend fun saveMerchantRule(item: MerchantRuleEntity) { dao.insertMerchantRule(item); requestSystemBackup() }

    suspend fun deleteMerchantRule(item: MerchantRuleEntity) { dao.deleteMerchantRule(item); requestSystemBackup() }

    suspend fun saveSubscription(item: SubscriptionEntity) { dao.insertSubscription(item); requestSystemBackup() }

    suspend fun deleteSubscription(item: SubscriptionEntity) { dao.deleteSubscription(item); requestSystemBackup() }

    suspend fun saveGoal(item: GoalEntity) { dao.insertGoal(item); requestSystemBackup() }

    suspend fun deleteGoal(item: GoalEntity) { dao.deleteGoal(item); requestSystemBackup() }

    suspend fun saveDebt(item: DebtEntity) { dao.insertDebt(item); requestSystemBackup() }

    suspend fun deleteDebt(item: DebtEntity) { dao.deleteDebt(item); requestSystemBackup() }

    suspend fun saveInvestment(item: InvestmentEntity) { dao.insertInvestment(item); requestSystemBackup() }

    suspend fun deleteInvestment(item: InvestmentEntity) { dao.deleteInvestment(item); requestSystemBackup() }

    suspend fun saveRecurringIncome(item: RecurringIncomeEntity) { dao.insertRecurringIncome(item); requestSystemBackup() }

    suspend fun deleteRecurringIncome(item: RecurringIncomeEntity) { dao.deleteRecurringIncome(item); requestSystemBackup() }

    suspend fun saveDebtor(item: DebtorEntity) {
        require(item.name.isNotBlank()) { "Escribe el nombre de la persona" }
        if (item.id == 0L) dao.insertDebtor(item.copy(name = item.name.trim())) else dao.updateDebtor(item.copy(name = item.name.trim()))
        requestSystemBackup()
    }

    suspend fun deleteDebtor(item: DebtorEntity) {
        database.withTransaction {
            dao.deleteReceivablesForDebtor(item.id)
            dao.deleteDebtor(item)
        }
        requestSystemBackup()
    }

    suspend fun saveReceivable(item: ReceivableEntity) {
        require(dao.getDebtor(item.debtorId) != null) { "No se encontró al deudor" }
        require(item.name.isNotBlank()) { "Escribe el concepto de la deuda" }
        require(item.amountCents > 0) { "Escribe un importe válido" }
        val cleaned = item.copy(name = item.name.trim(), category = item.category.ifBlank { "Otro" })
        if (item.id == 0L) dao.insertReceivable(cleaned) else dao.updateReceivable(cleaned)
        requestSystemBackup()
    }

    suspend fun deleteReceivable(item: ReceivableEntity) {
        require(item.status != "Pagada") { "Una deuda pagada conserva su vínculo con el ingreso" }
        dao.deleteReceivable(item)
        requestSystemBackup()
    }

    suspend fun markReceivablePaid(item: ReceivableEntity, destinationAccountName: String?, linkedTransactionId: String?) {
        database.withTransaction {
            val stored = dao.getReceivable(item.id) ?: error("No se encontró la deuda")
            require(stored.status != "Pagada") { "Esta deuda ya está pagada" }
            if (linkedTransactionId != null) {
                val transaction = dao.getTransaction(linkedTransactionId) ?: error("No se encontró el ingreso")
                require(transaction.status == "Confirmado" && transaction.type in listOf("Ingreso", "Devolución")) { "Selecciona un ingreso confirmado" }
                require(transaction.amountCents == stored.amountCents) { "El ingreso debe coincidir con ${stored.amountCents / 100.0}" }
                dao.updateReceivable(
                    stored.copy(
                        status = "Pagada",
                        paidEpochDay = transaction.dateEpochDay,
                        destinationAccountName = transaction.accountName,
                        linkedTransactionId = transaction.id
                    )
                )
            } else {
                val accountName = destinationAccountName?.takeIf { it.isNotBlank() } ?: error("Selecciona la cuenta que recibió el pago")
                val account = dao.getAccount(accountName) ?: error("No se encontró la cuenta")
                val debtor = dao.getDebtor(stored.debtorId) ?: error("No se encontró al deudor")
                val transactionId = "DEUDOR-${UUID.randomUUID()}"
                val today = LocalDate.now().toEpochDay()
                dao.updateAccount(account.copy(balanceCents = account.balanceCents + stored.amountCents, updatedAtEpochDay = today))
                dao.insertTransaction(
                    TransactionEntity(
                        id = transactionId,
                        dateEpochDay = today,
                        accountName = accountName,
                        description = "Pago de ${debtor.name}: ${stored.name}",
                        type = "Ingreso",
                        category = "Cobros",
                        amountCents = stored.amountCents,
                        budgetImpactCents = 0,
                        isMsi = false,
                        installment = null,
                        status = "Confirmado",
                        source = "Cobro de deuda",
                        notes = stored.notes,
                        relatedAccountName = null,
                        installmentPlanId = null
                    )
                )
                dao.updateReceivable(
                    stored.copy(
                        status = "Pagada",
                        paidEpochDay = today,
                        destinationAccountName = accountName,
                        linkedTransactionId = transactionId
                    )
                )
            }
        }
        requestSystemBackup()
    }

    suspend fun replaceMovement(item: TransactionEntity, request: MovementRequest) {
        require(item.source != "Conciliación manual") { "Corrige este ajuste desde Conciliar saldos" }
        require(request.amountCents > 0) { "Escribe un importe válido" }
        if (item.source == "Migrado de Google Sheets") {
            dao.updateTransaction(
                item.copy(
                    dateEpochDay = request.dateEpochDay,
                    accountName = request.sourceName,
                    description = request.description,
                    type = request.type,
                    category = request.category,
                    amountCents = request.amountCents,
                    budgetImpactCents = budgetImpactFor(request.type, request.amountCents),
                    notes = request.notes,
                    relatedAccountName = request.destinationName
                )
            )
            requestSystemBackup()
            return
        }
        database.withTransaction {
            val stored = dao.getTransaction(item.id) ?: error("Este movimiento ya fue eliminado o modificado")
            require(stored == item) { "El movimiento cambió; vuelve a abrirlo" }
            reverseMovement(stored)
            dao.deleteTransaction(stored)
            when (request.type) {
                "Gasto" -> registerExpense(request)
                "Ingreso" -> registerIncome(request)
                "Devolución" -> registerRefund(request)
                "Pago con puntos", "Bonificación" -> registerCardBenefit(request)
                "Transferencia" -> registerTransfer(request)
                "Pago tarjeta" -> registerCardPayment(request)
                "Compra a MSI" -> registerInstallmentPurchase(request)
                else -> error("Tipo de movimiento no compatible")
            }
        }
        requestSystemBackup()
    }

    suspend fun deleteMovement(item: TransactionEntity) {
        require(item.source != "Migrado de Google Sheets") { "Los movimientos importados se pueden editar, pero no eliminar" }
        require(item.source != "Conciliación manual") { "Los ajustes de conciliación se corrigen con una nueva conciliación" }
        database.withTransaction {
            val stored = dao.getTransaction(item.id) ?: error("Este movimiento ya fue eliminado o modificado")
            require(stored == item) { "El movimiento cambió; vuelve a abrirlo" }
            reverseMovement(stored)
            dao.deleteTransaction(stored)
        }
        requestSystemBackup()
    }

    private fun budgetImpactFor(type: String, amountCents: Long): Long = when (type) {
        "Gasto", "Comisión", "Mensualidad MSI", "Compra a MSI" -> amountCents
        "Devolución" -> -amountCents
        else -> 0
    }

    private suspend fun reverseMovement(item: TransactionEntity) {
        if (item.cardEffect != null) {
            val card = dao.getCard(item.accountName) ?: error("No se encontró la tarjeta")
            dao.updateCard(card.reverseEffect(item.cardEffect))
            if (item.type == "Pago tarjeta") {
                val source = item.relatedAccountName?.let { dao.getAccount(it) } ?: error("No se encontró la cuenta de pago")
                dao.updateAccount(source.copy(balanceCents = source.balanceCents + item.amountCents))
            }
            return
        }
        when (item.type) {
            "Gasto" -> {
                dao.getAccount(item.accountName)?.let { dao.updateAccount(it.copy(balanceCents = it.balanceCents + item.amountCents)) }
                dao.getCard(item.accountName)?.let { card ->
                    val updated = card.breakdown().reduce(
                        item.amountCents,
                        listOf(CardBalancePart.Current, CardBalancePart.Statement, CardBalancePart.Installment)
                    )
                    dao.updateCard(card.withBreakdown(updated))
                }
            }
            "Ingreso" -> dao.getAccount(item.accountName)?.let { dao.updateAccount(it.copy(balanceCents = it.balanceCents - item.amountCents)) }
            "Devolución" -> {
                dao.getAccount(item.accountName)?.let { dao.updateAccount(it.copy(balanceCents = it.balanceCents - item.amountCents)) }
                dao.getCard(item.accountName)?.let { card ->
                    val updated = card.breakdown().copy(currentCents = card.unbilledCents + item.amountCents)
                    dao.updateCard(card.withBreakdown(updated))
                }
            }
            "Transferencia" -> {
                val destinationName = item.relatedAccountName ?: item.notes?.substringAfter("Destino:", "")?.trim()
                val source = dao.getAccount(item.accountName)
                val destination = destinationName?.let { dao.getAccount(it) }
                if (source != null && destination != null) {
                    dao.updateAccount(source.copy(balanceCents = source.balanceCents + item.amountCents))
                    dao.updateAccount(destination.copy(balanceCents = destination.balanceCents - item.amountCents))
                }
            }
            "Pago tarjeta" -> {
                val sourceName = item.relatedAccountName ?: item.notes?.substringAfter("Pagada desde:", "")?.trim()
                sourceName?.let { name -> dao.getAccount(name)?.let { dao.updateAccount(it.copy(balanceCents = it.balanceCents + item.amountCents)) } }
                dao.getCard(item.accountName)?.let { card ->
                    val updated = card.breakdown().copy(statementCents = card.statementPaymentCents + item.amountCents)
                    dao.updateCard(card.withBreakdown(updated).copy(paymentConfirmed = false, status = "Pendiente"))
                }
            }
            "Compra a MSI" -> {
                val plan = item.installmentPlanId?.let { dao.getInstallmentPlan(it) }
                    ?: dao.findInstallmentPlan(item.accountName, item.description, item.amountCents)
                    ?: error("No se encontró el plan MSI relacionado")
                require(plan.currentPayment == 0) { "No se puede eliminar una compra MSI que ya generó mensualidades" }
                dao.getCard(item.accountName)?.let { card ->
                    val updated = card.breakdown().copy(
                        installmentCents = (card.msiBalanceCents - plan.pendingBalanceCents).coerceAtLeast(0)
                    )
                    dao.updateCard(card.withBreakdown(updated))
                }
                dao.deleteInstallmentPlan(plan)
            }
            "Pago MSI" -> {
                val plan = item.installmentPlanId?.let { dao.getInstallmentPlan(it) } ?: return
                item.relatedAccountName?.let { name -> dao.getAccount(name)?.let { dao.updateAccount(it.copy(balanceCents = it.balanceCents + item.amountCents)) } }
                dao.getCard(item.accountName)?.let { card ->
                    val updated = card.breakdown().copy(installmentCents = card.msiBalanceCents + item.amountCents)
                    dao.updateCard(card.withBreakdown(updated))
                }
                val pending = plan.pendingBalanceCents + item.amountCents
                val remaining = ((pending + plan.monthlyPaymentCents - 1) / plan.monthlyPaymentCents).toInt()
                dao.updateInstallmentPlan(plan.copy(pendingBalanceCents = pending, remainingMonths = remaining, currentPayment = (plan.totalMonths - remaining).coerceAtLeast(0), status = "Activo"))
            }
        }
    }

    suspend fun payInstallmentPlan(plan: InstallmentPlanEntity, sourceAccountName: String, amountCents: Long) {
        require(amountCents > 0) { "Escribe un importe válido" }
        require(amountCents <= plan.pendingBalanceCents) { "El abono supera el saldo pendiente" }
        database.withTransaction {
            val currentPlan = dao.getInstallmentPlan(plan.id) ?: error("No se encontró el plan")
            require(currentPlan == plan) { "El plan cambió; vuelve a abrirlo antes de pagar" }
            require(amountCents <= currentPlan.pendingBalanceCents) { "El abono supera el saldo pendiente actual" }
            require(currentPlan.monthlyPaymentCents > 0) { "Corrige la mensualidad antes de registrar un abono" }
            val account = dao.getAccount(sourceAccountName) ?: error("Selecciona una cuenta")
            val card = dao.getCard(currentPlan.cardName) ?: error("No se encontró la tarjeta")
            require(card.msiBalanceCents >= amountCents) { "El abono supera el saldo MSI de la tarjeta; concilia primero los saldos y planes" }
            require(account.balanceCents >= amountCents) { "La cuenta no tiene saldo suficiente" }
            val pending = currentPlan.pendingBalanceCents - amountCents
            val remaining = if (pending == 0L) 0 else ((pending + currentPlan.monthlyPaymentCents - 1) / currentPlan.monthlyPaymentCents).toInt()
            dao.updateAccount(account.copy(balanceCents = account.balanceCents - amountCents))
            val updated = card.breakdown().copy(installmentCents = card.msiBalanceCents - amountCents)
            dao.updateCard(card.withBreakdown(updated))
            dao.updateInstallmentPlan(currentPlan.copy(
                pendingBalanceCents = pending,
                remainingMonths = remaining,
                currentPayment = (currentPlan.totalMonths - remaining).coerceAtMost(currentPlan.totalMonths),
                status = if (pending == 0L) "Pagado" else "Activo"
            ))
            dao.insertTransaction(
                TransactionEntity(
                    id = "APP-${UUID.randomUUID()}",
                    dateEpochDay = LocalDate.now().toEpochDay(),
                    accountName = currentPlan.cardName,
                    description = if (pending == 0L) "Liquidación ${currentPlan.description}" else "Abono ${currentPlan.description}",
                    type = "Pago MSI",
                    category = "Pagos",
                    amountCents = amountCents,
                    budgetImpactCents = 0,
                    isMsi = true,
                    installment = null,
                    status = "Confirmado",
                    source = "Registro manual",
                    notes = "Pago anticipado",
                    relatedAccountName = sourceAccountName,
                    installmentPlanId = currentPlan.id
                )
            )
        }
        requestSystemBackup()
    }

    suspend fun saveInstallmentPlan(original: InstallmentPlanEntity, updated: InstallmentPlanEntity, preserveCardBalances: Boolean = false) {
        require(updated.id == original.id) { "No se pudo identificar el plan" }
        require(updated.originalAmountCents > 0) { "El importe original no es válido" }
        require(updated.monthlyPaymentCents > 0) { "La mensualidad no es válida" }
        require(updated.pendingBalanceCents in 0..updated.originalAmountCents) { "El saldo pendiente no es válido" }
        require(updated.totalMonths in 1..60) { "El plazo debe ser de 1 a 60 meses" }
        require(updated.remainingMonths == updated.totalMonths - updated.currentPayment) { "Las mensualidades restantes no coinciden" }
        require(updated.pendingBalanceCents == 0L || updated.remainingMonths > 0) { "No marques el plan como terminado si aún tiene saldo" }
        require((updated.pendingBalanceCents == 0L) == (updated.status == "Pagado")) { "El estado no coincide con el saldo pendiente" }
        require(updated.currentPayment in 0..updated.totalMonths) { "El progreso no es válido" }
        database.withTransaction {
            val stored = dao.getInstallmentPlan(original.id) ?: error("No se encontró el plan")
            require(stored == original) { "El plan cambió; vuelve a abrirlo antes de guardar" }
            require(dao.getCard(updated.cardName) != null) { "No se encontró la tarjeta seleccionada" }
            if (!preserveCardBalances) {
                require(dao.getCard(stored.cardName) != null) { "No se encontró la tarjeta de origen. Corrige solo el plan desde Revisar MSI" }
                if (stored.cardName == updated.cardName) {
                    dao.getCard(stored.cardName)?.let { card ->
                        val correctedMsi = card.msiBalanceCents - stored.pendingBalanceCents + updated.pendingBalanceCents
                        require(correctedMsi >= 0) { "El cambio supera el saldo MSI de la tarjeta. Corrige solo el plan desde Revisar MSI" }
                        dao.updateCard(card.withBreakdown(card.breakdown().copy(installmentCents = correctedMsi)))
                    }
                } else {
                    dao.getCard(stored.cardName)?.let { oldCard ->
                        val correctedMsi = oldCard.msiBalanceCents - stored.pendingBalanceCents
                        require(correctedMsi >= 0) { "El plan supera el saldo MSI de origen. Corrige solo el plan desde Revisar MSI" }
                        dao.updateCard(oldCard.withBreakdown(oldCard.breakdown().copy(installmentCents = correctedMsi)))
                    }
                    dao.getCard(updated.cardName)?.let { newCard ->
                        val correctedMsi = newCard.msiBalanceCents + updated.pendingBalanceCents
                        dao.updateCard(newCard.withBreakdown(newCard.breakdown().copy(installmentCents = correctedMsi)))
                    }
                }
            }
            dao.updateInstallmentPlan(updated)
        }
        requestSystemBackup()
    }

    suspend fun restoreBackup(json: String) {
        val root = JSONObject(json)
        require(root.optInt("formatVersion", 1) in 1..8) { "Este respaldo requiere una versión más reciente de la app" }
        require(root.has("transactions") && root.has("accounts") && root.has("cards")) { "El archivo no es un respaldo válido" }
        val accounts = root.getJSONArray("accounts").let { array ->
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                AccountEntity(
                    name = item.getString("name"),
                    type = item.optString("type", "Débito"),
                    balanceCents = item.optLong("balanceCents"),
                    includedInAvailable = item.optBoolean("included", true),
                    updatedAtEpochDay = item.optNullableLong("updatedAtEpochDay"),
                    colorHex = item.optLong("colorHex", 0xFF58E0C2),
                    iconKey = item.optString("iconKey", "wallet")
                )
            }
        }
        val cards = root.getJSONArray("cards").let { array ->
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val limit = item.optLong("limitCents")
                val balance = item.optLong("balanceCents")
                CardEntity(
                    name = item.getString("name"),
                    institution = item.optString("institution"),
                    lastFour = item.optNullableString("lastFour"),
                    cutoffEpochDay = item.optNullableLong("cutoffEpochDay"),
                    dueEpochDay = item.optNullableLong("dueEpochDay"),
                    creditLimitCents = limit,
                    balanceCents = balance,
                    msiBalanceCents = item.optLong("msiBalanceCents"),
                    statementPaymentCents = item.optLong("paymentCents"),
                    minimumPaymentCents = item.optNullableLong("minimumPaymentCents"),
                    availableCents = item.optLong("availableCents", (limit - balance).coerceAtLeast(0)),
                    paymentConfirmed = item.optBoolean("paymentConfirmed"),
                    status = item.optString("status", "Pendiente"),
                    colorHex = item.optLong("colorHex", 0xFF8EAEFF),
                    unbilledCents = item.optLong("unbilledCents"),
                    iconKey = item.optString("iconKey", "card"),
                    creditBalanceCents = if (item.has("creditBalanceCents")) item.getLong("creditBalanceCents") else 0L
                )
            }
        }
        val transactions = root.getJSONArray("transactions").let { array ->
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val type = item.optString("type", "Gasto")
                val amount = item.optLong("amountCents")
                TransactionEntity(
                    id = item.optString("id", "RESTORE-${UUID.randomUUID()}"),
                    dateEpochDay = item.optLong("dateEpochDay", LocalDate.now().toEpochDay()),
                    accountName = item.optString("account"),
                    description = item.optString("description", "Movimiento restaurado"),
                    type = type,
                    category = item.optString("category", "Sin clasificar"),
                    amountCents = amount,
                    budgetImpactCents = item.optLong("budgetImpactCents", budgetImpactFor(type, amount)),
                    isMsi = item.optBoolean("isMsi"),
                    installment = item.optNullableString("installment"),
                    status = item.optString("status", "Confirmado"),
                    source = item.optString("source", "Respaldo restaurado"),
                    notes = item.optNullableString("notes"),
                    createdAtMillis = item.optLong("createdAtMillis", System.currentTimeMillis()),
                    relatedAccountName = item.optNullableString("relatedAccountName"),
                    installmentPlanId = item.optNullableLong("installmentPlanId"),
                    cardEffect = item.optNullableString("cardEffect"),
                    benefitTarget = item.optString("benefitTarget", "Saldo actual")
                )
            }
        }
        val plans = root.optJSONArray("installmentPlans")?.let { array ->
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                InstallmentPlanEntity(
                    id = item.optLong("id"), cardName = item.getString("cardName"), description = item.getString("description"),
                    purchaseEpochDay = item.optLong("purchaseEpochDay"), originalAmountCents = item.optLong("originalAmountCents"),
                    totalMonths = item.optInt("totalMonths"), currentPayment = item.optInt("currentPayment"),
                    monthlyPaymentCents = item.optLong("monthlyPaymentCents"), pendingBalanceCents = item.optLong("pendingBalanceCents"),
                    remainingMonths = item.optInt("remainingMonths"), status = item.optString("status", "Activo"),
                    firstDueEpochDay = item.optNullableLong("firstDueEpochDay"), nextDueEpochDay = item.optNullableLong("nextDueEpochDay")
                )
            }
        }.orEmpty()
        database.withTransaction {
            dao.clearDetectedMovements(); dao.clearIgnoredNotifications()
            dao.clearTransactions(); dao.clearInstallmentPlans(); dao.clearAccounts(); dao.clearCards(); dao.clearBudgets()
            dao.clearSubscriptions(); dao.clearGoals(); dao.clearDebts(); dao.clearInvestments(); dao.clearRecurringIncome(); dao.clearCategories(); dao.clearMerchantRules(); dao.clearReceivables(); dao.clearDebtors()
            dao.insertAccounts(accounts); dao.insertCards(cards); dao.insertTransactions(transactions)
            if (plans.isNotEmpty()) dao.insertInstallmentPlans(plans)
            root.optJSONArray("budgets")?.let { array -> dao.insertBudgets((0 until array.length()).map { i -> array.getJSONObject(i).let { BudgetEntity(it.getString("category"), it.optNullableLong("limitCents")) } }) }
            root.optJSONArray("subscriptions")?.let { array -> dao.insertSubscriptions((0 until array.length()).map { i -> array.getJSONObject(i).let { SubscriptionEntity(it.getString("name"), it.optLong("amountCents"), it.optString("frequency", "Mensual"), it.optNullableLong("nextChargeEpochDay"), it.optString("account"), it.optString("status", "Activa")) } }) }
            root.optJSONArray("goals")?.let { array -> (0 until array.length()).forEach { i -> array.getJSONObject(i).let { dao.insertGoal(GoalEntity(it.optLong("id"), it.getString("name"), it.optLong("targetCents"), it.optLong("savedCents"), it.optNullableLong("targetEpochDay"), it.optLong("monthlyContributionCents"), it.optString("priority", "Media"), it.optString("status", "Activa"))) } } }
            root.optJSONArray("debts")?.let { array -> (0 until array.length()).forEach { i -> array.getJSONObject(i).let { dao.insertDebt(DebtEntity(it.optLong("id"), it.getString("name"), it.optString("creditor"), it.optLong("balanceCents"), it.optLong("paymentCents"), it.optNullableLong("nextPaymentEpochDay"), if (it.isNull("annualRate")) null else it.optDouble("annualRate"), it.optString("status", "Activa"))) } } }
            root.optJSONArray("investments")?.let { array -> (0 until array.length()).forEach { i -> array.getJSONObject(i).let { dao.insertInvestment(InvestmentEntity(it.optLong("id"), it.getString("name"), it.optString("institution"), it.optLong("contributedCents"), it.optLong("currentValueCents"), it.optNullableLong("updatedAtEpochDay"))) } } }
            root.optJSONArray("recurringIncome")?.let { array -> dao.insertRecurringIncome((0 until array.length()).map { i -> array.getJSONObject(i).let { RecurringIncomeEntity(it.optLong("id"), it.getString("description"), it.optLong("amountCents"), it.optString("cadence"), it.optString("destination"), it.optBoolean("enabled", true)) } }) }
            root.optJSONArray("categories")?.let { array -> dao.insertCategories((0 until array.length()).map { i -> array.getJSONObject(i).let { CategoryEntity(it.getString("name"), it.optNullableString("parentName"), it.optLong("colorHex", 0xFF64748B), it.optString("iconKey", "tag"), it.optBoolean("active", true)) } }) }
            root.optJSONArray("merchantRules")?.let { array -> (0 until array.length()).forEach { i -> array.getJSONObject(i).let { dao.insertMerchantRule(MerchantRuleEntity(it.optLong("id"), it.getString("keyword"), it.optNullableString("accountName"), it.getString("category"), it.optString("movementType", "Gasto"), it.optNullableString("merchantName"), it.optBoolean("enabled", true))) } } }
            root.optJSONArray("debtors")?.let { array -> dao.insertDebtors((0 until array.length()).map { i -> array.getJSONObject(i).let { DebtorEntity(it.optLong("id"), it.getString("name"), it.optNullableString("notes"), it.optLong("colorHex", 0xFF58DCC0), it.optLong("createdAtEpochDay", LocalDate.now().toEpochDay())) } }) }
            root.optJSONArray("receivables")?.let { array -> dao.insertReceivables((0 until array.length()).map { i -> array.getJSONObject(i).let { ReceivableEntity(it.optLong("id"), it.optLong("debtorId"), it.getString("name"), it.optString("category", "Otro"), it.optLong("amountCents"), it.optNullableString("notes"), it.optNullableLong("dueEpochDay"), it.optString("status", "Pendiente"), it.optNullableLong("paidEpochDay"), it.optNullableString("destinationAccountName"), it.optNullableString("linkedTransactionId")) } }) }
            root.optJSONArray("pendingDetections")?.let { array ->
                (0 until array.length()).forEach { i ->
                    val it = array.getJSONObject(i)
                    dao.insertDetected(DetectedMovementEntity(
                        id = it.optLong("id"), fingerprint = it.getString("fingerprint"), packageName = it.getString("packageName"),
                        accountName = it.getString("accountName"), merchant = it.getString("merchant"), amountCents = it.getLong("amountCents"),
                        suggestedCategory = it.getString("suggestedCategory"), postedAtMillis = it.getLong("postedAtMillis"),
                        rawTitle = it.getString("rawTitle"), rawText = it.getString("rawText"), status = "Pendiente",
                        movementType = it.optString("movementType", "Gasto"), relatedAccountName = it.optNullableString("relatedAccountName")
                    ))
                }
            }
            root.optJSONArray("ignoredNotifications")?.let { array ->
                (0 until array.length()).forEach { i ->
                    val it = array.getJSONObject(i)
                    dao.insertIgnoredNotification(IgnoredNotificationEntity(
                        id = it.optLong("id"), fingerprint = it.getString("fingerprint"), packageName = it.getString("packageName"),
                        bankName = it.getString("bankName"), title = it.getString("title"), text = it.getString("text"),
                        reason = it.getString("reason"), postedAtMillis = it.getLong("postedAtMillis")
                    ))
                }
            }
        }
        requestSystemBackup()
    }

    private fun JSONObject.optNullableLong(name: String): Long? = if (!has(name) || isNull(name)) null else getLong(name)
    private fun JSONObject.optNullableString(name: String): String? = if (!has(name) || isNull(name)) null else getString(name)

}


