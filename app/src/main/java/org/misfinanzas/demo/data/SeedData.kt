package org.misfinanzas.demo.data

import java.time.LocalDate

object SeedData {
    val categories = listOf(
        "Comida", "Transporte", "Compras", "Entretenimiento", "Suscripciones",
        "Viajes", "Servicios", "Salud", "Educación", "Vivienda", "Comisiones",
        "Bonificaciones", "Pagos", "Ajustes", "Ahorro", "Deudas", "Inversiones",
        "Sin clasificar", "Otros", "Ingresos"
    )

    private val today = LocalDate.now()

    val accounts = listOf(
        AccountEntity("Cuenta Demo", "Débito", 500_000, true, today.toEpochDay(), 0xFF10B981),
        AccountEntity("Efectivo Demo", "Efectivo", 50_000, true, today.toEpochDay(), 0xFFF59E0B)
    )

    val cards = listOf(
        card("Tarjeta Aurora Demo", 1_000_000, 100_000, 10_000, 20_000, 0xFF8B5CF6),
        card("Tarjeta Brisa Demo", 800_000, 60_000, 10_000, 0, 0xFF2563EB),
        card("Tarjeta Cobre Demo", 600_000, 30_000, 10_000, 10_000, 0xFFF59E0B)
    )

    val installmentPlans = listOf(
        plan("Tarjeta Aurora Demo", "Equipo de estudio Demo", 120_000, 12, 2),
        plan("Tarjeta Brisa Demo", "Mueble Demo", 90_000, 9, 3),
        plan("Tarjeta Cobre Demo", "Accesorios Demo", 60_000, 6, 3)
    )

    val transactions = listOf(
        TransactionEntity("demo-001", today.minusDays(2).toEpochDay(), "Cuenta Demo",
            "Supermercado Demo", "Gasto", "Comida", 15_000, 15_000, false, null,
            "Confirmado", "Datos ficticios de demostración", null),
        TransactionEntity("demo-002", today.minusDays(1).toEpochDay(), "Efectivo Demo",
            "Transporte Demo", "Gasto", "Transporte", 2_000, 2_000, false, null,
            "Confirmado", "Datos ficticios de demostración", null)
    )

    private fun card(name: String, limit: Long, msi: Long, statement: Long, current: Long, color: Long) =
        CardEntity(name, "Banco ficticio", null, today.plusDays(15).toEpochDay(), today.plusDays(5).toEpochDay(),
            limit, msi + statement + current, msi, statement, null, limit - msi - statement - current,
            false, "Demostración", color, unbilledCents = current)

    private fun plan(card: String, description: String, total: Long, months: Int, paid: Int) =
        InstallmentPlanEntity(cardName = card, description = description,
            purchaseEpochDay = today.minusMonths(paid.toLong()).toEpochDay(), originalAmountCents = total,
            totalMonths = months, currentPayment = paid, monthlyPaymentCents = total / months,
            pendingBalanceCents = total / months * (months - paid), remainingMonths = months - paid,
            status = "Activo", firstDueEpochDay = today.minusMonths(paid.toLong() - 1).plusDays(5).toEpochDay(),
            nextDueEpochDay = today.plusDays(5).toEpochDay())
}
