package org.misfinanzas.demo.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import org.misfinanzas.demo.data.AccountEntity
import org.misfinanzas.demo.data.paymentDates
import org.misfinanzas.demo.data.RECONCILIATION_SOURCE
import org.misfinanzas.demo.data.CardEntity
import org.misfinanzas.demo.data.DetectedMovementEntity
import org.misfinanzas.demo.data.BudgetEntity
import org.misfinanzas.demo.data.FinanceUiState
import org.misfinanzas.demo.data.FinanceViewModel
import org.misfinanzas.demo.data.InstallmentPlanEntity
import org.misfinanzas.demo.data.MovementRequest
import org.misfinanzas.demo.data.ProjectionEvent
import org.misfinanzas.demo.data.SeedData
import org.misfinanzas.demo.data.TransactionEntity
import org.misfinanzas.demo.quicksettings.AddMovementTileService
import org.misfinanzas.demo.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class AppTab(val title: String, val icon: ImageVector) {
    HomeTab("Inicio", Icons.Default.Home),
    Movements("Movimientos", Icons.Default.ReceiptLong),
    Plan("Plan", Icons.Default.PieChart),
    Accounts("Cuentas", Icons.Default.AccountBalanceWallet),
    Installments("MSI", Icons.Default.CalendarMonth),
    More("Más", Icons.Default.MoreHoriz)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceApp(openAddMovementTrigger: Int = 0, viewModel: FinanceViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<EditorTarget?>(null) }
    var selectedInstallmentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDebtors by rememberSaveable { mutableStateOf(false) }
    var selectedDebtorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showInstallmentReview by rememberSaveable { mutableStateOf(false) }
    var reviewCardName by rememberSaveable { mutableStateOf<String?>(null) }
    var showReconciliationHistory by rememberSaveable { mutableStateOf(false) }
    var historyAccountName by rememberSaveable { mutableStateOf<String?>(null) }
    var historyExpandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showReconciliation by rememberSaveable { mutableStateOf(false) }
    var showIgnoredNotifications by rememberSaveable { mutableStateOf(false) }
    val selectedInstallment = state.installmentPlans.firstOrNull { it.id == selectedInstallmentId }
    val selectedDebtor = state.debtors.firstOrNull { it.id == selectedDebtorId }

    LaunchedEffect(openAddMovementTrigger) {
        if (openAddMovementTrigger > 0) showAdd = true
    }

    if (selectedInstallment != null) {
        InstallmentDetailScreen(
            item = selectedInstallment,
            onClose = { selectedInstallmentId = null },
            onEdit = { editor = EditorTarget.InstallmentEdit(selectedInstallment, preserveCardBalances = showInstallmentReview) },
            onPay = { editor = EditorTarget.Installment(selectedInstallment) }
        )
        editor?.let { target ->
            ManagementDialog(target, state, viewModel, onDismiss = { editor = null })
        }
        return
    }

    if (selectedDebtor != null) {
        DebtorDetailScreen(
            debtor = selectedDebtor,
            state = state,
            onClose = { selectedDebtorId = null },
            onEditDebtor = { editor = EditorTarget.Debtor(selectedDebtor) },
            onAddDebt = { editor = EditorTarget.Receivable(selectedDebtor.id) },
            onEditDebt = { editor = EditorTarget.Receivable(selectedDebtor.id, it) },
            onReceivePayment = { editor = EditorTarget.ReceivePayment(it) }
        )
        editor?.let { ManagementDialog(it, state, viewModel, onDismiss = { editor = null }) }
        return
    }

    if (showDebtors) {
        DebtorsScreen(
            state = state,
            onClose = { showDebtors = false },
            onAdd = { editor = EditorTarget.Debtor() },
            onOpen = { selectedDebtorId = it.id }
        )
        editor?.let { ManagementDialog(it, state, viewModel, onDismiss = { editor = null }) }
        return
    }

    if (showInstallmentReview) {
        InstallmentReviewScreen(
            state = state,
            onClose = { showInstallmentReview = false },
            onOpen = { reviewCardName = it.cardName; selectedInstallmentId = it.id },
            onCorrect = { editor = EditorTarget.InstallmentEdit(it, preserveCardBalances = true) },
            initialCardName = reviewCardName
        )
        editor?.let { ManagementDialog(it, state, viewModel, onDismiss = { editor = null }) }
        return
    }

    if (showReconciliationHistory) {
        ReconciliationHistoryScreen(
            state = state,
            onClose = { showReconciliationHistory = false },
            initialAccountName = historyAccountName,
            initialExpandedId = historyExpandedId
        )
        return
    }

    if (showReconciliation) {
        ReconciliationScreen(
            state = state,
            onClose = { showReconciliation = false },
            onAccount = { editor = EditorTarget.AccountReconciliation(it) },
            onCard = { editor = EditorTarget.CardReconciliation(it) },
            onHistory = { historyAccountName = null; historyExpandedId = null; showReconciliationHistory = true }
        )
        editor?.let { ManagementDialog(it, state, viewModel, onDismiss = { editor = null }) }
        return
    }

    if (showIgnoredNotifications) {
        IgnoredNotificationsScreen(
            items = state.ignoredNotifications,
            onClose = { showIgnoredNotifications = false },
            onDelete = { viewModel.deleteIgnoredNotification(it) },
            onClear = { viewModel.clearIgnoredNotifications() }
        )
        return
    }

    Scaffold(
        containerColor = DeepNavy,
        bottomBar = {
            NavigationBar(
                containerColor = NavySurface,
                tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                AppTab.entries.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(item.icon, item.title) },
                        label = {
                            Text(
                                item.title,
                                fontSize = 8.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DeepNavy,
                            selectedTextColor = Mint,
                            indicatorColor = Mint,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = Mint,
                    contentColor = DeepNavy,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Movimiento", fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            when (AppTab.entries[selectedTab]) {
                AppTab.HomeTab -> HomeScreen(state, onOpenInbox = { selectedTab = AppTab.More.ordinal })
                AppTab.Movements -> MovementsScreen(state, onEdit = {
                    if (it.source == RECONCILIATION_SOURCE) {
                        historyAccountName = it.accountName
                        historyExpandedId = it.id
                        showReconciliationHistory = true
                    } else editor = EditorTarget.Transaction(it)
                })
                AppTab.Plan -> PlanScreen(
                    state,
                    onEdit = { editor = it },
                    onInstallmentClick = { selectedInstallmentId = it.id },
                    onOpenInstallments = { selectedTab = AppTab.Installments.ordinal }
                )
                AppTab.Accounts -> AccountsScreen(state, onEdit = { editor = it })
                AppTab.Installments -> InstallmentsScreen(state, onClose = null, onOpen = { selectedInstallmentId = it.id }, onReview = { reviewCardName = null; showInstallmentReview = true })
                AppTab.More -> MoreScreen(
                    state,
                    viewModel,
                    onEdit = { editor = it },
                    onOpenDebtors = { showDebtors = true },
                    onOpenReconciliation = { showReconciliation = true },
                    onOpenReconciliationHistory = { historyAccountName = null; historyExpandedId = null; showReconciliationHistory = true },
                    onOpenInstallmentReview = { reviewCardName = null; showInstallmentReview = true },
                    onOpenIgnoredNotifications = { showIgnoredNotifications = true }
                )
            }
        }
    }

    if (showAdd) {
        AddMovementSheet(
            state = state,
            onDismiss = { showAdd = false },
            onSave = { request, onResult ->
                viewModel.addMovement(request) { error ->
                    onResult(error)
                    if (error == null) showAdd = false
                }
            }
        )
    }

    editor?.let { target ->
        ManagementDialog(target, state, viewModel, onDismiss = { editor = null })
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(title, fontSize = 29.sp, fontWeight = FontWeight.Black, color = TextPrimary)
        subtitle?.let { Text(it, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp)) }
    }
}

