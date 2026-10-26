@file:OptIn(kotlin.time.ExperimentalTime::class)

package app.scifin.engine

import app.scifin.model.Money
import app.scifin.model.ProfileData
import app.scifin.model.RecurringExpense
import app.scifin.notify.ReminderRegistration
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/*
 * §3.11's due-date reminders and §4.3's import reminder, derived from state -- the list this
 * produces IS the argument the platform seam takes (NotificationScheduler.syncReminders).
 * The seam is declarative: hand over the whole schedule and the OS is made to match it, so
 * "rescheduled whenever dueDate advances" holds by construction.
 */

/** §12's single-sourced delivery hour: 09:00 device-local, a target inside the OS's window. */
const val NOTIFY_HOUR = 9

/** The reserved registration key for §4.3's import reminder; never a RecurringExpense id. */
const val IMPORT_REMINDER_KEY = "import:reminder"

/** The reserved registration key for §3.10's payday notification (owner, 2026-10-26). */
const val PAYDAY_REMINDER_KEY = "income:payday"

/** One armed due-date reminder, before it becomes a platform registration. */
data class ReminderEntry(val expenseId: String, val name: String, val amount: Money, val dueDate: LocalDate, val fireDate: LocalDate)

object Reminders {

    /** §3.11: the lead is counted back from `dueDate`, the live anchor; finished expenses never remind. */
    fun fireDate(e: RecurringExpense): LocalDate? {
        if (!e.reminderEnabled || e.finishedAt != null) return null
        return e.dueDate.minusDays(e.reminderLeadDays)
    }

    /** The whole schedule, sorted by fire date then name. */
    fun schedule(d: ProfileData): List<ReminderEntry> =
        d.recurringExpenses.mapNotNull { e -> fireDate(e)?.let { ReminderEntry(e.id, e.name, e.amount, e.dueDate, it) } }
            .sortedWith(compareBy({ it.fireDate }, { it.name }))

    /** §4.3's reminder day, on or after [from]: the `importReminderDay`-th of this or next month. */
    fun nextImportReminder(d: ProfileData, from: LocalDate): LocalDate? {
        if (!d.importState.importReminderEnabled) return null
        val day = d.importState.importReminderDay.coerceIn(1, 28)
        val thisMonth = dateWithDay(from.year, from.month.number, day)
        return if (thisMonth >= from) thisMonth else from.firstOfNextMonth().let { dateWithDay(it.year, it.month.number, day) }
    }

    /**
     * What the platform is asked to hold: every armed reminder whose FIRE TIME is still ahead of
     * [now], plus the import reminder. The instant is the whole once-per-occurrence guard: a
     * reminder is registered while its 09:00 is ahead and dropped once it has passed, so a
     * re-sync (reboot, open, write) never re-fires one, and a sync at 08:30 on the fire day keeps
     * it. Bodies are composed here, so no process has to be alive at delivery.
     */
    fun registrations(d: ProfileData, now: LocalDateTime): List<ReminderRegistration> {
        val at = LocalTime(NOTIFY_HOUR, 0)
        val out = ArrayList<ReminderRegistration>()
        for (r in schedule(d)) {
            val fireAt = r.fireDate.atTime(at)
            if (fireAt <= now) continue
            out.add(ReminderRegistration(
                expenseId = r.expenseId,
                title = r.name,
                body = "${moneyText(r.amount, d.settings.currency)} · due ${monthDay(r.dueDate)}",
                dueDate = r.dueDate,
                fireAt = fireAt,
            ))
        }
        val importDay = nextImportReminder(d, now.date)?.let { if (it.atTime(at) > now) it else nextImportReminder(d, now.date.plusDays(1)) }
        importDay?.let { day -> out.add(ReminderRegistration(IMPORT_REMINDER_KEY, "Sci-Fin", IMPORT_REMINDER_BODY, day, day.atTime(at))) }
        // §3.10: only while income is not recorded by itself -- hourly, or salaried without auto-deposit
        val s = d.settings
        if (Hourly.manualPayday(s) && s.paydayNotificationEnabled) {
            val next = Income.nextOccurrence(s, now.date)?.let { if (it.atTime(at) > now) it else Income.nextOccurrence(s, now.date.plusDays(1)) }
            next?.let { day -> out.add(ReminderRegistration(PAYDAY_REMINDER_KEY, "Sci-Fin", paydayBody(s), day, day.atTime(at))) }
        }
        return out
    }

    /** §3.9's New Month ping: this month's 1st at 09:00 while it is still ahead, else next month's. */
    fun nextNewMonthFire(now: LocalDateTime): LocalDateTime {
        val thisFirst = LocalDate(now.year, now.month, 1)
        val at = thisFirst.atTime(NOTIFY_HOUR, 0)
        return if (at > now) at else thisFirst.firstOfNextMonth().atTime(NOTIFY_HOUR, 0)
    }

    /** The wall-clock instant a sync compares against: the clock's own day, at the local time of day. */
    fun localNow(clock: Clock): LocalDateTime =
        clock.today().atTime(clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).time)

    const val IMPORT_REMINDER_BODY = "Ready to import last month’s statement?"

    fun paydayBody(s: app.scifin.model.Settings): String = if (Hourly.isHourly(s)) "Payday — confirm what arrived" else "Payday — log what arrived"
}
