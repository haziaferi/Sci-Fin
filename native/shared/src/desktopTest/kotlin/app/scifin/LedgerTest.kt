@file:OptIn(ExperimentalTime::class)

package app.scifin

import app.scifin.engine.Ledger
import app.scifin.engine.Movement
import app.scifin.engine.TransactionKey
import app.scifin.engine.Writes
import app.scifin.engine.savingsAmount
import app.scifin.model.Transaction
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** §3.6's sign table, row by row, and §7.3's pair-deletion conditions, one test each. */
class LedgerTest {
    private val day = LocalDate(2026, 9, 14)

    private fun tx(id: String, key: String, amount: Double, at: Instant, date: LocalDate = day, payee: String? = null, isIncome: Boolean = false) =
        Transaction(id, "p-1", date, at, payee, moneyOf(amount), key, "x", isIncome)

    // ---- §3.6 sign table ------------------------------------------------------------

    @Test fun `savings row moves Savings by +amount`() {
        assertEquals(moneyOf(20.0), Ledger.savingsEffect(tx("a", "savings", 20.0, Instant.DISTANT_PAST)))
        assertEquals(moneyOf(-20.0), Ledger.savingsEffect(tx("a", "savings", -20.0, Instant.DISTANT_PAST)))
    }

    @Test fun `goal row moves Savings by -amount`() {
        assertEquals(moneyOf(-20.0), Ledger.savingsEffect(tx("a", "goal:g", 20.0, Instant.DISTANT_PAST)))
    }

    @Test fun `category row moves Savings by -amount`() {
        assertEquals(moneyOf(-20.0), Ledger.savingsEffect(tx("a", "cat:c", 20.0, Instant.DISTANT_PAST)))
    }

    @Test fun `sweep row of +100 moves Savings -100 and its reversal of -100 moves +100`() {
        assertEquals(moneyOf(-100.0), Ledger.savingsEffect(tx("a", "rec:e", 100.0, Instant.DISTANT_PAST)))
        assertEquals(moneyOf(100.0), Ledger.savingsEffect(tx("b", "rec:e", -100.0, Instant.DISTANT_PAST)))
    }

    @Test fun `account row never moves Savings (§7_1)`() {
        assertEquals(0L, Ledger.savingsEffect(tx("a", "acct:x", 500.0, Instant.DISTANT_PAST)))
    }

    @Test fun `a key that is not in the table is refused`() {
        assertFailsWith<IllegalArgumentException> { TransactionKey.parse("wallet:1") }
        assertFailsWith<IllegalArgumentException> { TransactionKey.parse("goal") }
    }

    // ---- logAndMove ----------------------------------------------------------------

    @Test fun `a €20 category quick-add stores +20 on cat and moves Savings -20`() {
        val clock = FakeClock(day)
        val d0 = profileWith(savings = 100.0, categories = listOf(category("c1", "Groceries")))
        val d1 = Writes.categoryQuickAdd(d0, "c1", moneyOf(20.0), null, clock, "t1")
        assertEquals(moneyOf(80.0), d1.savings.amount)
        assertEquals(moneyOf(20.0), d1.categories[0].spent)
        val row = d1.transactions.single()
        assertEquals("cat:c1", row.key); assertEquals(moneyOf(20.0), row.amount); assertEquals("Groceries", row.payee)
    }

    @Test fun `any movement ends the null Savings state, treating the start as zero (§3_1)`() {
        val clock = FakeClock(day)
        val d0 = profileWith(savings = null, goals = listOf(goal("g1", "Trip")))
        assertNull(d0.savings.amount)
        val d1 = Writes.goalQuickAdd(d0, "g1", moneyOf(50.0), null, clock, "t1")
        assertEquals(moneyOf(-50.0), d1.savings.amount)      // Savings can go negative (§3.1)
        assertEquals("Toward Trip", d1.transactions.single().payee)
    }

