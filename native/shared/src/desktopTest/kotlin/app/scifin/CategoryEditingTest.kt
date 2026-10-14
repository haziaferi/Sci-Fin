package app.scifin

import app.scifin.engine.CategoryEditing
import app.scifin.engine.savingsAmount
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** §3.6's Category rules through the editor: a starting balance and a correction move Savings by the inverse; delete un-spends nothing. */
class CategoryEditingTest {
    private val clock = FakeClock(LocalDate(2026, 9, 14))
    private val ids = Ids("c")

    @Test fun `new with spent logs a starting balance, an edit of spent logs a correction, planned changes log nothing`() {
        var d = CategoryEditing.save(profileWith(savings = 1000.0), CategoryEditing.Draft(name = " Groceries ", planned = moneyOf(300.0), spent = moneyOf(40.0)), clock, ids::next)
        val c = d.categories.single()
        assertEquals("Groceries", c.name); assertEquals("2026-09", c.lastResetMonth); assertEquals(moneyOf(40.0), c.spent); assertEquals(moneyOf(960.0), d.savingsAmount)
        assertEquals("Starting balance", d.transactions.single().payee)
        d = CategoryEditing.save(d, CategoryEditing.Draft.of(c).copy(planned = moneyOf(350.0)), clock, ids::next)
        assertEquals(1, d.transactions.size); assertEquals(moneyOf(350.0), d.categories.single().planned)
        d = CategoryEditing.save(d, CategoryEditing.Draft.of(d.categories.single()).copy(spent = moneyOf(15.0)), clock, ids::next)   // a purchase mis-logged here
        assertEquals(moneyOf(985.0), d.savingsAmount); assertEquals("Correction", d.transactions.last().payee); assertEquals(moneyOf(-25.0), d.transactions.last().amount)
    }

    @Test fun `delete removes the label and writes no row`() {
        val d0 = CategoryEditing.save(profileWith(savings = 1000.0), CategoryEditing.Draft(name = "Food", spent = moneyOf(340.0)), clock, ids::next)
        val d = CategoryEditing.delete(d0, d0.categories.single().id)
        assertTrue(d.categories.isEmpty()); assertEquals(moneyOf(660.0), d.savingsAmount); assertEquals(1, d.transactions.size)
    }
}
