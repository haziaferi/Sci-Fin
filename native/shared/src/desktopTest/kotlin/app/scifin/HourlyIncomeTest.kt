package app.scifin

import app.scifin.engine.Backup
import app.scifin.engine.BackupResult
import app.scifin.engine.Hourly
import app.scifin.engine.Income
import app.scifin.engine.PAYDAY_REMINDER_KEY
import app.scifin.engine.Reminders
import app.scifin.engine.TimeWorth
import app.scifin.engine.Writes
import app.scifin.engine.plusDays
import app.scifin.model.IncomeCadence
import app.scifin.model.IncomeType
import app.scifin.model.ProfileData
import app.scifin.model.Shift
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §3.10's hourly income (owner, 2026-10-26), rule by rule. */
class HourlyIncomeTest {
    private val clock = FakeClock(LocalDate(2026, 11, 2))
    private val ids = Ids("h")
    private val rate = moneyOf(20.0)

    /** Hourly since 1 November, paid every two weeks from Friday 13 November. */
    private fun hourly(): ProfileData = profileWith(savings = 1000.0).let {
        it.copy(settings = it.settings.copy(
            incomeEnabled = true, incomeEnabledDate = LocalDate(2026, 11, 1), incomeCadence = IncomeCadence.BIWEEKLY,
            incomePayDate = LocalDate(2026, 11, 13), incomeType = IncomeType.HOURLY, hourlyRate = rate, lastPaydayHandled = LocalDate(2026, 10, 31),
        ))
    }

    private fun ProfileData.shift(day: Int, hours: Double, r: Double = 20.0) =
        Hourly.logShift(this, Shift(ids.next(), profileId, LocalDate(2026, 11, day), hours, moneyOf(r)))

    @Test fun `a logged shift moves nothing`() {
        val d = hourly()
        val after = d.shift(3, 6.0)
        assertEquals(d.savings, after.savings)
        assertEquals(d.transactions, after.transactions)
        assertEquals(0, Income.receivedThisMonth(after, LocalDate(2026, 11, 3)))
        assertEquals(1, after.shifts.size)
    }

    @Test fun `no prompt before the first payday, then one covering every shift dated on or before it`() {
        val d = hourly().shift(3, 6.0).shift(10, 8.5).shift(14, 4.0)
        assertNull(Hourly.duePayday(d, LocalDate(2026, 11, 12)))
        val p = assertNotNull(Hourly.duePayday(d, LocalDate(2026, 11, 13)))
        assertEquals(LocalDate(2026, 11, 13), p.date)
        assertEquals(14.5, p.hours)                                  // the 14th waits for the next payday
        assertEquals(moneyOf(290.0), p.gross)
    }

    @Test fun `confirming lands what arrived as income on the payday, with its hours and rate, and clears the shifts it paid`() {
        val d = hourly().shift(3, 6.0).shift(10, 8.5).shift(14, 4.0)
        val p = Hourly.duePayday(d, LocalDate(2026, 11, 13))!!
        val after = Hourly.confirmPayday(d, p, moneyOf(232.0), clock, ids.next())
        val tx = after.transactions.single()
        assertTrue(tx.isIncome); assertEquals(LocalDate(2026, 11, 13), tx.date); assertEquals(moneyOf(232.0), tx.amount)
        assertEquals(14.5, tx.hoursWorked); assertEquals(rate, tx.rate)
        assertEquals(moneyOf(1232.0), after.savings.amount)
        assertEquals(listOf(4.0), after.shifts.map { it.hours })
        assertNull(Hourly.duePayday(after, LocalDate(2026, 11, 20)), "answered, and the next is the 27th")
        assertEquals(LocalDate(2026, 11, 27), Hourly.duePayday(after, LocalDate(2026, 11, 27))!!.date)
    }

    @Test fun `nothing this time keeps the hours for the next payday, and an empty payday still asks`() {
        val d = hourly().shift(3, 6.0)
        val p = Hourly.duePayday(d, LocalDate(2026, 11, 13))!!
        val after = Hourly.nothingThisTime(d, p)
        assertEquals(d.savings, after.savings)
        assertEquals(6.0, Hourly.duePayday(after, LocalDate(2026, 11, 27))!!.hours)
        val empty = Hourly.duePayday(hourly(), LocalDate(2026, 11, 13))
        assertNotNull(empty); assertTrue(empty.shifts.isEmpty())
    }

    @Test fun `the estimate is before deductions until a payday, then follows the last three`() {
        var d = hourly().shift(3, 10.0)
        assertEquals(moneyOf(200.0) to true, Hourly.estimate(d, d.shifts))
        // three paydays at 80%, 80% and 50% of what was logged, then one at 100% that pushes the first out
        for ((i, share) in listOf(0.8, 0.8, 0.5, 1.0).withIndex()) {
            val day = LocalDate(2026, 11, 13).plusDays(14 * i)
            val p = Hourly.duePayday(d, day)!!
            d = Hourly.confirmPayday(d, p, (p.gross * share).toLong(), clock, ids.next())
            d = Hourly.logShift(d, Shift(ids.next(), d.profileId, day.plusDays(1), 10.0, rate))
        }
        assertEquals(moneyOf(153.33) to false, Hourly.estimate(d, d.shifts), "(0.8 + 0.5 + 1.0) / 3 of 200")
    }

