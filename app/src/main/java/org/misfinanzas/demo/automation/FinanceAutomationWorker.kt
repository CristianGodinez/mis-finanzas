package org.misfinanzas.demo.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.misfinanzas.demo.data.FinanceDatabase
import org.misfinanzas.demo.data.FinanceRepository

class FinanceAutomationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val repository = FinanceRepository(applicationContext, FinanceDatabase.getInstance(applicationContext))
        repository.initializeIfNeeded()
        repository.processRecurringIncome()
        repository.processRecurringExpenses()
        repository.processCardCycles()
        FinanceAlertManager.runChecks(applicationContext, FinanceDatabase.getInstance(applicationContext).financeDao())
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}