@Composable
private fun HomeScreen(state: FinanceUiState, onOpenInbox: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 112.dp)
    ) {
        item {
            ScreenHeader("Mis Finanzas", "Tu dinero, claro desde el primer vistazo")
        }
        if (state.pendingDetections.isNotEmpty()) {
            item {
                NotificationBanner(state.pendingDetections.size, onOpenInbox)
            }
        }
        item { HeroMoneyCard(state) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiniMetric("Dinero líquido", state.liquidMoneyCents.money(), Icons.Default.AccountBalanceWallet, Mint, Modifier.weight(1f))
                MiniMetric("Deuda total", state.cardDebtCents.money(), Icons.Default.CreditCard, Danger, Modifier.weight(1f))
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiniMetric("Crédito disponible", state.creditAvailableCents.money(), Icons.Default.TrendingUp, SoftBlue, Modifier.weight(1f))
                MiniMetric("Patrimonio neto", state.netWorthCents.money(), Icons.Default.Savings, WarmAmber, Modifier.weight(1f))
            }
        }
        item { SectionTitle("Próximos pagos", "Ordenados por vencimiento") }
        items(state.cards.filterNot { it.paymentConfirmed }.take(4), key = { it.name }) { card ->
            UpcomingPaymentRow(card)
        }
        item { SectionTitle("Movimientos recientes", "Tus últimos registros") }
        items(state.transactions.take(6), key = { it.id }) { transaction ->
            TransactionRow(transaction)
        }
    }
}

@Composable
private fun HeroMoneyCard(state: FinanceUiState) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF0B5F57), Color(0xFF19365A), Color(0xFF15213C))))
                .padding(22.dp)
        ) {
            Text("PUEDES GASTAR", color = Color.White.copy(alpha = .72f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(state.safeToSpendCents.money(), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 5.dp))
            Text("después de apartar tarjetas y compromisos del mes", color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
            HorizontalDivider(Modifier.padding(vertical = 18.dp), color = Color.White.copy(alpha = .14f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("TOTAL APARTADO", color = Color.White.copy(alpha = .65f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(state.totalReservedCents.money(), color = WarmAmber, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("INGRESO MENSUAL", color = Color.White.copy(alpha = .65f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(state.expectedMonthlyIncomeCents.money(), color = Mint, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Text(
                "Tarjetas ${state.upcomingPaymentsCents.money()} · Fijos ${state.fixedCommitmentsCents.money()}",
                color = Color.White.copy(alpha = .58f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 11.dp)
            )
        }
    }
}

@Composable
private fun NotificationBanner(count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF173A3B)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Mint.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AutoAwesome, null, tint = Mint)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("$count ${if (count == 1) "movimiento detectado" else "movimientos detectados"}", fontWeight = FontWeight.Bold)
                Text("Toca para revisar antes de registrar", color = TextMuted, fontSize = 12.sp)
            }
            Text("Revisar", color = Mint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MiniMetric(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = NavySurface)) {
        Column(Modifier.padding(15.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
            }
            Text(title, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 13.dp))
            Text(value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 9.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            subtitle?.let { Text(it, fontSize = 11.sp, color = TextMuted) }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = Mint, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun UpcomingPaymentRow(card: CardEntity) {
    val date = card.dueEpochDay?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("d MMM", Locale("es", "MX"))) } ?: "Sin fecha"
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(card.colorHex).copy(alpha = .2f)), contentAlignment = Alignment.Center) {
                Icon(accountIcon(card.iconKey), null, tint = Color(card.colorHex))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(card.name, fontWeight = FontWeight.Bold)
                Text("Vence $date · ${card.status}", color = TextMuted, fontSize = 11.sp)
            }
            Text(card.statementPaymentCents.money(), fontWeight = FontWeight.ExtraBold, color = WarmAmber)
        }
    }
}

@Composable
private fun MovementsScreen(state: FinanceUiState, onEdit: (TransactionEntity) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Todos") }
    val shown = remember(state.transactions, search, filter) {
        state.transactions.filter {
            (search.isBlank() || it.description.contains(search, true) || it.accountName.contains(search, true)) &&
                (filter == "Todos" || if (filter == "Ajustes") it.source == RECONCILIATION_SOURCE else it.type == filter)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        item { ScreenHeader("Movimientos", "${state.transactions.size} registros en tu historial") }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Buscar comercio o cuenta") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(18.dp)
            )
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todos", "Ajustes", "Gasto", "Ingreso", "Mensualidad MSI", "Pago tarjeta", "Devolución", "Pago con puntos", "Bonificación").forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) })
                }
            }
        }
        items(shown, key = { it.id }) { TransactionRow(it, onClick = { onEdit(it) }) }
        if (shown.isEmpty()) item { EmptyState("No encontramos movimientos", "Prueba otro filtro o registra uno nuevo.") }
    }
}

