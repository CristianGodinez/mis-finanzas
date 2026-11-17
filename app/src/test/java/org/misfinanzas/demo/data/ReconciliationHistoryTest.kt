package org.misfinanzas.demo.data

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReconciliationHistoryTest {
    @Test
    fun legacyAccountShowsRecordedBalancesAndSignedDifference() {
        val entry = transaction("account", "Efectivo", "Ajuste de saldo · Movimiento faltante", "Gasto", 200,
            "Saldo anterior: 1000; saldo real: 800").reconciliationEntry()!!
        assertFalse(entry.isCard)
        assertEquals("Movimiento faltante", entry.reason)
        assertEquals(-200L, entry.differenceCents)
        assertEquals(listOf(ReconciliationValue("Saldo", 1000, 800)), entry.values)
    }

    @Test
    fun legacyCardDoesNotInventOldAvailableOrMsiBalances() {
        val entry = transaction("card", "Tarjeta", "Ajuste de deuda · Cashback", "Devolución", 200,
            "Utilizado anterior: 1000; utilizado real: 800; disponible: 9200").reconciliationEntry()!!
        assertTrue(entry.isCard)
        assertEquals(-200L, entry.differenceCents)
        assertEquals(listOf(ReconciliationValue("Utilizado neto", 1000, 800), ReconciliationValue("Disponible", null, 9200)), entry.values)
        assertFalse(entry.values.any { it.label == "Saldo MSI" })
    }

    @Test
    fun structuredNotesKeepCreditAndAllComponents() {
        val before = linkedMapOf("Utilizado neto" to 1500L, "Saldo a pagar" to 1000L, "Saldo actual" to 500L,
            "Saldo MSI" to 2000L, "Saldo a favor" to 2000L, "Disponible" to 8500L, "Límite" to 10000L)
        val after = before + mapOf("Utilizado neto" to 1400L, "Saldo a favor" to 2100L, "Disponible" to 8600L)
        val entry = transaction("new", "Tarjeta", "Ajuste de deuda", "Devolución", 100,
            reconciliationNotes("Bonificación", true, before, after)).reconciliationEntry()!!
        assertEquals("Bonificación", entry.reason)
        assertEquals(7, entry.values.size)
        assertEquals(ReconciliationValue("Saldo a favor", 2000, 2100), entry.values.first { it.label == "Saldo a favor" })
        assertEquals(-100L, entry.differenceCents)
    }

    @Test
    fun filtersOnlyReconciliationsByExactAccountAndReasonAndOrdersNewestFirst() {
        val old = transaction("old", "Cuenta", "Ajuste de saldo · Cashback", "Ingreso", 100, null).copy(dateEpochDay = 100, createdAtMillis = 10)
        val newest = old.copy(id = "newest", dateEpochDay = 101, createdAtMillis = 20)
        val ordinary = old.copy(id = "ordinary", source = "Registro manual")
        val another = old.copy(id = "other", accountName = "Cuenta 2")
        val result = reconciliationHistory(listOf(old, ordinary, another, newest), "Cuenta", " CASHBACK ")
        assertEquals(listOf("newest", "old"), result.map { it.transaction.id })
        assertNull(ordinary.reconciliationEntry())
        assertEquals(3, reconciliationHistory(listOf(old, ordinary, another, newest)).size)
    }

    @Test
    fun malformedOrMissingNotesStayReadableWithoutInventingBalances() {
        val entry = transaction("bad", "Cuenta", "Ajuste de saldo · Otro", "Gasto", 123, "{invalid").reconciliationEntry()!!
        assertEquals(-123L, entry.differenceCents)
        assertEquals(listOf(ReconciliationValue("Saldo", null, null)), entry.values)
    }

    private fun transaction(id: String, account: String, description: String, type: String, amount: Long, notes: String?) =
        TransactionEntity(id, 20000, account, description, type, "Ajustes", amount, 0, false, null, "Confirmado", RECONCILIATION_SOURCE, notes)
}

