package org.misfinanzas.demo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.misfinanzas.demo.data.FinanceUiState
import org.misfinanzas.demo.data.InstallmentPlanEntity
import org.misfinanzas.demo.data.InstallmentReview
import org.misfinanzas.demo.data.reviewInstallments
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun InstallmentReviewScreen(
    state: FinanceUiState,
    onClose: () -> Unit,
    onOpen: (InstallmentPlanEntity) -> Unit,
    onCorrect: (InstallmentPlanEntity) -> Unit,
    initialCardName: String? = null
) {
    BackHandler(onBack = onClose)
    val reviews = remember(state.cards, state.installmentPlans) { reviewInstallments(state.cards, state.installmentPlans) }
    var onlyIssues by rememberSaveable { mutableStateOf(initialCardName == null) }
    var cardName by rememberSaveable(initialCardName) { mutableStateOf(initialCardName) }
    var expandedName by rememberSaveable(initialCardName) { mutableStateOf(initialCardName) }
    val shown = reviews.filter { (cardName == null || it.cardName == cardName) && (!onlyIssues || it.requiresReview) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DeepNavy).navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { DiagnosticHeader("Revisar MSI", "Compara tarjetas con planes individuales", onClose) }
        item {
            Text("Esta revisión compara datos de la app; no consulta el banco. El saldo a favor se muestra aparte y no se resta del saldo MSI.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = onlyIssues, onClick = { onlyIssues = true }, label = { Text("Por revisar (${reviews.count { it.requiresReview }})") })
                FilterChip(selected = !onlyIssues, onClick = { onlyIssues = false }, label = { Text("Todas") })
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = cardName == null, onClick = { cardName = null }, label = { Text("Cualquier tarjeta") })
                reviews.forEach { review ->
                    FilterChip(selected = cardName == review.cardName, onClick = { cardName = review.cardName; expandedName = review.cardName }, label = { Text(review.cardName) })
                }
            }
        }
        items(shown, key = { it.cardName }) { review ->
            InstallmentReviewCard(review, expandedName == review.cardName, {
                expandedName = if (expandedName == review.cardName) null else review.cardName
            }, onOpen, onCorrect)
        }
        if (shown.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(28.dp)) {
                    Text(if (reviews.none { it.requiresReview }) "No encontramos diferencias en la app" else "No hay resultados con estos filtros", color = Mint, fontWeight = FontWeight.ExtraBold)
                    Text("Selecciona Todas para consultar los planes. Para confirmar o corregir importes, usa el desglose actual del banco.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun InstallmentReviewCard(
    review: InstallmentReview,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: (InstallmentPlanEntity) -> Unit,
    onCorrect: (InstallmentPlanEntity) -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface, contentColor = TextPrimary),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Column(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
                Text(review.cardName, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (review.requiresReview) "Por revisar" else "Coincide en la app", color = if (review.requiresReview) WarmAmber else Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Saldo MSI de tarjeta: ${review.cardMsiCents?.reviewMoney() ?: "Tarjeta no encontrada"}", color = TextPrimary, modifier = Modifier.padding(top = 12.dp))
                Text("Planes individuales: ${review.plansPendingCents.reviewMoney()}", color = TextPrimary, modifier = Modifier.padding(top = 3.dp))
                val difference = review.differenceCents
                Text(
                    when {
                        difference == null -> "Reasigna los planes a una tarjeta existente"
                        difference > 0 -> "La tarjeta tiene ${difference.reviewMoney()} más que los planes"
                        difference < 0 -> "Los planes suman ${(-difference).reviewMoney()} más que la tarjeta"
                        else -> "Sin diferencia en el total MSI"
                    }, color = if (difference == 0L) Mint else WarmAmber, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
                )
                Text("${review.pendingPlanCount} planes con saldo · ${review.planIssues.size} con datos por revisar", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                Text(if (expanded) "Ocultar planes" else "Ver planes", color = Mint, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            }
            if (expanded) {
                val plans = review.plans.filter { it.pendingBalanceCents > 0 || review.planIssues.containsKey(it.id) }
                if (plans.isEmpty()) Text("No hay planes con saldo pendiente registrados para esta tarjeta.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                plans.forEach { plan ->
                    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                        Text(plan.description, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Pendiente: ${plan.pendingBalanceCents.reviewMoney()} · Mensualidad: ${plan.monthlyPaymentCents.reviewMoney()}", color = TextMuted, fontSize = 12.sp)
                        Text("Progreso registrado: ${plan.currentPayment}/${plan.totalMonths}", color = TextMuted, fontSize = 12.sp)
                        val date = plan.nextDueEpochDay?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "MX"))) }
                        Text("Próximo pago: ${date ?: "No registrado"}", color = TextMuted, fontSize = 12.sp)
                        review.planIssues[plan.id].orEmpty().forEach { issue -> Text(issue, color = WarmAmber, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onOpen(plan) }) { Text("Ver detalle", color = Mint) }
                            TextButton(onClick = { onCorrect(plan) }) { Text("Corregir plan", color = Mint) }
                        }
                    }
                }
                Text("Corregir plan conserva los saldos de las tarjetas. No repartas diferencias entre compras sin el desglose real.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

private fun Long.reviewMoney(): String = NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(this / 100.0)

