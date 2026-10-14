package app.scifin.engine

import app.scifin.model.AccountType
import app.scifin.model.Cadence
import app.scifin.model.DateMode
import app.scifin.model.Money
import app.scifin.model.ProfileData
import app.scifin.model.RecurringExpense
import app.scifin.model.ceilDiv
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/*
 * §3.11 -- Recurring Expenses: the occurrence walk, the sweep amounts, the anchor chain,
 * apportioned shares, the four creation branches, edit-and-reconcile, and the deletion
 * reversal.
 */

object Recurring {

    /** §3.11/§12: weekly 7 and biweekly 14 always; 30/60/365 are interval mode's creation defaults; semimonthly has no interval. */
    fun defaultIntervalDays(c: Cadence): Int? = when (c) {
        Cadence.WEEKLY -> 7; Cadence.BIWEEKLY -> 14; Cadence.MONTHLY -> 30; Cadence.BIMONTHLY -> 60; Cadence.YEARLY -> 365
        Cadence.SEMIMONTHLY -> null
    }

    /** The sweeps in one full cycle: 2 for bimonthly, 12 for yearly, 1 otherwise. */
    fun cycleLength(c: Cadence): Int = when (c) { Cadence.BIMONTHLY -> 2; Cadence.YEARLY -> 12; else -> 1 }

    val Cadence.apportioned: Boolean get() = this == Cadence.BIMONTHLY || this == Cadence.YEARLY

    private fun stepDays(e: RecurringExpense): Int = e.intervalDays ?: defaultIntervalDays(e.cadence) ?: 30

    /**
     * §3.11's occurrence walk, one implementation for every cadence of a month or less.
     * Returns the occurrence dates in the month, ascending, floored at `firstDueDate` and
     * capped at `endDate`. Apportioned cadences return null: they contribute shares, not
     * occurrences.
     */
    fun occurrenceDatesInMonth(e: RecurringExpense, year: Int, month: Int): List<LocalDate>? {
        if (e.cadence.apportioned) return null
        val monthStart = LocalDate(year, month, 1)
        val monthEnd = monthStart.firstOfNextMonth()
        val floor = e.firstDueDate
        val cap = e.endDate
        val out = ArrayList<LocalDate>()
        fun keep(d: LocalDate) { if (d >= monthStart && d < monthEnd && d >= floor && (cap == null || d <= cap)) out.add(d) }

        // Fixed calendar days: semimonthly's pair, and monthly in fixed-date mode. The anchor
        // day is read from dueDateAnchorDay, never from wherever the last occurrence landed.
        if (e.cadence == Cadence.SEMIMONTHLY || (e.cadence == Cadence.MONTHLY && e.dateMode != DateMode.INTERVAL)) {
            val days = if (e.cadence == Cadence.SEMIMONTHLY) listOfNotNull(floor.day, e.dueDate2?.day)
                       else listOf(e.dueDateAnchorDay ?: floor.day)
            days.forEach { keep(dateWithDay(year, month, it)) }
            return out.sorted()
        }

        // Interval chain from firstDueDate. A 30-day interval can genuinely put two
        // occurrences in one calendar month; that is real behaviour, not a rounding error.
        val step = stepDays(e)
        var cursor: LocalDate
        if (floor >= monthStart) {
            if (floor >= monthEnd) return emptyList()            // first occurrence is a later month (branch 3)
            cursor = floor
        } else {
            // Jump rather than step, so an expense years old costs the same as a new one.
            cursor = floor.plusDays((floor.daysTo(monthStart) / step) * step)
            if (cursor < monthStart) cursor = cursor.plusDays(step)
        }
        var guard = 0
        while (cursor < monthEnd && guard++ < 200) { keep(cursor); cursor = cursor.plusDays(step) }
        return out
    }

