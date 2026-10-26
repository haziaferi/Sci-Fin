package app.scifin

import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Files
import app.scifin.app.PickedFile
import app.scifin.app.Route
import app.scifin.app.Sheet
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.AccountEditing
import app.scifin.engine.Accounts
import app.scifin.engine.savingsAmount
import app.scifin.model.Account
import app.scifin.model.AccountType
import app.scifin.model.RateType
import app.scifin.model.Theme
import app.scifin.model.moneyOf
import app.scifin.engine.Entry
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §7.2's editor rules, the Accounts quick-add with §7.2's paired outflow, and §4.3's import through the store. */
class ActivityStoreTest {
    private val clock = FakeClock(LocalDate(2026, 9, 14))
    private val ids = Ids("a")

    @Test fun `a new Account with a balance logs an opening row, an edit logs a correction, auto-update needs a rate`() {
        val d0 = profileWith(savings = 1000.0)
        val card = AccountEditing.fresh("c1", "p-1").copy(name = " Visa ", balance = moneyOf(640.0), apr = 0.219)
        val d1 = AccountEditing.save(d0, card, clock, ids::next)
        assertEquals("Visa", d1.accounts.single().name); assertEquals(moneyOf(640.0), d1.accounts.single().balance)
        assertEquals(Accounts.OPENING_BALANCE, d1.transactions.single().payee); assertEquals(moneyOf(1000.0), d1.savingsAmount)   // §7.1: no Savings effect
        val d2 = AccountEditing.save(d1, d1.accounts.single().copy(balance = moneyOf(600.0)), clock, ids::next)
        assertEquals(2, d2.transactions.size); assertEquals(moneyOf(-40.0), d2.transactions.last().amount)
        val loan = Accounts.retype(AccountEditing.fresh("l1", "p-1").copy(name = "Car"), AccountType.LOAN).copy(autoUpdateFromPayments = true, principal = moneyOf(9800.0), termMonths = 48, rateType = RateType.FIXED)
        val d3 = AccountEditing.save(d2, loan, clock, ids::next)
        assertFalse(d3.accounts.last().autoUpdateFromPayments)                 // no rate: the toggle cannot be on
        assertEquals(LocalDate(2026, 9, 14), d3.accounts.last().originationDate)
        assertNull(Accounts.retype(d3.accounts.last(), AccountType.CREDIT).principal)   // §7.2: retyping clears what belonged to the type left
    }

    @Test fun `an account quick-add pays the balance and, when paired, takes the same from Savings`() {
        val d0 = AccountEditing.save(profileWith(savings = 1000.0), AccountEditing.fresh("c1", "p-1").copy(name = "Visa", balance = moneyOf(640.0)), clock, ids::next)
        val paid = Entry.write(d0, "acct:c1", "payment", moneyOf(100.0), null, clock.today(), true, clock, ids::next)
        assertEquals(moneyOf(540.0), paid.accounts.single().balance); assertEquals(moneyOf(900.0), paid.savingsAmount)
        val charged = Entry.write(d0, "acct:c1", "charge", moneyOf(25.0), "cafe", clock.today(), false, clock, ids::next)
        assertEquals(moneyOf(665.0), charged.accounts.single().balance); assertEquals(moneyOf(1000.0), charged.savingsAmount)
    }

    private class OneFile(val f: PickedFile?) : Files {
        override suspend fun open(extensions: List<String>) = f
        override suspend fun save(suggestedName: String, text: String) = false
    }

    @Test fun `import parses, annotates, lets a row be vetoed and assigned, and writes on confirm`() = runTest {
        val csv = "Date;Description;Amount\n2026-09-10;SUPERMARKET;-42,50\n2026-09-11;SALARY;1.450,00\n2026-09-12;COFFEE;-3,20\n"
        val db = buildInMemorySciFinDatabase()
        val store = AppStore(AppSession(db, RecordingScheduler(), clock, ids::next), db, this, OneFile(PickedFile("sept.csv", csv)))
        store.start(); store.createProfile("Ana", Theme.SWISS_BLUE, false).join(); store.completeOnboarding().join()
        store.dispatch { it.copy(categories = listOf(category("food", "Food").copy(profileId = it.profileId))) }.join()
        store.push(Route.Import)
        store.pickStatement().join()
        val review = store.state.value.importReview
        assertNotNull(review); assertEquals("CSV", review.format); assertEquals(3, review.rows.size); assertNull(store.state.value.importError)
        val coffee = review.rows.indexOfFirst { it.row.description == "COFFEE" }
        val market = review.rows.indexOfFirst { it.row.description == "SUPERMARKET" }
        store.toggleImportRow(coffee); store.setImportTarget(market, "cat:food")
        store.confirmImport().join()
        val d = store.state.value.data!!
        assertNull(store.state.value.importReview)
        assertEquals(2, d.transactions.size)                                      // coffee vetoed
        assertEquals(moneyOf(42.50), d.categories.single().spent)                  // assigned to Food
        assertEquals(moneyOf(1450.0 - 42.50), d.savingsAmount)                     // income in, groceries out
        assertEquals(LocalDate(2026, 9, 14), d.importState.lastImportDate)
        assertEquals("food", d.importState.payeeRules["supermarket"])              // learned for next time
        assertTrue(d.importState.importedKeys.isNotEmpty())

        // the same file again: everything already imported is unticked
        store.pickStatement().join()
        assertTrue(store.state.value.importReview!!.rows.filter { it.row.description != "COFFEE" }.none { it.include })
    }

    @Test fun `a file that is not a statement is refused as one, not as a file that moved`() = runTest {
        val db = buildInMemorySciFinDatabase()
        val store = AppStore(AppSession(db, RecordingScheduler(), clock, ids::next), db, this, OneFile(PickedFile("x.csv", "hello")))
        store.start(); store.createProfile("Ana", Theme.SWISS_BLUE, false).join(); store.completeOnboarding().join()
        store.pickStatement().join()
        assertNull(store.state.value.importReview); assertTrue(store.state.value.importError!!.startsWith("The app doesn’t recognise that file as a bank statement"))
    }
}
