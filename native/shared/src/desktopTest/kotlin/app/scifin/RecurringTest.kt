package app.scifin

import app.scifin.engine.Recurring
import app.scifin.engine.Recurring.apportioned
import app.scifin.model.Account
import app.scifin.model.AccountType
import app.scifin.model.Cadence
import app.scifin.model.DateMode
import app.scifin.model.RecurringExpense
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §3.11, bullet by bullet. Each test names the rule it holds. */
class RecurringTest {

    private fun exp(
        id: String, amount: Double, cadence: Cadence, firstDue: LocalDate, dueDate: LocalDate = firstDue,
        dateMode: DateMode? = if (cadence.apportioned || cadence == Cadence.MONTHLY) DateMode.FIXED_DATE else null,
        dueDate2: LocalDate? = null, endDate: LocalDate? = null, intervalDays: Int? = null, share: Double? = null,
        linkedAccountId: String? = null, lastSweptMonth: String? = null, finishedAt: LocalDate? = null,
    ) = RecurringExpense(
        id, "p-1", id, null, moneyOf(amount), cadence, firstDueDate = firstDue, dueDate = dueDate, dueDate2 = dueDate2,
        dueDateAnchorDay = if (dateMode == DateMode.FIXED_DATE) firstDue.day else null, dateMode = dateMode,
        intervalDays = intervalDays ?: if (dateMode == DateMode.INTERVAL) Recurring.defaultIntervalDays(cadence) else null,
        apportionedShare = share?.let { moneyOf(it) }, endDate = endDate, linkedAccountId = linkedAccountId,
        lastSweptMonth = lastSweptMonth, finishedAt = finishedAt,
    )

    // ---- the occurrence walk ----------------------------------------------------------------

    @Test fun `weekly and biweekly genuinely land 2 or 3 times some months`() {
        val w = exp("w", 10.0, Cadence.WEEKLY, LocalDate(2026, 1, 2))
        assertEquals(5, Recurring.occurrenceDatesInMonth(w, 2026, 1)!!.size)       // 2, 9, 16, 23, 30 Jan
        assertEquals(4, Recurring.occurrenceDatesInMonth(w, 2026, 2)!!.size)
        val b = exp("b", 10.0, Cadence.BIWEEKLY, LocalDate(2026, 1, 2))
        assertEquals(3, Recurring.occurrenceDatesInMonth(b, 2026, 1)!!.size)       // 2, 16, 30 Jan
        assertEquals(2, Recurring.occurrenceDatesInMonth(b, 2026, 2)!!.size)
    }

    @Test fun `no occurrence earlier than firstDueDate is ever generated`() {
        val e = exp("e", 10.0, Cadence.MONTHLY, LocalDate(2026, 3, 10), dueDate = LocalDate(2026, 9, 10))
        assertEquals(0, Recurring.occurrenceDatesInMonth(e, 2026, 2)!!.size)
        assertEquals(listOf(LocalDate(2026, 3, 10)), Recurring.occurrenceDatesInMonth(e, 2026, 3))
    }

    @Test fun `month-end snapping is one shared rule - a 31st anchor lands on the 28th without becoming the 28th`() {
        val e = exp("rent", 950.0, Cadence.MONTHLY, LocalDate(2026, 1, 31))
        assertEquals(listOf(LocalDate(2026, 2, 28)), Recurring.occurrenceDatesInMonth(e, 2026, 2))
        assertEquals(listOf(LocalDate(2026, 3, 31)), Recurring.occurrenceDatesInMonth(e, 2026, 3))
        assertEquals(LocalDate(2026, 3, 31), Recurring.nextOccurrenceAfter(e, LocalDate(2026, 2, 28)))
    }

    @Test fun `a 30-day interval can put two occurrences in one calendar month`() {
        val e = exp("sub", 10.0, Cadence.MONTHLY, LocalDate(2026, 1, 1), dateMode = DateMode.INTERVAL)
        assertEquals(listOf(LocalDate(2026, 1, 1), LocalDate(2026, 1, 31)), Recurring.occurrenceDatesInMonth(e, 2026, 1))
        assertEquals(listOf(LocalDate(2026, 3, 2)), Recurring.occurrenceDatesInMonth(e, 2026, 3))
    }

