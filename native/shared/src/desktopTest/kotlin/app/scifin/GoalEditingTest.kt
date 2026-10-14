package app.scifin

import app.scifin.engine.GoalDraft
import app.scifin.engine.GoalEditing
import app.scifin.engine.Goals
import app.scifin.engine.savingsAmount
import app.scifin.model.GoalMode
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §6.4's editor over the engine: what Save stores in each mode, and what "Already saved" and Delete do to Savings (§3.6). */
class GoalEditingTest {
    private val today = LocalDate(2026, 9, 14)
    private val clock = FakeClock(today)
    private val ids = Ids("g")

    @Test fun `by date stores the date and derives the monthly, rounding up`() {
        val draft = GoalDraft(name = "Trip", target = moneyOf(1200.0), saved = moneyOf(400.0), mode = GoalMode.DATE, months = 6).synced(GoalMode.DATE)
        assertEquals(moneyOf(133.34), draft.monthly)                                   // 800 / 6 = 133.333…, up to the minor unit
        val d = GoalEditing.save(profileWith(savings = 1000.0), draft, clock, ids::next)
        val g = d.goals.single()
        assertEquals(LocalDate(2027, 3, 14), g.targetDate); assertEquals(GoalMode.DATE, g.mode)
        assertEquals(6, Goals.derive(g, today).months)
        // "Already saved" on a new Goal is a starting balance: Savings moves by the inverse and it is logged
        assertEquals(moneyOf(400.0), g.saved); assertEquals(moneyOf(600.0), d.savingsAmount)
        assertEquals("Starting balance", d.transactions.single().payee)
    }

    @Test fun `a rename keeps the stored deadline, even a passed one, and its day`() {
        for (deadline in listOf(LocalDate(2026, 8, 1), LocalDate(2027, 1, 20))) {
            val g = app.scifin.model.Goal("g1", "p-1", "Trip", null, saved = moneyOf(100.0), target = moneyOf(1200.0), targetDate = deadline, mode = GoalMode.DATE)
            val d0 = profileWith().copy(goals = listOf(g))
            val d = GoalEditing.save(d0, GoalDraft.of(g, today).copy(name = "Holiday"), clock, ids::next)
            assertEquals(deadline, d.goals.single().targetDate, "a save that did not touch the months moved $deadline")
            // changing the months is what moves it
            val moved = GoalEditing.save(d0, GoalDraft.of(g, today).copy(months = 3).synced(GoalMode.DATE), clock, ids::next)
            assertEquals(LocalDate(2026, 12, 14), moved.goals.single().targetDate)
        }
    }

    @Test fun `by amount stores the rate, not a date, and the months round up`() {
        val draft = GoalDraft(name = "Laptop", target = moneyOf(1000.0), mode = GoalMode.AMOUNT, monthly = moneyOf(300.0)).synced(GoalMode.AMOUNT)
        assertEquals(4, draft.months)
        val g = GoalEditing.save(profileWith(), draft, clock, ids::next).goals.single()
        assertNull(g.targetDate); assertEquals(moneyOf(300.0), g.monthly); assertEquals(4, Goals.derive(g, today).months)
    }

    @Test fun `no-target stores nothing to divide, and an edit of saved logs a correction`() {
        var d = GoalEditing.save(profileWith(savings = 1000.0), GoalDraft(name = "Rainy day", noTarget = true), clock, ids::next)
        val g = d.goals.single()
        assertNull(g.target); assertNull(g.targetDate); assertNull(g.monthly); assertTrue(d.transactions.isEmpty())
        d = GoalEditing.save(d, GoalDraft.of(g, today).copy(saved = moneyOf(50.0), name = "Rainy day fund"), clock, ids::next)
        assertEquals("Rainy day fund", d.goals.single().name); assertEquals(moneyOf(50.0), d.goals.single().saved)
        assertEquals(moneyOf(950.0), d.savingsAmount); assertEquals("Correction", d.transactions.single().payee)
    }

    @Test fun `delete returns saved to Savings only on accept, and the history stays either way`() {
        val d0 = GoalEditing.save(profileWith(savings = 1000.0), GoalDraft(name = "Trip", target = moneyOf(500.0), saved = moneyOf(200.0)).synced(GoalMode.DATE), clock, ids::next)
        val id = d0.goals.single().id
        val kept = GoalEditing.delete(d0, id, returnSaved = false, clock, ids::next)
        assertTrue(kept.goals.isEmpty()); assertEquals(moneyOf(800.0), kept.savingsAmount); assertEquals(1, kept.transactions.size)
        val back = GoalEditing.delete(d0, id, returnSaved = true, clock, ids::next)
        assertTrue(back.goals.isEmpty()); assertEquals(moneyOf(1000.0), back.savingsAmount); assertEquals(2, back.transactions.size)
        assertEquals("Goal deleted — returned to Savings", back.transactions.last().payee)
    }

    @Test fun `a blank name saves nothing`() {
        val d = profileWith()
        assertEquals(d, GoalEditing.save(d, GoalDraft(name = "  "), clock, ids::next))
    }
}
