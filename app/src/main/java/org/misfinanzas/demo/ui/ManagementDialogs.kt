package org.misfinanzas.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.misfinanzas.demo.data.AccountEntity
import org.misfinanzas.demo.data.BudgetEntity
import org.misfinanzas.demo.data.CardEntity
import org.misfinanzas.demo.data.CategoryEntity
import org.misfinanzas.demo.data.DebtEntity
import org.misfinanzas.demo.data.DebtorEntity
import org.misfinanzas.demo.data.FinanceUiState
import org.misfinanzas.demo.data.FinanceViewModel
import org.misfinanzas.demo.data.GoalEntity
import org.misfinanzas.demo.data.InvestmentEntity
import org.misfinanzas.demo.data.InstallmentPlanEntity
import org.misfinanzas.demo.data.MovementRequest
import org.misfinanzas.demo.data.MerchantRuleEntity
import org.misfinanzas.demo.data.RecurringIncomeEntity
import org.misfinanzas.demo.data.ReceivableEntity
import org.misfinanzas.demo.data.SeedData
import org.misfinanzas.demo.data.SubscriptionEntity
import org.misfinanzas.demo.data.TransactionEntity
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

sealed interface EditorTarget {
    data class Transaction(val item: TransactionEntity) : EditorTarget
    data class Detection(val item: org.misfinanzas.demo.data.DetectedMovementEntity) : EditorTarget
    data class Account(val item: AccountEntity? = null) : EditorTarget
    data class Card(val item: CardEntity? = null) : EditorTarget
    data class AccountReconciliation(val item: AccountEntity) : EditorTarget
    data class CardReconciliation(val item: CardEntity) : EditorTarget
    data class Budget(val item: BudgetEntity) : EditorTarget
    data class Subscription(val item: SubscriptionEntity? = null) : EditorTarget
    data class Goal(val item: GoalEntity? = null) : EditorTarget
    data class Debt(val item: DebtEntity? = null) : EditorTarget
    data class Investment(val item: InvestmentEntity? = null) : EditorTarget
    data class RecurringIncome(val item: RecurringIncomeEntity? = null) : EditorTarget
    data class Installment(val item: InstallmentPlanEntity) : EditorTarget
    data class InstallmentEdit(val item: InstallmentPlanEntity, val preserveCardBalances: Boolean = false) : EditorTarget
    data class Category(val item: CategoryEntity? = null) : EditorTarget
    data class MerchantRule(val item: MerchantRuleEntity? = null) : EditorTarget
    data class Debtor(val item: DebtorEntity? = null) : EditorTarget
    data class Receivable(val debtorId: Long, val item: ReceivableEntity? = null) : EditorTarget
    data class ReceivePayment(val item: ReceivableEntity) : EditorTarget
}

@Composable
fun ManagementDialog(target: EditorTarget, state: FinanceUiState, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    when (target) {
        is EditorTarget.Transaction -> TransactionDialog(target.item, state, viewModel, onDismiss)
        is EditorTarget.Detection -> DetectionDialog(target.item, state, viewModel, onDismiss)
        is EditorTarget.Account -> AccountDialog(target.item, viewModel, onDismiss)
        is EditorTarget.Card -> CardDialog(target.item, viewModel, onDismiss)
        is EditorTarget.AccountReconciliation -> AccountReconciliationDialog(target.item, viewModel, onDismiss)
        is EditorTarget.CardReconciliation -> CardReconciliationDialog(target.item, viewModel, onDismiss)
        is EditorTarget.Budget -> BudgetDialog(target.item, viewModel, onDismiss)
        is EditorTarget.Subscription -> SubscriptionDialog(target.item, state, viewModel, onDismiss)
        is EditorTarget.Goal -> GoalDialog(target.item, viewModel, onDismiss)
        is EditorTarget.Debt -> DebtDialog(target.item, viewModel, onDismiss)
        is EditorTarget.Investment -> InvestmentDialog(target.item, viewModel, onDismiss)
        is EditorTarget.RecurringIncome -> RecurringIncomeDialog(target.item, state, viewModel, onDismiss)
        is EditorTarget.Installment -> InstallmentDialog(target.item, state, viewModel, onDismiss)
        is EditorTarget.InstallmentEdit -> InstallmentEditDialog(target.item, state, viewModel, onDismiss, target.preserveCardBalances)
        is EditorTarget.Category -> CategoryDialog(target.item, state, viewModel, onDismiss)
        is EditorTarget.MerchantRule -> MerchantRuleDialog(target.item, state, viewModel, onDismiss)
        is EditorTarget.Debtor -> DebtorDialog(target.item, viewModel, onDismiss)
        is EditorTarget.Receivable -> ReceivableDialog(target.debtorId, target.item, viewModel, onDismiss)
        is EditorTarget.ReceivePayment -> ReceivePaymentDialog(target.item, state, viewModel, onDismiss)
    }
}