    @Test fun `an account edit is logged but Savings does not move`() {
        val clock = FakeClock(day)
        val d0 = profileWith(savings = 100.0)
        val d1 = Ledger.logAccountEdit(d0, "a1", "Card", moneyOf(45.0), "Charge", clock, "t1")
        assertEquals(moneyOf(100.0), d1.savings.amount)
        assertEquals(1, d1.transactions.size)
    }

    @Test fun `income quick-add tags the row and marks lastIncomeLoggedDate with the row's date`() {
        val clock = FakeClock(day)
        val d1 = Writes.savingsQuickAdd(profileWith(), moneyOf(1450.0), "Salary", true, clock, "t1", date = LocalDate(2026, 9, 1))
        assertTrue(d1.transactions.single().isIncome)
        assertEquals(LocalDate(2026, 9, 1), d1.settings.lastIncomeLoggedDate)
        assertEquals(moneyOf(2450.0), d1.savings.amount)
    }

    @Test fun `the entered date governs the row while the figure moves now (§3_6)`() {
        val clock = FakeClock(day)
        val d1 = Writes.savingsQuickAdd(profileWith(), moneyOf(-10.0), null, false, clock, "t1", date = LocalDate(2026, 8, 2))
        assertEquals(LocalDate(2026, 8, 2), d1.transactions.single().date)
        assertEquals(clock.now(), d1.transactions.single().loggedAt)
    }

    // ---- direct edits (§3.6): the editor is a second door into the same mechanism ----

    @Test fun `direct edit logs the signed delta and moves Savings by the inverse`() {
        val clock = FakeClock(day)
        val d0 = profileWith(savings = 100.0, goals = listOf(goal("g1", "Trip", saved = 40.0)))
        val d1 = Writes.goalSavedEdit(d0, "g1", moneyOf(25.0), null, clock, "t1")
        assertEquals(moneyOf(25.0), d1.goals[0].saved)
        assertEquals(moneyOf(115.0), d1.savings.amount)             // -(-15) = +15
        assertEquals(moneyOf(-15.0), d1.transactions.single().amount)
        assertEquals(Writes.CORRECTION, d1.transactions.single().payee)
    }

    @Test fun `a relabelling correction is Savings-neutral across its two actions`() {
        val clock = FakeClock(day)
        val d0 = profileWith(savings = 100.0, categories = listOf(category("c1", "Dining", spent = 20.0), category("c2", "Groceries")))
        val d1 = Writes.categorySpentEdit(d0, "c1", 0, null, clock, "t1")
        val d2 = Writes.categoryQuickAdd(d1, "c2", moneyOf(20.0), null, clock, "t2")
        assertEquals(moneyOf(100.0), d2.savings.amount)
    }

    @Test fun `savings direct edit logs the delta not the absolute value`() {
        val clock = FakeClock(day)
        val d1 = Writes.savingsDirectEdit(profileWith(savings = 100.0), moneyOf(130.0), null, clock, "t1")
        assertEquals(moneyOf(130.0), d1.savings.amount)
        assertEquals(moneyOf(30.0), d1.transactions.single().amount)
        assertFalse(d1.transactions.single().isIncome)
        val d2 = Writes.savingsDirectEdit(profileWith(savings = null), 0, null, clock, "t2")
        assertEquals(0L, d2.savings.amount); assertEquals(0, d2.transactions.size)   // ends null, logs nothing
    }

    // ---- §7.3 pair deletion ---------------------------------------------------------

    private val t0 = Instant.parse("2026-09-14T10:00:00Z")

    @Test fun `condition 1 - only an exact opposite on the same key pairs`() {
        val rows = listOf(tx("a", "cat:c", 40.0, t0), tx("b", "cat:c", -40.0, t0 + 1.hours), tx("c", "cat:d", -40.0, t0 + 2.hours))
        val p = Ledger.pairEligibility(rows)
        assertEquals("b", p["a"]); assertEquals("a", p["b"]); assertNull(p["c"])
        assertTrue(Ledger.pairEligibility(listOf(tx("a", "cat:c", 40.0, t0), tx("b", "cat:c", -39.0, t0 + 1.hours))).isEmpty())
    }