    @Test fun `semimonthly is two fixed dates, each snapping`() {
        val e = exp("s", 300.0, Cadence.SEMIMONTHLY, LocalDate(2026, 1, 15), dueDate2 = LocalDate(2026, 1, 31), dateMode = null)
        assertEquals(listOf(LocalDate(2026, 2, 15), LocalDate(2026, 2, 28)), Recurring.occurrenceDatesInMonth(e, 2026, 2))
        assertEquals(LocalDate(2026, 2, 28), Recurring.nextOccurrenceAfter(e, LocalDate(2026, 2, 15)))
        assertEquals(LocalDate(2026, 3, 15), Recurring.nextOccurrenceAfter(e, LocalDate(2026, 2, 28)))
    }

    @Test fun `an occurrence sweeps iff its own due date is on or before endDate`() {
        val e = exp("e", 100.0, Cadence.MONTHLY, LocalDate(2026, 1, 20), dueDate = LocalDate(2026, 9, 20), endDate = LocalDate(2026, 9, 20))
        assertEquals(1, Recurring.occurrenceDatesInMonth(e, 2026, 9)!!.size)
        assertEquals(0, Recurring.occurrenceDatesInMonth(e, 2026, 10)!!.size)
        val cut = e.copy(endDate = LocalDate(2026, 9, 19))
        assertEquals(0, Recurring.occurrenceDatesInMonth(cut, 2026, 9)!!.size)   // due-date-to-endDate, not sweep-date
    }

    @Test fun `apportioned cadences contribute shares, not occurrences`() {
        assertNull(Recurring.occurrenceDatesInMonth(exp("y", 600.0, Cadence.YEARLY, LocalDate(2026, 3, 1), share = 50.0), 2026, 9))
        assertEquals(moneyOf(50.0), Recurring.sweepAmountForMonth(exp("y", 600.0, Cadence.YEARLY, LocalDate(2026, 3, 1), share = 50.0), 2026, 9))
        assertEquals(moneyOf(45.0), Recurring.sweepAmountForMonth(exp("b", 90.0, Cadence.BIMONTHLY, LocalDate(2026, 1, 5)), 2026, 9))   // fallback: amount / 2
    }

    @Test fun `a finished expense sweeps nothing`() {
        assertEquals(0L, Recurring.sweepAmountForMonth(exp("e", 100.0, Cadence.MONTHLY, LocalDate(2026, 1, 1), finishedAt = LocalDate(2026, 8, 1)), 2026, 9))
    }

    // ---- the anchor chain -------------------------------------------------------------------

    @Test fun `dueDate advances when its date passes, from the start of the following day, never on sweep`() {
        val e = exp("e", 100.0, Cadence.MONTHLY, LocalDate(2026, 9, 10))
        val d = profileWith().copy(recurringExpenses = listOf(e))
        assertEquals(LocalDate(2026, 9, 10), Recurring.advanceDueDates(d, LocalDate(2026, 9, 10)).recurringExpenses[0].dueDate)   // due today: not passed
        assertEquals(LocalDate(2026, 10, 10), Recurring.advanceDueDates(d, LocalDate(2026, 9, 11)).recurringExpenses[0].dueDate)
        assertEquals(LocalDate(2027, 1, 10), Recurring.advanceDueDates(d, LocalDate(2026, 12, 25)).recurringExpenses[0].dueDate)  // catch-up rolls the whole way
    }

    @Test fun `interval mode is a rolling chain that drifts, fixed-date never drifts`() {
        val i = exp("i", 10.0, Cadence.MONTHLY, LocalDate(2026, 1, 31), dateMode = DateMode.INTERVAL)
        assertEquals(LocalDate(2026, 3, 2), Recurring.nextOccurrenceAfter(i, LocalDate(2026, 1, 31)))
        val f = exp("f", 10.0, Cadence.MONTHLY, LocalDate(2026, 1, 31))
        assertEquals(LocalDate(2026, 2, 28), Recurring.nextOccurrenceAfter(f, LocalDate(2026, 1, 31)))
    }

    @Test fun `a past due date is normalised forward at creation, firstDueDate keeps the entered value`() {
        val clock = FakeClock(LocalDate(2026, 9, 14))
        val draft = exp("e", 100.0, Cadence.MONTHLY, LocalDate(2026, 3, 5))
        val d = Recurring.create(profileWith(), draft, clock) { Ids().next() }
        val e = d.recurringExpenses.single()
        assertEquals(LocalDate(2026, 3, 5), e.firstDueDate)
        assertEquals(LocalDate(2026, 10, 5), e.dueDate)
        assertEquals(5, e.dueDateAnchorDay)
    }