@Composable
private fun AccountReconciliationDialog(item: AccountEntity, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var balance by remember { mutableStateOf(item.balanceCents.fromCents()) }
    var reason by remember { mutableStateOf("Saldo del banco") }
    var error by remember { mutableStateOf<String?>(null) }
    val actual = balance.centsOrNull()
    val difference = actual?.minus(item.balanceCents)
    EditorDialog(
        title = "Conciliar ${item.name}",
        error = error,
        onDismiss = onDismiss,
        onSave = {
            when {
                actual == null -> error = "Escribe un saldo válido"
                else -> viewModel.reconcileAccount(item, actual, reason) { result -> error = result; if (result == null) onDismiss() }
            }
        }
    ) {
        Text("Saldo en la app: ${item.balanceCents.fromCents()}", color = TextMuted)
        MoneyField(balance, "Saldo real actual", onChange = { balance = it })
        Selector(reason, listOf("Saldo del banco", "Cashback", "Intereses", "Comisión", "Movimiento faltante", "Otro"), "Motivo", onSelected = { reason = it })
        difference?.let {
            Text(
                if (it == 0L) "Los saldos ya coinciden" else "Ajuste: ${if (it > 0) "+" else "−"}${kotlin.math.abs(it).fromCents()}",
                color = if (it == 0L) Mint else WarmAmber,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Text("El ajuste quedará en Movimientos y no afectará tu presupuesto.", color = TextMuted, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CardReconciliationDialog(item: CardEntity, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var statement by remember { mutableStateOf(item.statementPaymentCents.fromCents()) }
    var currentBalance by remember { mutableStateOf(item.unbilledCents.fromCents()) }
    var msiBalance by remember { mutableStateOf(item.msiBalanceCents.fromCents()) }
    var creditBalance by remember { mutableStateOf(item.creditBalanceCents.fromCents()) }
    var available by remember { mutableStateOf(item.availableCents.fromCents()) }
    var reason by remember { mutableStateOf("Saldo del banco") }
    var error by remember { mutableStateOf<String?>(null) }
    val actualStatement = statement.centsOrNull()
    val actualCurrent = currentBalance.centsOrNull()
    val actualMsi = msiBalance.centsOrNull()
    val actualAvailable = available.centsOrNull()
    val actualCredit = creditBalance.centsOrNull()
    val actualDebt = listOfNotNull(actualStatement, actualCurrent, actualMsi).takeIf { it.size == 3 }?.sum()?.let { total -> actualCredit?.let { total - it } }
    val difference = actualDebt?.minus(item.balanceCents)
    val limitDifference = if (actualDebt != null && actualAvailable != null) item.creditLimitCents - actualDebt - actualAvailable else null
    EditorDialog(
        title = "Conciliar ${item.name}",
        error = error,
        onDismiss = onDismiss,
        onSave = {
            when {
                actualStatement == null || actualStatement < 0 -> error = "Escribe un pago del periodo válido"
                actualCurrent == null || actualCurrent < 0 -> error = "Escribe un saldo actual válido"
                actualMsi == null || actualMsi < 0 -> error = "Escribe un saldo a meses válido"
                actualCredit == null || actualCredit < 0 -> error = "Escribe un saldo a favor válido"
                actualAvailable == null || actualAvailable < 0 -> error = "Escribe el crédito disponible"
                else -> viewModel.reconcileCard(item, actualStatement, actualCurrent, actualMsi, actualAvailable, reason, actualCredit) { result -> error = result; if (result == null) onDismiss() }
            }
        }
    ) {
        Text("Límite registrado: ${item.creditLimitCents.fromCents()}", color = TextMuted)
        MoneyField(statement, "Saldo a pagar", onChange = { statement = it })
        MoneyField(currentBalance, "Saldo actual / posterior al corte", onChange = { currentBalance = it })
        MoneyField(msiBalance, "Saldo pendiente a meses", onChange = { msiBalance = it })
        MoneyField(creditBalance, "Saldo a favor", onChange = { creditBalance = it })
        MoneyField(available, "Crédito disponible", onChange = { available = it })
        Selector(reason, listOf("Saldo del banco", "Cashback", "Pago con puntos", "Bonificación", "Intereses", "Comisión", "Movimiento faltante", "Otro"), "Motivo", onSelected = { reason = it })
        actualDebt?.let { Text("Crédito utilizado: ${it.fromCents()}", color = TextPrimary, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp)) }
        difference?.let {
            Text(
                if (it == 0L) "La deuda total ya coincide" else "Ajuste de deuda: ${if (it > 0) "+" else "−"}${kotlin.math.abs(it).fromCents()}",
                color = if (it == 0L) Mint else WarmAmber,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        if (limitDifference != null && kotlin.math.abs(limitDifference) > 1L) {
            Text("Hay ${kotlin.math.abs(limitDifference).fromCents()} sin desglosar entre el límite, lo utilizado y lo disponible.", color = WarmAmber, modifier = Modifier.padding(top = 6.dp))
        }
        Text("La conciliación conservará cada componente por separado.", color = TextMuted, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun DebtorDialog(item: DebtorEntity?, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var notes by remember { mutableStateOf(item?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog(
        title = if (item == null) "Agregar deudor" else "Editar deudor",
        error = error,
        onDismiss = onDismiss,
        onDelete = item?.let { existing -> { viewModel.deleteDebtor(existing) { result -> error = result; if (result == null) onDismiss() } } },
        onSave = {
            if (name.isBlank()) error = "Escribe el nombre de la persona" else viewModel.saveDebtor(
                item?.copy(name = name.trim(), notes = notes.trim().ifBlank { null })
                    ?: DebtorEntity(name = name.trim(), notes = notes.trim().ifBlank { null })
            ) { result -> error = result; if (result == null) onDismiss() }
        }
    ) {
        TextField(name, "Nombre", onChange = { name = it })
        TextField(notes, "Nota opcional", onChange = { notes = it })
        if (item != null) Text("Eliminar a la persona también elimina su historial de deudas.", color = TextMuted, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ReceivableDialog(debtorId: Long, item: ReceivableEntity?, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var category by remember { mutableStateOf(item?.category ?: "Préstamo") }
    var amount by remember { mutableStateOf(item?.amountCents?.fromCents().orEmpty()) }
    var dueDate by remember { mutableStateOf(item?.dueEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()) }
    var notes by remember { mutableStateOf(item?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog(
        title = if (item == null) "Nueva deuda por cobrar" else "Editar deuda",
        error = error,
        onDismiss = onDismiss,
        onDelete = item?.takeIf { it.status == "Pendiente" }?.let { existing -> { viewModel.deleteReceivable(existing) { result -> error = result; if (result == null) onDismiss() } } },
        onSave = {
            val cents = amount.centsOrNull()
            val parsedDate = dueDate.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() }
            when {
                name.isBlank() -> error = "Escribe el concepto"
                cents == null || cents <= 0 -> error = "Escribe un importe válido"
                dueDate.isNotBlank() && parsedDate == null -> error = "Usa el formato AAAA-MM-DD"
                else -> viewModel.saveReceivable(
                    item?.copy(name = name.trim(), category = category, amountCents = cents!!, notes = notes.trim().ifBlank { null }, dueEpochDay = parsedDate)
                        ?: ReceivableEntity(debtorId = debtorId, name = name.trim(), category = category, amountCents = cents!!, notes = notes.trim().ifBlank { null }, dueEpochDay = parsedDate)
                ) { result -> error = result; if (result == null) onDismiss() }
            }
        }
    ) {
        TextField(name, "Concepto", onChange = { name = it })
        Selector(category, listOf("Préstamo", "Compra compartida", "Comida", "Servicio", "Venta", "Otro"), "Clasificación", onSelected = { category = it })
        MoneyField(amount, "Importe", onChange = { amount = it })
        TextField(dueDate, "Fecha límite opcional (AAAA-MM-DD)", onChange = { dueDate = it })
        TextField(notes, "Nota opcional", onChange = { notes = it })
        if (item?.status == "Pagada") Text("Esta deuda ya está vinculada a un ingreso y su historial se conservará.", color = Mint, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ReceivePaymentDialog(item: ReceivableEntity, state: FinanceUiState, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf("Registrar ingreso") }
    var account by remember { mutableStateOf(state.accounts.firstOrNull()?.name.orEmpty()) }
    var transactionLabel by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val linkedIds = state.receivables.mapNotNull { it.linkedTransactionId }.toSet()
    val cutoff = LocalDate.now().minusDays(60).toEpochDay()
    val candidates = state.transactions.filter {
        it.id !in linkedIds && it.dateEpochDay >= cutoff && it.status == "Confirmado" &&
            it.type in listOf("Ingreso", "Devolución") && it.amountCents == item.amountCents
    }
    val labels = candidates.associateBy {
        "${LocalDate.ofEpochDay(it.dateEpochDay)} · ${it.accountName} · ${it.description} · ${it.amountCents.fromCents()} [${it.id.takeLast(6)}]"
    }
    EditorDialog(
        title = "Confirmar pago recibido",
        error = error,
        onDismiss = onDismiss,
        onSave = {
            if (mode == "Registrar ingreso" && account.isBlank()) error = "Selecciona la cuenta que recibió el pago"
            else if (mode == "Vincular ingreso existente" && labels[transactionLabel] == null) error = "Selecciona el ingreso que corresponde"
            else viewModel.markReceivablePaid(
                item,
                account.takeIf { mode == "Registrar ingreso" },
                labels[transactionLabel]?.id.takeIf { mode == "Vincular ingreso existente" }
            ) { result -> error = result; if (result == null) onDismiss() }
        }
    ) {
        Text("Se cobrarán ${item.amountCents.fromCents()}", fontWeight = FontWeight.ExtraBold)
        Text("La deuda solo afectará tu dinero después de confirmar este paso.", color = TextMuted, modifier = Modifier.padding(top = 4.dp))
        Selector(mode, listOf("Registrar ingreso", "Vincular ingreso existente"), "Cómo llegó el pago", onSelected = { mode = it })
        if (mode == "Registrar ingreso") {
            Selector(account, state.accounts.map { it.name }, "Cuenta que recibió el dinero", onSelected = { account = it })
        } else if (labels.isEmpty()) {
            Text("No hay ingresos recientes sin vincular por este mismo importe.", color = WarmAmber, modifier = Modifier.padding(top = 12.dp))
        } else {
            Selector(transactionLabel, labels.keys.toList(), "Ingreso reciente", onSelected = { transactionLabel = it })
        }
    }
}

@Composable
private fun DetectionDialog(item: org.misfinanzas.demo.data.DetectedMovementEntity, state: FinanceUiState, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var description by remember { mutableStateOf(item.merchant) }
    var amount by remember { mutableStateOf(item.amountCents.fromCents()) }
    var type by remember { mutableStateOf(item.movementType) }
    var category by remember { mutableStateOf(item.suggestedCategory) }
    var source by remember { mutableStateOf(item.accountName) }
    var destination by remember { mutableStateOf(item.relatedAccountName.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val accountNames = state.accounts.map { it.name }
    val cardNames = state.cards.map { it.name }
    val sourceOptions = when (type) {
        "Transferencia", "Pago tarjeta" -> accountNames
        else -> accountNames + cardNames
    }
    val destinationOptions = when (type) {
        "Transferencia" -> accountNames.filterNot { it == source }
        "Pago tarjeta" -> cardNames
        else -> emptyList()
    }

    EditorDialog(
        title = "Revisar movimiento detectado",
        error = error,
        onDismiss = onDismiss,
        onSave = {
            val cents = amount.centsOrNull()
            val needsDestination = type in listOf("Transferencia", "Pago tarjeta")
            when {
                description.isBlank() -> error = "Escribe una descripción"
                cents == null || cents <= 0 -> error = "Escribe un importe válido"
                source.isBlank() -> error = "Selecciona la cuenta de origen"
                needsDestination && destination.isBlank() -> error = "Selecciona el destino"
                else -> {
                    val date = Instant.ofEpochMilli(item.postedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
                    viewModel.confirmDetection(
                        item,
                        MovementRequest(type, source, destination.ifBlank { null }, description, category, cents!!, "Detectado desde notificación", dateEpochDay = date)
                    ) { result -> error = result; if (result == null) onDismiss() }
                }
            }
        }
    ) {
        Selector(type, listOf("Gasto", "Transferencia", "Pago tarjeta", "Devolución"), "Tipo", onSelected = {
            type = it
            source = when (it) {
                "Transferencia", "Pago tarjeta" -> source.takeIf(accountNames::contains) ?: accountNames.firstOrNull().orEmpty()
                else -> source
            }
            destination = when (it) {
                "Pago tarjeta" -> destination.takeIf(cardNames::contains) ?: cardNames.firstOrNull().orEmpty()
                "Transferencia" -> destination.takeIf(accountNames::contains) ?: accountNames.firstOrNull { name -> name != source }.orEmpty()
                else -> ""
            }
        })
        MoneyField(amount, "Importe", onChange = { amount = it })
        TextField(description, "Descripción o comercio", onChange = { description = it })
        Selector(source, sourceOptions, "Origen", onSelected = { source = it; if (destination == it) destination = "" })
        if (destinationOptions.isNotEmpty()) Selector(destination, destinationOptions, "Destino", onSelected = { destination = it })
        Selector(category, state.categoryNames(), "Categoría", onSelected = { category = it })
    }
}

@Composable
private fun EditorDialog(
    title: String,
    error: String?,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                content()
                error?.let { Text(it, color = Danger, modifier = Modifier.padding(top = 10.dp)) }
            }
        },
        confirmButton = {
            Button(onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DeepNavy)) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                onDelete?.let { TextButton(onClick = it) { Text("Eliminar", color = Danger) } }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

@Composable
private fun MoneyField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
        label = { Text(label) },
        prefix = { Text("$") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun TextField(value: String, label: String, keyboardType: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun Selector(value: String, options: List<String>, label: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, color = TextMuted)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(value.ifBlank { "Seleccionar" }, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(.78f).background(NavySurface)) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); expanded = false })
                }
            }
        }
    }
}

private fun FinanceUiState.categoryNames(): List<String> = categories.map { it.name }.ifEmpty { SeedData.categories }

@Composable
private fun CategoryDialog(item: CategoryEntity?, state: FinanceUiState, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var parent by remember { mutableStateOf(item?.parentName.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog(
        title = if (item == null) "Nueva categoría" else "Editar categoría",
        error = error,
        onDismiss = onDismiss,
        onDelete = item?.let { existing -> { viewModel.deleteCategory(existing) { result -> error = result; if (result == null) onDismiss() } } },
        onSave = {
            if (name.isBlank()) error = "Escribe un nombre" else viewModel.saveCategory(
                CategoryEntity(name.trim(), parent.trim().ifBlank { null }, item?.colorHex ?: 0xFF64748B, item?.iconKey ?: "tag")
            ) { result -> error = result; if (result == null) onDismiss() }
        }
    ) {
        TextField(name, "Nombre", onChange = { name = it })
        Selector(parent, listOf("") + state.categories.filter { it.parentName == null && it.name != name }.map { it.name }, "Categoría principal (opcional)", onSelected = { parent = it })
    }
}

@Composable
private fun MerchantRuleDialog(item: MerchantRuleEntity?, state: FinanceUiState, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var keyword by remember { mutableStateOf(item?.keyword.orEmpty()) }
    var merchant by remember { mutableStateOf(item?.merchantName.orEmpty()) }
    var account by remember { mutableStateOf(item?.accountName.orEmpty()) }
    var category by remember { mutableStateOf(item?.category ?: state.categoryNames().firstOrNull().orEmpty()) }
    var type by remember { mutableStateOf(item?.movementType ?: "Gasto") }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog(
        title = if (item == null) "Nueva regla automática" else "Editar regla automática",
        error = error,
        onDismiss = onDismiss,
        onDelete = item?.let { existing -> { viewModel.deleteMerchantRule(existing) { result -> error = result; if (result == null) onDismiss() } } },
        onSave = {
            if (keyword.isBlank()) error = "Escribe la palabra que debe detectar" else viewModel.saveMerchantRule(
                MerchantRuleEntity(item?.id ?: 0, keyword.trim().lowercase(), account.ifBlank { null }, category, type, merchant.trim().ifBlank { null }, true)
            ) { result -> error = result; if (result == null) onDismiss() }
        }
    ) {
        TextField(keyword, "Texto a detectar", onChange = { keyword = it })
        TextField(merchant, "Nombre del comercio (opcional)", onChange = { merchant = it })
        Selector(account, listOf("") + state.accounts.map { it.name } + state.cards.map { it.name }, "Cuenta específica (opcional)", onSelected = { account = it })
        Selector(type, listOf("Gasto", "Ingreso", "Devolución"), "Tipo", onSelected = { type = it })
        Selector(category, state.categoryNames(), "Categoría", onSelected = { category = it })
    }
}

@Composable
private fun TransactionDialog(item: TransactionEntity, state: FinanceUiState, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var description by remember { mutableStateOf(item.description) }
    var amount by remember { mutableStateOf(item.amountCents.fromCents()) }
    var date by remember { mutableStateOf(LocalDate.ofEpochDay(item.dateEpochDay).toString()) }
    var type by remember { mutableStateOf(item.type) }
    var category by remember { mutableStateOf(item.category) }
    var notes by remember { mutableStateOf(item.notes.orEmpty()) }
    val accountNames = state.accounts.map { it.name }
    val cardNames = state.cards.map { it.name }
    var source by remember {
        mutableStateOf(
            when (item.type) {
                "Pago tarjeta", "Pago MSI" -> item.relatedAccountName ?: item.notes?.substringAfter("Pagada desde:", "")?.trim().orEmpty()
                else -> item.accountName
            }
        )
    }
    var destination by remember {
        mutableStateOf(
            when (item.type) {
                "Transferencia" -> item.relatedAccountName ?: item.notes?.substringAfter("Destino:", "")?.trim().orEmpty()
                "Pago tarjeta" -> item.accountName
                else -> ""
            }
        )
    }
    var months by remember { mutableStateOf(item.installment?.substringAfter('/')?.toIntOrNull() ?: 3) }
    var benefitTarget by remember { mutableStateOf(item.benefitTarget) }
    var error by remember { mutableStateOf<String?>(null) }
    val supportedTypes = (listOf("Gasto", "Ingreso", "Transferencia", "Pago tarjeta", "Devolución", "Compra a MSI", "Pago con puntos", "Bonificación") + item.type).distinct()

    fun sourceOptions(): List<String> = when (type) {
        "Ingreso", "Transferencia", "Pago tarjeta" -> accountNames
        "Compra a MSI", "Pago con puntos", "Bonificación" -> cardNames
        else -> accountNames + cardNames
    }

    EditorDialog(
        title = "Editar movimiento",
        error = error,
        onDismiss = onDismiss,
        onSave = {
            val cents = amount.centsOrNull()
            val epochDay = runCatching { LocalDate.parse(date).toEpochDay() }.getOrNull()
            val validation = when {
                description.isBlank() -> "Escribe una descripción"
                cents == null || cents <= 0 -> "Escribe un importe válido"
                epochDay == null -> "Escribe una fecha válida"
                epochDay > LocalDate.now().toEpochDay() -> "La fecha no puede estar en el futuro"
                source.isBlank() -> "Selecciona una cuenta o tarjeta"
                type in listOf("Transferencia", "Pago tarjeta") && destination.isBlank() -> "Selecciona el destino"
                else -> null
            }
            if (validation != null) error = validation else viewModel.replaceMovement(
                item,
                MovementRequest(type, source, destination.ifBlank { null }, description, category, cents!!, notes.ifBlank { null }, months.takeIf { type == "Compra a MSI" }, epochDay!!, benefitTarget)
            ) { error = it; if (it == null) onDismiss() }
        },
        onDelete = {
            viewModel.deleteMovement(item) {
                error = it
                if (it == null) onDismiss()
            }
        }
    ) {
        Selector(type, supportedTypes, "Tipo", onSelected = {
            type = it
            source = sourceOptions().firstOrNull().orEmpty()
            destination = if (it == "Pago tarjeta") cardNames.firstOrNull().orEmpty() else if (it == "Transferencia") accountNames.firstOrNull { name -> name != source }.orEmpty() else ""
        })
        MoneyField(amount, "Importe", onChange = { amount = it })
        if (type in listOf("Pago con puntos", "Bonificación")) {
            Selector(benefitTarget, listOf("Saldo actual", "Saldo a pagar"), "Aplicar beneficio a", onSelected = { benefitTarget = it })
            Text("Captura el importe en pesos aplicado por el banco, una sola vez. Reduce el componente seleccionado y el excedente queda a favor; no modifica planes MSI ni aumenta tu dinero disponible.", color = TextMuted)
        }
        TextField(date, "Fecha (AAAA-MM-DD)", onChange = { date = it })
        TextField(description, "Descripción", onChange = { description = it })
        Selector(source, sourceOptions(), if (type == "Pago tarjeta") "Pagar desde" else "Cuenta o tarjeta", onSelected = { source = it })
        if (type == "Transferencia" || type == "Pago tarjeta") {
            Selector(destination, if (type == "Transferencia") accountNames.filter { it != source } else cardNames, if (type == "Transferencia") "Destino" else "Tarjeta", onSelected = { destination = it })
        }
        if (type == "Compra a MSI") {
            Selector("$months meses", listOf(3, 6, 9, 12, 18, 24).map { "$it meses" }, "Plazo", onSelected = { months = it.substringBefore(' ').toInt() })
        }
        Selector(category, state.categoryNames(), "Categoría", onSelected = { category = it })
        TextField(notes, "Nota", onChange = { notes = it })
    }
}

@Composable
private fun InstallmentDialog(item: InstallmentPlanEntity, state: FinanceUiState, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var source by remember { mutableStateOf(state.accounts.firstOrNull { it.includedInAvailable }?.name.orEmpty()) }
    var amount by remember { mutableStateOf(item.monthlyPaymentCents.coerceAtMost(item.pendingBalanceCents).fromCents()) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog("Administrar MSI", error, onDismiss, onSave = {
        val cents = amount.centsOrNull()
        if (source.isBlank() || cents == null || cents <= 0) error = "Selecciona una cuenta e importe" else viewModel.payInstallmentPlan(item, source, cents) {
            error = it
            if (it == null) onDismiss()
        }
    }) {
        Text(item.description, fontWeight = FontWeight.Black)
        Text("${item.cardName} · Pendiente ${item.pendingBalanceCents.asMoney()}", color = TextMuted, modifier = Modifier.padding(top = 4.dp))
        Selector(source, state.accounts.map { it.name }, "Pagar desde", onSelected = { source = it })
        MoneyField(amount, "Importe del abono", onChange = { amount = it })
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { amount = item.monthlyPaymentCents.coerceAtMost(item.pendingBalanceCents).fromCents() }, modifier = Modifier.weight(1f)) { Text("1 mensualidad") }
            OutlinedButton(onClick = { amount = item.pendingBalanceCents.fromCents() }, modifier = Modifier.weight(1f)) { Text("Liquidar") }
        }
    }
}

@Composable
private fun InstallmentEditDialog(item: InstallmentPlanEntity, state: FinanceUiState, viewModel: FinanceViewModel, onDismiss: () -> Unit, preserveCardBalances: Boolean) {
    var description by remember { mutableStateOf(item.description) }
    var cardName by remember { mutableStateOf(item.cardName) }
    var originalAmount by remember { mutableStateOf(item.originalAmountCents.fromCents()) }
    var totalMonths by remember { mutableStateOf(item.totalMonths.toString()) }
    var currentPayment by remember { mutableStateOf(item.currentPayment.toString()) }
    var monthlyPayment by remember { mutableStateOf(item.monthlyPaymentCents.fromCents()) }
    var pendingBalance by remember { mutableStateOf(item.pendingBalanceCents.fromCents()) }
    var purchaseDate by remember { mutableStateOf(LocalDate.ofEpochDay(item.purchaseEpochDay).toString()) }
    var nextDueDate by remember { mutableStateOf(item.nextDueEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    EditorDialog(if (preserveCardBalances) "Corregir plan MSI" else "Editar compra a MSI", error, onDismiss, onSave = {
        val originalCents = originalAmount.centsOrNull()
        val monthlyCents = monthlyPayment.centsOrNull()
        val pendingCents = pendingBalance.centsOrNull()
        val months = totalMonths.toIntOrNull()
        val paid = currentPayment.toIntOrNull()
        val purchase = runCatching { LocalDate.parse(purchaseDate) }.getOrNull()
        val nextDue = nextDueDate.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        when {
            description.isBlank() -> error = "Escribe el comercio o descripción"
            cardName.isBlank() -> error = "Selecciona una tarjeta"
            originalCents == null || originalCents <= 0 -> error = "Escribe un importe original válido"
            months == null || months !in 1..60 -> error = "El plazo debe ser de 1 a 60 meses"
            paid == null || paid !in 0..months -> error = "El número de pagos cubiertos no es válido"
            monthlyCents == null || monthlyCents <= 0 -> error = "Escribe una mensualidad válida"
            pendingCents == null || pendingCents < 0 || pendingCents > originalCents -> error = "El saldo pendiente debe estar entre cero y el importe original"
            paid == months && pendingCents > 0 -> error = "Aún hay saldo pendiente; revisa el número de mensualidades cubiertas"
            purchase == null -> error = "Usa AAAA-MM-DD en la fecha de compra"
            nextDueDate.isNotBlank() && nextDue == null -> error = "Usa AAAA-MM-DD en el próximo pago"
            else -> {
                val remaining = (months - paid).coerceAtLeast(0)
                val settled = pendingCents == 0L || remaining == 0
                viewModel.saveInstallmentPlan(
                    item,
                    item.copy(
                        cardName = cardName,
                        description = description.trim(),
                        purchaseEpochDay = purchase.toEpochDay(),
                        originalAmountCents = originalCents,
                        totalMonths = months,
                        currentPayment = if (settled) months else paid,
                        monthlyPaymentCents = monthlyCents,
                        pendingBalanceCents = if (settled) 0 else pendingCents,
                        remainingMonths = if (settled) 0 else remaining,
                        status = if (settled) "Pagado" else "Activo",
                        nextDueEpochDay = if (settled) null else nextDue?.toEpochDay()
                    ),
                    preserveCardBalances = preserveCardBalances
                ) {
                    error = it
                    if (it == null) onDismiss()
                }
            }
        }
    }) {
        Text(if (preserveCardBalances) "Esta corrección modifica solo el plan. Conserva el saldo MSI, utilizado y disponible de las tarjetas; usa el desglose real del banco." else "Esta edición también actualizará el saldo MSI de las tarjetas por la diferencia. Para corregir solo el desglose, entra desde Revisar MSI.", color = TextMuted)
        TextField(description, "Comercio o descripción", onChange = { description = it })
        Selector(cardName, state.cards.map { it.name }, "Tarjeta", onSelected = { cardName = it })
        MoneyField(originalAmount, "Importe original", onChange = { originalAmount = it })
        TextField(totalMonths, "Número total de mensualidades", KeyboardType.Number, onChange = { totalMonths = it })
        TextField(currentPayment, "Mensualidades ya cubiertas", KeyboardType.Number, onChange = { currentPayment = it })
        MoneyField(monthlyPayment, "Importe por mensualidad", onChange = { monthlyPayment = it })
        MoneyField(pendingBalance, "Saldo pendiente", onChange = { pendingBalance = it })
        TextField(purchaseDate, "Fecha de compra (AAAA-MM-DD)", onChange = { purchaseDate = it })
        TextField(nextDueDate, "Próximo pago (AAAA-MM-DD)", onChange = { nextDueDate = it })
    }
}

@Composable
private fun AccountDialog(item: AccountEntity?, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var type by remember { mutableStateOf(item?.type ?: "Débito") }
    var balance by remember { mutableStateOf(item?.balanceCents?.fromCents().orEmpty()) }
    var included by remember { mutableStateOf(item?.includedInAvailable ?: true) }
    var colorHex by remember { mutableStateOf(item?.colorHex ?: accountColorOptions.first()) }
    var iconKey by remember { mutableStateOf(item?.iconKey ?: "wallet") }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog("${if (item == null) "Nueva" else "Editar"} cuenta", error, onDismiss, onSave = {
        val cents = balance.centsOrNull()
        if (name.isBlank() || cents == null) error = "Completa nombre y saldo" else viewModel.saveAccount(
            AccountEntity(name, type, cents, included, LocalDate.now().toEpochDay(), colorHex, iconKey)
        ) { error = it; if (it == null) onDismiss() }
    }, onDelete = item?.let { { viewModel.deleteAccount(it) { e -> error = e; if (e == null) onDismiss() } } }) {
        TextField(name, "Nombre", onChange = { name = it })
        Selector(type, listOf("Débito", "Efectivo", "Ahorro líquido", "Billetera digital"), "Tipo", onSelected = { type = it })
        MoneyField(balance, "Saldo actual", onChange = { balance = it })
        Text("Color", color = TextMuted, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            accountColorOptions.forEach { option ->
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color(option)).clickable { colorHex = option },
                    contentAlignment = Alignment.Center
                ) {
                    if (colorHex == option) Icon(Icons.Default.Check, null, tint = if (option == 0xFFFFFFFF) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        Text("Icono", color = TextMuted, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            accountIconOptions.forEach { option ->
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(if (iconKey == option.key) Color(colorHex).copy(alpha = .28f) else NavySurfaceHigh).clickable { iconKey = option.key },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(option.icon, option.label, tint = if (iconKey == option.key) Color(colorHex) else TextMuted)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Contar como dinero disponible")
            Switch(checked = included, onCheckedChange = { included = it })
        }
    }
}

@Composable
private fun CardDialog(item: CardEntity?, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var institution by remember { mutableStateOf(item?.institution.orEmpty()) }
    var lastFour by remember { mutableStateOf(item?.lastFour.orEmpty()) }
    var limit by remember { mutableStateOf(item?.creditLimitCents?.fromCents().orEmpty()) }
    var payment by remember { mutableStateOf(item?.statementPaymentCents?.fromCents().orEmpty()) }
    var unbilled by remember { mutableStateOf(item?.unbilledCents?.fromCents().orEmpty()) }
    var msi by remember { mutableStateOf(item?.msiBalanceCents?.fromCents().orEmpty()) }
    var creditBalance by remember { mutableStateOf((item?.creditBalanceCents ?: 0L).fromCents()) }
    var available by remember { mutableStateOf(item?.availableCents?.fromCents().orEmpty()) }
    var cutoffDate by remember { mutableStateOf(item?.cutoffEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()) }
    var dueDate by remember { mutableStateOf(item?.dueEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()) }
    var colorHex by remember { mutableStateOf(item?.colorHex ?: accountColorOptions.first()) }
    var iconKey by remember { mutableStateOf(item?.iconKey ?: "card") }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog("${if (item == null) "Nueva" else "Editar"} tarjeta", error, onDismiss, onSave = {
        val limitCents = limit.centsOrNull()
        val paymentCents = payment.centsOrNull()
        val unbilledCents = unbilled.centsOrNull()
        val msiCents = msi.centsOrNull()
        val availableCents = available.centsOrNull()
        val creditCents = creditBalance.centsOrNull()
        val balanceCents = if (paymentCents != null && unbilledCents != null && msiCents != null && creditCents != null) paymentCents + unbilledCents + msiCents - creditCents else null
        val cutoff = runCatching { cutoffDate.takeIf(String::isNotBlank)?.let(LocalDate::parse)?.toEpochDay() }.getOrNull()
        val due = runCatching { dueDate.takeIf(String::isNotBlank)?.let(LocalDate::parse)?.toEpochDay() }.getOrNull()
        if (name.isBlank() || institution.isBlank() || limitCents == null || balanceCents == null || paymentCents == null || unbilledCents == null || msiCents == null || availableCents == null) {
            error = "Completa los datos obligatorios"
        } else {
            viewModel.saveCard(
                CardEntity(
                    name, institution, lastFour.ifBlank { null }, cutoff, due, limitCents, balanceCents,
                    msiCents, paymentCents, item?.minimumPaymentCents,
                    availableCents, paymentCents == 0L,
                    if (paymentCents == 0L) "Sin pago requerido" else "Pendiente", colorHex,
                    unbilledCents, iconKey, creditCents!!
                )
            ) { error = it; if (it == null) onDismiss() }
        }
    }, onDelete = item?.let { { viewModel.deleteCard(it) { e -> error = e; if (e == null) onDismiss() } } }) {
        TextField(name, "Nombre", onChange = { name = it })
        TextField(institution, "Banco", onChange = { institution = it })
        TextField(lastFour, "Últimos 4 dígitos", onChange = { lastFour = it.take(4) })
        MoneyField(limit, "Límite de crédito", onChange = { limit = it })
        MoneyField(payment, "Saldo a pagar", onChange = { payment = it })
        MoneyField(unbilled, "Saldo actual / posterior al corte", onChange = { unbilled = it })
        MoneyField(msi, "Saldo pendiente a meses", onChange = { msi = it })
        MoneyField(creditBalance, "Saldo a favor", onChange = { creditBalance = it })
        val calculatedDebt = listOf(payment.centsOrNull(), unbilled.centsOrNull(), msi.centsOrNull())
            .takeIf { values -> values.all { it != null } }
            ?.sumOf { it ?: 0L }?.let { total -> creditBalance.centsOrNull()?.let { total - it } }
        Text(
            "Crédito utilizado calculado: ${calculatedDebt?.fromCents() ?: "—"}",
            color = Mint,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )
        MoneyField(available, "Crédito disponible", onChange = { available = it })
        TextField(cutoffDate, "Fecha de corte (AAAA-MM-DD)", onChange = { cutoffDate = it })
        TextField(dueDate, "Fecha límite (AAAA-MM-DD)", onChange = { dueDate = it })
        Text("Color", color = TextMuted, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            accountColorOptions.forEach { option ->
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color(option)).clickable { colorHex = option },
                    contentAlignment = Alignment.Center
                ) {
                    if (colorHex == option) Icon(Icons.Default.Check, null, tint = if (option == 0xFFFFFFFF) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        Text("Icono", color = TextMuted, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            accountIconOptions.forEach { option ->
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(if (iconKey == option.key) Color(colorHex).copy(alpha = .28f) else NavySurfaceHigh).clickable { iconKey = option.key },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(option.icon, option.label, tint = if (iconKey == option.key) Color(colorHex) else TextMuted)
                }
            }
        }
    }
}

@Composable
private fun BudgetDialog(item: BudgetEntity, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var limit by remember { mutableStateOf(item.limitCents?.fromCents().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog("Presupuesto · ${item.category}", error, onDismiss, onSave = {
        val cents = limit.centsOrNull()
        if (limit.isNotBlank() && cents == null) error = "Escribe un importe válido" else viewModel.saveBudget(item.category, cents) {
            error = it; if (it == null) onDismiss()
        }
    }) { MoneyField(limit, "Límite mensual", onChange = { limit = it }) }
}

@Composable
private fun SubscriptionDialog(item: SubscriptionEntity?, state: FinanceUiState, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var amount by remember { mutableStateOf(item?.amountCents?.fromCents().orEmpty()) }
    var frequency by remember { mutableStateOf(item?.frequency ?: "Mensual") }
    var account by remember { mutableStateOf(item?.accountName ?: (state.cards.firstOrNull()?.name ?: state.accounts.firstOrNull()?.name.orEmpty())) }
    var nextCharge by remember { mutableStateOf(item?.nextChargeEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: LocalDate.now().plusMonths(1).toString()) }
    var active by remember { mutableStateOf(item?.status !in listOf("Pausada", "Cancelada")) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog("${if (item == null) "Nueva" else "Editar"} suscripción", error, onDismiss, onSave = {
        val cents = amount.centsOrNull()
        val nextEpochDay = runCatching { LocalDate.parse(nextCharge).toEpochDay() }.getOrNull()
        if (name.isBlank() || cents == null || cents <= 0 || account.isBlank() || nextEpochDay == null) error = "Completa los datos con una fecha válida" else viewModel.saveSubscription(
            SubscriptionEntity(name, cents, frequency, nextEpochDay, account, if (active) "Activa" else "Pausada")
        ) { error = it; if (it == null) onDismiss() }
    }, onDelete = item?.let { { viewModel.deleteSubscription(it) { e -> error = e; if (e == null) onDismiss() } } }) {
        TextField(name, "Nombre", onChange = { name = it })
        MoneyField(amount, "Importe", onChange = { amount = it })
        Selector(frequency, listOf("Semanal", "Quincenal", "Mensual", "Anual"), "Frecuencia", onSelected = { frequency = it })
        Selector(account, state.accounts.map { it.name } + state.cards.map { it.name }, "Cuenta o tarjeta", onSelected = { account = it })
        TextField(nextCharge, "Próximo cargo (AAAA-MM-DD)", onChange = { nextCharge = it })
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (active) "Automatización activa" else "Automatización pausada")
            Switch(checked = active, onCheckedChange = { active = it })
        }
    }
}

@Composable
private fun GoalDialog(item: GoalEntity?, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var target by remember { mutableStateOf(item?.targetCents?.fromCents().orEmpty()) }
    var saved by remember { mutableStateOf(item?.savedCents?.fromCents() ?: "0") }
    var contribution by remember { mutableStateOf(item?.monthlyContributionCents?.fromCents() ?: "0") }
    var targetDate by remember { mutableStateOf(item?.targetEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()) }
    var priority by remember { mutableStateOf(item?.priority ?: "Media") }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog("${if (item == null) "Nueva" else "Editar"} meta", error, onDismiss, onSave = {
        val targetCents = target.centsOrNull(); val savedCents = saved.centsOrNull(); val contributionCents = contribution.centsOrNull()
        val targetEpochDay = if (targetDate.isBlank()) null else runCatching { LocalDate.parse(targetDate).toEpochDay() }.getOrNull()
        if (name.isBlank() || targetCents == null || savedCents == null || contributionCents == null || (targetDate.isNotBlank() && targetEpochDay == null)) error = "Completa los datos" else viewModel.saveGoal(
            GoalEntity(item?.id ?: 0, name, targetCents, savedCents, targetEpochDay, contributionCents, priority, "Activa")
        ) { error = it; if (it == null) onDismiss() }
    }, onDelete = item?.let { { viewModel.deleteGoal(it) { e -> error = e; if (e == null) onDismiss() } } }) {
        TextField(name, "Nombre", onChange = { name = it })
        MoneyField(target, "Objetivo", onChange = { target = it })
        MoneyField(saved, "Ahorrado", onChange = { saved = it })
        MoneyField(contribution, "Aportación mensual reservada", onChange = { contribution = it })
        TextField(targetDate, "Fecha objetivo (AAAA-MM-DD, opcional)", onChange = { targetDate = it })
        Selector(priority, listOf("Alta", "Media", "Baja"), "Prioridad", onSelected = { priority = it })
    }
}

@Composable
private fun DebtDialog(item: DebtEntity?, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var creditor by remember { mutableStateOf(item?.creditor.orEmpty()) }
    var balance by remember { mutableStateOf(item?.balanceCents?.fromCents().orEmpty()) }
    var payment by remember { mutableStateOf(item?.monthlyPaymentCents?.fromCents() ?: "0") }
    var nextPayment by remember { mutableStateOf(item?.nextPaymentEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()) }
    var annualRate by remember { mutableStateOf(item?.annualRate?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog("${if (item == null) "Nueva" else "Editar"} deuda", error, onDismiss, onSave = {
        val balanceCents = balance.centsOrNull(); val paymentCents = payment.centsOrNull()
        val nextEpochDay = if (nextPayment.isBlank()) null else runCatching { LocalDate.parse(nextPayment).toEpochDay() }.getOrNull()
        val rate = if (annualRate.isBlank()) null else annualRate.replace(',', '.').toDoubleOrNull()
        if (name.isBlank() || balanceCents == null || paymentCents == null || (nextPayment.isNotBlank() && nextEpochDay == null) || (annualRate.isNotBlank() && rate == null)) error = "Completa los datos" else viewModel.saveDebt(
            DebtEntity(item?.id ?: 0, name, creditor, balanceCents, paymentCents, nextEpochDay, rate, "Activa")
        ) { error = it; if (it == null) onDismiss() }
    }, onDelete = item?.let { { viewModel.deleteDebt(it) { e -> error = e; if (e == null) onDismiss() } } }) {
        TextField(name, "Nombre", onChange = { name = it })
        TextField(creditor, "Acreedor", onChange = { creditor = it })
        MoneyField(balance, "Saldo pendiente", onChange = { balance = it })
        MoneyField(payment, "Pago mensual", onChange = { payment = it })
        TextField(nextPayment, "Próximo pago (AAAA-MM-DD, opcional)", onChange = { nextPayment = it })
        TextField(annualRate, "Tasa anual % (opcional)", onChange = { annualRate = it })
    }
}

@Composable
private fun InvestmentDialog(item: InvestmentEntity?, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var institution by remember { mutableStateOf(item?.institution.orEmpty()) }
    var contributed by remember { mutableStateOf(item?.contributedCents?.fromCents() ?: "0") }
    var current by remember { mutableStateOf(item?.currentValueCents?.fromCents() ?: "0") }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog("${if (item == null) "Nueva" else "Editar"} inversión", error, onDismiss, onSave = {
        val contributedCents = contributed.centsOrNull(); val currentCents = current.centsOrNull()
        if (name.isBlank() || contributedCents == null || currentCents == null) error = "Completa los datos" else viewModel.saveInvestment(
            InvestmentEntity(item?.id ?: 0, name, institution, contributedCents, currentCents, LocalDate.now().toEpochDay())
        ) { error = it; if (it == null) onDismiss() }
    }, onDelete = item?.let { { viewModel.deleteInvestment(it) { e -> error = e; if (e == null) onDismiss() } } }) {
        TextField(name, "Nombre", onChange = { name = it })
        TextField(institution, "Institución", onChange = { institution = it })
        MoneyField(contributed, "Aportado", onChange = { contributed = it })
        MoneyField(current, "Valor actual", onChange = { current = it })
    }
}

@Composable
private fun RecurringIncomeDialog(item: RecurringIncomeEntity?, state: FinanceUiState, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    var description by remember { mutableStateOf(item?.description.orEmpty()) }
    var amount by remember { mutableStateOf(item?.amountCents?.fromCents().orEmpty()) }
    var cadence by remember { mutableStateOf(item?.cadence ?: "Cada lunes") }
    var destination by remember { mutableStateOf(item?.destinationAccount ?: state.accounts.firstOrNull()?.name.orEmpty()) }
    var enabled by remember { mutableStateOf(item?.enabled ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog("${if (item == null) "Nuevo" else "Editar"} ingreso recurrente", error, onDismiss, onSave = {
        val cents = amount.centsOrNull()
        if (description.isBlank() || cents == null || destination.isBlank()) error = "Completa los datos" else viewModel.saveRecurringIncome(
            RecurringIncomeEntity(item?.id ?: 0, description, cents, cadence, destination, enabled)
        ) { error = it; if (it == null) onDismiss() }
    }, onDelete = item?.let { { viewModel.deleteRecurringIncome(it) { e -> error = e; if (e == null) onDismiss() } } }) {
        TextField(description, "Descripción", onChange = { description = it })
        MoneyField(amount, "Importe", onChange = { amount = it })
        Selector(cadence, listOf("Cada lunes", "Último día del mes"), "Frecuencia", onSelected = { cadence = it })
        Selector(destination, state.accounts.map { it.name }, "Cuenta destino", onSelected = { destination = it })
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Activo")
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
    }
}

private fun String.centsOrNull(): Long? = replace(',', '.').toBigDecimalOrNull()?.movePointRight(2)?.toLong()
private fun Long.fromCents(): String = "%.2f".format(this / 100.0)
private fun Long.asMoney(): String = "$" + "%,.2f".format(this / 100.0)


