package app.scifin

import app.scifin.engine.IMPORT_REMINDER_KEY
import app.scifin.engine.NOTIFY_HOUR
import app.scifin.engine.Recurring
import app.scifin.engine.Reminders
import app.scifin.engine.moneyText
import app.scifin.model.Cadence
import app.scifin.model.DateMode
import app.scifin.model.RecurringExpense
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §3.11's reminders and §4.3's import reminder, as the platform seam receives them. */
class RemindersTest {

    private fun rent(due: LocalDate, lead: Int = 3, enabled: Boolean = true, finished: LocalDate? = null) =
        RecurringExpense("rent", "p-1", "Rent", null, moneyOf(950.0), Cadence.MONTHLY, firstDueDate = LocalDate(2026, 1, 31), dueDate = due,
            dueDateAnchorDay = 31, dateMode = DateMode.FIXED_DATE, reminderEnabled = enabled, reminderLeadDays = lead, finishedAt = finished)

    @Test fun `the lead is counted back from the live anchor, so the schedule follows the chain`() {
        // A 31st-anchored rent fires on 25 February and returns to 28 March.
        val d = profileWith().copy(recurringExpenses = listOf(rent(LocalDate(2026, 1, 31))))
        val feb = Recurring.advanceDueDates(d, LocalDate(2026, 2, 1))
        assertEquals(LocalDate(2026, 2, 25), Reminders.schedule(feb).single().fireDate)
        val mar = Recurring.advanceDueDates(d, LocalDate(2026, 3, 1))
        assertEquals(LocalDate(2026, 3, 28), Reminders.schedule(mar).single().fireDate)
    }

    @Test fun `off by default, and finished expenses never remind`() {
        assertNull(Reminders.fireDate(rent(LocalDate(2026, 9, 30), enabled = false)))
        assertNull(Reminders.fireDate(rent(LocalDate(2026, 9, 30), finished = LocalDate(2026, 9, 1))))
    }

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDate(y, m, d).atTime(h, min)

    @Test fun `registrations carry a composed body, and the fire time is the only guard`() {
        val d = profileWith().copy(recurringExpenses = listOf(rent(LocalDate(2026, 9, 30))))
        val regs = Reminders.registrations(d, at(2026, 9, 14, 12))
        assertEquals(1, regs.size)
        assertEquals("Rent", regs[0].title)
        assertEquals("€950 · due Sep 30", regs[0].body)
        assertEquals(NOTIFY_HOUR, regs[0].fireAt.hour)
        assertEquals(LocalDate(2026, 9, 27), regs[0].fireAt.date)
        // audit 2026-09-25: a sync at 08:30 on the fire day keeps that day's 09:00 reminder...
        assertEquals(1, Reminders.registrations(d, at(2026, 9, 27, 8, 30)).size)
        // ...and once 09:00 has passed it is gone, so no re-sync or reboot can fire it twice
        assertTrue(Reminders.registrations(d, at(2026, 9, 27, 9, 0)).isEmpty())
        // the advanced anchor re-arms it by itself
        val advanced = Recurring.advanceDueDates(d, LocalDate(2026, 10, 1))
        assertEquals(LocalDate(2026, 10, 28), Reminders.registrations(advanced, at(2026, 10, 1, 12)).single().fireAt.date)
    }

    @Test fun `the New Month ping is this month's 1st until its 09 00 has passed`() {
        assertEquals(at(2026, 10, 1, 9), Reminders.nextNewMonthFire(at(2026, 10, 1, 8, 30)))
        assertEquals(at(2026, 11, 1, 9), Reminders.nextNewMonthFire(at(2026, 10, 1, 9, 0)))
        assertEquals(at(2026, 11, 1, 9), Reminders.nextNewMonthFire(at(2026, 10, 14, 12)))
        assertEquals(at(2027, 1, 1, 9), Reminders.nextNewMonthFire(at(2026, 12, 31, 23)))
    }

    @Test fun `the import reminder is opt-in, on its day, keyed apart from expenses`() {
        val off = profileWith()
        assertTrue(Reminders.registrations(off, at(2026, 9, 14, 12)).none { it.expenseId == IMPORT_REMINDER_KEY })
        val on = off.copy(importState = off.importState.copy(importReminderEnabled = true, importReminderDay = 1))
        val r = Reminders.registrations(on, at(2026, 9, 14, 12)).single { it.expenseId == IMPORT_REMINDER_KEY }
        assertEquals(LocalDate(2026, 10, 1), r.fireAt.date)
        assertEquals(Reminders.IMPORT_REMINDER_BODY, r.body)
    }

    @Test fun `money text for notification bodies`() {
        assertEquals("€1,234.50", moneyText(moneyOf(1234.5), "EUR"))
        assertEquals("€45", moneyText(moneyOf(45.0), "EUR"))
        assertEquals("−€12.34", moneyText(moneyOf(-12.34), "EUR"))
        assertEquals("CHF 9.99", moneyText(moneyOf(9.99), "CHF"))
    }
}
