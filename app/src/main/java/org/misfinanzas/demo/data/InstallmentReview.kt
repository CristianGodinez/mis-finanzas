package org.misfinanzas.demo.data

import java.time.LocalDate

data class InstallmentReview(
    val cardName: String,
    val cardMsiCents: Long?,
    val plans: List<InstallmentPlanEntity>,
    val planIssues: Map<Long, List<String>>
) {
    val plansPendingCents: Long get() = plans.sumOf { it.pendingBalanceCents.coerceAtLeast(0) }
    val differenceCents: Long? get() = cardMsiCents?.minus(plansPendingCents)
    val requiresReview: Boolean get() = cardMsiCents == null || differenceCents != 0L || planIssues.isNotEmpty()
    val pendingPlanCount: Int get() = plans.count { it.pendingBalanceCents > 0 }
}

fun reviewInstallments(cards: List<CardEntity>, plans: List<InstallmentPlanEntity>): List<InstallmentReview> {
    val cardsByName = cards.associateBy { it.name }
    return (cards.map { it.name } + plans.map { it.cardName }).distinct().map { name ->
        val cardPlans = plans.filter { it.cardName == name }.sortedWith(
            compareByDescending<InstallmentPlanEntity> { it.pendingBalanceCents > 0 }.thenBy { it.description }.thenBy { it.id }
        )
        InstallmentReview(
            name, cardsByName[name]?.msiBalanceCents, cardPlans,
            cardPlans.mapNotNull { plan -> plan.reviewIssues().takeIf { it.isNotEmpty() }?.let { plan.id to it } }.toMap()
        )
    }.sortedWith(compareByDescending<InstallmentReview> { it.requiresReview }.thenBy { it.cardName })
}

fun InstallmentPlanEntity.reviewIssues(): List<String> = buildList {
    if (originalAmountCents <= 0) add("Revisa el importe original")
    if (totalMonths !in 1..60) add("Revisa el plazo")
    if (currentPayment !in 0..totalMonths) add("Revisa el progreso")
    if (remainingMonths != (totalMonths - currentPayment).coerceAtLeast(0)) add("Las mensualidades restantes no coinciden con el progreso")
    if (monthlyPaymentCents <= 0) add("Falta una mensualidad válida")
    if (pendingBalanceCents < 0 || pendingBalanceCents > originalAmountCents) add("Revisa el saldo pendiente")
    if (pendingBalanceCents > 0 && (status == "Pagado" || remainingMonths <= 0)) add("El plan tiene saldo pendiente, pero figura como terminado")
    if (pendingBalanceCents == 0L && status != "Pagado") add("El plan no tiene saldo, pero no figura como pagado")
    if (pendingBalanceCents > 0 && nextDueEpochDay == null) add("Falta la próxima fecha de pago")
}

fun InstallmentPlanEntity.paymentDates(): List<LocalDate> {
    val total = totalMonths.coerceIn(1, 60)
    val completed = currentPayment.coerceIn(0, total)
    val first = firstDueEpochDay?.let(LocalDate::ofEpochDay)
        ?: nextDueEpochDay?.let { LocalDate.ofEpochDay(it).minusMonths(completed.toLong()) }
        ?: LocalDate.ofEpochDay(purchaseEpochDay).plusMonths(1)
    val next = nextDueEpochDay?.let(LocalDate::ofEpochDay) ?: first.plusMonths(completed.toLong())
    return (0 until total).map { index ->
        if (index < completed) first.plusMonths(index.toLong()) else next.plusMonths((index - completed).toLong())
    }
}

