package org.misfinanzas.demo.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.misfinanzas.demo.data.FinanceUiState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

fun shareTransactionsCsv(context: Context, state: FinanceUiState) {
    val file = File(context.cacheDir, "movimientos-${LocalDate.now()}.csv")
    val content = buildString {
        appendLine("Fecha,Cuenta,Descripción,Tipo,Categoría,Importe,Estado,Origen,Notas")
        state.transactions.forEach { item ->
            appendLine(listOf(
                LocalDate.ofEpochDay(item.dateEpochDay).toString(), item.accountName, item.description, item.type,
                item.category, "%.2f".format(item.amountCents / 100.0), item.status, item.source, item.notes.orEmpty()
            ).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
        }
    }
    file.writeText(content)
    shareFile(context, file, "text/csv", "Exportar movimientos")
}

fun financeBackupJson(state: FinanceUiState): JSONObject {
    val root = JSONObject()
    root.put("formatVersion", 8)
    root.put("createdAt", LocalDate.now().toString())
    root.put("transactions", JSONArray(state.transactions.map { item ->
        JSONObject().apply {
            put("id", item.id); put("dateEpochDay", item.dateEpochDay); put("account", item.accountName)
            put("description", item.description); put("type", item.type); put("category", item.category)
            put("amountCents", item.amountCents); put("budgetImpactCents", item.budgetImpactCents)
            put("isMsi", item.isMsi); put("installment", item.installment); put("status", item.status)
            put("source", item.source); put("notes", item.notes); put("createdAtMillis", item.createdAtMillis)
            put("relatedAccountName", item.relatedAccountName); put("installmentPlanId", item.installmentPlanId); put("cardEffect", item.cardEffect); put("benefitTarget", item.benefitTarget)
        }
    }))
    root.put("accounts", JSONArray(state.accounts.map { item ->
        JSONObject().apply { put("name", item.name); put("type", item.type); put("balanceCents", item.balanceCents); put("included", item.includedInAvailable); put("updatedAtEpochDay", item.updatedAtEpochDay); put("colorHex", item.colorHex); put("iconKey", item.iconKey) }
    }))
    root.put("cards", JSONArray(state.cards.map { item ->
        JSONObject().apply {
            put("name", item.name); put("institution", item.institution); put("lastFour", item.lastFour)
            put("cutoffEpochDay", item.cutoffEpochDay); put("dueEpochDay", item.dueEpochDay)
            put("limitCents", item.creditLimitCents); put("balanceCents", item.balanceCents); put("msiBalanceCents", item.msiBalanceCents)
            put("paymentCents", item.statementPaymentCents); put("minimumPaymentCents", item.minimumPaymentCents)
            put("availableCents", item.availableCents); put("paymentConfirmed", item.paymentConfirmed); put("status", item.status)
            put("colorHex", item.colorHex); put("unbilledCents", item.unbilledCents); put("iconKey", item.iconKey); put("creditBalanceCents", item.creditBalanceCents)
        }
    }))
    root.put("installmentPlans", JSONArray(state.installmentPlans.map { item ->
        JSONObject().apply {
            put("id", item.id); put("cardName", item.cardName); put("description", item.description); put("purchaseEpochDay", item.purchaseEpochDay)
            put("originalAmountCents", item.originalAmountCents); put("totalMonths", item.totalMonths); put("currentPayment", item.currentPayment)
            put("monthlyPaymentCents", item.monthlyPaymentCents); put("pendingBalanceCents", item.pendingBalanceCents); put("remainingMonths", item.remainingMonths); put("status", item.status)
            put("firstDueEpochDay", item.firstDueEpochDay); put("nextDueEpochDay", item.nextDueEpochDay)
        }
    }))
    root.put("budgets", JSONArray(state.budgets.map { JSONObject().put("category", it.category).put("limitCents", it.limitCents) }))
    root.put("subscriptions", JSONArray(state.subscriptions.map { JSONObject().put("name", it.name).put("amountCents", it.amountCents).put("frequency", it.frequency).put("nextChargeEpochDay", it.nextChargeEpochDay).put("account", it.accountName).put("status", it.status) }))
    root.put("goals", JSONArray(state.goals.map { JSONObject().put("id", it.id).put("name", it.name).put("targetCents", it.targetCents).put("savedCents", it.savedCents).put("targetEpochDay", it.targetEpochDay).put("monthlyContributionCents", it.monthlyContributionCents).put("priority", it.priority).put("status", it.status) }))
    root.put("debts", JSONArray(state.debts.map { JSONObject().put("id", it.id).put("name", it.name).put("creditor", it.creditor).put("balanceCents", it.balanceCents).put("paymentCents", it.monthlyPaymentCents).put("nextPaymentEpochDay", it.nextPaymentEpochDay).put("annualRate", it.annualRate).put("status", it.status) }))
    root.put("investments", JSONArray(state.investments.map { JSONObject().put("id", it.id).put("name", it.name).put("institution", it.institution).put("contributedCents", it.contributedCents).put("currentValueCents", it.currentValueCents).put("updatedAtEpochDay", it.updatedAtEpochDay) }))
    root.put("recurringIncome", JSONArray(state.recurringIncome.map { JSONObject().put("id", it.id).put("description", it.description).put("amountCents", it.amountCents).put("cadence", it.cadence).put("destination", it.destinationAccount).put("enabled", it.enabled) }))
    root.put("categories", JSONArray(state.categories.map { JSONObject().put("name", it.name).put("parentName", it.parentName).put("colorHex", it.colorHex).put("iconKey", it.iconKey).put("active", it.active) }))
    root.put("merchantRules", JSONArray(state.merchantRules.map { JSONObject().put("id", it.id).put("keyword", it.keyword).put("accountName", it.accountName).put("category", it.category).put("movementType", it.movementType).put("merchantName", it.merchantName).put("enabled", it.enabled) }))
    root.put("debtors", JSONArray(state.debtors.map { JSONObject().put("id", it.id).put("name", it.name).put("notes", it.notes).put("colorHex", it.colorHex).put("createdAtEpochDay", it.createdAtEpochDay) }))
    root.put("receivables", JSONArray(state.receivables.map { JSONObject().put("id", it.id).put("debtorId", it.debtorId).put("name", it.name).put("category", it.category).put("amountCents", it.amountCents).put("notes", it.notes).put("dueEpochDay", it.dueEpochDay).put("status", it.status).put("paidEpochDay", it.paidEpochDay).put("destinationAccountName", it.destinationAccountName).put("linkedTransactionId", it.linkedTransactionId) }))
    root.put("pendingDetections", JSONArray(state.pendingDetections.map { item ->
        JSONObject().apply {
            put("id", item.id); put("fingerprint", item.fingerprint); put("packageName", item.packageName)
            put("accountName", item.accountName); put("merchant", item.merchant); put("amountCents", item.amountCents)
            put("suggestedCategory", item.suggestedCategory); put("postedAtMillis", item.postedAtMillis)
            put("rawTitle", item.rawTitle); put("rawText", item.rawText); put("status", item.status)
            put("movementType", item.movementType); put("relatedAccountName", item.relatedAccountName)
        }
    }))
    root.put("ignoredNotifications", JSONArray(state.ignoredNotifications.map { item ->
        JSONObject().apply {
            put("id", item.id); put("fingerprint", item.fingerprint); put("packageName", item.packageName)
            put("bankName", item.bankName); put("title", item.title); put("text", item.text)
            put("reason", item.reason); put("postedAtMillis", item.postedAtMillis)
        }
    }))
    return root
}

fun shareFinanceBackup(context: Context, state: FinanceUiState) {
    val root = financeBackupJson(state)
    val file = File(context.cacheDir, "respaldo-mis-finanzas-${LocalDate.now()}.json")
    file.writeText(root.toString(2))
    shareFile(context, file, "application/json", "Guardar respaldo")
}

private fun shareFile(context: Context, file: File, mimeType: String, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}


