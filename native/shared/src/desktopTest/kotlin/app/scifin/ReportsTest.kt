@file:OptIn(ExperimentalTime::class)

package app.scifin

import app.scifin.engine.Reports
import app.scifin.engine.Writes
import app.scifin.engine.daysTo
import app.scifin.engine.savingsAmount
import app.scifin.model.Transaction
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import app.scifin.engine.Income

/** §5's engines, by rule, then in parity with the prototype's seeded figures. */
class ReportsTest {
    private val today = LocalDate(2026, 9, 14)
    private val t0 = Instant.parse("2026-01-01T10:00:00Z")
    private fun tx(id: String, key: String, amount: Double, date: LocalDate, income: Boolean = false) =
        Transaction(id, "p-1", date, t0, null, moneyOf(amount), key, "x", income)

    // ---- §5.1 ------------------------------------------------------------------------

    @Test fun `age of money is the age of the oldest lot still holding money, FIFO`() {
        val d = profileWith(savings = 150.0).copy(transactions = listOf(
            tx("a", "savings", 100.0, LocalDate(2026, 6, 1)),
            tx("b", "savings", 100.0, LocalDate(2026, 8, 1)),
            tx("c", "cat:x", 50.0, LocalDate(2026, 8, 10)),        // consumes 50 of the June lot
        ))
        assertEquals(LocalDate(2026, 6, 1).daysTo(today), Reports.ageOfMoney(d, today))
        val d2 = d.copy(transactions = d.transactions + tx("e", "cat:x", 60.0, LocalDate(2026, 9, 1)))   // June lot gone
        assertEquals(LocalDate(2026, 8, 1).daysTo(today), Reports.ageOfMoney(d2, today))
        assertEquals(0, Reports.ageOfMoney(profileWith(), today))
    }

    @Test fun `an overshoot is carried as a deficit the next deposit absorbs - section 5-1's worked example`() {
        // €100 in, €350 out (a €250 overshoot), €1,000 in, €400 out: the lots hold €350, as Savings does
        val base = listOf(
            tx("a", "savings", 100.0, LocalDate(2026, 6, 1)),
            tx("b", "cat:x", 350.0, LocalDate(2026, 6, 10)),
        )
        val negative = profileWith(savings = -250.0).copy(transactions = base)
        assertEquals(0, Reports.ageOfMoney(negative, today))                                   // Savings ≤ 0: no oldest euro to date
        val d = profileWith(savings = 350.0).copy(transactions = base + listOf(
            tx("c", "savings", 1000.0, LocalDate(2026, 7, 1)),
            tx("e", "cat:x", 400.0, LocalDate(2026, 8, 1)),
        ))
        assertEquals(LocalDate(2026, 7, 1).daysTo(today), Reports.ageOfMoney(d, today))        // the July lot, €350 of it, not a €1,000 one
        val emptied = d.copy(transactions = d.transactions + tx("f", "cat:x", 351.0, LocalDate(2026, 9, 1)))
        assertEquals(0, Reports.ageOfMoney(emptied, today))                                    // €1 below zero reads 0, not the July date
    }

    @Test fun `each trend point is as of its month's final day, the last one live`() {
        val d = profileWith(savings = 100.0).copy(transactions = listOf(tx("a", "savings", 100.0, LocalDate(2026, 8, 20))))
        val pts = Reports.ageOfMoneyTrend(d, today, 2)
        assertEquals(LocalDate(2026, 8, 20).daysTo(LocalDate(2026, 8, 31)).toLong(), pts[0].value)   // not 0, which the same-day reading gave
        assertEquals(LocalDate(2026, 8, 20).daysTo(today).toLong(), pts[1].value)
    }

    @Test fun `months before the opening lot are left out, and a zero after it is kept`() {
        // opening lot in July; August overspends it to below zero; the live month is back above
        val d = profileWith(savings = 100.0).copy(transactions = listOf(
            tx("a", "savings", 300.0, LocalDate(2026, 7, 10)),
            tx("b", "cat:x", 400.0, LocalDate(2026, 8, 5)),
            tx("c", "savings", 200.0, LocalDate(2026, 9, 1)),
        ))
        val pts = Reports.ageOfMoneyTrend(d, today, 6)
        assertEquals(listOf(7, 8, 9), pts.map { it.month.month.number })     // April-June omitted, not drawn as 0
        assertEquals(0L, pts[1].value)                                        // August's deficit is a real zero and stays
    }

    @Test fun `account rows never open or close a lot`() {
        val d = profileWith().copy(transactions = listOf(tx("a", "acct:c", 500.0, LocalDate(2026, 6, 1))))
        assertEquals(0, Reports.ageOfMoney(d, today))
    }

    @Test fun `the prospective window is the trailing three complete months`() {
        val w = Reports.trailingSpendWindow(today)
        assertEquals(LocalDate(2026, 6, 1), w.start); assertEquals(LocalDate(2026, 9, 1), w.end); assertEquals(92, w.days)
    }

    @Test fun `coverage is null without three complete months, null Savings, or zero outflow, and floors at zero`() {
        assertNull(Reports.coverageDaysOrNull(profileWith(savings = null), today))
        val young = profileWith(savings = 1000.0).copy(transactions = listOf(tx("a", "cat:x", 10.0, LocalDate(2026, 7, 1))))
        assertNull(Reports.coverageDaysOrNull(young, today))
        val noOut = profileWith(savings = 1000.0).copy(transactions = listOf(tx("a", "savings", 10.0, LocalDate(2026, 5, 1))))
        assertNull(Reports.coverageDaysOrNull(noOut, today))
        val ok = profileWith(savings = 920.0).copy(transactions = listOf(tx("a", "savings", 10.0, LocalDate(2026, 5, 1)), tx("b", "cat:x", 920.0, LocalDate(2026, 7, 1))))
        assertEquals(92, Reports.coverageDaysOrNull(ok, today))        // 920 over 92 days = 10/day; 920 Savings = 92 days
        assertEquals(0, Reports.coverageDaysOrNull(ok.copy(savings = ok.savings.copy(amount = moneyOf(-5.0))), today))
    }

