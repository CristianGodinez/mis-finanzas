package org.misfinanzas.demo.data

data class CardBreakdown(
    val statementCents: Long,
    val currentCents: Long,
    val installmentCents: Long,
    val creditCents: Long = 0
) {
    val grossCents: Long get() = statementCents + currentCents + installmentCents
    val totalCents: Long get() = grossCents - creditCents

    fun reduce(amountCents: Long, order: List<CardBalancePart>): CardBreakdown {
        require(amountCents >= 0) { "El importe no puede ser negativo" }
        var remaining = amountCents
        var statement = statementCents
        var current = currentCents
        var installment = installmentCents
        order.distinct().forEach { part ->
            when (part) {
                CardBalancePart.Statement -> {
                    val applied = remaining.coerceAtMost(statement)
                    statement -= applied
                    remaining -= applied
                }
                CardBalancePart.Current -> {
                    val applied = remaining.coerceAtMost(current)
                    current -= applied
                    remaining -= applied
                }
                CardBalancePart.Installment -> {
                    val applied = remaining.coerceAtMost(installment)
                    installment -= applied
                    remaining -= applied
                }
            }
        }
        return CardBreakdown(statement, current, installment, creditCents + remaining)
    }

    fun charge(amountCents: Long): CardBreakdown {
        require(amountCents >= 0) { "El importe no puede ser negativo" }
        val covered = amountCents.coerceAtMost(creditCents)
        return copy(currentCents = currentCents + amountCents - covered, creditCents = creditCents - covered)
    }

    fun closeCycle(installmentCentsDue: Long): CardBreakdown {
        require(installmentCentsDue in 0..installmentCents) { "La mensualidad supera el saldo MSI; concilia los planes" }
        val statement = statementCents + currentCents + installmentCentsDue
        val covered = creditCents.coerceAtMost(statement)
        return CardBreakdown(statement - covered, 0, installmentCents - installmentCentsDue, creditCents - covered)
    }
}

enum class CardBalancePart { Statement, Current, Installment }

data class CardBalanceCheck(val componentDifferenceCents: Long, val creditDifferenceCents: Long) {
    val isConsistent: Boolean get() = componentDifferenceCents == 0L && creditDifferenceCents == 0L
}

fun CardEntity.breakdown(): CardBreakdown = CardBreakdown(statementPaymentCents, unbilledCents, msiBalanceCents, creditBalanceCents)

fun CardEntity.balanceCheck(): CardBalanceCheck = CardBalanceCheck(
    balanceCents - breakdown().totalCents,
    (creditLimitCents - balanceCents).coerceAtLeast(0) - availableCents
)

fun CardEntity.withBreakdown(value: CardBreakdown): CardEntity {
    require(listOf(value.statementCents, value.currentCents, value.installmentCents, value.creditCents).all { it >= 0 }) {
        "El desglose no puede contener importes negativos"
    }
    return copy(
        balanceCents = value.totalCents,
        statementPaymentCents = value.statementCents,
        unbilledCents = value.currentCents,
        msiBalanceCents = value.installmentCents,
        creditBalanceCents = value.creditCents,
        availableCents = (creditLimitCents - value.totalCents).coerceAtLeast(0)
    )
}

fun CardEntity.effectTo(updated: CardEntity): String = listOf(
    updated.statementPaymentCents - statementPaymentCents,
    updated.unbilledCents - unbilledCents,
    updated.msiBalanceCents - msiBalanceCents,
    updated.creditBalanceCents - creditBalanceCents,
    cutoffEpochDay?.toString() ?: "null"
).joinToString("|")

fun CardEntity.reverseEffect(effect: String): CardEntity {
    val parts = effect.split('|')
    require(parts.size == 5) { "El movimiento tiene un desglose inválido" }
    require(parts[4] == (cutoffEpochDay?.toString() ?: "null")) {
        "La tarjeta ya cambió de corte; corrige este movimiento desde Conciliar saldos"
    }
    val values = parts.take(4).map(String::toLong)
    val originalParts = CardBreakdown(
        statementPaymentCents - values[0], unbilledCents - values[1],
        msiBalanceCents - values[2], creditBalanceCents - values[3]
    )
    require(listOf(originalParts.statementCents, originalParts.currentCents, originalParts.installmentCents, originalParts.creditCents).all { it >= 0 }) {
        "Otros movimientos ya utilizaron este saldo; corrige la diferencia desde Conciliar saldos"
    }
    val updated = withBreakdown(originalParts)
    return updated.copy(
        paymentConfirmed = updated.statementPaymentCents == 0L,
        status = if (updated.statementPaymentCents == 0L) "Sin pago requerido" else "Pendiente"
    )
}

