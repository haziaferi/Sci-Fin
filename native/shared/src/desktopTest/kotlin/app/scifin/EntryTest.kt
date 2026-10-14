package app.scifin

import app.scifin.engine.Entry
import app.scifin.engine.Ledger
import app.scifin.engine.savingsAmount
import app.scifin.model.Account
import app.scifin.model.AccountType
import app.scifin.model.Cadence
import app.scifin.model.DateMode
import app.scifin.model.Goal
import app.scifin.model.RecurringExpense
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §3.6's entry (Decided 2026-10-09): one sentence per area, the readout, the captions and the writes. */
class EntryTest {
    private val clock = FakeClock(LocalDate(2026, 10, 9))
    private val ids = Ids()
    private val today = clock.today()
    private fun world(incomeOn: Boolean = true) = profileWith(savings = 1000.0, categories = listOf(category("c", "Entertainment"))).let { w ->
        w.copy(
            goals = listOf(Goal("g", w.profileId, "Holidays", saved = moneyOf(120.0))),
            accounts = listOf(
                Account("cc", w.profileId, "Credit Card", null, AccountType.CREDIT, balance = moneyOf(640.0)),
                Account("loan", w.profileId, "Car Loan", null, AccountType.LOAN, balance = moneyOf(5000.0), linkedRecurringExpenseId = "pay"),
            ),
            settings = w.settings.copy(incomeEnabled = incomeOn),
        )
    }

    @Test fun `each area has its own words, and Savings has no Money out`() {
        val d = world()
        assertEquals(listOf("Add", "Income"), Entry.directions(d, "savings")!!.map { it.second })
        assertNull(Entry.directions(world(incomeOn = false), "savings"))                      // one direction: no control, "Add"
        assertEquals(listOf("Add", "Spent"), Entry.directions(d, "goal:g")!!.map { it.second })
        assertNull(Entry.directions(d, "cat:c")); assertEquals("Spent", Entry.fixedVerb("cat:c"))
        assertEquals(listOf("Charged", "Paid off"), Entry.directions(d, "acct:cc")!!.map { it.second })
        assertEquals(listOf("Paid off", "Increased"), Entry.directions(d, "acct:loan")!!.map { it.second })
        assertNull(Entry.preposition("savings", "in"))
        assertEquals("to", Entry.preposition("goal:g", "in")); assertEquals("from", Entry.preposition("goal:g", "spend"))
        assertEquals("on", Entry.preposition("cat:c", "spend")); assertEquals("on", Entry.preposition("acct:cc", "charge"))
        assertEquals("from", Entry.noteWord("savings", "income")); assertEquals("for", Entry.noteWord("goal:g", "in"))
        assertEquals("at", Entry.noteWord("goal:g", "spend")); assertEquals("at", Entry.noteWord("cat:c", "spend"))
        assertNull(Entry.noteWord("acct:cc", "payment"))
    }

    @Test fun `the readout says what moves, and a Goal spends at most what it holds`() {
        val d = world()
        assertEquals(listOf("Holidays" to moneyOf(50.0), "Savings" to moneyOf(-50.0)), Entry.moves(d, "goal:g", "in", moneyOf(50.0), false))
        assertEquals(listOf("Holidays" to moneyOf(-80.0)), Entry.moves(d, "goal:g", "spend", moneyOf(80.0), false))
        assertNull(Entry.moves(d, "goal:g", "spend", moneyOf(200.0), false))
        assertTrue(Entry.captions(d, "goal:g", "spend", moneyOf(200.0), "", false) { "€" + it / 100 }.single().startsWith("Holidays holds €120"))
        assertEquals(listOf("Entertainment spent" to moneyOf(30.0), "Savings" to moneyOf(-30.0)), Entry.moves(d, "cat:c", "spend", moneyOf(30.0), false))
        assertEquals(listOf("Credit Card owed" to moneyOf(-40.0), "Savings" to moneyOf(-40.0)), Entry.moves(d, "acct:cc", "payment", moneyOf(40.0), true))
        assertNull(Entry.moves(d, "cat:c", "spend", null, false)); assertNull(Entry.moves(d, "cat:gone", "spend", moneyOf(1.0), false))
    }

