package app.scifin

import app.scifin.engine.GoalFrame
import app.scifin.engine.Goals
import app.scifin.engine.Writes
import app.scifin.model.Goal
import app.scifin.model.GoalMode
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §6.1's arithmetic, §6.2's commitment rules, §6.4's thresholds. */
class GoalsTest {
    private val today = LocalDate(2026, 9, 14)

    // ---- §6.1 ------------------------------------------------------------------------

    @Test fun `the amount divided is target minus saved`() {
        // A €1,200 goal with €400 already saved over six months is €133.34/month (rounded up), not €200.
        val g = Goal("g", "p-1", "Bike", saved = moneyOf(400.0), target = moneyOf(1200.0), targetDate = LocalDate(2027, 3, 14), mode = GoalMode.DATE)
        val d = Goals.derive(g, today)
        assertEquals(6, d.months)
        assertEquals(moneyOf(133.34), d.monthly)
    }

    @Test fun `months are calendar months from this month to the target month, never below one`() {
        assertEquals(1, Goals.calendarMonthsUntil(today, LocalDate(2026, 9, 30)))
        assertEquals(1, Goals.calendarMonthsUntil(today, LocalDate(2026, 10, 1)))
        assertEquals(8, Goals.calendarMonthsUntil(today, LocalDate(2027, 5, 14)))
        assertEquals(1, Goals.calendarMonthsUntil(today, LocalDate(2026, 1, 1)))      // past date: still 1
    }

    @Test fun `by-date rounds the monthly figure up to the minor unit`() {
        val g = Goal("g", "p-1", "x", target = moneyOf(100.0), targetDate = LocalDate(2026, 11, 1), mode = GoalMode.DATE)   // 2 months
        assertEquals(moneyOf(50.0), Goals.derive(g, today).monthly)
        val g3 = g.copy(targetDate = LocalDate(2026, 12, 1))                                                             // 3 months: 33.333.. -> 33.34
        assertEquals(moneyOf(33.34), Goals.derive(g3, today).monthly)
    }

    @Test fun `by-amount rounds the timeline up to the next whole month`() {
        val g = Goal("g", "p-1", "x", target = moneyOf(900.0), monthly = moneyOf(74.0), mode = GoalMode.AMOUNT, saved = moneyOf(400.0))
        assertEquals(7, Goals.derive(g, today).months)        // 500 / 74 = 6.76 -> 7
        assertEquals(moneyOf(74.0), Goals.derive(g, today).monthly)
    }

    @Test fun `no target means nothing to compute, and a reached goal needs nothing more`() {
        assertEquals(null, Goals.derive(Goal("g", "p-1", "Someday"), today).monthly)
        val reached = Goal("g", "p-1", "x", saved = moneyOf(500.0), target = moneyOf(500.0), targetDate = LocalDate(2027, 1, 1))
        assertEquals(0L, Goals.derive(reached, today).monthly)
    }

    @Test fun `a quick-add re-derives the rate underneath the date`() {
        val clock = FakeClock(today)
        val g = Goal("g", "p-1", "x", target = moneyOf(1000.0), targetDate = LocalDate(2027, 7, 14), mode = GoalMode.DATE)   // 10 months
        var d = profileWith(savings = 5000.0, goals = listOf(Goals.rederive(g, today)))
        assertEquals(moneyOf(100.0), d.goals[0].monthly)
        d = Writes.goalQuickAdd(d, "g", moneyOf(500.0), null, clock, "t1")
        d = d.copy(goals = d.goals.map { Goals.rederive(it, today) })
        assertEquals(moneyOf(50.0), d.goals[0].monthly)
    }

    // ---- §6.2 ------------------------------------------------------------------------

    @Test fun `a commitment ends at saved greater or equal target, the goal stays open`() {
        assertTrue(Goals.commitmentEnded(Goal("g", "p-1", "x", saved = moneyOf(200.0), target = moneyOf(200.0))))
        assertTrue(!Goals.commitmentEnded(Goal("g", "p-1", "x", saved = moneyOf(199.0), target = moneyOf(200.0))))
        assertTrue(!Goals.commitmentEnded(Goal("g", "p-1", "x", saved = moneyOf(999.0))))    // no target: never "ended"
    }