    @Test fun `the chain stops at endDate, and finishing is left to the month boundary`() {
        val e = exp("e", 100.0, Cadence.MONTHLY, LocalDate(2026, 6, 20), endDate = LocalDate(2026, 9, 20))
        val out = Recurring.advanceDueDates(profileWith().copy(recurringExpenses = listOf(e)), LocalDate(2026, 10, 2)).recurringExpenses[0]
        assertEquals(LocalDate(2026, 9, 20), out.dueDate)
        assertNull(out.finishedAt)                         // audit 2026-09-25: NewMonth.processMonth finishes it, after its last sweep
    }

    // ---- apportioned share ------------------------------------------------------------------

    @Test fun `share is amount over the 1st-of-month sweeps after now and on or before the due date`() {
        // §3.11's worked example: due 15 July 2027, cycle began 15 July 2026 -> twelve sweeps -> €100.
        val e = exp("y", 1200.0, Cadence.YEARLY, LocalDate(2026, 7, 15), dueDate = LocalDate(2027, 7, 15))
        assertEquals(moneyOf(100.0), Recurring.apportionedShareFor(e, LocalDate(2026, 7, 15)))
        // Created mid-cycle on 14 Sep 2026 with the same due date: 1 Oct .. 1 Jul = ten sweeps -> €120.
        assertEquals(moneyOf(120.0), Recurring.apportionedShareFor(e, LocalDate(2026, 9, 14)))
    }

    @Test fun `the divisor floors at one`() {
        val e = exp("y", 1200.0, Cadence.YEARLY, LocalDate(2026, 6, 25), dueDate = LocalDate(2026, 6, 25))
        assertEquals(moneyOf(1200.0), Recurring.apportionedShareFor(e, LocalDate(2026, 6, 20)))
    }

    @Test fun `the share rounds up so the full amount is there when it lands`() {
        val e = exp("y", 1000.0, Cadence.YEARLY, LocalDate(2026, 1, 1), dueDate = LocalDate(2026, 4, 1))
        assertEquals(moneyOf(333.34), Recurring.apportionedShareFor(e, LocalDate(2026, 1, 1)))
    }

    @Test fun `the share is recomputed only when the anchor advances, measured from the previous occurrence`() {
        val e = exp("y", 1200.0, Cadence.YEARLY, LocalDate(2025, 7, 15), dueDate = LocalDate(2026, 7, 15), share = 300.0)
        val d = profileWith().copy(recurringExpenses = listOf(e))
        assertEquals(moneyOf(300.0), Recurring.advanceDueDates(d, LocalDate(2026, 7, 15)).recurringExpenses[0].apportionedShare)   // not passed: held
        val rolled = Recurring.advanceDueDates(d, LocalDate(2026, 9, 14)).recurringExpenses[0]
        assertEquals(LocalDate(2027, 7, 15), rolled.dueDate)
        assertEquals(moneyOf(100.0), rolled.apportionedShare)                                                                    // twelve sweeps from 15 Jul 2026
    }

    @Test fun `an edit that changes none of the plan fields keeps the held share`() {
        val clock = FakeClock(LocalDate(2026, 9, 14))
        val e = exp("y", 1200.0, Cadence.YEARLY, LocalDate(2026, 7, 15), dueDate = LocalDate(2027, 7, 15), share = 100.0, lastSweptMonth = "2026-09")
        val d = profileWith().copy(recurringExpenses = listOf(e))
        val (renamed, r1) = Recurring.edit(d, e.copy(name = "Insurance (car)"), clock.today())
        assertEquals(moneyOf(100.0), renamed.recurringExpenses[0].apportionedShare); assertNull(r1)
        val (replanned, _) = Recurring.edit(d, e.copy(amount = moneyOf(2400.0)), clock.today())
        assertEquals(moneyOf(240.0), replanned.recurringExpenses[0].apportionedShare)                                              // recomputed from today: ten sweeps
    }

    // ---- the four creation branches ---------------------------------------------------------

    @Test fun `branch 1 - first due still ahead this month sweeps now and sets the marker`() {
        val clock = FakeClock(LocalDate(2026, 9, 14))
        val d = Recurring.create(profileWith(savings = 1000.0), exp("e", 100.0, Cadence.MONTHLY, LocalDate(2026, 9, 20)), clock) { Ids().next() }
        assertEquals(moneyOf(900.0), d.savings.amount)
        assertEquals(LocalDate(2026, 9, 20), d.transactions.single().date)
        assertEquals("2026-09", d.recurringExpenses[0].lastSweptMonth)
    }

