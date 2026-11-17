package org.misfinanzas.demo.data

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class DemoInitializationTest {
    private lateinit var database: FinanceDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("public_demo", 0).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).allowMainThreadQueries().build()
        repository = FinanceRepository(context, database)
    }

    @After
    fun teardown() { database.close() }

    @Test
    fun demoCardsAndInstallmentsHaveConsistentBalances() {
        SeedData.cards.forEach { card ->
            assertTrue(card.balanceCheck().isConsistent)
            val plans = SeedData.installmentPlans.filter { it.cardName == card.name }
            assertEquals(card.msiBalanceCents, plans.sumOf { it.pendingBalanceCents })
            assertNull(card.lastFour)
            assertEquals("Banco ficticio", card.institution)
        }
    }

    @Test
    fun initializationDoesNotReapplyDemoAfterTransactionsAreCleared() = runBlocking {
        repository.initializeIfNeeded()
        val dao = database.financeDao()
        val edited = dao.getAccounts().first().copy(balanceCents = 12_345)
        dao.insertAccount(edited)
        dao.clearTransactions()
        repository.initializeIfNeeded()
        assertEquals(edited, dao.getAccount(edited.name))
        assertTrue(dao.observeTransactions().first().isEmpty())
        assertEquals(3, dao.getCards().size)
    }

    @Test
    fun existingDataPreventsDemoInsertion() = runBlocking {
        val dao = database.financeDao()
        val existing = AccountEntity("Cuenta existente", "Débito", 25_000, true, null, 0)
        dao.insertAccount(existing)
        repository.initializeIfNeeded()
        assertEquals(listOf(existing), dao.getAccounts())
        assertTrue(dao.getCards().isEmpty())
        assertTrue(dao.observeInstallmentPlans().first().isEmpty())
    }
}