    /**
     * What one month sweeps for this expense: the held share for apportioned cadences
     * (never `amount / a shrinking divisor`), `amount × occurrences` otherwise, 0 once finished.
     * An apportioned share stops after the last occurrence `endDate` allows: no cycle follows it.
     */
    fun sweepAmountForMonth(e: RecurringExpense, year: Int, month: Int): Money {
        if (e.finishedAt != null) return 0
        if (e.cadence.apportioned && e.endDate != null && LocalDate(year, month, 1) > e.dueDate &&
            nextOccurrenceAfter(e, e.dueDate) > e.endDate) return 0
        return when (e.cadence) {
            Cadence.BIMONTHLY -> e.apportionedShare ?: e.amount.ceilDiv(2)
            Cadence.YEARLY -> e.apportionedShare ?: e.amount.ceilDiv(12)
            else -> e.amount * (occurrenceDatesInMonth(e, year, month)?.size ?: 0)
        }
    }

    /** §3.5 / §6.2: the month's SCHEDULED total -- what is committed, not what has swept so far. */
    fun scheduledTotal(d: ProfileData, today: LocalDate): Money =
        d.recurringExpenses.sumOf { sweepAmountForMonth(it, today.year, today.month.number) }

    // ---- the anchor chain ------------------------------------------------------------------

    /** The occurrence strictly after [d] on this expense's chain. */
    fun nextOccurrenceAfter(e: RecurringExpense, d: LocalDate): LocalDate {
        if (e.cadence == Cadence.SEMIMONTHLY) {
            val days = listOfNotNull(e.firstDueDate.day, e.dueDate2?.day).sorted()
            if (days.isEmpty()) return d.plusDays(15)
            val next = d.firstOfNextMonth()
            val cands = days.map { dateWithDay(d.year, d.month.number, it) } + days.map { dateWithDay(next.year, next.month.number, it) }
            return cands.firstOrNull { it > d } ?: d.plusDays(15)
        }
        if (e.dateMode == DateMode.FIXED_DATE) {
            val months = cycleLength(e.cadence)
            // Month arithmetic from day 1, then the immutable anchor day clamped: 31 January
            // + 1 month lands on 28 February and returns to the 31st in March.
            return d.plusMonthsClamped(months, e.dueDateAnchorDay ?: d.day)
        }
        return d.plusDays(stepDays(e))
    }

    /**
     * §3.11's semimonthly pair as the editor shows it: the month's two days in calendar order,
     * each as its next date on the chain (the live `dueDate` alternates between them).
     */
    fun semimonthlyFields(e: RecurringExpense): Pair<LocalDate, LocalDate> {
        val second = e.dueDate2 ?: return e.dueDate to e.dueDate
        val (early, late) = listOf(e.firstDueDate.day, second.day).sorted()
        fun nextOn(day: Int): LocalDate {
            val here = dateWithDay(e.dueDate.year, e.dueDate.month.number, day)
            if (here >= e.dueDate) return here
            val next = e.dueDate.firstOfNextMonth()
            return dateWithDay(next.year, next.month.number, day)
        }
        return nextOn(early) to nextOn(late)
    }

    /**
     * The two fields back onto the expense: the earlier date is the chain's next due date, the
     * later one the second day. The chain reads one day from `firstDueDate`, so when the earlier
     * date's day is not that one, `firstDueDate` moves to it too -- otherwise editing one field
     * could leave both days the same.
     */
    fun withSemimonthlyFields(e: RecurringExpense, a: LocalDate, b: LocalDate): RecurringExpense {
        val (lo, hi) = if (a <= b) a to b else b to a
        return e.copy(firstDueDate = if (e.firstDueDate.day == lo.day) e.firstDueDate else lo, dueDate = lo, dueDate2 = hi)
    }

    /** One cycle back from the live anchor, or null before the first occurrence. */
    fun previousOccurrence(e: RecurringExpense): LocalDate? {
        val d = e.dueDate
        val prev = if (e.dateMode == DateMode.FIXED_DATE) {
            d.plusMonthsClamped(-cycleLength(e.cadence), e.dueDateAnchorDay ?: d.day)
        } else d.minusDays(stepDays(e))
        return if (prev < e.firstDueDate) null else prev
    }

