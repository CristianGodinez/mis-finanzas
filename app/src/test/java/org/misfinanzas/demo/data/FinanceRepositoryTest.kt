package org.misfinanzas.demo.data

import android.app.Application
import androidx.room.Room
import org.misfinanzas.demo.ui.financeBackupJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class FinanceRepositoryTest {
    private lateinit var database: FinanceDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var dao: FinanceDao

    @Before
    fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).allowMainThreadQueries().build()
        dao = database.financeDao()
        repository = FinanceRepository(context, database)
        dao.insertAccounts(listOf(account("Cuenta", 100000), account("Destino", 20000)))
        dao.insertCards(listOf(card()))
    }

    @After
    fun teardown() { database.close() }

    @Test
    fun pointsReduceSelectedBalanceWithoutTouchingCashBudgetOrMsi() = runBlocking {
        repository.addMovement(movement("Pago con puntos", 6000))
        val card = dao.getCard("Tarjeta")!!
        assertEquals(94000L, card.unbilledCents)
        assertEquals(894000L, card.balanceCents)
        assertEquals(1106000L, card.availableCents)
        assertEquals(50000L, card.statementPaymentCents)
        assertEquals(750000L, card.msiBalanceCents)
        assertEquals(100000L, dao.getAccount("Cuenta")!!.balanceCents)
        assertEquals(0L, dao.observeTransactions().first().single().budgetImpactCents)
        assertTrue(card.balanceCheck().isConsistent)
    }

    @Test
    fun benefitCanReduceStatementAndRemainTraceable() = runBlocking {
        repository.addMovement(movement("Bonificación", 10000).copy(benefitTarget = "Saldo a pagar"))
        assertEquals(40000L, dao.getCard("Tarjeta")!!.statementPaymentCents)
        val transaction = dao.observeTransactions().first().single()
        assertEquals("Saldo a pagar", transaction.benefitTarget)
        repository.deleteMovement(transaction)
        assertEquals(card().breakdown(), dao.getCard("Tarjeta")!!.breakdown())
    }

    @Test
    fun editingAndDeletingBenefitRestoresExactlyOnce() = runBlocking {
        repository.addMovement(movement("Pago con puntos", 6000))
        val original = dao.observeTransactions().first().single()
        repository.replaceMovement(original, movement("Pago con puntos", 2000))
        assertEquals(98000L, dao.getCard("Tarjeta")!!.unbilledCents)
        val edited = dao.observeTransactions().first().single()
        repository.deleteMovement(edited)
        assertEquals(card().breakdown(), dao.getCard("Tarjeta")!!.breakdown())
        expectFailure { repository.deleteMovement(edited) }
        assertEquals(card().breakdown(), dao.getCard("Tarjeta")!!.breakdown())
    }

    @Test
    fun overpaymentCreatesCreditAndCanBeReversed() = runBlocking {
        dao.insertCard(card().withBreakdown(CardBreakdown(1000, 500, 2000)))
        repository.addMovement(movement("Pago tarjeta", 4000).copy(sourceName = "Cuenta", destinationName = "Tarjeta"))
        val paid = dao.getCard("Tarjeta")!!
        assertEquals(500L, paid.creditBalanceCents)
        assertEquals(-500L, paid.balanceCents)
        assertEquals(96000L, dao.getAccount("Cuenta")!!.balanceCents)
        assertTrue(paid.balanceCheck().isConsistent)
        repository.deleteMovement(dao.observeTransactions().first().single())
        assertEquals(CardBreakdown(1000, 500, 2000), dao.getCard("Tarjeta")!!.breakdown())
        assertEquals(100000L, dao.getAccount("Cuenta")!!.balanceCents)
    }

    @Test
    fun partialCardPaymentRestoresOriginalAllocationWhenDeleted() = runBlocking {
        dao.insertCard(card().withBreakdown(CardBreakdown(1000, 500, 2000)))
        repository.addMovement(movement("Pago tarjeta", 1200).copy(sourceName = "Cuenta", destinationName = "Tarjeta"))
        assertEquals(CardBreakdown(0, 300, 2000), dao.getCard("Tarjeta")!!.breakdown())
        repository.deleteMovement(dao.observeTransactions().first().single())
        assertEquals(CardBreakdown(1000, 500, 2000), dao.getCard("Tarjeta")!!.breakdown())
        assertEquals(100000L, dao.getAccount("Cuenta")!!.balanceCents)
    }

    @Test
    fun reconciliationPreservesCreditAndDoesNotAffectBudget() = runBlocking {
        val before = dao.getCard("Tarjeta")!!
        repository.reconcileCard(before, 0, 0, 180000, 1822000, "Saldo del banco", 2000)
        val updated = dao.getCard("Tarjeta")!!
        assertEquals(CardBreakdown(0, 0, 180000, 2000), updated.breakdown())
        assertTrue(updated.balanceCheck().isConsistent)
        val adjustment = dao.observeTransactions().first().single()
        assertEquals(0L, adjustment.budgetImpactCents)
        expectFailure { repository.deleteMovement(adjustment) }
        assertEquals(updated, dao.getCard("Tarjeta"))
    }

    @Test
    fun accountReconciliationStoresActualBeforeAndAfterAndSkipsNoOp() = runBlocking {
        val account = dao.getAccount("Cuenta")!!
        repository.reconcileAccount(account, 105000, "Movimiento faltante")
        val adjustment = dao.observeTransactions().first().single()
        val entry = adjustment.reconciliationEntry()!!
        assertEquals(listOf(ReconciliationValue("Saldo", 100000, 105000)), entry.values)
        assertEquals(5000L, entry.differenceCents)
        assertEquals(0L, adjustment.budgetImpactCents)
        repository.reconcileAccount(dao.getAccount("Cuenta")!!, 105000, "Sin cambio")
        assertEquals(1, dao.observeTransactions().first().size)
    }

    @Test
    fun cardBreakdownOnlyAdjustmentKeepsBothSnapshotsWithZeroDifference() = runBlocking {
        val before = dao.getCard("Tarjeta")!!
        repository.reconcileCard(before, 49000, 101000, before.msiBalanceCents, before.availableCents, "Desglose del banco")
        val entry = dao.observeTransactions().first().single().reconciliationEntry()!!
        assertEquals(0L, entry.differenceCents)
        assertEquals(ReconciliationValue("Saldo a pagar", 50000, 49000), entry.values.first { it.label == "Saldo a pagar" })
        assertEquals(ReconciliationValue("Saldo actual", 100000, 101000), entry.values.first { it.label == "Saldo actual" })
        assertEquals(before.balanceCents, dao.getCard("Tarjeta")!!.balanceCents)
    }

    @Test
    fun reconciliationSnapshotsSurviveBackupRoundTrip() = runBlocking {
        repository.reconcileAccount(dao.getAccount("Cuenta")!!, 99000, "Corrección")
        val before = dao.observeTransactions().first().single().reconciliationEntry()
        val backup = financeBackupJson(FinanceUiState(accounts = dao.getAccounts(), cards = dao.getCards(), transactions = dao.observeTransactions().first()))
        repository.restoreBackup(backup.toString())
        assertEquals(before, dao.observeTransactions().first().single().reconciliationEntry())
    }

    @Test
    fun purchasesConsumeCreditAndDeletingPurchaseRestoresCredit() = runBlocking {
        dao.insertCard(card().withBreakdown(CardBreakdown(0, 0, 180000, 2000)))
        repository.addMovement(movement("Gasto", 3000))
        assertEquals(1000L, dao.getCard("Tarjeta")!!.unbilledCents)
        assertEquals(0L, dao.getCard("Tarjeta")!!.creditBalanceCents)
        repository.deleteMovement(dao.observeTransactions().first().single())
        assertEquals(CardBreakdown(0, 0, 180000, 2000), dao.getCard("Tarjeta")!!.breakdown())
    }

    @Test
    fun refundExcessCreatesCreditWithoutModifyingMsiPlans() = runBlocking {
        dao.insertCard(card().withBreakdown(CardBreakdown(1000, 500, 2000)))
        repository.addMovement(movement("Devolución", 2000))
        assertEquals(CardBreakdown(0, 0, 2000, 500), dao.getCard("Tarjeta")!!.breakdown())
        repository.deleteMovement(dao.observeTransactions().first().single())
        assertEquals(CardBreakdown(1000, 500, 2000), dao.getCard("Tarjeta")!!.breakdown())
    }

    @Test
    fun cannotDeleteBenefitWhoseCreditWasAlreadySpent() = runBlocking {
        dao.insertCard(card().withBreakdown(CardBreakdown(0, 0, 2000)))
        repository.addMovement(movement("Bonificación", 3000))
        val benefit = dao.observeTransactions().first().single()
        repository.addMovement(movement("Gasto", 1500))
        val before = dao.getCard("Tarjeta")!!
        expectFailure { repository.deleteMovement(benefit) }
        assertEquals(before, dao.getCard("Tarjeta"))
        assertEquals(2, dao.observeTransactions().first().size)
    }

    @Test
    fun transfersAndTheirDeletionAffectBothAccountsOnce() = runBlocking {
        repository.addMovement(movement("Transferencia", 3000).copy(sourceName = "Cuenta", destinationName = "Destino"))
        assertEquals(97000L, dao.getAccount("Cuenta")!!.balanceCents)
        assertEquals(23000L, dao.getAccount("Destino")!!.balanceCents)
        repository.deleteMovement(dao.observeTransactions().first().single())
        assertEquals(100000L, dao.getAccount("Cuenta")!!.balanceCents)
        assertEquals(20000L, dao.getAccount("Destino")!!.balanceCents)
    }

    @Test
    fun backupRoundTripKeepsCreditEffectsAndPendingNotifications() = runBlocking {
        dao.insertCard(card().withBreakdown(CardBreakdown(0, 0, 180000, 2000)))
        repository.addMovement(movement("Gasto", 3000))
        val detection = DetectedMovementEntity(1, "pending", "bank", "Tarjeta", "Comercio", 100, "Compras", 1000, "Compra", "Texto")
        val ignored = IgnoredNotificationEntity(1, "ignored", "bank", "Banco", "Promo", "Texto", "Promoción", 1000)
        dao.insertDetected(detection)
        dao.insertIgnoredNotification(ignored)
        val state = FinanceUiState(
            accounts = dao.getAccounts(), cards = dao.getCards(), transactions = dao.observeTransactions().first(),
            pendingDetections = dao.getPendingDetections(), ignoredNotifications = dao.observeIgnoredNotifications().first()
        )
        val json = financeBackupJson(state)
        assertEquals(8, json.getInt("formatVersion"))
        repository.restoreBackup(json.toString())
        assertEquals(state.cards, dao.getCards())
        assertEquals(state.accounts, dao.getAccounts())
        assertEquals(state.transactions, dao.observeTransactions().first())
        assertEquals(listOf(detection), dao.getPendingDetections())
        assertEquals(listOf(ignored), dao.observeIgnoredNotifications().first())
        repository.deleteMovement(dao.observeTransactions().first().single())
        assertEquals(2000L, dao.getCard("Tarjeta")!!.creditBalanceCents)
    }

    @Test
    fun legacyBackupRemainsReadableAndDoesNotInventCredit() = runBlocking {
        val json = financeBackupJson(FinanceUiState(accounts = dao.getAccounts(), cards = dao.getCards()))
        json.put("formatVersion", 6)
        json.remove("pendingDetections")
        json.remove("ignoredNotifications")
        json.getJSONArray("cards").getJSONObject(0).remove("creditBalanceCents")
        repository.restoreBackup(json.toString())
        assertEquals(card().breakdown(), dao.getCard("Tarjeta")!!.breakdown())
    }

    @Test
    fun invalidBackupRollsBackAllWritesAndFutureFormatIsRejected() = runBlocking {
        val before = dao.getCards()
        val json = financeBackupJson(FinanceUiState(accounts = dao.getAccounts(), cards = before))
        json.put("budgets", org.json.JSONArray().put(org.json.JSONObject().put("limitCents", 100)))
        expectFailure { repository.restoreBackup(json.toString()) }
        assertEquals(before, dao.getCards())
        assertEquals(100000L, dao.getAccount("Cuenta")!!.balanceCents)
        json.put("formatVersion", 99)
        expectFailure { repository.restoreBackup(json.toString()) }
        assertEquals(before, dao.getCards())
    }

    @Test
    fun failedReplacementKeepsOriginalMovementAndBalances() = runBlocking {
        repository.addMovement(movement("Gasto", 1000))
        val before = dao.getCard("Tarjeta")!!
        val transaction = dao.observeTransactions().first().single()
        expectFailure { repository.replaceMovement(transaction, movement("Pago con puntos", 1000).copy(sourceName = "No existe")) }
        assertEquals(before, dao.getCard("Tarjeta"))
        assertEquals(listOf(transaction), dao.observeTransactions().first())
    }

    @Test
    fun correctingOnlyPlanKeepsAllFinancialBalancesAndSurvivesBackup() = runBlocking {
        val plan = testPlan()
        dao.insertInstallmentPlan(plan)
        val beforeCards = dao.getCards()
        val beforeAccounts = dao.getAccounts()
        val corrected = plan.copy(pendingBalanceCents = 8000, monthlyPaymentCents = 800)
        repository.saveInstallmentPlan(plan, corrected, preserveCardBalances = true)
        assertEquals(beforeCards, dao.getCards())
        assertEquals(beforeAccounts, dao.getAccounts())
        assertTrue(dao.observeTransactions().first().isEmpty())
        assertEquals(corrected, dao.getInstallmentPlan(plan.id))
        val backup = financeBackupJson(FinanceUiState(accounts = beforeAccounts, cards = beforeCards, installmentPlans = dao.observeInstallmentPlans().first()))
        repository.restoreBackup(backup.toString())
        assertEquals(corrected, dao.getInstallmentPlan(plan.id))
        assertEquals(beforeCards, dao.getCards())
    }

    @Test
    fun movingPlanInReviewKeepsBothCardsUnchanged() = runBlocking {
        val plan = testPlan()
        dao.insertInstallmentPlan(plan)
        dao.insertCard(card().copy(name = "Otra tarjeta"))
        val before = dao.getCards()
        repository.saveInstallmentPlan(plan, plan.copy(cardName = "Otra tarjeta"), preserveCardBalances = true)
        assertEquals(before, dao.getCards())
        assertEquals("Otra tarjeta", dao.getInstallmentPlan(plan.id)!!.cardName)
    }

    @Test
    fun regularPlanEditUpdatesOnlyMsiDifference() = runBlocking {
        val plan = testPlan()
        dao.insertInstallmentPlan(plan)
        val before = dao.getCard("Tarjeta")!!
        repository.saveInstallmentPlan(plan, plan.copy(pendingBalanceCents = 8000))
        val after = dao.getCard("Tarjeta")!!
        assertEquals(before.msiBalanceCents - 2000, after.msiBalanceCents)
        assertEquals(before.statementPaymentCents, after.statementPaymentCents)
        assertEquals(before.unbilledCents, after.unbilledCents)
        assertEquals(before.creditBalanceCents, after.creditBalanceCents)
        assertTrue(after.balanceCheck().isConsistent)
    }

    @Test
    fun impossibleCardAdjustmentIsRejectedInsteadOfClampingToZero() = runBlocking {
        val plan = testPlan()
        dao.insertInstallmentPlan(plan)
        dao.insertCard(card().withBreakdown(CardBreakdown(0, 0, 500)))
        val before = dao.getCard("Tarjeta")!!
        val paid = plan.copy(pendingBalanceCents = 0, currentPayment = 12, remainingMonths = 0, status = "Pagado", nextDueEpochDay = null)
        expectFailure { repository.saveInstallmentPlan(plan, paid) }
        assertEquals(before, dao.getCard("Tarjeta"))
        assertEquals(plan, dao.getInstallmentPlan(plan.id))
        repository.saveInstallmentPlan(plan, paid, preserveCardBalances = true)
        assertEquals(before, dao.getCard("Tarjeta"))
        assertEquals(paid, dao.getInstallmentPlan(plan.id))
    }

    @Test
    fun stalePlanCorrectionCannotOverwriteNewerData() = runBlocking {
        val plan = testPlan()
        dao.insertInstallmentPlan(plan)
        val latest = plan.copy(description = "Nombre corregido")
        repository.saveInstallmentPlan(plan, latest, preserveCardBalances = true)
        expectFailure { repository.saveInstallmentPlan(plan, plan.copy(pendingBalanceCents = 7000), preserveCardBalances = true) }
        assertEquals(latest, dao.getInstallmentPlan(plan.id))
    }

    @Test
    fun cannotMarkPlanPaidWhilePendingBalanceIsPositive() = runBlocking {
        val plan = testPlan()
        dao.insertInstallmentPlan(plan)
        expectFailure { repository.saveInstallmentPlan(plan, plan.copy(currentPayment = 12, remainingMonths = 0, status = "Pagado"), preserveCardBalances = true) }
        assertEquals(plan, dao.getInstallmentPlan(plan.id))
    }

    @Test
    fun installmentPaymentAffectsCashAndMsiOnceAndRejectsStaleRetry() = runBlocking {
        val plan = testPlan()
        dao.insertInstallmentPlan(plan)
        val before = dao.getCard("Tarjeta")!!
        repository.payInstallmentPlan(plan, "Cuenta", 1000)
        val after = dao.getCard("Tarjeta")!!
        assertEquals(99000L, dao.getAccount("Cuenta")!!.balanceCents)
        assertEquals(before.msiBalanceCents - 1000, after.msiBalanceCents)
        assertEquals(before.statementPaymentCents, after.statementPaymentCents)
        assertEquals(before.unbilledCents, after.unbilledCents)
        assertEquals(before.creditBalanceCents, after.creditBalanceCents)
        assertEquals(9000L, dao.getInstallmentPlan(plan.id)!!.pendingBalanceCents)
        expectFailure { repository.payInstallmentPlan(plan, "Cuenta", 1000) }
        assertEquals(after, dao.getCard("Tarjeta"))
        assertEquals(99000L, dao.getAccount("Cuenta")!!.balanceCents)
        assertEquals(1, dao.observeTransactions().first().size)
    }

    @Test
    fun installmentPaymentCannotSilentlyExceedCardMsi() = runBlocking {
        val plan = testPlan()
        dao.insertInstallmentPlan(plan)
        dao.insertCard(card().withBreakdown(CardBreakdown(0, 0, 500)))
        val before = dao.getCard("Tarjeta")!!
        expectFailure { repository.payInstallmentPlan(plan, "Cuenta", 1000) }
        assertEquals(before, dao.getCard("Tarjeta"))
        assertEquals(100000L, dao.getAccount("Cuenta")!!.balanceCents)
        assertEquals(plan, dao.getInstallmentPlan(plan.id))
        assertTrue(dao.observeTransactions().first().isEmpty())
    }

    private fun testPlan() = InstallmentPlanEntity(
        id = 101, cardName = "Tarjeta", description = "Compra", purchaseEpochDay = 20000,
        originalAmountCents = 12000, totalMonths = 12, currentPayment = 2, monthlyPaymentCents = 1000,
        pendingBalanceCents = 10000, remainingMonths = 10, status = "Activo", nextDueEpochDay = 21000
    )

    private suspend fun expectFailure(block: suspend () -> Unit) {
        var failed = false
        try { block() } catch (_: Exception) { failed = true }
        assertTrue("La operación debía rechazarse", failed)
    }

    private fun movement(type: String, amount: Long) = MovementRequest(type, "Tarjeta", description = type, category = "Compras", amountCents = amount)

    private fun account(name: String, amount: Long) = AccountEntity(name, "Débito", amount, true, null, 0)

    private fun card() = CardEntity(
        "Tarjeta", "Banco", null, 20000, 20020, 2000000, 900000, 750000, 50000, null,
        1100000, false, "Pendiente", 0, 100000
    )
}

