package app.scifin

import app.scifin.engine.Income
import app.scifin.engine.Ledger
import app.scifin.engine.Writes
import app.scifin.engine.plusMonthsClamped
import app.scifin.model.IncomeCadence
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** §3.3, rule by rule, plus the calendar arithmetic §3.11's anchors will lean on. */
class IncomeTest {
    private val day = LocalDate(2026, 9, 14)

    private fun incomeOn(cadence: IncomeCadence, payDate: LocalDate?, payDate2: LocalDate? = null, auto: Boolean = false, amount: Double = 1450.0, enabledOn: LocalDate = LocalDate(2026, 3, 1)) =
        profileWith(savings = 1000.0).let {
            it.copy(settings = it.settings.copy(
                incomeEnabled = true, income = moneyOf(amount), incomeCadence = cadence,
                incomePayDate = payDate, incomePayDate2 = payDate2, incomeAutoDeposit = auto, incomeEnabledDate = enabledOn,
            ))
        }

    // ---- calendar arithmetic ----------------------------------------------------------

    @Test fun `month arithmetic clamps rather than overflows`() {
        assertEquals(LocalDate(2026, 2, 28), LocalDate(2026, 1, 31).plusMonthsClamped(1))
        assertEquals(LocalDate(2026, 3, 31), LocalDate(2026, 1, 31).plusMonthsClamped(2))       // the anchor survives the short month
        assertEquals(LocalDate(2028, 2, 29), LocalDate(2028, 1, 31).plusMonthsClamped(1))
    }

    // ---- cadence: "each subsequent deposit follows the real mechanics of its own cadence" ----

    @Test fun `biweekly steps 14 days from the anchor`() {
        val s = incomeOn(IncomeCadence.BIWEEKLY, LocalDate(2026, 9, 11)).settings
        assertEquals(LocalDate(2026, 9, 11), Income.nextOccurrence(s, LocalDate(2026, 9, 11)))
        assertEquals(LocalDate(2026, 9, 25), Income.nextOccurrence(s, LocalDate(2026, 9, 12)))
        assertEquals(LocalDate(2026, 10, 9), Income.nextOccurrence(s, LocalDate(2026, 9, 26)))
    }

    @Test fun `weekly steps 7 days`() {
        val s = incomeOn(IncomeCadence.WEEKLY, LocalDate(2026, 9, 11)).settings
        assertEquals(LocalDate(2026, 9, 18), Income.nextOccurrence(s, LocalDate(2026, 9, 12)))
    }

    @Test fun `monthly is calendar-day anchored, not a rolling 30 days, and snaps at month end`() {
        val s = incomeOn(IncomeCadence.MONTHLY, LocalDate(2026, 1, 31)).settings
        assertEquals(LocalDate(2026, 2, 28), Income.nextOccurrence(s, LocalDate(2026, 2, 1)))
        assertEquals(LocalDate(2026, 3, 31), Income.nextOccurrence(s, LocalDate(2026, 3, 1)))
        assertEquals(LocalDate(2026, 3, 31), Income.nextOccurrence(s, LocalDate(2026, 3, 31)))
    }

    @Test fun `semimonthly uses two fixed dates, each snapping at month end`() {
        val s = incomeOn(IncomeCadence.SEMIMONTHLY, LocalDate(2026, 9, 15), LocalDate(2026, 9, 30)).settings
        assertEquals(LocalDate(2026, 9, 15), Income.nextOccurrence(s, LocalDate(2026, 9, 2)))
        assertEquals(LocalDate(2026, 9, 30), Income.nextOccurrence(s, LocalDate(2026, 9, 16)))
        assertEquals(LocalDate(2026, 10, 15), Income.nextOccurrence(s, LocalDate(2026, 10, 1)))
        // The "last day" second date snaps in a 28-day month rather than rolling into March.
        val feb = incomeOn(IncomeCadence.SEMIMONTHLY, LocalDate(2026, 1, 15), LocalDate(2026, 1, 31)).settings
        assertEquals(LocalDate(2026, 2, 28), Income.nextOccurrence(feb, LocalDate(2026, 2, 16)))
    }

