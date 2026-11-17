package org.misfinanzas.demo.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceUiStateTest {
    @Test
    fun calculatesBalancesWithoutCountingExcludedAccounts() {
        val state = FinanceUiState(
            accounts = listOf(
                AccountEntity("Principal", "Débito", 100_000L, true, null, 0),
                AccountEntity("Oculta", "Débito", 50_000L, false, null, 0)
            ),
            cards = listOf(
                CardEntity("Crédito", "Banco", null, null, null, 100_000L, 40_000L, 0, 10_000L, null, 60_000L, false, "Activa", 0)
            ),
            debts = listOf(
                DebtEntity(name = "Préstamo", creditor = "Banco", balanceCents = 10_000L, monthlyPaymentCents = 3_000L, nextPaymentEpochDay = LocalDate.now().toEpochDay(), annualRate = null, status = "Activa")
            ),
            investments = listOf(
                InvestmentEntity(name = "Ahorro", institution = "Banco", contributedCents = 15_000L, currentValueCents = 20_000L, updatedAtEpochDay = null)
            )
        )

        assertEquals(100_000L, state.liquidMoneyCents)
        assertEquals(40_000L, state.cardDebtCents)
        assertEquals(60_000L, state.creditAvailableCents)
        assertEquals(87_000L, state.safeToSpendCents)
        assertEquals(70_000L, state.netWorthCents)
    }

    @Test
    fun creditOnOneCardDoesNotHideDebtOnAnotherOrBecomeLiquidMoney() {
        val base = CardEntity("Crédito", "Banco", null, null, null, 100000, 40000, 0, 10000, null, 60000, false, "Activa", 0)
        val state = FinanceUiState(
            accounts = listOf(AccountEntity("Efectivo", "Efectivo", 10000, true, null, 0)),
            cards = listOf(base, base.copy(name = "A favor").withBreakdown(CardBreakdown(0, 0, 0, 5000)))
        )
        assertEquals(40000L, state.cardDebtCents)
        assertEquals(5000L, state.netCardCreditCents)
        assertEquals(10000L, state.liquidMoneyCents)
        assertEquals(-25000L, state.netWorthCents)
    }

    @Test
    fun keepsFutureIncomeInProjectionInsteadOfCurrentBalance() {
        val state = FinanceUiState(
            accounts = listOf(AccountEntity("Cajita Nu", "Ahorro", 100_000L, true, null, 0)),
            recurringIncome = listOf(
                RecurringIncomeEntity(description = "Ingreso semanal", amountCents = 50_000L, cadence = "Cada lunes", destinationAccount = "Cajita Nu", enabled = true)
            )
        )

        assertEquals(100_000L, state.liquidMoneyCents)
        assertEquals(state.projectionEvents30.sortedBy { it.dateEpochDay }, state.projectionEvents30)
        assertEquals(state.liquidMoneyCents + state.projectedIncome30Cents, state.projectedBalance30Cents)
    }
}