    @Test fun `branch 2 - first due already passed does not back-sweep, and the first real sweep is the next 1st`() {
        val clock = FakeClock(LocalDate(2026, 9, 14))
        val d = Recurring.create(profileWith(savings = 1000.0), exp("e", 100.0, Cadence.MONTHLY, LocalDate(2026, 9, 10)), clock) { Ids().next() }
        assertEquals(moneyOf(1000.0), d.savings.amount)
        assertEquals("2026-09", d.recurringExpenses[0].lastSweptMonth)
        // A biweekly whose FIRST due passed but whose second lands later this month: still branch 2.
        val b = Recurring.create(profileWith(savings = 1000.0), exp("b", 100.0, Cadence.BIWEEKLY, LocalDate(2026, 9, 10), dateMode = null), clock) { Ids().next() }
        assertEquals(moneyOf(1000.0), b.savings.amount)
    }

    @Test fun `branch 3 - first due in a later month sweeps nothing for this month`() {
        val clock = FakeClock(LocalDate(2026, 9, 25))
        val d = Recurring.create(profileWith(savings = 1000.0), exp("e", 100.0, Cadence.MONTHLY, LocalDate(2026, 10, 5)), clock) { Ids().next() }
        assertEquals(moneyOf(1000.0), d.savings.amount)
        assertEquals("2026-09", d.recurringExpenses[0].lastSweptMonth)
    }

    @Test fun `branch 4 - apportioned cadences never sweep on creation, and the share counts from now`() {
        val clock = FakeClock(LocalDate(2026, 9, 14))
        val d = Recurring.create(profileWith(savings = 1000.0), exp("y", 1200.0, Cadence.YEARLY, LocalDate(2027, 7, 15)), clock) { Ids().next() }
        assertEquals(moneyOf(1000.0), d.savings.amount)
        assertEquals(moneyOf(120.0), d.recurringExpenses[0].apportionedShare)
        assertEquals("2026-09", d.recurringExpenses[0].lastSweptMonth)
    }

    @Test fun `dateMode is unrepresentable on the three cadences it cannot apply to`() {
        val clock = FakeClock(LocalDate(2026, 9, 14))
        val d = Recurring.create(profileWith(), exp("w", 10.0, Cadence.WEEKLY, LocalDate(2026, 9, 20), dateMode = DateMode.FIXED_DATE), clock) { Ids().next() }
        assertNull(d.recurringExpenses[0].dateMode)
        assertNull(d.recurringExpenses[0].dueDateAnchorDay)
    }

    // ---- edit-and-reconcile -----------------------------------------------------------------

    @Test fun `editing an already-swept expense offers the difference dated to the occurrence it adjusts`() {
        val clock = FakeClock(LocalDate(2026, 9, 10))
        val ids = Ids()
        var d = Recurring.create(profileWith(savings = 2000.0), exp("rent", 1000.0, Cadence.MONTHLY, LocalDate(2026, 9, 20)), clock) { ids.next() }
        val e = d.recurringExpenses[0]
        val (edited, r) = Recurring.edit(d, e.copy(amount = moneyOf(1200.0)), clock.today())
        assertNotNull(r)
        assertEquals(moneyOf(200.0), r.delta)
        assertEquals(LocalDate(2026, 9, 20), r.occurrenceDate)
        d = Recurring.applyReconcile(edited, r, clock) { ids.next() }
        assertEquals(moneyOf(800.0), d.savings.amount)                                          // 2000 - 1000 - 200
        assertEquals(moneyOf(1200.0), Recurring.reversalAmount(d, d.recurringExpenses[0], clock.today()))   // the row nets in
    }

    @Test fun `an edit before this month's sweep offers nothing`() {
        val clock = FakeClock(LocalDate(2026, 9, 10))
        val e = exp("e", 100.0, Cadence.MONTHLY, LocalDate(2026, 9, 20), lastSweptMonth = "2026-08")
        val (_, r) = Recurring.edit(profileWith().copy(recurringExpenses = listOf(e)), e.copy(amount = moneyOf(150.0)), clock.today())
        assertNull(r)
    }

    // ---- deletion reversal ------------------------------------------------------------------

