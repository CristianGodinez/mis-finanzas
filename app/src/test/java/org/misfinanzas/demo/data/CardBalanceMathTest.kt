package org.misfinanzas.demo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardBalanceMathTest {
    @Test
    fun recognizesConsistentSyntheticBreakdown() {
        val card = card(
            limit = 2_000_000,
            balance = 600_000,
            statement = 100_000,
            current = 200_000,
            installment = 300_000,
            available = 1_400_000
        )

        assertTrue(card.balanceCheck().isConsistent)
    }

    @Test
    fun detectsAComponentMismatch() {
        val card = card(
            limit = 1_000_000,
            balance = 400_000,
            statement = 100_000,
            current = 100_000,
            installment = 150_000,
            available = 600_000
        )

        assertFalse(card.balanceCheck().isConsistent)
        assertEquals(50_000L, card.balanceCheck().componentDifferenceCents)
    }

    @Test
    fun cardPaymentConsumesStatementThenCurrentThenInstallments() {
        val result = CardBreakdown(100_000, 50_000, 200_000).reduce(
            175_000,
            listOf(CardBalancePart.Statement, CardBalancePart.Current, CardBalancePart.Installment)
        )

        assertEquals(0L, result.statementCents)
        assertEquals(0L, result.currentCents)
        assertEquals(175_000L, result.installmentCents)
        assertEquals(175_000L, result.totalCents)
    }

    @Test
    fun directInstallmentPaymentDoesNotReduceStatementTwice() {
        val card = card(
            limit = 1_000_000,
            balance = 350_000,
            statement = 100_000,
            current = 50_000,
            installment = 200_000,
            available = 650_000
        )
        val updated = card.withBreakdown(card.breakdown().copy(installmentCents = 175_000))

        assertEquals(100_000L, updated.statementPaymentCents)
        assertEquals(50_000L, updated.unbilledCents)
        assertEquals(175_000L, updated.msiBalanceCents)
        assertEquals(325_000L, updated.balanceCents)
        assertEquals(675_000L, updated.availableCents)
        assertTrue(updated.balanceCheck().isConsistent)
    }

    @Test
    fun creditCoexistsWithFutureInstallmentsAndCarriesAcrossCutoff() {
        val original = CardBreakdown(0, 0, 180000, 2000)
        val closed = original.closeCycle(20000)
        assertEquals(CardBreakdown(18000, 0, 160000, 0), closed)
        assertEquals(original.totalCents, closed.totalCents)
    }

    @Test
    fun creditOnlyCardCanSpendPartOfItsCredit() {
        val original = CardBreakdown(0, 0, 0, 10000)
        assertEquals(CardBreakdown(0, 0, 0, 7000), original.charge(3000))
        assertEquals(CardBreakdown(0, 5000, 0, 0), original.charge(15000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotReverseRecordedEffectAfterAnotherCutoff() {
        val original = card(100000, 10000, 0, 10000, 0, 90000)
        val updated = original.withBreakdown(original.breakdown().charge(1000))
        updated.copy(cutoffEpochDay = 21000).reverseEffect(original.effectTo(updated))
    }

    private fun card(
        limit: Long,
        balance: Long,
        statement: Long,
        current: Long,
        installment: Long,
        available: Long
    ) = CardEntity(
        name = "Tarjeta",
        institution = "Banco",
        lastFour = null,
        cutoffEpochDay = null,
        dueEpochDay = null,
        creditLimitCents = limit,
        balanceCents = balance,
        msiBalanceCents = installment,
        statementPaymentCents = statement,
        minimumPaymentCents = null,
        availableCents = available,
        paymentConfirmed = statement == 0L,
        status = "Activa",
        colorHex = 0,
        unbilledCents = current
    )
}


