package app.scifin.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/*
 * Calendar-day arithmetic on §12's `date` type. Everything here is timezone-free by
 * construction: a LocalDate has no zone to re-interpret, which is the property §12's "types
 * of time" rule exists for.
 */

/** `YYYY-MM`, the key §3.9 and §3.11 use for "this month" markers. */
fun LocalDate.ymKey(): String = toString().substring(0, 7)

fun LocalDate.plusDays(n: Int): LocalDate = plus(n, DateTimeUnit.DAY)
fun LocalDate.minusDays(n: Int): LocalDate = minus(n, DateTimeUnit.DAY)

/** Whole days from this to [other]; negative when [other] is earlier. */
fun LocalDate.daysTo(other: LocalDate): Int = this.daysUntil(other)

fun lastDayOfMonth(year: Int, month: Int): Int =
    LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minusDays(1).day

/** The prototype's `dateWithDay`: a day-of-month clamped to the month's length. */
fun dateWithDay(year: Int, month: Int, day: Int): LocalDate =
    LocalDate(year, month, minOf(day, lastDayOfMonth(year, month)))

/**
 * Month arithmetic that CLAMPS rather than overflows: 31 Jan + 1 month is 28/29 Feb, not
 * 2/3 March. The prototype's `addMonths` used JS `setMonth`, which overflows; §3.11's
 * `dueDateAnchorDay` exists precisely so a clamped date never becomes the new anchor.
 */
fun LocalDate.plusMonthsClamped(n: Int, anchorDay: Int = day): LocalDate {
    val shifted = LocalDate(year, month, 1).plus(n, DateTimeUnit.MONTH)
    return dateWithDay(shifted.year, shifted.month.number, anchorDay)
}

fun LocalDate.firstOfMonth(): LocalDate = LocalDate(year, month, 1)
fun LocalDate.firstOfNextMonth(): LocalDate = firstOfMonth().plus(1, DateTimeUnit.MONTH)