    @Test fun `the amount offered is what the ledger recorded, not the entity's amount`() {
        val clock = FakeClock(LocalDate(2026, 9, 10))
        val ids = Ids()
        var d = Recurring.create(profileWith(savings = 2000.0), exp("rent", 1000.0, Cadence.MONTHLY, LocalDate(2026, 9, 20)), clock) { ids.next() }
        d = d.copy(recurringExpenses = d.recurringExpenses.map { it.copy(amount = moneyOf(1200.0)) })   // edited without reconciling
        assertEquals(moneyOf(1000.0), Recurring.reversalAmount(d, d.recurringExpenses[0], clock.today()))
    }

    @Test fun `semimonthly reversal is per occurrence - due 15th and 30th, deleted on the 20th, one returns`() {
        val clock = FakeClock(LocalDate(2026, 9, 1))
        val ids = Ids()
        var d = Recurring.create(profileWith(savings = 2000.0), exp("s", 300.0, Cadence.SEMIMONTHLY, LocalDate(2026, 9, 15), dueDate2 = LocalDate(2026, 9, 30), dateMode = null), clock) { ids.next() }
        assertEquals(moneyOf(1400.0), d.savings.amount)
        clock.setDay(LocalDate(2026, 9, 20))
        assertEquals(moneyOf(300.0), Recurring.reversalAmount(d, d.recurringExpenses[0], clock.today()))
        d = Recurring.delete(d, "s", reverse = true, clock) { ids.next() }
        assertEquals(moneyOf(1700.0), d.savings.amount)
        assertEquals(0, d.recurringExpenses.size)
        assertEquals(3, d.transactions.size)                                                    // both sweeps and the reversal stay
    }

    @Test fun `due today is still un-elapsed and still reversible`() {
        val clock = FakeClock(LocalDate(2026, 9, 1))
        val ids = Ids()
        val d = Recurring.create(profileWith(savings = 2000.0), exp("e", 100.0, Cadence.MONTHLY, LocalDate(2026, 9, 1)), clock) { ids.next() }
        assertEquals(moneyOf(100.0), Recurring.reversalAmount(d, d.recurringExpenses[0], LocalDate(2026, 9, 1)))
        assertEquals(0L, Recurring.reversalAmount(d, d.recurringExpenses[0], LocalDate(2026, 9, 2)))
    }

    @Test fun `apportioned reversal returns the shares swept so far in the current cycle`() {
        val clock = FakeClock(LocalDate(2026, 9, 14))
        val ids = Ids()
        val e = exp("y", 1200.0, Cadence.YEARLY, LocalDate(2026, 7, 15), dueDate = LocalDate(2027, 7, 15), share = 100.0)
        var d = profileWith(savings = 2000.0).copy(recurringExpenses = listOf(e))
        d = Recurring.sweepOccurrences(d, "y", LocalDate(2026, 8, 1), clock) { ids.next() }.first
        d = Recurring.sweepOccurrences(d, "y", LocalDate(2026, 9, 1), clock) { ids.next() }.first
        assertEquals(moneyOf(200.0), Recurring.reversalAmount(d, d.recurringExpenses[0], clock.today()))
    }

    @Test fun `a finished expense's obligation is discharged - nothing to reverse`() {
        val e = exp("e", 100.0, Cadence.MONTHLY, LocalDate(2026, 9, 20), finishedAt = LocalDate(2026, 9, 1))
        assertEquals(0L, Recurring.reversalAmount(profileWith().copy(recurringExpenses = listOf(e)), e, LocalDate(2026, 9, 14)))
    }

    @Test fun `deleting either side of a linked pair removes both, with a closing row on the account`() {
        val clock = FakeClock(LocalDate(2026, 9, 14))
        val ids = Ids()
        val e = exp("pay", 260.0, Cadence.MONTHLY, LocalDate(2026, 9, 15), linkedAccountId = "loan", lastSweptMonth = "2026-09")
        val a = Account("loan", "p-1", "Car Loan", null, AccountType.LOAN, balance = moneyOf(5000.0), linkedRecurringExpenseId = "pay")
        val d = Recurring.delete(profileWith().copy(recurringExpenses = listOf(e), accounts = listOf(a)), "pay", reverse = false, clock) { ids.next() }
        assertTrue(d.accounts.isEmpty()); assertTrue(d.recurringExpenses.isEmpty())
        assertEquals(moneyOf(-5000.0), d.transactions.single().amount)
        assertEquals("acct:loan", d.transactions.single().key)
    }
}
