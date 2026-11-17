package org.misfinanzas.demo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.misfinanzas.demo.data.DebtorEntity
import org.misfinanzas.demo.data.FinanceUiState
import org.misfinanzas.demo.data.InstallmentPlanEntity
import org.misfinanzas.demo.data.ReceivableEntity
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun InstallmentsScreen(state: FinanceUiState, onClose: (() -> Unit)?, onOpen: (InstallmentPlanEntity) -> Unit, onReview: () -> Unit) {
    if (onClose != null) BackHandler(onBack = onClose)
    var filter by remember { mutableStateOf("Activos") }
    val plans = state.installmentPlans.filter { filter == "Todos" || if (filter == "Activos") it.status == "Activo" else it.status == "Pagado" }
    val pending = state.installmentPlans.filter { it.status == "Activo" }.sumOf { it.pendingBalanceCents }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DeepNavy).navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            if (onClose != null) FullScreenHeader("Compras a MSI", "Progreso, mensualidades y próximas fechas", onClose)
            else TabScreenHeader("Compras a MSI", "Progreso, mensualidades y próximas fechas")
        }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15333D), contentColor = TextPrimary),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("SALDO PENDIENTE EN MSI", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(pending.receivableMoney(), color = Mint, fontSize = 31.sp, fontWeight = FontWeight.Black)
                    Text("${state.installmentPlans.count { it.status == "Activo" }} compras activas", color = TextMuted)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Activos", "Pagados", "Todos").forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = DeepNavy,
                            labelColor = TextMuted,
                            selectedContainerColor = NavySurfaceHigh,
                            selectedLabelColor = TextPrimary
                        )
                    )
                }
            }
        }
        item {
            TextButton(onClick = onReview, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("Revisar saldos y planes MSI", color = Mint, fontWeight = FontWeight.Bold)
            }
        }
        items(plans, key = { "all-msi-${it.id}" }) { plan ->
            InstallmentListCard(plan) { onOpen(plan) }
        }
        if (plans.isEmpty()) item { FullEmpty("No hay compras en esta vista", "Cuando registres una compra a MSI aparecerá aquí.") }
    }
}

@Composable
fun DebtorsScreen(state: FinanceUiState, onClose: () -> Unit, onAdd: () -> Unit, onOpen: (DebtorEntity) -> Unit) {
    BackHandler(onBack = onClose)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DeepNavy).navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { FullScreenHeader("Deudores", "Personas y dinero pendiente por cobrar", onClose, onAdd) }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15333D), contentColor = TextPrimary),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("TOTAL POR COBRAR", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(state.pendingReceivablesCents.receivableMoney(), color = Mint, fontSize = 31.sp, fontWeight = FontWeight.Black)
                    Text("No se suma a tu saldo hasta que confirmes un pago", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
        items(state.debtors, key = { "debtor-${it.id}" }) { debtor ->
            val debts = state.receivables.filter { it.debtorId == debtor.id && it.status == "Pendiente" }
            DebtorCard(debtor, debts.sumOf { it.amountCents }, debts.size) { onOpen(debtor) }
        }
        if (state.debtors.isEmpty()) item { FullEmpty("Aún no hay deudores", "Agrega una persona y después registra todo lo que te debe.") }
    }
}

@Composable
fun DebtorDetailScreen(
    debtor: DebtorEntity,
    state: FinanceUiState,
    onClose: () -> Unit,
    onEditDebtor: () -> Unit,
    onAddDebt: () -> Unit,
    onEditDebt: (ReceivableEntity) -> Unit,
    onReceivePayment: (ReceivableEntity) -> Unit
) {
    BackHandler(onBack = onClose)
    val debts = state.receivables.filter { it.debtorId == debtor.id }
    val pending = debts.filter { it.status == "Pendiente" }
    val paid = debts.filter { it.status == "Pagada" }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DeepNavy).navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { FullScreenHeader(debtor.name, debtor.notes ?: "Detalle de deudas", onClose, onEditDebtor, true) }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(debtor.colorHex).copy(alpha = .18f), contentColor = TextPrimary),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("TE DEBE", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(pending.sumOf { it.amountCents }.receivableMoney(), color = Mint, fontSize = 31.sp, fontWeight = FontWeight.Black)
                    Text("${pending.size} deudas pendientes · ${paid.size} pagadas", color = TextMuted)
                    Button(
                        onClick = onAddDebt,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DeepNavy)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Agregar deuda", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (pending.isNotEmpty()) item { SubsectionTitle("Pendientes") }
        items(pending, key = { "pending-${it.id}" }) { debt ->
            ReceivableCard(debt, { onEditDebt(debt) }, { onReceivePayment(debt) })
        }
        if (paid.isNotEmpty()) item { SubsectionTitle("Historial pagado") }
        items(paid, key = { "paid-${it.id}" }) { debt ->
            ReceivableCard(debt, { onEditDebt(debt) }, null)
        }
        if (debts.isEmpty()) item { FullEmpty("Sin deudas registradas", "Agrega el primer concepto que esta persona te debe.") }
    }
}