    /** §3.11: a due date entered in the past is normalised forward until it lands today or later. */
    fun normalisedNextDue(e: RecurringExpense, today: LocalDate): LocalDate {
        var d = e.dueDate
        val anchored = e.copy(dueDateAnchorDay = e.dueDateAnchorDay ?: d.day, intervalDays = e.intervalDays ?: defaultIntervalDays(e.cadence))
        var guard = 0
        while (d < today && guard++ < 600) d = nextOccurrenceAfter(anchored, d)
        return d
    }

    /**
     * §3.11's apportioned share: `amount / the number of 1st-of-month sweeps strictly after
     * [from] and on or before the due date`, floored at 1, rounded UP to the minor unit so the
     * full amount is there when the charge lands. [from] is the moment the current cycle began
     * -- today at creation, the previous occurrence when the anchor advances.
     */
    fun apportionedShareFor(e: RecurringExpense, from: LocalDate): Money? {
        if (!e.cadence.apportioned) return null
        var sweep = from.firstOfNextMonth()
        var count = 0
        var guard = 0
        while (sweep <= e.dueDate && guard++ < 400) { count++; sweep = sweep.firstOfNextMonth() }
        return e.amount.ceilDiv(maxOf(1, count).toLong())
    }

    /**
     * §3.11: `dueDate` advances when its date passes in the real world -- from the start of
     * the following day -- never when the sweep fires. The share is recomputed only when the
     * anchor actually moved, measured from the previous occurrence. The chain stops at the last
     * occurrence on or before `endDate`; FINISHING is the month boundary's (`NewMonth.processMonth`,
     * §3.11 "Who sets it at endDate").
     */
    fun advanceDueDates(d: ProfileData, today: LocalDate): ProfileData = d.copy(recurringExpenses = d.recurringExpenses.map { e ->
        if (e.finishedAt != null) return@map e
        var cur = if (e.dueDate < e.firstDueDate) e.firstDueDate else e.dueDate
        var guard = 0
        while (cur < today && guard++ < 600) {
            val nxt = nextOccurrenceAfter(e, cur)
            if (e.endDate != null && nxt > e.endDate) break
            cur = nxt
        }
        val advanced = cur != e.dueDate
        var out = e.copy(dueDate = cur)
        if (advanced && e.cadence.apportioned) {
            out = out.copy(apportionedShare = apportionedShareFor(out, previousOccurrence(out) ?: today))
        }
        out
    })

    // ---- creation, edit, deletion --------------------------------------------------------------

    /** The fields an edit may touch; §3.11: "an edit is a new plan" only when one of these moved. */
    fun planChanged(before: RecurringExpense, after: RecurringExpense): Boolean =
        scheduleChanged(before, after) || before.amount != after.amount || before.endDate != after.endDate

    /**
     * The fields §3.11 derives from what the person entered, in one place: `dateMode` nulled on
     * the modeless cadences, `firstDueDate` and the anchor day taken from the entered date, the
     * interval set, and `dueDate` normalised forward. Creation runs it, and so does an edit that
     * moved the date, the mode or the cadence -- "an edit is a new plan", and the anchor
     * "never changes on its own", which a person's own edit is not.
     */
    fun derivePlan(entered: RecurringExpense, today: LocalDate, firstDue: LocalDate = entered.dueDate): RecurringExpense {
        val dateMode = if (!entered.cadence.monthAnchored) null else entered.dateMode
        val e = entered.copy(
            dateMode = dateMode,
            firstDueDate = firstDue,
            dueDateAnchorDay = if (dateMode == DateMode.FIXED_DATE) entered.dueDate.day else null,
            intervalDays = if (dateMode == DateMode.INTERVAL) defaultIntervalDays(entered.cadence) else null,
        )
        return e.copy(dueDate = normalisedNextDue(e, today))
    }

    /** Whether an edit moved what [derivePlan] derives from. */
    private fun scheduleChanged(before: RecurringExpense, after: RecurringExpense): Boolean =
        before.cadence != after.cadence || before.dueDate != after.dueDate || before.dueDate2 != after.dueDate2 || before.dateMode != after.dateMode