    @Test fun `other goals' total excludes reached goals, rateless goals and the goal itself`() {
        val d = profileWith(goals = listOf(
            Goal("a", "p-1", "A", target = moneyOf(600.0), monthly = moneyOf(200.0), mode = GoalMode.AMOUNT),
            Goal("b", "p-1", "B", saved = moneyOf(600.0), target = moneyOf(600.0), monthly = moneyOf(200.0), mode = GoalMode.AMOUNT),   // reached
            Goal("c", "p-1", "C"),                                                                                                    // no target
            Goal("d", "p-1", "D", target = moneyOf(500.0), targetDate = LocalDate(2026, 1, 1), mode = GoalMode.DATE),                    // date passed, unmet: counts (500/1)
            Goal("e", "p-1", "E", target = moneyOf(300.0), monthly = moneyOf(100.0), mode = GoalMode.AMOUNT),
        ))
        assertEquals(moneyOf(700.0), Goals.otherGoalsMonthlyTotal(d, "e", today))    // A 200 + D 500; B reached, C rateless, E is the goal itself
    }

    @Test fun `income-on frame reads landed income minus recurring minus other goals`() {
        val clock = FakeClock(today)
        var d = profileWith(savings = 2000.0, goals = listOf(Goal("a", "p-1", "A", target = moneyOf(600.0), monthly = moneyOf(200.0), mode = GoalMode.AMOUNT)))
        d = d.copy(settings = d.settings.copy(incomeEnabled = true, incomeEnabledDate = LocalDate(2026, 1, 1)))
        d = Writes.savingsQuickAdd(d, moneyOf(1450.0), "Salary", true, clock, "t1", date = LocalDate(2026, 9, 5))
        val f = Goals.frame(d, moneyOf(150.0), excludeId = "new", scheduledRecurring = moneyOf(1000.0), today = today) as GoalFrame.IncomeOn
        assertEquals(moneyOf(250.0), f.available)
        assertTrue(f.fits)
        val g = Goals.frame(d, moneyOf(300.0), "new", moneyOf(1000.0), today) as GoalFrame.IncomeOn
        assertTrue(!g.fits)
    }

    @Test fun `income-off frame reads committed rate against the Savings stock`() {
        val d = profileWith(savings = 1000.0, goals = listOf(Goal("a", "p-1", "A", target = moneyOf(600.0), monthly = moneyOf(200.0), mode = GoalMode.AMOUNT)))
        val f = Goals.frame(d, moneyOf(150.0), "new", moneyOf(600.0), today) as GoalFrame.IncomeOff
        assertEquals(moneyOf(950.0), f.committed); assertEquals(moneyOf(1000.0), f.savings); assertTrue(f.fits)
    }

    // ---- §6.4 ------------------------------------------------------------------------

    @Test fun `sustainability base is Savings when income is off, one twelfth threshold, three gentler options`() {
        val d = profileWith(savings = 2400.0)
        assertNull(Goals.sustainability(d, moneyOf(200.0), today))                 // exactly 1/12: within
        val s = Goals.sustainability(d, moneyOf(201.0), today)!!
        assertEquals(false, s.incomeOn)
        assertEquals(listOf(moneyOf(120.0), moneyOf(150.0), moneyOf(200.0)), s.suggestions)   // 1/20, 1/16, 1/12
    }

    @Test fun `sustainability base is landed income when income is on, one sixth threshold`() {
        val clock = FakeClock(today)
        var d = profileWith(savings = 100000.0)
        d = d.copy(settings = d.settings.copy(incomeEnabled = true, incomeEnabledDate = LocalDate(2026, 1, 1)))
        d = Writes.savingsQuickAdd(d, moneyOf(1200.0), "Salary", true, clock, "t1", date = LocalDate(2026, 9, 5))
        assertNull(Goals.sustainability(d, moneyOf(200.0), today))
        val s = Goals.sustainability(d, moneyOf(201.0), today)!!
        assertEquals(true, s.incomeOn)
        assertEquals(listOf(moneyOf(86.0), moneyOf(100.0), moneyOf(200.0)), s.suggestions)     // 1/14, 1/12, 1/6
    }

    @Test fun `at a zero or negative base the check fires with no numbers`() {
        val s = Goals.sustainability(profileWith(savings = 0.0), moneyOf(10.0), today)!!
        assertNull(s.suggestions)
        val neg = Goals.sustainability(profileWith(savings = -50.0), moneyOf(10.0), today)!!
        assertNull(neg.suggestions)
    }
}