@Composable
private fun FullScreenHeader(title: String, subtitle: String, onClose: () -> Unit, onAction: (() -> Unit)? = null, editAction: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, modifier = Modifier.clip(CircleShape).background(NavySurfaceHigh)) { Icon(Icons.Default.Close, "Volver", tint = TextPrimary) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        onAction?.let {
            IconButton(onClick = it, modifier = Modifier.clip(CircleShape).background(Mint)) {
                Icon(if (editAction) Icons.Default.Edit else Icons.Default.Add, null, tint = DeepNavy)
            }
        }
    }
}

@Composable
private fun TabScreenHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(title, color = TextPrimary, fontSize = 29.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun InstallmentListCard(plan: InstallmentPlanEntity, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = NavySurface, contentColor = TextPrimary),
        shape = RoundedCornerShape(21.dp)
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(SoftBlue.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ReceiptLong, null, tint = SoftBlue)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(plan.description, color = TextPrimary, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${plan.cardName} · ${plan.currentPayment} de ${plan.totalMonths}", color = TextMuted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (plan.status == "Pagado") "Pagado" else plan.pendingBalanceCents.receivableMoney(), color = if (plan.status == "Pagado") Mint else TextPrimary, fontWeight = FontWeight.Black)
                    Text("${plan.monthlyPaymentCents.receivableMoney()} / mes", color = TextMuted, fontSize = 10.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(plan.totalMonths.coerceAtMost(24)) { index ->
                    val paid = index < plan.currentPayment || plan.status == "Pagado"
                    Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(if (paid) Mint else NavySurfaceHigh))
                }
            }
        }
    }
}

@Composable
private fun DebtorCard(debtor: DebtorEntity, pendingCents: Long, count: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(debtor.colorHex).copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null, tint = Color(debtor.colorHex))
        }
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(debtor.name, color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text(if (count == 1) "1 deuda pendiente" else "$count deudas pendientes", color = TextMuted, fontSize = 11.sp)
        }
        Text(pendingCents.receivableMoney(), color = if (pendingCents > 0) Mint else TextMuted, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ReceivableCard(debt: ReceivableEntity, onEdit: () -> Unit, onPay: (() -> Unit)?) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = NavySurface, contentColor = TextPrimary),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(if (debt.status == "Pagada") Mint.copy(alpha = .14f) else WarmAmber.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                    Icon(if (debt.status == "Pagada") Icons.Default.Check else Icons.Default.ReceiptLong, null, tint = if (debt.status == "Pagada") Mint else WarmAmber)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(debt.name, color = TextPrimary, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(buildString {
                        append(debt.category)
                        debt.dueEpochDay?.let { append(" · vence ").append(LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("d MMM", Locale("es", "MX")))) }
                    }, color = TextMuted, fontSize = 11.sp)
                }
                Text(debt.amountCents.receivableMoney(), fontWeight = FontWeight.Black, color = if (debt.status == "Pagada") TextMuted else TextPrimary)
            }
            onPay?.let {
                Button(
                    onClick = it,
                    modifier = Modifier.fillMaxWidth().padding(top = 13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DeepNavy)
                ) { Text("Confirmar pago recibido", fontWeight = FontWeight.Bold) }
            }
            if (debt.status == "Pagada") {
                Text("Pagada${debt.destinationAccountName?.let { " · $it" }.orEmpty()}", color = Mint, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun SubsectionTitle(title: String) {
    Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp))
}

@Composable
private fun FullEmpty(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = TextPrimary, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

private fun Long.receivableMoney(): String = NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(this / 100.0)