    /**
     * §3.11's creation: `firstDueDate` keeps the entered (pre-normalisation) date, the anchor
     * day and interval are fixed once, `dateMode` is nulled for the three inherently modeless
     * cadences, the share is computed against the normalised due date, and one of the four
     * creation branches decides whether anything sweeps now. All four set `lastSweptMonth`.
     */
    fun create(d: ProfileData, draft: RecurringExpense, clock: Clock, ids: () -> String): ProfileData {
        val today = clock.today()
        var e = derivePlan(draft.copy(profileId = d.profileId, finishedAt = null, lastSweptMonth = null), today)
        e = e.copy(apportionedShare = apportionedShareFor(e, today))

        var next = d.copy(recurringExpenses = d.recurringExpenses + e)
        val ym = today.ymKey()
        val firstDue = e.firstDueDate
        when {
            e.cadence.apportioned -> Unit                                   // 4. never sweeps on creation
            firstDue.ymKey() > ym -> Unit                                   // 3. first due in a later month
            firstDue >= today -> next = sweepOccurrences(next, e.id, today, clock, ids).first   // 1. still ahead this month
            else -> Unit                                                    // 2. already passed: no back-sweep
        }
        return next.copy(recurringExpenses = next.recurringExpenses.map { if (it.id == e.id) it.copy(lastSweptMonth = ym) else it })
    }

    /** What an edit may leave to settle: the difference this month's sweep would have under the new settings. */
    data class Reconcile(val expenseId: String, val delta: Money, val occurrenceDate: LocalDate)

    /**
     * §3.11's edit-and-reconcile: the new settings are stored; if this month has already
     * swept and the scheduled total moved, the difference is OFFERED (never applied here) with
     * the date of the occurrence it adjusts. The share is recomputed only when the plan changed.
     */
    fun edit(d: ProfileData, edited: RecurringExpense, today: LocalDate): Pair<ProfileData, Reconcile?> {
        val before = d.recurringExpenses.first { it.id == edited.id }
        val sweptThisMonth = before.lastSweptMonth == today.ymKey()
        val beforeAmt = if (sweptThisMonth) sweepAmountForMonth(before, today.year, today.month.number) else 0
        var e = edited.copy(profileId = d.profileId)
        // the chain restarts from the date on screen; only a semimonthly edit of the second date alone keeps the first
        if (scheduleChanged(before, e)) {
            val secondOnly = e.dueDate == before.dueDate && e.firstDueDate == before.firstDueDate && e.cadence == before.cadence && e.dateMode == before.dateMode
            e = derivePlan(e, today, firstDue = if (secondOnly) before.firstDueDate else e.dueDate)
        }
        e = if (e.cadence.apportioned) {
            if (planChanged(before, e)) e.copy(apportionedShare = apportionedShareFor(e, today)) else e.copy(apportionedShare = before.apportionedShare)
        } else e.copy(apportionedShare = null)
        val next = d.copy(recurringExpenses = d.recurringExpenses.map { if (it.id == e.id) e else it })
        if (!sweptThisMonth) return next to null
        val afterAmt = sweepAmountForMonth(e, today.year, today.month.number)
        val delta = afterAmt - beforeAmt
        if (delta == 0L) return next to null
        return next to Reconcile(e.id, delta, reconcileOccurrenceDate(e, today))
    }

    /** The earliest still-reversible occurrence, else the month's last; apportioned stays on the 1st. */
    fun reconcileOccurrenceDate(e: RecurringExpense, today: LocalDate): LocalDate {
        val dates = occurrenceDatesInMonth(e, today.year, today.month.number)
        if (dates.isNullOrEmpty()) return today.firstOfMonth()
        return dates.firstOrNull { it >= today } ?: dates.last()
    }

    /**
     * Applies an accepted reconciliation: one `rec:` row for the difference, dated to the
     * occurrence. On a Loan-linked expense the tracked debt follows the Savings movement, with
     * its own `acct:` row on the same date -- a further €200 taken reduces the debt by €200 (§3.11).
     */
    fun applyReconcile(d: ProfileData, r: Reconcile, clock: Clock, ids: () -> String): ProfileData {
        val e = d.recurringExpenses.first { it.id == r.expenseId }
        var next = Ledger.logAndMove(d, Movement(r.delta, TransactionKey.RecurringKey(e.id), e.name, RECONCILE_PAYEE, date = r.occurrenceDate), clock, ids()).first
        next.accounts.firstOrNull { it.id == e.linkedAccountId && it.type == AccountType.LOAN }?.let { loan ->
            next = Accounts.balanceEdit(next, loan.id, loan.balance - r.delta, RECONCILE_PAYEE, clock, ids(), r.occurrenceDate)
        }
        return next
    }

