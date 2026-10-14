package app.scifin.engine

import app.scifin.model.Goal
import app.scifin.model.Money
import app.scifin.model.ProfileData
import app.scifin.model.ceilDiv
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.math.roundToLong

/*
 * §6 -- the Goal calculator's arithmetic (§6.1), the feasibility frame (§6.2), no-target mode
 * (§6.3) and the sustainability check (§6.4). `months` and `monthly` are DERIVED (§12):
 * re-derived whenever `saved`, `target` or `targetDate` changes, never left standing after a
 * quick-add moves `saved` underneath them.
 *
 * "Scheduled recurring total" is an input here rather than computed here: it is §3.11's
 * occurrence math (Recurring.kt), and taking it as a parameter keeps this file testable on its
 * own.
 */

/** §6.1's derived pair. `months` is null when there is nothing to compute (§6.4's own test). */
data class GoalDerivation(val months: Int?, val monthly: Money?)

/** §6.2's frame, always present once a monthly figure exists. */
sealed class GoalFrame {
    abstract val monthly: Money
    abstract val fits: Boolean
    /** Income on: `available` = income received this month − scheduled recurring − other goals. */
    data class IncomeOn(override val monthly: Money, val available: Money, override val fits: Boolean) : GoalFrame()
    /** Income off: `committed` = recurring + other goals + this goal, read against Savings. */
    data class IncomeOff(override val monthly: Money, val committed: Money, val savings: Money, override val fits: Boolean) : GoalFrame()
}

/** §6.4: null when the rate is within the threshold; otherwise three gentler suggestions,
 *  or none at all when the base is zero or negative (the numbers would be 0 or negative). */
data class Sustainability(val incomeOn: Boolean, val suggestions: List<Money>?)

object Goals {

    /** §6.1: calendar months from the current month to the target month, never below 1. */
    fun calendarMonthsUntil(today: LocalDate, target: LocalDate): Int =
        maxOf(1, (target.year - today.year) * 12 + (target.month.number - today.month.number))

    /**
     * §6.1's three rules: the amount divided is `target − saved`; months are calendar months
     * from the stored `targetDate`; by-date rounds the monthly figure UP to the minor unit, and
     * by-amount rounds the timeline UP to the next whole month.
     */
    fun derive(g: Goal, today: LocalDate): GoalDerivation {
        val target = g.target ?: return GoalDerivation(null, null)          // §6.3: nothing to divide
        val remaining = maxOf(0L, target - g.saved)
        g.targetDate?.let { date ->
            val months = calendarMonthsUntil(today, date)
            return GoalDerivation(months, remaining.ceilDiv(months.toLong()))
        }
        val monthly = g.monthly
        if (monthly != null && monthly > 0) {
            return GoalDerivation(remaining.ceilDiv(monthly).toInt(), monthly)
        }
        return GoalDerivation(null, null)
    }

    /** Applies [derive] to the stored `monthly`; `months` is never stored (§12). */
    fun rederive(g: Goal, today: LocalDate): Goal = g.copy(monthly = derive(g, today).monthly)

    /** §6.2: a commitment ends once `saved >= target`; the Goal itself stays open. */
    fun commitmentEnded(g: Goal): Boolean = g.target != null && g.saved >= g.target

    /** §6.2: every other goal's forward commitment; reached goals and rateless goals count 0. */
    fun otherGoalsMonthlyTotal(d: ProfileData, excludeId: String?, today: LocalDate): Money =
        d.goals.filter { it.id != excludeId && it.target != null && !commitmentEnded(it) }
            .sumOf { derive(it, today).monthly ?: 0 }

    /** §6.2's frame. Income on reads the landed figure, never a projection. */
    fun frame(d: ProfileData, monthly: Money, excludeId: String?, scheduledRecurring: Money, today: LocalDate): GoalFrame {
        val others = otherGoalsMonthlyTotal(d, excludeId, today)
        return if (d.settings.incomeEnabled) {
            val available = Income.receivedThisMonth(d, today) - scheduledRecurring - others
            GoalFrame.IncomeOn(monthly, available, monthly <= available)
        } else {
            val committed = scheduledRecurring + others + monthly
            GoalFrame.IncomeOff(monthly, committed, d.savingsAmount, committed <= d.savingsAmount)
        }
    }

    /**
     * §6.4: the threshold base is income when Income is on (1/6) and Savings when it is not
     * (1/12) -- keyed to real money, not to the toggle. Suggestions are 1/14, 1/12, 1/6 of
     * income or 1/20, 1/16, 1/12 of Savings, rounded to the major unit as the prototype shows.
     */
    fun sustainability(d: ProfileData, monthly: Money, today: LocalDate): Sustainability? {
        val incomeOn = d.settings.incomeEnabled
        val base = if (incomeOn) Income.receivedThisMonth(d, today) else d.savingsAmount
        val threshold = if (incomeOn) base / 6 else base / 12
        if (monthly <= threshold) return null
        if (base <= 0) return Sustainability(incomeOn, null)
        val fracs = if (incomeOn) listOf(14, 12, 6) else listOf(20, 16, 12)
        return Sustainability(incomeOn, fracs.map { ((base.toDouble() / it) / 100.0).roundToLong() * 100 })
    }
}
