package org.misfinanzas.demo.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.misfinanzas.demo.data.FinanceDatabase
import org.misfinanzas.demo.data.IgnoredNotificationEntity
import org.misfinanzas.demo.automation.FinanceAlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FinanceNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (!NotificationParser.shouldProcessNotification(sbn.packageName, isGroupSummary, sbn.postTime, System.currentTimeMillis())) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = buildString {
            append(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let {
                if (it.isNotBlank() && !contains(it)) append(" ").append(it)
            }
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach {
                val line = it.toString()
                if (line.isNotBlank() && !contains(line)) append(" ").append(line)
            }
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.let {
                if (it.isNotBlank() && !contains(it)) append(" ").append(it)
            }
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.let {
                if (it.isNotBlank() && !contains(it)) append(" ").append(it)
            }
        }.trim()
        if (title.isBlank() && text.isBlank()) return

        val parsed = NotificationParser.parse(sbn.packageName, title, text, sbn.postTime)
        if (parsed == null) {
            val bankName = NotificationParser.supportedBankName(sbn.packageName) ?: return
            scope.launch {
                val dao = FinanceDatabase.getInstance(applicationContext).financeDao()
                dao.insertIgnoredNotification(
                    IgnoredNotificationEntity(
                        fingerprint = NotificationParser.notificationFingerprint(sbn.packageName, title, text, sbn.postTime),
                        packageName = sbn.packageName,
                        bankName = bankName,
                        title = title,
                        text = text,
                        reason = NotificationParser.ignoredReason(sbn.packageName, title, text),
                        postedAtMillis = sbn.postTime
                    )
                )
                dao.pruneIgnoredNotifications(System.currentTimeMillis() - 30L * 24 * 60 * 60_000L)
                dao.trimIgnoredNotifications(100)
            }
            return
        }
        scope.launch {
            val dao = FinanceDatabase.getInstance(applicationContext).financeDao()
            val learned = dao.findLearnedCategory(parsed.merchant)
            val ruleText = "${parsed.merchant} ${parsed.rawTitle} ${parsed.rawText}".lowercase()
            val rule = dao.getEnabledMerchantRules().firstOrNull {
                ruleText.contains(it.keyword.lowercase()) && (it.accountName == null || it.accountName == parsed.accountName)
            }
            val candidate = when {
                rule != null -> parsed.copy(
                    merchant = rule.merchantName ?: parsed.merchant,
                    suggestedCategory = rule.category,
                    movementType = rule.movementType
                )
                !learned.isNullOrBlank() -> parsed.copy(suggestedCategory = learned)
                else -> parsed
            }
            val paired = dao.findRecentDetectionNearAmount(
                candidate.amountCents,
                (candidate.amountCents - NotificationParser.TRANSFER_PAIR_TOLERANCE_CENTS).coerceAtLeast(1L),
                candidate.amountCents + NotificationParser.TRANSFER_PAIR_TOLERANCE_CENTS,
                candidate.postedAtMillis - 10 * 60_000L
            )
            val pairedOutgoingAccount = when {
                paired?.movementType == "Transferencia" -> paired.accountName
                paired?.movementType == "Gasto" -> NotificationParser.liquidDebitNoticeAccount(paired.packageName, paired.rawTitle, paired.rawText)
                else -> null
            }
            val candidateOutgoingAccount = when {
                candidate.movementType == "Transferencia" -> candidate.accountName
                candidate.movementType == "Gasto" -> NotificationParser.liquidDebitNoticeAccount(candidate.packageName, candidate.rawTitle, candidate.rawText)
                else -> null
            }
            val pairedIncomingAccount = when {
                paired?.movementType == "Ingreso" -> paired.accountName
                paired?.movementType == "Gasto" && candidate.movementType == "Transferencia" -> NotificationParser.liquidAccountForPackage(paired.packageName)
                else -> null
            }
            val candidateIncomingAccount = when {
                candidate.movementType == "Ingreso" -> candidate.accountName
                candidate.movementType == "Gasto" && paired?.movementType == "Transferencia" -> NotificationParser.liquidAccountForPackage(candidate.packageName)
                else -> null
            }
            val oppositeSides = paired != null && (
                (pairedOutgoingAccount != null && candidateIncomingAccount != null && pairedOutgoingAccount != candidateIncomingAccount) ||
                    (candidateOutgoingAccount != null && pairedIncomingAccount != null && candidateOutgoingAccount != pairedIncomingAccount)
                )
            if (oppositeSides) {
                val candidateIsOutgoing = candidateOutgoingAccount != null && pairedIncomingAccount != null
                val outgoing = if (candidateIsOutgoing) candidate else paired!!
                val outgoingAccount = if (candidateIsOutgoing) candidateOutgoingAccount!! else pairedOutgoingAccount!!
                val incoming = if (candidateIsOutgoing) paired!! else candidate
                val incomingAccount = if (candidateIsOutgoing) pairedIncomingAccount!! else candidateIncomingAccount!!
                if (NotificationParser.canPairDestination(outgoing.relatedAccountName, incomingAccount, outgoing.rawTitle, outgoing.rawText)) {
                    val updated = paired!!.copy(
                        packageName = outgoing.packageName,
                        accountName = outgoingAccount,
                        merchant = "Transferencia a $incomingAccount",
                        amountCents = incoming.amountCents,
                        suggestedCategory = "Transferencias",
                        postedAtMillis = maxOf(outgoing.postedAtMillis, incoming.postedAtMillis),
                        rawTitle = "${outgoing.rawTitle} · ${incoming.rawTitle}",
                        rawText = "${outgoing.rawText} · ${incoming.rawText}",
                        movementType = "Transferencia",
                        relatedAccountName = incomingAccount
                    )
                    dao.updateDetected(updated)
                    FinanceAlertManager.notifyDetectedMovement(applicationContext, updated)
                    return@launch
                }
            }
            val pairedTypes = setOf("Transferencia", "Pago tarjeta")
            val sameFlow = paired != null && paired.movementType in pairedTypes && candidate.movementType in pairedTypes &&
                paired.accountName == candidate.accountName &&
                (paired.relatedAccountName == null || candidate.relatedAccountName == null || paired.relatedAccountName == candidate.relatedAccountName)
            if (sameFlow) {
                if (candidate.movementType == "Pago tarjeta" && paired!!.movementType != "Pago tarjeta") {
                    val upgraded = paired.copy(
                        accountName = candidate.accountName,
                        merchant = candidate.merchant,
                        suggestedCategory = candidate.suggestedCategory,
                        rawTitle = candidate.rawTitle,
                        rawText = candidate.rawText,
                        movementType = candidate.movementType,
                        relatedAccountName = candidate.relatedAccountName,
                        postedAtMillis = candidate.postedAtMillis
                    )
                    dao.updateDetected(upgraded)
                    FinanceAlertManager.notifyDetectedMovement(applicationContext, upgraded)
                }
                return@launch
            }
            val duplicate = dao.findRecentDetection(
                candidate.accountName,
                candidate.merchant,
                candidate.amountCents,
                candidate.postedAtMillis - 10 * 60_000L
            )
            if (duplicate == null) {
                val id = dao.insertDetected(candidate)
                if (id > 0) FinanceAlertManager.notifyDetectedMovement(applicationContext, candidate.copy(id = id))
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