    @Test fun `condition 2 - immediately following in loggedAt order, not date order`() {
        // Backdated three weeks by `date`, but logged BETWEEN the pair in real time: it breaks the pair.
        val rows = listOf(
            tx("a", "savings", -40.0, t0),
            tx("x", "savings", 5.0, t0 + 30.minutes, date = LocalDate(2026, 8, 20)),
            tx("b", "savings", 40.0, t0 + 1.hours),
        )
        assertTrue(Ledger.pairEligibility(rows).isEmpty())
    }

    @Test fun `condition 3 - forward only and one pair per row`() {
        // a(+40) b(-40) c(+40): a-b pairs, c is left alone rather than pairing with b again.
        val rows = listOf(tx("a", "cat:c", 40.0, t0), tx("b", "cat:c", -40.0, t0 + 1.hours), tx("c", "cat:c", 40.0, t0 + 2.hours))
        val p = Ledger.pairEligibility(rows)
        assertEquals("b", p["a"]); assertNull(p["c"])
    }

    @Test fun `condition 4 - both within 48 hours on loggedAt`() {
        assertTrue(Ledger.pairEligibility(listOf(tx("a", "cat:c", 40.0, t0), tx("b", "cat:c", -40.0, t0 + 48.hours))).isNotEmpty())
        assertTrue(Ledger.pairEligibility(listOf(tx("a", "cat:c", 40.0, t0), tx("b", "cat:c", -40.0, t0 + 48.hours + 1.minutes))).isEmpty())
    }

    @Test fun `condition 5 - sweep rows are never eligible`() {
        assertTrue(Ledger.pairEligibility(listOf(tx("a", "rec:e", 100.0, t0), tx("b", "rec:e", -100.0, t0 + 1.hours))).isEmpty())
    }

    @Test fun `a zero row never pairs`() {
        assertTrue(Ledger.pairEligibility(listOf(tx("a", "cat:c", 0.0, t0), tx("b", "cat:c", 0.0, t0 + 1.hours))).isEmpty())
    }

    @Test fun `deleting a pair removes both rows and recomputes the income marks from survivors`() {
        val d0 = profileWith(savings = 100.0).let {
            it.copy(
                transactions = listOf(
                    tx("i1", "savings", 1000.0, t0 - 72.hours, date = LocalDate(2026, 9, 1), payee = Ledger.AUTO_DEPOSIT_PAYEE, isIncome = true),
                    tx("i2", "savings", 50.0, t0 - 24.hours, date = LocalDate(2026, 9, 13), payee = "Gift", isIncome = true),
                    tx("m", "savings", 50.0, t0, date = LocalDate(2026, 9, 14), payee = "Gift", isIncome = true),
                    tx("c", "savings", -50.0, t0 + 1.hours, date = LocalDate(2026, 9, 14)),
                ),
                settings = it.settings.copy(incomeEnabled = true, lastIncomeLoggedDate = LocalDate(2026, 9, 14), lastIncomeDepositDate = LocalDate(2026, 9, 1)),
            )
        }
        val p = Ledger.pairEligibility(d0.transactions)
        assertEquals("c", p["m"])
        val d1 = Ledger.deletePair(d0, "m", "c")
        assertEquals(listOf("i1", "i2"), d1.transactions.map { it.id })
        assertEquals(LocalDate(2026, 9, 13), d1.settings.lastIncomeLoggedDate)
        assertEquals(LocalDate(2026, 9, 1), d1.settings.lastIncomeDepositDate)
        assertEquals(moneyOf(100.0), d1.savings.amount)     // the pair netted to zero; nothing else moves
    }

    @Test fun `savingsAmount reads null as zero for arithmetic only`() {
        assertEquals(0L, profileWith(savings = null).savingsAmount)
        assertNull(profileWith(savings = null).savings.amount)
    }
}
