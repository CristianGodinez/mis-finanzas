package org.misfinanzas.demo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.misfinanzas.demo.data.AccountEntity
import org.misfinanzas.demo.data.CardEntity
import org.misfinanzas.demo.data.FinanceUiState
import org.misfinanzas.demo.data.IgnoredNotificationEntity
import org.misfinanzas.demo.data.balanceCheck
import org.misfinanzas.demo.data.ReconciliationEntry
import org.misfinanzas.demo.data.reconciliationHistory
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReconciliationScreen(
    state: FinanceUiState,
    onClose: () -> Unit,
    onAccount: (AccountEntity) -> Unit,
    onCard: (CardEntity) -> Unit,
    onHistory: () -> Unit
) {
    BackHandler(onBack = onClose)
    val inconsistentCards = state.cards.count { !it.balanceCheck().isConsistent }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DeepNavy).navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { DiagnosticHeader("Conciliar saldos", "Compara la app con cada banco", onClose) }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15333D), contentColor = TextPrimary),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("CONTROL DE SALDOS", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Toca una cuenta o tarjeta", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp))
                    Text("Escribe el saldo real que muestra el banco. La diferencia quedará registrada como un ajuste sin afectar tu presupuesto.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        if (inconsistentCards == 0) "Todas las tarjetas tienen un desglose consistente" else "$inconsistentCards ${if (inconsistentCards == 1) "tarjeta requiere" else "tarjetas requieren"} revisión",
                        color = if (inconsistentCards == 0) Mint else WarmAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
        item {
            TextButton(onClick = onHistory, modifier = Modifier.padding(horizontal = 20.dp)) {
                Icon(Icons.Default.History, null, tint = Mint)
                Text("Ver historial de ajustes", color = Mint, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
        }
        item { DiagnosticSection("Dinero disponible") }
        items(state.accounts, key = { "reconcile-account-${it.name}" }) { account ->
            DiagnosticBalanceRow(Icons.Default.AccountBalance, account.name, account.type, account.balanceCents.diagnosticMoney(), Color(account.colorHex)) { onAccount(account) }
        }
        item { DiagnosticSection("Tarjetas de crédito") }
        items(state.cards, key = { "reconcile-card-${it.name}" }) { card ->
            val check = card.balanceCheck()
            val subtitle = if (check.isConsistent) {
                "Desglose verificado · ${card.status}"
            } else {
                val difference = kotlin.math.abs(check.componentDifferenceCents)
                if (difference > 0) "Revisar desglose · diferencia ${difference.diagnosticMoney()}" else "Revisar límite y disponible"
            }
            DiagnosticBalanceRow(Icons.Default.CreditCard, card.name, subtitle, card.balanceCents.diagnosticMoney(), if (check.isConsistent) Color(card.colorHex) else WarmAmber) { onCard(card) }
        }
    }
}

@Composable
fun ReconciliationHistoryScreen(
    state: FinanceUiState,
    onClose: () -> Unit,
    initialAccountName: String? = null,
    initialExpandedId: String? = null
) {
    BackHandler(onBack = onClose)
    var accountName by rememberSaveable(initialAccountName) { mutableStateOf(initialAccountName) }
    var query by rememberSaveable { mutableStateOf("") }
    var expandedId by rememberSaveable(initialExpandedId) { mutableStateOf(initialExpandedId) }
    val allEntries = remember(state.transactions) { reconciliationHistory(state.transactions) }
    val names = remember(allEntries) { allEntries.map { it.transaction.accountName }.distinct().sorted() }
    val entries = remember(state.transactions, accountName, query) { reconciliationHistory(state.transactions, accountName, query) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DeepNavy).navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { DiagnosticHeader("Historial de ajustes", "Conciliaciones de cuentas y tarjetas", onClose) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar cuenta o motivo", color = TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(18.dp)
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = accountName == null, onClick = { accountName = null }, label = { Text("Todas") })
                names.forEach { name ->
                    FilterChip(selected = accountName == name, onClick = { accountName = name }, label = { Text(name) })
                }
            }
            Text("${entries.size} ajustes · Sin impacto en presupuesto", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
            Text("Toca un ajuste para ver el detalle. Para corregirlo, realiza otra conciliación.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        if (entries.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (allEntries.isEmpty()) "Aún no hay conciliaciones" else "No hay ajustes con esos filtros", color = TextPrimary, fontWeight = FontWeight.ExtraBold)
                    Text(if (allEntries.isEmpty()) "Los cambios guardados desde Conciliar saldos aparecerán aquí." else "Selecciona Todas o cambia la búsqueda.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        items(entries, key = { it.transaction.id }) { entry ->
            ReconciliationHistoryCard(entry, expandedId == entry.transaction.id) {
                expandedId = if (expandedId == entry.transaction.id) null else entry.transaction.id
            }
        }
    }
}

@Composable
private fun ReconciliationHistoryCard(entry: ReconciliationEntry, expanded: Boolean, onToggle: () -> Unit) {
    val transaction = entry.transaction
    val date = java.time.LocalDate.ofEpochDay(transaction.dateEpochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "MX")))
    val difference = entry.differenceCents
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = NavySurface, contentColor = TextPrimary),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(transaction.accountName, color = Mint, fontWeight = FontWeight.ExtraBold)
            Text("$date · ${if (entry.isCard) "Tarjeta" else "Cuenta"}", color = TextMuted, fontSize = 11.sp)
            Text(entry.reason, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            Text(
                if (difference == 0L) "Desglose actualizado · Sin cambio en el total" else "${if (entry.isCard) "Deuda" else "Saldo"}: ${if (difference > 0) "+" else "−"}${kotlin.math.abs(difference).diagnosticMoney()}",
                color = SoftBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp)
            )
            Text(if (expanded) "Ocultar detalle" else "Ver antes y después", color = Mint, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            if (expanded) {
                entry.values.forEach { value ->
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(value.label, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Antes: ${value.beforeCents?.diagnosticMoney() ?: "No registrado"}", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("Después: ${value.afterCents?.diagnosticMoney() ?: "No registrado"}", color = Mint, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (entry.values.any { it.beforeCents == null || it.afterCents == null }) {
                    Text("Este ajuste antiguo no conserva todos los componentes. Solo se muestran las cifras registradas.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
                    if (entry.values.all { it.beforeCents == null && it.afterCents == null }) {
                        transaction.notes?.takeIf { it.isNotBlank() }?.let { Text(it, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun IgnoredNotificationsScreen(
    items: List<IgnoredNotificationEntity>,
    onClose: () -> Unit,
    onDelete: (IgnoredNotificationEntity) -> Unit,
    onClear: () -> Unit
) {
    BackHandler(onBack = onClose)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DeepNavy).navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { DiagnosticHeader("Alertas ignoradas", "Avisos bancarios que no generaron movimientos", onClose, if (items.isEmpty()) null else onClear) }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15333D), contentColor = TextPrimary),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Mint.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.History, null, tint = Mint)
                    }
                    Column(Modifier.padding(start = 13.dp)) {
                        Text("Diagnóstico local", color = TextPrimary, fontWeight = FontWeight.ExtraBold)
                        Text("Se conservan hasta 100 avisos durante 30 días. Aquí puedes ver por qué no fueron detectados.", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
        if (items.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No hay alertas ignoradas", color = TextPrimary, fontWeight = FontWeight.ExtraBold)
                    Text("Los avisos bancarios no reconocidos aparecerán aquí.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            items(items, key = { "ignored-${it.id}" }) { item -> IgnoredNotificationCard(item, onDelete) }
        }
    }
}

@Composable
internal fun DiagnosticHeader(title: String, subtitle: String, onClose: () -> Unit, onClear: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, modifier = Modifier.clip(CircleShape).background(NavySurfaceHigh)) {
            Icon(Icons.Default.Close, "Volver", tint = TextPrimary)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        onClear?.let { action ->
            TextButton(onClick = action) { Text("Borrar todo", color = Danger, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun DiagnosticSection(title: String) {
    Text(title, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
}

@Composable
private fun DiagnosticBalanceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    value: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = NavySurface, contentColor = TextPrimary),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = TextMuted, fontSize = 11.sp)
            }
            Text(value, color = color, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun IgnoredNotificationCard(item: IgnoredNotificationEntity, onDelete: (IgnoredNotificationEntity) -> Unit) {
    val date = Instant.ofEpochMilli(item.postedAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale("es", "MX")))
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface, contentColor = TextPrimary),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.bankName, color = Mint, fontWeight = FontWeight.ExtraBold)
                    Text(date, color = TextMuted, fontSize = 10.sp)
                }
                IconButton(onClick = { onDelete(item) }) { Icon(Icons.Default.DeleteOutline, "Eliminar", tint = TextMuted) }
            }
            Text(item.reason, color = WarmAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            if (item.title.isNotBlank()) Text(item.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            if (item.text.isNotBlank()) Text(item.text, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Long.diagnosticMoney(): String = NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(this / 100.0)


