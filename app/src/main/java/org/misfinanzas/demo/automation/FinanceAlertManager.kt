package org.misfinanzas.demo.automation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.misfinanzas.demo.MainActivity
import org.misfinanzas.demo.R
import org.misfinanzas.demo.data.DetectedMovementEntity
import org.misfinanzas.demo.data.FinanceDao
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.absoluteValue

object FinanceAlertManager {
    private const val channelId = "finance_alerts"

    suspend fun runChecks(context: Context, dao: FinanceDao) {
        if (!canNotify(context)) return
        createChannel(context)
        val today = LocalDate.now()

        dao.getCards().filter { !it.paymentConfirmed && it.statementPaymentCents > 0 }.forEach { card ->
            val due = card.dueEpochDay?.let(LocalDate::ofEpochDay) ?: return@forEach
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
            if (days in 0..3) {
                val label = if (days == 0L) "vence hoy" else "vence en $days ${if (days == 1L) "día" else "días"}"
                notifyOnce(context, "card-${card.name}", card.name, "${card.statementPaymentCents.money()} $label", card.name.hashCode())
            }
        }

        val month = YearMonth.from(today)
        val spent = dao.getConfirmedTransactionsBetween(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())
            .groupBy { it.category }
            .mapValues { it.value.sumOf { movement -> movement.budgetImpactCents }.coerceAtLeast(0) }
        dao.getBudgets().forEach { budget ->
            val limit = budget.limitCents ?: return@forEach
            if (limit <= 0) return@forEach
            val amount = spent[budget.category] ?: 0
            if (amount >= limit) {
                notifyOnce(context, "budget-over-${budget.category}", "Presupuesto excedido", "${budget.category}: ${amount.money()} de ${limit.money()}", 20_000 + budget.category.hashCode())
            } else if (amount * 100 >= limit * 80) {
                notifyOnce(context, "budget-80-${budget.category}", "Presupuesto al 80%", "En ${budget.category} llevas ${amount.money()} de ${limit.money()}", 30_000 + budget.category.hashCode())
            }
        }

        dao.getActiveSubscriptions().forEach { subscription ->
            val due = subscription.nextChargeEpochDay?.let(LocalDate::ofEpochDay) ?: return@forEach
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
            if (days in 0..2) {
                val label = if (days == 0L) "se cobra hoy" else "se cobra en $days ${if (days == 1L) "día" else "días"}"
                notifyOnce(context, "subscription-${subscription.name}", subscription.name, "${subscription.amountCents.money()} $label en ${subscription.accountName}", 40_000 + subscription.name.hashCode())
            }
        }

        val liquid = dao.getAccounts().filter { it.includedInAvailable }.sumOf { it.balanceCents }
        val cards = dao.getCards().filterNot { it.paymentConfirmed }.sumOf { it.statementPaymentCents }
        val horizon = today.plusDays(30).toEpochDay()
        val subscriptions = dao.getActiveSubscriptions().filter { it.nextChargeEpochDay != null && it.nextChargeEpochDay <= horizon }.sumOf { it.amountCents }
        val debts = dao.getActiveDebts().filter { it.nextPaymentEpochDay == null || it.nextPaymentEpochDay <= horizon }.sumOf { it.monthlyPaymentCents }
        val goals = dao.getActiveGoals().sumOf { it.monthlyContributionCents }
        val remaining = liquid - cards - subscriptions - debts - goals
        if (remaining < 0) {
            notifyOnce(context, "insufficient-funds", "Tus compromisos superan tu saldo", "Te faltan ${remaining.absoluteValue.money()} para cubrir los próximos pagos", 50_001)
        }
    }

    fun notifyDetectedMovement(context: Context, item: DetectedMovementEntity) {
        if (!canNotify(context)) return
        createChannel(context)
        notify(context, "Movimiento por confirmar", "${item.merchant} · ${item.amountCents.money()} en ${item.accountName}", 60_000 + item.id.toInt())
    }

    private fun notifyOnce(context: Context, key: String, title: String, body: String, id: Int) {
        val preferences = context.getSharedPreferences("finance_alerts", Context.MODE_PRIVATE)
        val today = LocalDate.now().toEpochDay()
        if (preferences.getLong(key, Long.MIN_VALUE) == today) return
        notify(context, title, body, id)
        preferences.edit().putLong(key, today).apply()
    }

    private fun notify(context: Context, title: String, body: String, id: Int) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channelId, "Alertas financieras", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Pagos, presupuestos, cargos recurrentes y movimientos detectados"
        })
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun Long.money(): String = NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(this / 100.0)
}

