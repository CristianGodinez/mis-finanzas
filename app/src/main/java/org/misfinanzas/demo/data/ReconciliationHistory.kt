package org.misfinanzas.demo.data

import org.json.JSONObject

const val RECONCILIATION_SOURCE = "Conciliación manual"

data class ReconciliationValue(val label: String, val beforeCents: Long?, val afterCents: Long?)

data class ReconciliationEntry(
    val transaction: TransactionEntity,
    val reason: String,
    val isCard: Boolean,
    val differenceCents: Long,
    val values: List<ReconciliationValue>
)

fun reconciliationNotes(
    reason: String,
    isCard: Boolean,
    before: Map<String, Long>,
    after: Map<String, Long>
): String = JSONObject().apply {
    put("format", "reconciliation-v1")
    put("reason", reason.ifBlank { "Conciliación" })
    put("isCard", isCard)
    put("before", JSONObject(before))
    put("after", JSONObject(after))
}.toString()

fun AccountEntity.reconciliationValues(): Map<String, Long> = linkedMapOf("Saldo" to balanceCents)

fun CardEntity.reconciliationValues(): Map<String, Long> = linkedMapOf(
    "Utilizado neto" to balanceCents,
    "Saldo a pagar" to statementPaymentCents,
    "Saldo actual" to unbilledCents,
    "Saldo MSI" to msiBalanceCents,
    "Saldo a favor" to creditBalanceCents,
    "Disponible" to availableCents,
    "Límite" to creditLimitCents
)

fun TransactionEntity.reconciliationEntry(): ReconciliationEntry? {
    if (source != RECONCILIATION_SOURCE) return null
    val snapshot = runCatching { JSONObject(notes.orEmpty()).takeIf { it.optString("format") == "reconciliation-v1" } }.getOrNull()
    val isCard = snapshot?.optBoolean("isCard") ?: description.startsWith("Ajuste de deuda")
    val reason = snapshot?.optString("reason")?.takeIf { it.isNotBlank() }
        ?: description.substringAfter(" · ", "Conciliación")
    val values = if (snapshot != null) {
        val before = snapshot.optJSONObject("before")
        val after = snapshot.optJSONObject("after")
        val labels = if (isCard) listOf("Utilizado neto", "Saldo a pagar", "Saldo actual", "Saldo MSI", "Saldo a favor", "Disponible", "Límite") else listOf("Saldo")
        labels.map { label -> ReconciliationValue(label, before?.moneyValue(label), after?.moneyValue(label)) }
    } else {
        fun legacyValue(label: String): Long? = Regex("(?:^|;\\s*)${Regex.escape(label)}: (-?\\d+)(?:;|$)")
            .find(notes.orEmpty())?.groupValues?.get(1)?.toLongOrNull()
        if (isCard) listOf(
            ReconciliationValue("Utilizado neto", legacyValue("Utilizado anterior"), legacyValue("utilizado real")),
            ReconciliationValue("Disponible", null, legacyValue("disponible"))
        ) else listOf(ReconciliationValue("Saldo", legacyValue("Saldo anterior"), legacyValue("saldo real")))
    }
    val total = values.firstOrNull()
    val difference = if (total?.beforeCents != null && total.afterCents != null) total.afterCents - total.beforeCents else {
        val increases = if (isCard) type == "Gasto" else type == "Ingreso"
        if (increases) amountCents else -amountCents
    }
    return ReconciliationEntry(this, reason, isCard, difference, values)
}

fun reconciliationHistory(
    transactions: List<TransactionEntity>,
    accountName: String? = null,
    query: String = ""
): List<ReconciliationEntry> {
    val search = query.trim()
    return transactions.asSequence()
        .mapNotNull { it.reconciliationEntry() }
        .filter { accountName == null || it.transaction.accountName == accountName }
        .filter { search.isBlank() || "${it.transaction.accountName} ${it.reason} ${it.transaction.description}".contains(search, ignoreCase = true) }
        .sortedWith(compareByDescending<ReconciliationEntry> { it.transaction.dateEpochDay }
            .thenByDescending { it.transaction.createdAtMillis }.thenBy { it.transaction.id })
        .toList()
}

private fun JSONObject.moneyValue(key: String): Long? = if (has(key) && !isNull(key)) opt(key)?.toString()?.toLongOrNull() else null