    @Test fun `no anchor means no occurrence`() {
        assertNull(Income.nextOccurrence(incomeOn(IncomeCadence.MONTHLY, null).settings, day))
    }

    // ---- auto-deposit catch-up (§3.9's second trigger) ---------------------------------

    @Test fun `catch-up replays every elapsed payday with its real date, never a lump sum`() {
        val clock = FakeClock(LocalDate(2026, 9, 14))
        val ids = Ids()
        val d0 = incomeOn(IncomeCadence.BIWEEKLY, LocalDate(2026, 8, 7), auto = true)
        val (d1, c) = Income.runCatchUp(d0, clock) { ids.next() }
        assertEquals(3, c.count)                                            // 7 Aug, 21 Aug, 4 Sep
        assertEquals(listOf(LocalDate(2026, 8, 7), LocalDate(2026, 8, 21), LocalDate(2026, 9, 4)), d1.transactions.map { it.date })
        assertEquals(moneyOf(1000.0 + 3 * 1450.0), d1.savings.amount)
        assertEquals(LocalDate(2026, 9, 4), d1.settings.lastIncomeDepositDate)
        assertEquals(0, d1.settings.catchUpDepositCount)                   // recorded only when §3.9 has a card to show (NewMonth)
        assertEquals(Ledger.AUTO_DEPOSIT_PAYEE, d1.transactions[0].payee)
        // Idempotent: a second run on the same day deposits nothing.
        val (d2, c2) = Income.runCatchUp(d1, clock) { ids.next() }
        assertEquals(0, c2.count); assertEquals(d1.transactions.size, d2.transactions.size)
    }

    @Test fun `with no deposit yet the replay starts at incomePayDate, not today`() {
        val clock = FakeClock(LocalDate(2026, 9, 14))
        val d0 = incomeOn(IncomeCadence.MONTHLY, LocalDate(2026, 6, 10), auto = true)
        val (d1, c) = Income.runCatchUp(d0, clock) { Ids().next() }
        assertEquals(4, c.count)                                            // 10 Jun, 10 Jul, 10 Aug, 10 Sep
        assertEquals(LocalDate(2026, 6, 10), d1.transactions.first().date)
    }

    @Test fun `a future anchor deposits nothing yet`() {
        val clock = FakeClock(LocalDate(2026, 9, 14))
        val (d1, c) = Income.runCatchUp(incomeOn(IncomeCadence.MONTHLY, LocalDate(2026, 9, 20), auto = true), clock) { Ids().next() }
        assertEquals(0, c.count); assertNull(d1.settings.lastIncomeDepositDate)
    }

    @Test fun `manual path or income off deposits nothing`() {
        val clock = FakeClock(day)
        assertEquals(0, Income.runCatchUp(incomeOn(IncomeCadence.MONTHLY, LocalDate(2026, 6, 10), auto = false), clock) { Ids().next() }.second.count)
        assertEquals(0, Income.runCatchUp(profileWith(), clock) { Ids().next() }.second.count)
    }

    // ---- incomeReceivedThisMonth: derived, month-bounded, floored at incomeEnabledDate ----

    @Test fun `received this month sums income rows in the month on or after the enable date`() {
        val clock = FakeClock(LocalDate(2026, 9, 14))
        val ids = Ids()
        var d = incomeOn(IncomeCadence.BIWEEKLY, LocalDate(2026, 9, 11), enabledOn = LocalDate(2026, 9, 5))
        d = Writes.savingsQuickAdd(d, moneyOf(1450.0), "Salary", true, clock, ids.next(), date = LocalDate(2026, 9, 11))
        d = Writes.savingsQuickAdd(d, moneyOf(1450.0), "Salary", true, clock, ids.next(), date = LocalDate(2026, 8, 28))   // last month
        d = Writes.savingsQuickAdd(d, moneyOf(300.0), "Old", true, clock, ids.next(), date = LocalDate(2026, 9, 2))      // before the enable date
        d = Writes.savingsQuickAdd(d, moneyOf(50.0), "Gift", false, clock, ids.next(), date = LocalDate(2026, 9, 12))    // not income
        assertEquals(moneyOf(1450.0), Income.receivedThisMonth(d, clock.today()))
    }

