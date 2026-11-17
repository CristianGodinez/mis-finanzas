package org.misfinanzas.demo.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class FinanceMigrationTest {
    @Test
    fun versionSevenUpgradePreservesRowsWithoutInventingCredit() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val original = Room.databaseBuilder(context, FinanceDatabase::class.java, name).allowMainThreadQueries().build()
        val dao = original.financeDao()
        val mercado = CardEntity("Tarjeta Demo", "Banco ficticio", null, null, null, 500000, 180000, 180000, 0, 0, 320000, true, "Demostración", 0)
        val other = mercado.copy(name = "Otra tarjeta")
        dao.insertCards(listOf(mercado, other))
        dao.insertAccounts(listOf(AccountEntity("Ahorro", "Débito", 300000, true, null, 0)))
        val transaction = TransactionEntity("keep", 20000, "Ahorro", "Ingreso", "Ingreso", "Ingresos", 500, 0, false, null, "Confirmado", "Registro manual", null)
        dao.insertTransaction(transaction)
        original.close()
        val legacy = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        legacy.execSQL("ALTER TABLE cards DROP COLUMN creditBalanceCents")
        legacy.execSQL("ALTER TABLE transactions DROP COLUMN cardEffect")
        legacy.execSQL("ALTER TABLE transactions DROP COLUMN benefitTarget")
        legacy.version = 7
        legacy.close()
        val upgraded = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            .addMigrations(FinanceDatabase.migration7To8).allowMainThreadQueries().build()
        try {
            val updated = upgraded.financeDao()
            assertEquals(0L, updated.getCard(mercado.name)!!.creditBalanceCents)
            assertEquals(mercado.balanceCents, updated.getCard(mercado.name)!!.balanceCents)
            assertEquals(mercado.availableCents, updated.getCard(mercado.name)!!.availableCents)
            assertTrue(updated.getCard(mercado.name)!!.balanceCheck().isConsistent)
            assertEquals(other, updated.getCard(other.name))
            assertEquals(300000L, updated.getAccount("Ahorro")!!.balanceCents)
            assertEquals(transaction, updated.getTransaction("keep"))
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }
}