    @Test fun `the Savings switch on a payment follows the account's linkage`() {
        val d = world().let { it.copy(recurringExpenses = listOf(RecurringExpense("pay", it.profileId, "Car loan payment", null, moneyOf(260.0), Cadence.MONTHLY,
            firstDueDate = LocalDate(2026, 1, 5), dueDate = LocalDate(2026, 11, 5), dueDateAnchorDay = 5, dateMode = DateMode.FIXED_DATE, lastSweptMonth = "2026-10"))) }
        assertTrue(Entry.pairDefault(d, "acct:cc")); assertTrue(!Entry.pairDefault(d, "acct:loan"))
        assertEquals("Savings stays: Car loan payment already takes this from Savings on its date.", Entry.captions(d, "acct:loan", "payment", moneyOf(260.0), "", false) { "" }.single())
        assertTrue(Entry.captions(d, "acct:loan", "payment", moneyOf(260.0), "", true) { "" }.single().endsWith("so this would count it twice."))
    }

    @Test fun `spending from a Goal lowers it, leaves Savings alone, and is logged`() {
        val after = Entry.write(world(), "goal:g", "spend", moneyOf(80.0), "Lisbon hotel", today, false, clock, ids::next)
        assertEquals(moneyOf(40.0), after.goals.single().saved); assertEquals(moneyOf(1000.0), after.savingsAmount)
        val tx = after.transactions.single()
        assertEquals("spend:g", tx.key); assertEquals(moneyOf(-80.0), tx.amount); assertEquals(0L, Ledger.savingsEffect(tx)); assertEquals("Lisbon hotel", tx.payee)
        assertFails { Entry.write(world(), "goal:g", "spend", moneyOf(500.0), null, today, false, clock, ids::next) }
    }

    @Test fun `Savings only takes money in, and Income tags the row`() {
        val add = Entry.write(world(), "savings", "in", moneyOf(40.0), "Anna", today, false, clock, ids::next)
        assertEquals(moneyOf(1040.0), add.savingsAmount); assertEquals(false, add.transactions.single().isIncome)
        val income = Entry.write(world(), "savings", "income", moneyOf(1450.0), null, today, false, clock, ids::next)
        assertEquals(moneyOf(2450.0), income.savingsAmount); assertTrue(income.transactions.single().isIncome)
    }

    @Test fun `a backdated entry keeps its date, and an account payment pairs its Savings row`() {
        val spent = Entry.write(world(), "cat:c", "spend", moneyOf(60.0), null, LocalDate(2026, 9, 28), false, clock, ids::next)
        assertEquals(moneyOf(60.0), spent.categories.single().spent); assertEquals(LocalDate(2026, 9, 28), spent.transactions.single().date)
        val paid = Entry.write(world(), "acct:cc", "payment", moneyOf(100.0), null, LocalDate(2026, 10, 2), true, clock, ids::next)
        assertEquals(moneyOf(540.0), paid.accounts.first().balance); assertEquals(moneyOf(900.0), paid.savingsAmount)
        assertEquals(listOf(LocalDate(2026, 10, 2), LocalDate(2026, 10, 2)), paid.transactions.map { it.date })
        // with no note, the account row says what happened rather than "Correction"; the paired Savings row is unchanged
        assertEquals("Paid off", paid.transactions.first { it.key == "acct:cc" }.payee)
        assertEquals("Payment to Credit Card", paid.transactions.first { it.key == "savings" }.payee)
        assertEquals("Charged", Entry.write(world(), "acct:cc", "charge", moneyOf(9.0), null, today, false, clock, ids::next).transactions.single().payee)
        assertEquals("Fuel", Entry.write(world(), "acct:cc", "charge", moneyOf(9.0), "Fuel", today, false, clock, ids::next).transactions.single().payee)
    }

    @Test fun `before anything is logged the floating + opens the first Category`() {
        assertEquals("cat:c", Entry.defaultTarget(world()))
        assertEquals("savings", Entry.defaultTarget(world().copy(categories = emptyList())))
    }
}
