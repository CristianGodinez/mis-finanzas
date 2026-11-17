package org.misfinanzas.demo

import android.app.Application
import org.misfinanzas.demo.data.FinanceDatabase
import org.misfinanzas.demo.data.FinanceRepository
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.misfinanzas.demo.automation.FinanceAutomationWorker
import java.util.concurrent.TimeUnit

class FinanzasApplication : Application() {
    val database by lazy { FinanceDatabase.getInstance(this) }
    val repository by lazy { FinanceRepository(this, database) }

    override fun onCreate() {
        super.onCreate()
        val request = PeriodicWorkRequestBuilder<FinanceAutomationWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "finance-automation",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

