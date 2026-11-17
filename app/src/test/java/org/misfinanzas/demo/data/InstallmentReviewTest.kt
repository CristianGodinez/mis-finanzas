package org.misfinanzas.demo.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class InstallmentReviewTest {
    @Test
    fun reportsExactDifferenceWithoutChangingPlansOrCard() {
        val card = card("Tarjeta Demo", 100000)
        val plans = listOf(plan(1, "Tarjeta Demo", 20000).copy(originalAmountCents = 40000), plan(2, "Tarjeta Demo", 500000).copy(originalAmountCents = 500000))
        val review = reviewInstallments(listOf(card), plans).single()
        assertEquals(520000L, review.plansPendingCents)
        assertEquals(-420000L, review.differenceCents)
        assertTrue(review.requiresReview)
        assertEquals(100000L, card.msiBalanceCents)
        assertEquals(500000L, plans[1].pendingBalanceCents)
    }

    @Test
    fun detectsMissingIndividualPlans() {
        val review = reviewInstallments(listOf(card("Tarjeta", 10000)), emptyList()).single()
        assertEquals(10000L, review.differenceCents)
        assertTrue(review.requiresReview)
    }

    @Test
    fun doesNotSubtractCreditBalanceFromMsiComparison() {
        val card = card("Tarjeta Demo", 180000, 2000)
        val plan = plan(1, card.name, 180000).copy(originalAmountCents = 240000, totalMonths = 12, currentPayment = 3, remainingMonths = 9, monthlyPaymentCents = 20000)
        val review = reviewInstallments(listOf(card), listOf(plan)).single()
        assertEquals(0L, review.differenceCents)
        assertFalse(review.requiresReview)
        assertEquals(178000L, card.balanceCents)
    }

    @Test
    fun paidZeroBalanceDoesNotIncreasePendingTotal() {
        val paid = plan(2, "Tarjeta", 0).copy(currentPayment = 12, remainingMonths = 0, status = "Pagado", nextDueEpochDay = null)
        val review = reviewInstallments(listOf(card("Tarjeta", 10000)), listOf(plan(1, "Tarjeta", 10000), paid)).single()
        assertEquals(10000L, review.plansPendingCents)
        assertEquals(1, review.pendingPlanCount)
        assertFalse(review.requiresReview)
    }

    @Test
    fun flagsPaidPlanWithBalanceInsteadOfHidingIt() {
        val inconsistent = plan(1, "Tarjeta", 10000).copy(status = "Pagado")
        val review = reviewInstallments(listOf(card("Tarjeta", 10000)), listOf(inconsistent)).single()
        assertEquals(10000L, review.plansPendingCents)
        assertTrue(review.requiresReview)
        assertTrue(review.planIssues.getValue(1).any { it.contains("terminado") })
    }

    @Test
    fun retainsPlansWhoseCardNoLongerExists() {
        val review = reviewInstallments(emptyList(), listOf(plan(1, "Anterior", 10000))).single()
        assertNull(review.cardMsiCents)
        assertNull(review.differenceCents)
        assertTrue(review.requiresReview)
        assertEquals("Anterior", review.cardName)
    }

    @Test
    fun allowsUnequalLastPaymentAndRounding() {
        val plan = plan(1, "Tarjeta", 6667).copy(originalAmountCents = 10000, totalMonths = 3, currentPayment = 1, remainingMonths = 2, monthlyPaymentCents = 3333)
        assertTrue(plan.reviewIssues().isEmpty())
        assertFalse(reviewInstallments(listOf(card("Tarjeta", 6667)), listOf(plan)).single().requiresReview)
    }

    @Test
    fun zeroCardWithNoPlansIsConsistent() {
        val review = reviewInstallments(listOf(card("Tarjeta", 0)), emptyList()).single()
        assertFalse(review.requiresReview)
        assertEquals(0L, review.plansPendingCents)
    }

    @Test
    fun flagsMissingNextPaymentAndInvalidProgress() {
        val incomplete = plan(1, "Tarjeta", 10000).copy(nextDueEpochDay = null, currentPayment = 12, remainingMonths = 0)
        val issues = incomplete.reviewIssues()
        assertTrue(issues.any { it.contains("fecha") })
        assertTrue(issues.any { it.contains("terminado") })
    }

    @Test
    fun doesNotTreatNegativePendingBalanceAsCredit() {
        val invalid = plan(1, "Tarjeta", -100)
        val review = reviewInstallments(listOf(card("Tarjeta", 0)), listOf(invalid)).single()
        assertEquals(0L, review.plansPendingCents)
        assertTrue(review.requiresReview)
    }

    @Test
    fun correctedNextDateOverridesOldScheduleForFuturePayments() {
        val plan = plan(1, "Tarjeta", 10000).copy(
            firstDueEpochDay = day("2026-08-10"), nextDueEpochDay = day("2026-11-15")
        )
        val dates = plan.paymentDates()
        assertEquals(LocalDate.parse("2026-08-10"), dates[0])
        assertEquals(LocalDate.parse("2026-09-10"), dates[1])
        assertEquals(LocalDate.parse("2026-11-15"), dates[2])
        assertEquals(LocalDate.parse("2026-12-15"), dates[3])
    }

    @Test
    fun legacyPlanWithoutNextDateStillHasEstimatedSchedule() {
        val plan = plan(1, "Tarjeta", 10000).copy(firstDueEpochDay = day("2026-08-10"), nextDueEpochDay = null)
        assertEquals(LocalDate.parse("2026-10-10"), plan.paymentDates()[2])
    }

    private fun day(value: String) = LocalDate.parse(value).toEpochDay()

    private fun card(name: String, msi: Long, credit: Long = 0) = CardEntity(
        name, "Banco", null, null, null, 1000000, msi - credit, msi, 0, null,
        1000000 - msi + credit, true, "Sin pago requerido", 0, creditBalanceCents = credit
    )

    private fun plan(id: Long, card: String, pending: Long) = InstallmentPlanEntity(
        id, card, "Compra $id", day("2026-07-01"), 12000, 12, 2, 1000, pending, 10, "Activo",
        nextDueEpochDay = day("2026-11-10")
    )
}