    @Test fun `hourly has no auto-deposit and no missed-paycheck note -- the prompt does that job`() {
        val d = hourly().let { it.copy(settings = it.settings.copy(incomeAutoDeposit = true, income = moneyOf(1000.0))) }
        assertEquals(0, Income.runCatchUp(d, FakeClock(LocalDate(2026, 12, 31)), ids::next).second.count)
        assertNull(Income.noIncomeLoggedDays(d, LocalDate(2026, 12, 31)))
        val switched = Hourly.setType(profileWith().let { it.copy(settings = it.settings.copy(incomeEnabled = true, incomeAutoDeposit = true)) }, IncomeType.HOURLY, clock.today)
        assertFalse(switched.settings.incomeAutoDeposit)
        assertEquals(clock.today, switched.settings.lastPaydayHandled, "no payday behind the switch, nor on its day, is asked about")
        val onPayday = Hourly.setType(hourly().let { it.copy(settings = it.settings.copy(incomeType = IncomeType.SALARIED)) }, IncomeType.HOURLY, LocalDate(2026, 11, 13))
        assertNull(Hourly.duePayday(onPayday, LocalDate(2026, 11, 13)), "switching on a payday does not ask about it")
        assertNotNull(Hourly.duePayday(onPayday, LocalDate(2026, 11, 27)))
    }

    @Test fun `leaving hourly settles the logged hours -- confirmed now, or discarded`() {
        val d = hourly().shift(3, 6.0).shift(4, 4.0)
        val confirmed = Hourly.settleOnSwitch(d, moneyOf(170.0), clock, ids.next())
        assertTrue(confirmed.shifts.isEmpty())
        assertEquals(clock.today, confirmed.transactions.single().date)
        assertEquals(10.0, confirmed.transactions.single().hoursWorked)
        val discarded = Hourly.settleOnSwitch(d, null, clock, ids.next())
        assertTrue(discarded.shifts.isEmpty() && discarded.transactions.isEmpty())
        assertEquals(IncomeType.SALARIED, Hourly.setType(discarded, IncomeType.SALARIED, clock.today).settings.incomeType)
        // Income off clears what is left, as §3.3's toggle clears everything else
        val off = Income.setEnabled(d, false, clock.today)
        assertTrue(off.shifts.isEmpty()); assertEquals(IncomeType.SALARIED, off.settings.incomeType); assertEquals(0, off.settings.hourlyRate)
    }

    @Test fun `salaried by hand -- the payday since the last one handled, until income is logged`() {
        val d = profileWith().let {
            it.copy(settings = it.settings.copy(
                incomeEnabled = true, incomeEnabledDate = LocalDate(2026, 11, 1), income = moneyOf(1450.0),
                incomeCadence = IncomeCadence.MONTHLY, incomePayDate = LocalDate(2026, 11, 10),
            ))
        }
        assertNull(Hourly.salariedPaydayToLog(d, LocalDate(2026, 11, 9)))
        assertEquals(LocalDate(2026, 11, 10), Hourly.salariedPaydayToLog(d, LocalDate(2026, 11, 10)))
        assertEquals(LocalDate(2026, 12, 10), Hourly.salariedPaydayToLog(d, LocalDate(2026, 12, 11)), "one prompt, for the latest, however many were missed")
        assertNull(Hourly.salariedPaydayToLog(d, LocalDate(2026, 11, 12)), "two days on, the no-income note has it")
        val logged = Writes.savingsQuickAdd(d, moneyOf(1450.0), null, true, FakeClock(LocalDate(2026, 11, 10)), "i-1", LocalDate(2026, 11, 10))
        assertNull(Hourly.salariedPaydayToLog(logged, LocalDate(2026, 11, 11)))
        val handled = d.copy(settings = d.settings.copy(lastPaydayHandled = LocalDate(2026, 11, 10)))
        assertNull(Hourly.salariedPaydayToLog(handled, LocalDate(2026, 11, 11)))
        assertNull(Hourly.salariedPaydayToLog(d.copy(settings = d.settings.copy(incomeAutoDeposit = true)), LocalDate(2026, 11, 11)))
    }

    @Test fun `payday notifies only while income is not recorded by itself, and can be switched off`() {
        val now = LocalDate(2026, 11, 2).atTime(12, 0)
        fun payday(d: ProfileData) = Reminders.registrations(d, now).singleOrNull { it.expenseId == PAYDAY_REMINDER_KEY }
        val h = payday(hourly())
        assertEquals(LocalDate(2026, 11, 13), h?.dueDate); assertEquals("Payday — confirm what arrived", h?.body)
        val manual = hourly().let { it.copy(settings = it.settings.copy(incomeType = IncomeType.SALARIED, income = moneyOf(1450.0))) }
        assertEquals("Payday — log what arrived", payday(manual)?.body)
        assertNull(payday(manual.copy(settings = manual.settings.copy(incomeAutoDeposit = true))))
        assertNull(payday(hourly().let { it.copy(settings = it.settings.copy(paydayNotificationEnabled = false)) }))
        assertNull(payday(profileWith()))
    }

    @Test fun `a backup carries the logged hours, and an older file without them reads as none`() {
        val d = hourly().shift(3, 6.0)
        val text = Backup.encode(Backup.build(listOf(d), d.profileId, clock.today))
        val back = (Backup.validate(text) as BackupResult.Ok).backup.profiles.single()
        assertEquals(d.shifts, back.shifts); assertEquals(IncomeType.HOURLY, back.settings.incomeType)
        val older = text.replace(Regex(""",\s*"shifts": \[[^\]]*\]"""), "")
        assertFalse("\"shifts\"" in older)
        assertTrue((Backup.validate(older) as BackupResult.Ok).backup.profiles.single().shifts.isEmpty())
    }

    @Test fun `the time calculator starts from the hourly rate`() {
        assertEquals(rate, TimeWorth.prefillRate(hourly().settings, 160.0))
        assertNull(TimeWorth.monthlyEquivalent(hourly().settings), "no expected-hours field on hourly")
    }
}