    /**
     * §3.11: the amount a deletion offers back is what the ledger recorded -- the sum of the
     * expense's un-elapsed `rec:` rows -- never the entity's `amount`. Apportioned: the shares
     * swept so far in the current cycle. A finished expense's obligation is discharged: 0.
     */
    fun reversalAmount(d: ProfileData, e: RecurringExpense, today: LocalDate): Money {
        if (e.finishedAt != null) return 0
        val rows = d.transactions.filter { it.key == "rec:${e.id}" }
        if (e.cadence.apportioned) {
            val prev = previousOccurrence(e)
            return rows.filter { prev == null || it.date > prev }.sumOf { it.amount }
        }
        // A due date is "passed" from the START OF THE FOLLOWING DAY: due today is still un-elapsed.
        return rows.filter { it.date >= today }.sumOf { it.amount }
    }

    /** §3.11's reversal: the un-elapsed sweeps go back to Savings as one `rec:` row. Shared by every delete path that offers it. */
    fun applyReversal(d: ProfileData, e: RecurringExpense, clock: Clock, ids: () -> String): ProfileData {
        val back = reversalAmount(d, e, clock.today())
        return if (back > 0) Ledger.logAndMove(d, Movement(-back, TransactionKey.RecurringKey(e.id), e.name, REVERSAL_PAYEE), clock, ids()).first else d
    }

    /**
     * §3.11 / §7.2: delete, optionally reversing the un-elapsed sweeps (the reversal stores
     * −amount and moves Savings +amount; both rows stay in the ledger). A linked Account goes
     * too, with its own closing row (§5.3) -- there is no unlink-only path.
     */
    fun delete(d: ProfileData, expenseId: String, reverse: Boolean, clock: Clock, ids: () -> String): ProfileData {
        val e = d.recurringExpenses.firstOrNull { it.id == expenseId } ?: return d
        var next = if (reverse) applyReversal(d, e, clock, ids) else d
        e.linkedAccountId?.let { accId ->
            next.accounts.firstOrNull { it.id == accId }?.let { acct -> next = Accounts.close(next, acct, clock, ids) }
        }
        return next.copy(recurringExpenses = next.recurringExpenses.filter { it.id != expenseId })
    }

    /**
     * §3.11: each real occurrence writes its own row, dated to its real due date; an
     * apportioned share is one row dated to the 1st it swept on. Returns the total swept.
     */
    fun sweepOccurrences(d: ProfileData, expenseId: String, monthDate: LocalDate, clock: Clock, ids: () -> String): Pair<ProfileData, Money> {
        val e = d.recurringExpenses.first { it.id == expenseId }
        val dates = occurrenceDatesInMonth(e, monthDate.year, monthDate.month.number)
        var next = d
        if (dates == null) {
            val share = sweepAmountForMonth(e, monthDate.year, monthDate.month.number)
            if (share > 0) next = Ledger.logAndMove(next, Movement(share, TransactionKey.RecurringKey(e.id), e.name, SWEEP_PAYEE, date = monthDate.firstOfMonth()), clock, ids()).first
            return next to share
        }
        if (e.finishedAt != null) return next to 0
        var total = 0L
        for (day in dates) {
            next = Ledger.logAndMove(next, Movement(e.amount, TransactionKey.RecurringKey(e.id), e.name, SWEEP_PAYEE, date = day), clock, ids()).first
            total += e.amount
        }
        return next to total
    }

    const val SWEEP_PAYEE = "Recurring sweep"
    const val REVERSAL_PAYEE = "Deletion reversal"
    const val RECONCILE_PAYEE = "Edit reconciliation"
}