@Composable
private fun TransactionRow(item: TransactionEntity, onClick: (() -> Unit)? = null) {
    val isIncome = item.type in listOf("Ingreso", "Devolución", "Pago con puntos", "Bonificación")
    val color = if (isIncome) Mint else if (item.budgetImpactCents == 0L) SoftBlue else TextPrimary
    val sign = if (isIncome) "+" else if (item.type == "Pago tarjeta") "" else "−"
    Row(
        Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() }.padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = .12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null, tint = color, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(item.description, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.accountName} · ${item.category}", color = TextMuted, fontSize = 11.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$sign${item.amountCents.money()}", color = color, fontWeight = FontWeight.Bold)
            Text(LocalDate.ofEpochDay(item.dateEpochDay).format(DateTimeFormatter.ofPattern("d MMM", Locale("es", "MX"))), color = TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PlanScreen(
    state: FinanceUiState,
    onEdit: (EditorTarget) -> Unit,
    onInstallmentClick: (InstallmentPlanEntity) -> Unit,
    onOpenInstallments: () -> Unit
) {
    val projectionEvents = remember(
        state.accounts,
        state.cards,
        state.subscriptions,
        state.debts,
        state.goals,
        state.recurringIncome
    ) {
        runCatching { state.projectionEvents30 }.getOrDefault(emptyList())
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
        item { ScreenHeader("Plan financiero", "Presupuesto, MSI, suscripciones y metas") }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = NavySurface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("GASTO NETO DEL MES", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(state.currentMonthSpentCents.money(), fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text("Asigna presupuestos para calcular cuánto queda por categoría.", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
        item { ProjectionCard(state.liquidMoneyCents, projectionEvents) }
        item { SectionTitle("Calendario de los próximos 30 días", "Ingresos programados y compromisos pendientes") }
        itemsIndexed(
            projectionEvents.take(12),
            key = { index, event -> "$index-${event.dateEpochDay}-${event.kind}-${event.description}" }
        ) { _, event ->
            ProjectionRow(event)
        }
        if (projectionEvents.isEmpty()) {
            item { EmptyState("Sin movimientos programados", "No hay ingresos ni pagos registrados para los próximos 30 días.") }
        }
        item { MonthlyTrendCard(state) }
        item { SectionTitle("Presupuesto por categoría", "Toca una categoría para asignar su límite") }
        val currentMonth = YearMonth.now()
        val spendByCategory = state.transactions
            .filter { it.status == "Confirmado" && YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == currentMonth }
            .groupBy { it.category }
            .mapValues { it.value.sumOf(TransactionEntity::budgetImpactCents) }
        items(state.categories.map { it.name }.ifEmpty { SeedData.categories }) { category ->
            val spent = spendByCategory[category] ?: 0
            val limit = state.budgets.firstOrNull { it.category == category }?.limitCents
            BudgetRow(category, spent, limit, onClick = { onEdit(EditorTarget.Budget(state.budgets.firstOrNull { it.category == category } ?: BudgetEntity(category, null))) })
        }
        item { SectionTitle("Meses sin intereses", "${state.installmentPlans.count { it.status == "Activo" }} planes activos", "Ver todos", onOpenInstallments) }
        item {
            MsiPlanGroup(
                state.installmentPlans.filter { it.status == "Activo" } + state.installmentPlans.filter { it.status == "Pagado" }.take(3),
                onClick = onInstallmentClick
            )
        }
        item { SectionTitle("Suscripciones", actionLabel = "Agregar", onAction = { onEdit(EditorTarget.Subscription()) }) }
        items(state.subscriptions, key = { "subscription-${it.name}" }) { subscription ->
            Box(Modifier.clickable { onEdit(EditorTarget.Subscription(subscription)) }) {
                val next = subscription.nextChargeEpochDay?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("d MMM", Locale("es", "MX"))) } ?: "Sin fecha"
                SimpleInfoRow(Icons.Default.ReceiptLong, subscription.name, "${subscription.frequency} · $next · ${subscription.status}", subscription.amountCents.money(), if (subscription.status == "Pausada") TextMuted else SoftBlue)
            }
        }
        item { SectionTitle("Metas", actionLabel = "Agregar", onAction = { onEdit(EditorTarget.Goal()) }) }
        items(state.goals, key = { "goal-${it.id}" }) { goal ->
            Box(Modifier.clickable { onEdit(EditorTarget.Goal(goal)) }) {
                SimpleInfoRow(Icons.Default.Savings, goal.name, "Ahorrado ${goal.savedCents.money()}", goal.targetCents.money(), Mint)
            }
        }
        if (state.goals.isEmpty()) item { EmptyState("Aún no hay metas", "Después podremos añadir fondo de emergencia, viajes o compras.") }
    }
}

@Composable
private fun ProjectionCard(liquidMoneyCents: Long, events: List<ProjectionEvent>) {
    val incoming = events.filter { it.amountCents > 0 }.sumOf { it.amountCents }
    val outgoing = events.filter { it.amountCents < 0 }.sumOf { -it.amountCents }
    val projectedBalance = liquidMoneyCents + events.sumOf { it.amountCents }
    val positive = projectedBalance >= 0
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF15333D)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("PROYECCIÓN A 30 DÍAS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(projectedBalance.money(), color = if (positive) Mint else Danger, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Saldo estimado después de ingresos y pagos programados", color = TextMuted, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Ingresará", color = TextMuted, fontSize = 10.sp)
                    Text("+${incoming.money()}", color = Mint, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Saldrá", color = TextMuted, fontSize = 10.sp)
                    Text("−${outgoing.money()}", color = WarmAmber, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ProjectionRow(event: ProjectionEvent) {
    val income = event.amountCents >= 0
    val color = if (income) Mint else WarmAmber
    val date = runCatching {
        LocalDate.ofEpochDay(event.dateEpochDay).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale("es", "MX")))
    }.getOrDefault("Fecha pendiente")
    SimpleInfoRow(
        if (income) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
        event.description,
        "$date · ${event.kind}",
        "${if (income) "+" else "−"}${kotlin.math.abs(event.amountCents).money()}",
        color
    )
}

@Composable
private fun MonthlyTrendCard(state: FinanceUiState) {
    val months = remember(state.transactions) {
        val current = YearMonth.now()
        (5 downTo 0).map { current.minusMonths(it.toLong()) }.map { month ->
            month to state.transactions
                .filter { it.status == "Confirmado" && YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == month }
                .sumOf { it.budgetImpactCents }
                .coerceAtLeast(0)
        }
    }
    val maximum = months.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("GASTO DE LOS ÚLTIMOS 6 MESES", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().height(145.dp).padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                months.forEach { (month, amount) ->
                    val height = (100f * amount.toFloat() / maximum.toFloat()).coerceAtLeast(4f)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(Modifier.fillMaxWidth(.58f).height(height.dp).clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)).background(if (month == YearMonth.now()) Mint else SoftBlue.copy(alpha = .55f)))
                        Text(month.month.name.take(3), color = TextMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(category: String, spent: Long, limit: Long?, onClick: () -> Unit) {
    val progress = if (limit == null || limit <= 0) 0f else (spent.toFloat() / limit).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(category, fontWeight = FontWeight.SemiBold)
            Text(if (limit == null) spent.money() else "${spent.money()} / ${limit.money()}", color = if (limit != null && spent > limit) Danger else TextMuted, fontSize = 12.sp)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp).height(7.dp).clip(CircleShape),
            color = if (progress >= .8f) WarmAmber else Mint,
            trackColor = NavySurfaceHigh
        )
    }
}

@Composable
private fun MsiPlanGroup(items: List<InstallmentPlanEntity>, onClick: (InstallmentPlanEntity) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(horizontal = 17.dp, vertical = 18.dp)) {
            Text("Pagos a meses activos", fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 6.dp))
            if (items.isEmpty()) {
                Text("No tienes compras a meses registradas.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 18.dp))
            }
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = NavySurfaceHigh.copy(alpha = .65f), modifier = Modifier.padding(start = 54.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { onClick(item) }.padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(Color(item.cardName.hashCode()).copy(alpha = .24f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item.description.take(1).uppercase(), color = Mint, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(item.description, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.status == "Pagado") {
                            Text(item.cardName, color = TextMuted, fontSize = 10.sp, maxLines = 1)
                        } else {
                            SegmentedMsiProgress(item.currentPayment, item.totalMonths)
                            val nextDue = item.nextDueEpochDay?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("d MMM", Locale("es", "MX"))) }
                            Text("${item.currentPayment} de ${item.totalMonths}${nextDue?.let { " · Próximo $it" }.orEmpty()}", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (item.status == "Pagado") {
                            Text("Pagado ✓", color = Mint, fontWeight = FontWeight.ExtraBold)
                        } else {
                            Text(item.pendingBalanceCents.money(), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Text("Saldo restante", color = TextMuted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedMsiProgress(currentPayment: Int, totalMonths: Int) {
    val segments = totalMonths.coerceIn(1, 24)
    val completed = currentPayment.coerceIn(0, segments)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(segments) { index ->
            Box(
                Modifier.weight(1f).height(5.dp).clip(CircleShape)
                    .background(if (index < completed) Mint else Color(0xFF515867))
            )
        }
    }
}

@Composable
private fun InstallmentDetailScreen(
    item: InstallmentPlanEntity,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onPay: () -> Unit
) {
    BackHandler(onBack = onClose)
    val totalMonths = item.totalMonths.coerceIn(1, 60)
    val completedPayments = item.currentPayment.coerceIn(0, totalMonths)
    val remainingPayments = (totalMonths - completedPayments).coerceAtLeast(0)
    val paidCents = (item.originalAmountCents - item.pendingBalanceCents).coerceAtLeast(0)
    val dates = remember(item) { item.paymentDates() }
    val nextDueDate = if (remainingPayments > 0) dates.getOrNull(completedPayments) else null
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es", "MX")) }

    Scaffold(containerColor = Color.Black) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 34.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onClose,
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = Color(0xFF1B1C20),
                        contentColor = TextPrimary,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF36383F))
                    ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, "Cerrar", modifier = Modifier.size(28.dp)) } }
                    Surface(
                        onClick = onEdit,
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = Mint,
                        contentColor = DeepNavy
                    ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Edit, "Editar MSI", modifier = Modifier.size(25.dp)) } }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (item.status == "Pagado") "Compra liquidada" else "Pago restante ${item.pendingBalanceCents.money()}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Text("${paidCents.money()} pagados", color = TextMuted, fontSize = 17.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1B1F), contentColor = TextPrimary),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(Color(item.cardName.hashCode()).copy(alpha = .30f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(item.description.take(1).uppercase(), color = Mint, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(item.description, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(item.cardName, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                        Text("−${item.originalAmountCents.money()}", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1B1F), contentColor = TextPrimary),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
                        Text(
                            if (remainingPayments == 1) "1 pago restante" else "$remainingPayments pagos restantes",
                            color = TextPrimary,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            nextDueDate?.let { "Próximo pago el ${it.format(dateFormatter)}" } ?: "Todos los pagos están cubiertos",
                            color = TextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 3.dp, bottom = 16.dp)
                        )
                        repeat(totalMonths) { index ->
                            val paymentNumber = index + 1
                            val completed = paymentNumber <= completedPayments
                            val current = !completed && paymentNumber == completedPayments + 1
                            val dueDate = dates[index]
                            val amount = if (paymentNumber == totalMonths) {
                                (item.originalAmountCents - item.monthlyPaymentCents * (totalMonths - 1)).coerceAtLeast(0)
                                    .takeIf { it > 0 } ?: item.monthlyPaymentCents
                            } else item.monthlyPaymentCents
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape)
                                        .background(if (completed) Color(0xFF05C966) else Color(0xFF303137))
                                        .then(if (current) Modifier.border(2.dp, Color(0xFF2F6BFF), CircleShape) else Modifier),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (completed) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                    else Text(paymentNumber.toString(), color = if (current) TextPrimary else TextMuted, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    dueDate.format(dateFormatter),
                                    modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                                    fontSize = 16.sp,
                                    color = if (completed || current) TextPrimary else TextMuted
                                )
                                Text(amount.money(), fontSize = 16.sp, color = if (completed || current) TextPrimary else TextMuted)
                            }
                        }
                    }
                }
            }
            if (item.status != "Pagado" && item.pendingBalanceCents > 0) {
                item {
                    Button(
                        onClick = onPay,
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DeepNavy)
                    ) { Text("Registrar abono o liquidar", fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}

@Composable
private fun AccountsScreen(state: FinanceUiState, onEdit: (EditorTarget) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
        item { ScreenHeader("Cuentas y tarjetas", "Liquidez y crédito en un solo lugar") }
        item { SectionTitle("Dinero disponible", actionLabel = "Agregar", onAction = { onEdit(EditorTarget.Account()) }) }
        items(state.accounts, key = { "account-${it.name}" }) { item ->
            Box(Modifier.clickable { onEdit(EditorTarget.Account(item)) }) { AccountRow(item) }
        }
        item { SectionTitle("Tarjetas de crédito", actionLabel = "Agregar", onAction = { onEdit(EditorTarget.Card()) }) }
        items(state.cards, key = { "card-${it.name}" }) { item ->
            Box(Modifier.clickable { onEdit(EditorTarget.Card(item)) }) { CardOverview(item) }
        }
    }
}

@Composable
private fun AccountRow(item: AccountEntity) {
    SimpleInfoRow(accountIcon(item.iconKey), item.name, item.type, item.balanceCents.money(), accountColor(item.colorHex))
}

@Composable
private fun CardOverview(item: CardEntity) {
    val usage = if (item.creditLimitCents == 0L) 0f else (item.balanceCents.toFloat() / item.creditLimitCents).coerceIn(0f, 1f)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color(item.colorHex).copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                    Icon(accountIcon(item.iconKey), null, tint = Color(item.colorHex))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(item.name, fontWeight = FontWeight.ExtraBold)
                    Text(item.lastFour?.let { "•••• $it" } ?: item.institution, color = TextMuted, fontSize = 11.sp)
                }
                Text(item.status, color = if (item.paymentConfirmed) Mint else WarmAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().padding(top = 17.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(if (item.balanceCents < 0) "Saldo neto a favor" else "Crédito utilizado", color = TextMuted, fontSize = 10.sp)
                    Text(kotlin.math.abs(item.balanceCents).money(), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Disponible", color = TextMuted, fontSize = 10.sp)
                    Text(item.availableCents.money(), color = Mint, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                }
            }
            if (item.statementPaymentCents > 0) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Saldo a pagar", color = TextMuted, fontSize = 10.sp)
                    Text(item.statementPaymentCents.money(), color = WarmAmber, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            if (item.unbilledCents > 0) {
                Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Saldo actual / posterior al corte", color = TextMuted, fontSize = 10.sp)
                    Text(item.unbilledCents.money(), color = SoftBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            if (item.creditBalanceCents > 0) {
                Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Saldo a favor", color = TextMuted, fontSize = 10.sp)
                    Text(item.creditBalanceCents.money(), color = Mint, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            if (item.msiBalanceCents > 0) {
                Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Saldo pendiente a meses", color = TextMuted, fontSize = 10.sp)
                    Text(item.msiBalanceCents.money(), color = Color(item.colorHex), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            LinearProgressIndicator(
                progress = { usage },
                modifier = Modifier.fillMaxWidth().padding(top = 13.dp).height(7.dp).clip(CircleShape),
                color = if (usage > .75f) WarmAmber else Color(item.colorHex),
                trackColor = NavySurfaceHigh
            )
        }
    }
}

@Composable
private fun MoreScreen(
    state: FinanceUiState,
    viewModel: FinanceViewModel,
    onEdit: (EditorTarget) -> Unit,
    onOpenDebtors: () -> Unit,
    onOpenReconciliation: () -> Unit,
    onOpenReconciliationHistory: () -> Unit,
    onOpenInstallmentReview: () -> Unit,
    onOpenIgnoredNotifications: () -> Unit
) {
    val context = LocalContext.current
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("No se pudo leer el archivo") }
            .onSuccess { json ->
                viewModel.restoreBackup(json) { error ->
                    Toast.makeText(context, error ?: "Respaldo restaurado", Toast.LENGTH_LONG).show()
                }
            }
            .onFailure { Toast.makeText(context, it.message ?: "No se pudo leer el respaldo", Toast.LENGTH_LONG).show() }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
        item { ScreenHeader("Más", "Automatización y módulos avanzados") }
        item { SectionTitle("Por confirmar", "Movimientos encontrados en notificaciones") }
        if (state.pendingDetections.isEmpty()) {
            item { EmptyState("Todo está revisado", "Las próximas compras detectadas aparecerán aquí.") }
        } else {
            items(state.pendingDetections, key = { "detection-${it.id}" }) { item ->
                DetectionCard(item, { onEdit(EditorTarget.Detection(item)) }, { viewModel.dismissDetection(item) })
            }
        }
        item { SectionTitle("Control y diagnóstico", "Corrige saldos y revisa alertas no detectadas") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DiagnosticShortcut(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = "Conciliar saldos",
                    subtitle = "Ajustar con el banco",
                    value = "${state.accounts.size + state.cards.size}",
                    onClick = onOpenReconciliation,
                    modifier = Modifier.weight(1f)
                )
                DiagnosticShortcut(
                    icon = Icons.Default.NotificationsActive,
                    title = "Alertas ignoradas",
                    subtitle = "Ver por qué",
                    value = state.ignoredNotifications.size.toString(),
                    onClick = onOpenIgnoredNotifications,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            TextButton(onClick = onOpenReconciliationHistory, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Icon(Icons.Default.History, null, tint = Mint)
                Text("Historial de ajustes", color = Mint, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
        }
        item {
            TextButton(onClick = onOpenInstallmentReview, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("Revisar saldos y planes MSI", color = Mint, fontWeight = FontWeight.Bold)
            }
        }
        item { SectionTitle("Deudores", "Dinero que otras personas te deben", "Abrir", onOpenDebtors) }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable(onClick = onOpenDebtors),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15333D), contentColor = TextPrimary),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Mint.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ArrowDownward, null, tint = Mint)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("${state.debtors.size} personas", color = TextPrimary, fontWeight = FontWeight.ExtraBold)
                        Text("Solo se abonará cuando confirmes el pago", color = TextMuted, fontSize = 11.sp)
                    }
                    Text(state.pendingReceivablesCents.money(), color = Mint, fontWeight = FontWeight.Black)
                }
            }
        }
        item { SectionTitle("Categorías y subcategorías", "Personaliza cómo se organiza tu dinero", "Agregar", onAction = { onEdit(EditorTarget.Category()) }) }
        items(state.categories, key = { "category-${it.name}" }) { category ->
            Box(Modifier.clickable { onEdit(EditorTarget.Category(category)) }) {
                SimpleInfoRow(Icons.Default.Label, category.name, category.parentName?.let { "Subcategoría de $it" } ?: "Categoría principal", "", Color(category.colorHex))
            }
        }
        item { SectionTitle("Reglas automáticas", "Aprende comercios y corrige notificaciones", "Agregar", onAction = { onEdit(EditorTarget.MerchantRule()) }) }
        items(state.merchantRules, key = { "rule-${it.id}" }) { rule ->
            Box(Modifier.clickable { onEdit(EditorTarget.MerchantRule(rule)) }) {
                SimpleInfoRow(Icons.Default.AutoAwesome, rule.keyword, rule.accountName ?: "Cualquier cuenta", rule.category, Mint)
            }
        }
        item { SectionTitle("Respaldo y exportación", "Incluye saldos, movimientos, pendientes y alertas ignoradas. Restaurar reemplaza los datos actuales.") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = { shareTransactionsCsv(context, state) }, modifier = Modifier.weight(1f)) {
                    Text("Exportar CSV")
                }
                OutlinedButton(onClick = { shareFinanceBackup(context, state) }, modifier = Modifier.weight(1f)) {
                    Text("Crear respaldo")
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain")) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text("Restaurar respaldo")
            }
        }
        item { SectionTitle("Ingresos recurrentes", "Se registran únicamente cuando llega la fecha", "Agregar", onAction = { onEdit(EditorTarget.RecurringIncome()) }) }
        items(state.recurringIncome, key = { "income-${it.id}" }) { income ->
            Box(Modifier.clickable { onEdit(EditorTarget.RecurringIncome(income)) }) {
                SimpleInfoRow(Icons.Default.ArrowDownward, income.description, "${income.cadence} · ${income.destinationAccount}", income.amountCents.money(), Mint)
            }
        }
        item { SectionTitle("Otras deudas", actionLabel = "Agregar", onAction = { onEdit(EditorTarget.Debt()) }) }
        items(state.debts, key = { "debt-${it.id}" }) { debt ->
            Box(Modifier.clickable { onEdit(EditorTarget.Debt(debt)) }) {
                SimpleInfoRow(Icons.Default.WarningAmber, debt.name, debt.creditor, debt.balanceCents.money(), WarmAmber)
            }
        }
        if (state.debts.isEmpty()) item { EmptyState("Sin otras deudas", "Tus tarjetas ya se controlan por separado.") }
        item { SectionTitle("Inversiones", actionLabel = "Agregar", onAction = { onEdit(EditorTarget.Investment()) }) }
        items(state.investments, key = { "investment-${it.id}" }) { investment ->
            Box(Modifier.clickable { onEdit(EditorTarget.Investment(investment)) }) {
                SimpleInfoRow(Icons.Default.TrendingUp, investment.name, investment.institution, investment.currentValueCents.money(), Mint)
            }
        }
        if (state.investments.isEmpty()) item { EmptyState("Sin inversiones registradas", "Aquí verás aportado, valor actual y rendimiento.") }
        item { SectionTitle("Detección automática", "Conecta las notificaciones de tus bancos") }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15333D), contentColor = TextPrimary),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null, tint = Mint, modifier = Modifier.size(30.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                            Text("Detección automática", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text("Lee compras notificadas por tus bancos", color = TextMuted, fontSize = 11.sp)
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(if (notificationsGranted) "Alertas activas" else "Falta permiso") },
                            colors = AssistChipDefaults.assistChipColors(labelColor = if (notificationsGranted) Mint else WarmAmber)
                        )
                    }
                    Button(
                        onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DeepNavy)
                    ) {
                        Icon(Icons.Default.Settings, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Configurar acceso", fontWeight = FontWeight.Bold)
                    }
                    if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        OutlinedButton(
                            onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text("Permitir recordatorios", color = TextPrimary)
                        }
                    }
                }
            }
        }
        item { SectionTitle("Acceso rápido", "Abre el registro desde el panel de Samsung") }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                colors = CardDefaults.cardColors(containerColor = NavySurface, contentColor = TextPrimary),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(17.dp)) {
                    Text("Registrar movimiento", color = TextPrimary, fontWeight = FontWeight.ExtraBold)
                    Text("Añade el botón junto a Wi‑Fi, Bluetooth y los demás controles rápidos.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    Button(
                        onClick = { requestQuickTile(context) },
                        modifier = Modifier.fillMaxWidth().padding(top = 13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DeepNavy)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Agregar al panel rápido", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Text("Versión 0.12.2-public · Edición pública", color = TextMuted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(24.dp))
        }
    }
}