    // ---- §5.2 ------------------------------------------------------------------------

    @Test fun `goals are one combined slice, income is no slice, uncategorised is Savings-decreasing savings rows`() {
        val d = profileWith(goals = listOf(goal("g1", "A"), goal("g2", "B")), categories = listOf(category("c", "Food"))).copy(transactions = listOf(
            tx("1", "goal:g1", 100.0, today), tx("2", "goal:g2", 50.0, today), tx("3", "cat:c", 30.0, today),
            tx("4", "savings", 1450.0, today, income = true), tx("5", "savings", -20.0, today), tx("6", "acct:x", 99.0, today),
            tx("7", "cat:c", 5.0, LocalDate(2026, 8, 30)),      // last month: excluded
        ))
        val s = Reports.spendingComposition(d, today)
        assertEquals(listOf("goals" to 15000L, "cat:c" to 3000L, "uncat" to 2000L), s.slices.map { it.key to it.amount })
        assertEquals(moneyOf(200.0), s.net); assertEquals(false, s.plainList)
    }

    @Test fun `a slice that nets negative or a net of zero falls back to a plain list`() {
        val d = profileWith(categories = listOf(category("c", "Food"))).copy(transactions = listOf(tx("1", "cat:c", 30.0, today), tx("2", "cat:c", -50.0, today)))
        assertTrue(Reports.spendingComposition(d, today).plainList)
        assertTrue(Reports.spendingComposition(profileWith(), today).plainList)
    }

    // ---- §5.3 / §5.4 ------------------------------------------------------------------

    @Test fun `savings vs owed subtracts every account balance and its history replays both series`() {
        val d = Fixture.profile
        assertEquals(d.savingsAmount - d.accounts.sumOf { it.balance }, Reports.savingsVsOwedNow(d))
        val trend = Reports.savingsVsOwedTrend(d, Fixture.simDate)
        assertEquals(6, trend.size)
        assertEquals(Reports.savingsVsOwedNow(d), trend.last().value)
    }

    @Test fun `income vs expenses is absent with income off and counts Goal money once, when it is spent`() {
        assertNull(Reports.incomeVsExpenses(profileWith(), today))
        val clock = FakeClock(today)
        var d = profileWith(savings = 1000.0, goals = listOf(goal("g", "G")), categories = listOf(category("c", "Food")))
        d = d.copy(settings = d.settings.copy(incomeEnabled = true))
        d = Writes.savingsQuickAdd(d, moneyOf(1450.0), "Salary", true, clock, "t1")
        d = Writes.goalQuickAdd(d, "g", moneyOf(200.0), null, clock, "t2")
        d = Writes.categoryQuickAdd(d, "c", moneyOf(30.0), null, clock, "t3")
        val pts = Reports.incomeVsExpenses(d, today)!!
        assertEquals(6, pts.size); assertEquals(moneyOf(1450.0), pts.last().income); assertEquals(moneyOf(30.0), pts.last().expenses)
        d = Writes.goalSpend(d, "g", moneyOf(80.0), "Hotel", clock, "t4")
        assertEquals(moneyOf(110.0), Reports.incomeVsExpenses(d, today)!!.last().expenses)       // the contribution still isn't
    }

    // ---- parity with the prototype's seeded figures -------------------------------------

    @Test fun `parity - age of money, its trend, coverage, spending, savings vs owed`() {
        val d = Fixture.profile; val day = Fixture.simDate; val f = Fixture.figures
        // Age of Money is NOT the prototype's (audit 2026-09-25): its seeded ledger dips to −€1,045,
        // and the prototype drops the overshoot, so its lots hold €4,372 against a balance of €3,327
        // -- §5.1's invariant broken. Carrying the deficit restores lots = balance and gives 42, not
        // 59; its trend also read same-day, not month-end. These values come from an independent
        // replay of the fixture (the audit's aom.py), never from the engine under test.
        assertEquals(59, f["ageOfMoney"]!!.jsonPrimitive.intOrNull)                                     // the drift, pinned so a re-export shows up
        assertEquals(42, Reports.ageOfMoney(d, day))
        assertEquals(listOf(13L, 28L, 27L, 44L, 45L, 42L), Reports.ageOfMoneyTrend(d, day).map { it.value })
        assertEquals(f["buffer"]!!.jsonPrimitive.intOrNull, Reports.coverageDaysOrNull(d, day))
        val sp = f["spending"]!!.jsonObject
        val mine = Reports.spendingComposition(d, day)
        assertEquals(moneyOf(sp["net"]!!.jsonPrimitive.doubleOrNull!!), mine.net)
        assertEquals(sp["rows"]!!.jsonArray.map { it.jsonObject["label"]!!.jsonPrimitive.content to moneyOf(it.jsonObject["amt"]!!.jsonPrimitive.doubleOrNull!!) },
            mine.slices.map { it.label to it.amount })
        assertEquals(moneyOf(f["savingsVsOwedNow"]!!.jsonPrimitive.doubleOrNull!!), Reports.savingsVsOwedNow(d))
        assertEquals(f["savingsVsOwedTrend"]!!.jsonArray.map { moneyOf(it.jsonPrimitive.doubleOrNull!!) }, Reports.savingsVsOwedTrend(d, day).map { it.value })
        assertEquals(moneyOf(f["incomeReceivedThisMonth"]!!.jsonPrimitive.doubleOrNull!!), Income.receivedThisMonth(d, day))
    }
}
