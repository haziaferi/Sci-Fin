package app.scifin

import app.scifin.engine.Threshold
import app.scifin.engine.Writes
import app.scifin.model.Cadence
import app.scifin.model.DateMode
import app.scifin.model.RecurringExpense
import app.scifin.model.Savings
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §3.5, rule by rule. The note was a display-side function in the prototype; here it is the engine's, so the sentence and the test read one figure. */
class ThresholdTest {
    private val today = LocalDate(2026, 9, 14)
    private val clock = FakeClock(today)
    private fun rent(amount: Double) = RecurringExpense("r", "p-1", "Rent", null, moneyOf(amount), Cadence.MONTHLY, firstDueDate = LocalDate(2026, 1, 5), dueDate = LocalDate(2026, 10, 5), dueDateAnchorDay = 5, dateMode = DateMode.FIXED_DATE)

    @Test fun `no income - flags past a twelfth of Savings, fires on any total at Savings at or below zero, never at null`() {
        val d = profileWith(savings = 12000.0).copy(recurringExpenses = listOf(rent(900.0)))
        assertNull(Threshold.recurringNote(d, today))                                                   // 900 ≤ 1,000
        val n = Threshold.recurringNote(d.copy(recurringExpenses = listOf(rent(1100.0))), today)!!
        assertEquals(moneyOf(1100.0), n.scheduled); assertEquals(moneyOf(12000.0), n.base); assertEquals(false, n.incomeOn)
        val broke = Threshold.recurringNote(d.copy(savings = Savings("p-1", moneyOf(-5.0))), today)!!
        assertTrue(broke.baseIsEmpty)
        assertNull(Threshold.recurringNote(d.copy(savings = Savings("p-1", null)), today))               // §3.1: no figure to take a twelfth of
        assertNull(Threshold.recurringNote(profileWith(savings = 0.0), today))                          // nothing scheduled
    }

    @Test fun `income on - flags past a third of income received this month, and at a zero base states the figure`() {
        var d = profileWith(savings = 50000.0).copy(recurringExpenses = listOf(rent(900.0)))
        d = d.copy(settings = d.settings.copy(incomeEnabled = true, incomeEnabledDate = LocalDate(2026, 9, 1)))
        val early = Threshold.recurringNote(d, today)!!                                                   // nothing landed yet: fires, base empty
        assertTrue(early.incomeOn); assertTrue(early.baseIsEmpty)
        d = Writes.savingsQuickAdd(d, moneyOf(1450.0), "pay", true, clock, "t1")
        val n = Threshold.recurringNote(d, today)!!                                                       // 900 > 483
        assertEquals(moneyOf(1450.0), n.base)
        d = Writes.savingsQuickAdd(d, moneyOf(1450.0), "pay", true, clock, "t2")
        assertNull(Threshold.recurringNote(d, today))                                                     // 900 ≤ 966: Savings is not consulted
    }
}