@Composable
private fun DiagnosticShortcut(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = NavySurface, contentColor = TextPrimary),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Mint.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Mint, modifier = Modifier.size(20.dp))
                }
                Text(value, color = Mint, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
            Text(title, color = TextPrimary, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetectionCard(item: DetectedMovementEntity, onReview: () -> Unit, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = NavySurface, contentColor = TextPrimary), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Mint.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Mint)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(item.merchant, color = TextPrimary, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text(
                        buildString {
                            append(item.movementType).append(" · ").append(item.accountName)
                            item.relatedAccountName?.let { append(" → ").append(it) }
                        },
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                Text(item.amountCents.money(), color = WarmAmber, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Descartar")
                }
                Button(onClick = onReview, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DeepNavy)) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Revisar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SimpleInfoRow(icon: ImageVector, title: String, subtitle: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        Text(value, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.WarningAmber, null, tint = TextMuted.copy(alpha = .6f), modifier = Modifier.size(34.dp))
        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(message, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMovementSheet(
    state: FinanceUiState,
    onDismiss: () -> Unit,
    onSave: (MovementRequest, (String?) -> Unit) -> Unit
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("Gasto") }
    var source by rememberSaveable { mutableStateOf(state.accounts.firstOrNull()?.name ?: state.cards.firstOrNull()?.name.orEmpty()) }
    var destination by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Comida") }
    var months by rememberSaveable { mutableIntStateOf(3) }
    var notes by rememberSaveable { mutableStateOf("") }
    var benefitTarget by rememberSaveable { mutableStateOf("Saldo actual") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val accountNames = remember(state.accounts) { state.accounts.map { it.name } }
    val cardNames = remember(state.cards) { state.cards.map { it.name } }
    val allSources = remember(accountNames, cardNames) { accountNames + cardNames }
    val types = listOf("Gasto", "Ingreso", "Transferencia", "Pago tarjeta", "Devolución", "Compra a MSI", "Pago con puntos", "Bonificación")
    val categoryOptions = state.categories.map { it.name }.ifEmpty { SeedData.categories }

    fun optionsFor(selectedType: String): List<String> = when (selectedType) {
        "Ingreso", "Transferencia", "Pago tarjeta" -> accountNames
        "Compra a MSI", "Pago con puntos", "Bonificación" -> cardNames
        else -> allSources
    }

    fun selectType(newType: String) {
        type = newType
        source = optionsFor(newType).firstOrNull().orEmpty()
        destination = when (newType) {
            "Transferencia" -> accountNames.firstOrNull { it != source }.orEmpty()
            "Pago tarjeta" -> cardNames.firstOrNull().orEmpty()
            else -> ""
        }
        category = when (newType) {
            "Ingreso" -> "Ingresos"
            "Transferencia" -> "Transferencias"
            "Pago tarjeta" -> "Pago de tarjeta"
            "Pago con puntos", "Bonificación" -> "Bonificaciones"
            else -> "Comida"
        }
        error = null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NavySurface, dragHandle = null) {
        LazyColumn(
            Modifier.fillMaxWidth().imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 36.dp)
        ) {
            item {
                Text("Registrar movimiento", fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("Saldos, presupuesto y plan se actualizarán al guardar", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp, bottom = 16.dp))
            }
            item {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { value ->
                        amount = value.filter { char -> char.isDigit() || char == '.' || char == ',' }
                        error = null
                    },
                    label = { Text("Importe") },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                )
            }
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción o comercio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 11.dp),
                    shape = RoundedCornerShape(18.dp)
                )
            }
            item {
                PickerLabel("¿Qué quieres registrar?")
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    types.chunked(2).forEach { rowTypes ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowTypes.forEach { option ->
                                FilterChip(
                                    selected = type == option,
                                    onClick = { selectType(option) },
                                    label = { Text(option, maxLines = 1, fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowTypes.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                PickerLabel(
                    when (type) {
                        "Pago tarjeta" -> "Pagar desde"
                        "Transferencia" -> "Cuenta de origen"
                        "Compra a MSI" -> "Tarjeta de crédito"
                        else -> "Cuenta o tarjeta"
                    }
                )
                DropdownSelector(
                    value = source,
                    options = optionsFor(type),
                    placeholder = "Selecciona una opción",
                    onSelected = { source = it; error = null }
                )
            }
            if (type == "Transferencia" || type == "Pago tarjeta") {
                item {
                    PickerLabel(if (type == "Transferencia") "Cuenta de destino" else "Tarjeta que pagarás")
                    DropdownSelector(
                        value = destination,
                        options = if (type == "Transferencia") accountNames.filter { it != source } else cardNames,
                        placeholder = "Selecciona el destino",
                        onSelected = { destination = it; error = null }
                    )
                }
            }
            if (type !in listOf("Transferencia", "Pago tarjeta", "Ingreso")) {
                item {
                    PickerLabel("Categoría")
                    DropdownSelector(
                        value = category,
                        options = categoryOptions,
                        placeholder = "Selecciona una categoría",
                        onSelected = { category = it; error = null }
                    )
                }
            }
            if (type == "Compra a MSI") {
                item {
                    PickerLabel("Plazo sin intereses")
                    DropdownSelector(
                        value = "$months meses",
                        options = listOf(3, 6, 9, 12, 18, 24).map { "$it meses" },
                        placeholder = "Selecciona el plazo",
                        onSelected = { months = it.substringBefore(' ').toInt() }
                    )
                    val cents = amount.toMoneyCentsOrNull() ?: 0
                    if (cents > 0) {
                        Surface(
                            color = Mint.copy(alpha = .10f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 9.dp)
                        ) {
                            Text(
                                "Mensualidad estimada: ${((cents + months - 1) / months).money()}",
                                color = Mint,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(13.dp)
                            )
                        }
                    }
                }
            }
            if (type in listOf("Pago con puntos", "Bonificación")) {
                item {
                    PickerLabel("Aplicar beneficio a")
                    DropdownSelector(benefitTarget, listOf("Saldo actual", "Saldo a pagar"), "Selecciona el componente", onSelected = { benefitTarget = it })
                    Text("Registra el importe en pesos aplicado por el banco, no la cantidad de puntos. Reduce el componente seleccionado y el excedente queda a favor; no modifica tus planes MSI ni aumenta tu dinero disponible. Si ya conciliaste este descuento, no lo registres otra vez.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                }
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Nota (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    shape = RoundedCornerShape(18.dp)
                )
            }
            item {
                error?.let {
                    Text(it, color = Danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                }
                Button(
                    onClick = {
                        val cents = amount.toMoneyCentsOrNull()
                        val validation = when {
                            cents == null || cents <= 0 -> "Escribe un importe válido"
                            source.isBlank() -> "Selecciona una cuenta o tarjeta"
                            type in listOf("Transferencia", "Pago tarjeta") && destination.isBlank() -> "Selecciona el destino"
                            else -> null
                        }
                        if (validation != null) {
                            error = validation
                        } else {
                            saving = true
                            onSave(
                                MovementRequest(
                                    type = type,
                                    sourceName = source,
                                    destinationName = destination.ifBlank { null },
                                    description = description.ifBlank { type },
                                    category = category,
                                    amountCents = cents!!,
                                    notes = notes.ifBlank { null },
                                    installmentMonths = months.takeIf { type == "Compra a MSI" },
                                    benefitTarget = benefitTarget
                                )
                            ) { result ->
                                saving = false
                                error = result
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DeepNavy),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(if (saving) "Guardando…" else "Guardar movimiento", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun DropdownSelector(
    value: String,
    options: List<String>,
    placeholder: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text(value.ifBlank { placeholder }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(.88f).background(NavySurface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                    trailingIcon = {
                        if (option == value) Icon(Icons.Default.Check, null, tint = Mint)
                    }
                )
            }
        }
    }
}

@Composable
private fun PickerLabel(text: String) {
    Text(text, fontWeight = FontWeight.Bold, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 17.dp, bottom = 5.dp))
}

@Composable
private fun ChipPicker(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = selected == option, onClick = { onSelected(option) }, label = { Text(option) })
        }
    }
}

private fun Long.money(): String = NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(this / 100.0)

private fun String.toMoneyCentsOrNull(): Long? = replace(',', '.')
    .toBigDecimalOrNull()
    ?.movePointRight(2)
    ?.toLong()

private fun requestQuickTile(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Abre el lápiz del panel rápido y arrastra Registrar movimiento", Toast.LENGTH_LONG).show()
        return
    }
    val manager = context.getSystemService(StatusBarManager::class.java)
    manager.requestAddTileService(
        ComponentName(context, AddMovementTileService::class.java),
        context.getString(R.string.quick_tile_label),
        Icon.createWithResource(context, R.drawable.ic_notification),
        ContextCompat.getMainExecutor(context)
    ) { result ->
        val message = when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Acceso rápido agregado"
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "El acceso rápido ya estaba agregado"
            else -> "Puedes agregarlo manualmente desde el lápiz del panel rápido"
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}