    // ---- "No income logged in the last X days" -----------------------------------------

    @Test fun `X is today minus the reference point and fires past the cadence threshold`() {
        val d = incomeOn(IncomeCadence.BIWEEKLY, LocalDate(2026, 8, 7)).let { it.copy(settings = it.settings.copy(lastIncomeLoggedDate = LocalDate(2026, 8, 20))) }
        assertEquals(25, Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 14)))
        assertNull(Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 3)))        // 14 days: not yet past
        assertEquals(15, Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 4)))
    }

    @Test fun `the fallback reference is the enable date, so re-enabling never surfaces a stale count`() {
        val d = incomeOn(IncomeCadence.WEEKLY, LocalDate(2026, 9, 10), enabledOn = LocalDate(2026, 9, 10))
        assertNull(Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 14)))
        assertEquals(8, Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 18)))
    }

    @Test fun `monthly fires only once the next calendar payday has passed`() {
        val d = incomeOn(IncomeCadence.MONTHLY, LocalDate(2026, 8, 10)).let { it.copy(settings = it.settings.copy(lastIncomeLoggedDate = LocalDate(2026, 8, 10))) }
        assertNull(Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 9)))
        assertEquals(31, Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 10)))
    }

    @Test fun `semimonthly fires against whichever anchor most recently passed`() {
        val d = incomeOn(IncomeCadence.SEMIMONTHLY, LocalDate(2026, 9, 15), LocalDate(2026, 9, 30)).let { it.copy(settings = it.settings.copy(lastIncomeLoggedDate = LocalDate(2026, 9, 1))) }
        assertNull(Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 14)))
        assertEquals(15, Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 16)))
    }

    @Test fun `auto-deposit path never shows the note`() {
        val d = incomeOn(IncomeCadence.WEEKLY, LocalDate(2026, 8, 1), auto = true).let { it.copy(settings = it.settings.copy(lastIncomeLoggedDate = LocalDate(2026, 8, 1))) }
        assertNull(Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 14)))
    }

    // ---- the toggle and the cadence change --------------------------------------------

    @Test fun `toggling off clears the whole set - cleared, not remembered`() {
        val d = incomeOn(IncomeCadence.SEMIMONTHLY, LocalDate(2026, 9, 15), LocalDate(2026, 9, 30), auto = true)
            .let { it.copy(settings = it.settings.copy(lastIncomeLoggedDate = day, lastIncomeDepositDate = day)) }
        val s = Income.setEnabled(d, false, day).settings
        assertEquals(false, s.incomeEnabled); assertEquals(0L, s.income); assertEquals(false, s.incomeAutoDeposit)
        assertNull(s.incomePayDate); assertNull(s.incomePayDate2); assertNull(s.incomeEnabledDate)
        assertNull(s.lastIncomeLoggedDate); assertNull(s.lastIncomeDepositDate)
    }

    @Test fun `toggling on stamps the enable date and defaults the anchor to today`() {
        val s = Income.setEnabled(profileWith(), true, day).settings
        assertEquals(true, s.incomeEnabled); assertEquals(day, s.incomeEnabledDate); assertEquals(day, s.incomePayDate)
    }

    @Test fun `changing cadence re-asks the anchor, drops the second date, and resets the deposit marker`() {
        val d = incomeOn(IncomeCadence.SEMIMONTHLY, LocalDate(2026, 9, 15), LocalDate(2026, 9, 30)).let { it.copy(settings = it.settings.copy(lastIncomeDepositDate = day)) }
        val s = Income.setCadence(d, IncomeCadence.MONTHLY).settings
        assertEquals(IncomeCadence.MONTHLY, s.incomeCadence)
        assertNull(s.incomePayDate); assertNull(s.incomePayDate2); assertNull(s.lastIncomeDepositDate)
        assertEquals(d, Income.setCadence(d, IncomeCadence.SEMIMONTHLY))        // same cadence: untouched
    }
}
