package app.scifin.engine

import app.scifin.i18n.stored
import app.scifin.model.Account
import app.scifin.model.AccountType
import app.scifin.model.Money
import app.scifin.model.ProfileData
import app.scifin.model.RateType
import app.scifin.model.RecurringExpense
import app.scifin.model.Settings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.math.roundToLong

/*
 * §3.9 -- the new-month engine: one shared 1st-of-month trigger for the Category reset and
 * §3.11's Recurring Expense sweep, driven by the gap between what was last processed and
 * what month it actually is: every elapsed month is replayed in order with its real dates,
 * and running it twice in the same month does nothing. Auto-deposit is the SECOND trigger,
 * on its own payday clock (Income.runCatchUp); it runs here too so a clock jump catches its
 * cycles up, and the two are order-independent.
 */

/** §3.11's completion notice for one expense: plain, non-graded, stated as fact. */
data class Ended(val name: String, val note: String)

/** What one month's processing did. */
data class MonthResult(val ym: String, val sweptTotal: Money, val endedNow: List<Ended>)

/** What a catch-up run did, for §3.9's summary card. `monthsReplayed > 1` means time was missed. */
data class CatchUpSummary(val monthsReplayed: Int, val sweptTotal: Money, val endedNow: List<Ended>, val deposits: CatchUp)

object NewMonth {

    /** A Loan whose scheduled payments retire it: everything but interest-only and variable (§9.1). */
    fun paysOffAtTerm(a: Account): Boolean =
        a.type == AccountType.LOAN && a.rateType != RateType.INTEREST_ONLY && a.rateType != RateType.VARIABLE

    /**
     * §3.11's completion notice. §9.1 requires three variants: variable-rate and interest-only
     * loans are NOT reliably paid off at their endDate and the default line would be an
     * assertion the app cannot support. A balance that auto-update took to zero is clear
     * whatever the rate type.
     */
    fun completionNoticeFor(d: ProfileData, e: RecurringExpense): String {
        val acct = e.linkedAccountId?.let { id -> d.accounts.firstOrNull { it.id == id } }
        val paidOffForReal = acct != null && acct.autoUpdateFromPayments && acct.balance == 0L
        if (acct != null && acct.type == AccountType.LOAN && !paidOffForReal) {
            if (acct.rateType == RateType.INTEREST_ONLY)
                return stored("The interest-only period has ended — this recurring expense has stopped. The principal is still outstanding.")
            if (acct.rateType == RateType.VARIABLE)
                return stored("This recurring expense has reached the end of its term and stopped. Check the balance with your lender.")
        }
        if (acct != null && acct.type == AccountType.LOAN) return stored("Loan paid off — this recurring expense has ended.")
        return stored("This recurring expense has ended.")
    }

    /**
     * One month, idempotent per marker; a marker already at or past the month is left alone.
     * The Category reset is a period-counter clear, not a correction: no Transaction, no Savings
     * change (§3.9). Each expense sweeps once per month, `endDate` finishes it with a closing
     * row on a linked Loan, and §7.2's auto-update pays principal down from the sweep it was
     * linked to -- only a sweep made in this run.
     */
    fun processMonth(d: ProfileData, monthDate: LocalDate, clock: Clock, ids: () -> String): Pair<ProfileData, MonthResult> {
        val ym = monthDate.ymKey()
        val first = monthDate.firstOfMonth()
        var next = d.copy(categories = d.categories.map { c ->
            val lr = c.lastResetMonth
            if (lr == null || lr < ym) c.copy(spent = 0, lastResetMonth = ym) else c
        })
        var sweptTotal = 0L
        val ending = ArrayList<RecurringExpense>()
        val sweptIds = HashSet<String>()

        for (id in d.recurringExpenses.map { it.id }) {
            val e = next.recurringExpenses.first { it.id == id }
            if ((e.lastSweptMonth ?: "") >= ym) continue
            val (afterSweep, swept) = Recurring.sweepOccurrences(next, id, first, clock, ids)
            next = afterSweep
            sweptTotal += swept
            sweptIds.add(id)
            var updated = next.recurringExpenses.first { it.id == id }.copy(lastSweptMonth = ym)
            if (e.endDate != null && e.finishedAt == null && first >= e.endDate) {
                ending.add(e)
                updated = updated.copy(finishedAt = first)
                e.linkedAccountId?.let { accId ->
                    next.accounts.firstOrNull { it.id == accId }?.let { acct ->
                        // §9.1: only an amortising loan is paid off by running to term; interest-only and variable keep what they owe
                        if (acct.balance != 0L && paysOffAtTerm(acct)) {
                            next = Ledger.logAccountEdit(next, acct.id, acct.name, -acct.balance, stored("Loan finished"), clock, ids(), first)
                            next = next.copy(accounts = next.accounts.map { if (it.id == acct.id) it.copy(balance = 0) else it })
                        }
                    }
                }
            }
            next = next.copy(recurringExpenses = next.recurringExpenses.map { if (it.id == id) updated else it })
        }

        // §7.2's loan auto-update: the real split, recomputed from live state each cycle.
        for (acct in next.accounts.filter { it.type == AccountType.LOAN && it.autoUpdateFromPayments && it.linkedRecurringExpenseId != null }) {
            val e = next.recurringExpenses.firstOrNull { it.id == acct.linkedRecurringExpenseId } ?: continue
            if (e.id !in sweptIds) continue
            if (e.finishedAt != null && e.finishedAt != first) continue
            val payment = Recurring.sweepAmountForMonth(e.copy(finishedAt = null), first.year, first.month.number)
            val interest: Money = acct.interestRate?.let { (acct.balance * it / 12.0).roundToLong() } ?: 0
            val principal = maxOf(0L, minOf(acct.balance, payment - interest))
            if (principal > 0) {
                // §7.2: dated to the payment's real occurrence (the 1st for an apportioned share), payee the payment's own name
                val on = Recurring.occurrenceDatesInMonth(e, first.year, first.month.number)?.firstOrNull() ?: first
                next = Ledger.logAccountEdit(next, acct.id, acct.name, -principal, e.name, clock, ids(), on)
                next = next.copy(accounts = next.accounts.map { if (it.id == acct.id) it.copy(balance = it.balance - principal) else it })
            }
            val nowBalance = next.accounts.first { it.id == acct.id }.balance
            if (nowBalance == 0L && e.finishedAt == null) {
                next = next.copy(recurringExpenses = next.recurringExpenses.map { if (it.id == e.id) it.copy(finishedAt = first) else it })
            }
        }
        // the notice reads the balance after this month's payment, so a variable loan that payment cleared is paid off
        val ended = ending.map { Ended(it.name, completionNoticeFor(next, it)) }
        return next to MonthResult(ym, sweptTotal, ended)
    }

    /**
     * §3.9's trigger, run on every open: advance the anchor chain, then replay every month
     * between the oldest marker and now. Returns null when nothing elapsed. The summary fires
     * only after a REAL absence (two or more months replayed); one month is the ordinary
     * boundary everyone crosses.
     */
    fun runCatchUp(d: ProfileData, clock: Clock, ids: () -> String): Pair<ProfileData, CatchUpSummary?> {
        val today = clock.today()
        var next = Recurring.advanceDueDates(d, today)
        val nowYm = today.ymKey()
        val marks = (next.categories.mapNotNull { it.lastResetMonth } + next.recurringExpenses.mapNotNull { it.lastSweptMonth }).sorted()
        if (marks.isEmpty() || marks.first() >= nowYm) {
            // The other trigger still runs: a payday may have passed inside the month.
            val (afterIncome, _) = Income.runCatchUp(next, clock, ids)
            return afterIncome to null
        }
        val months = ArrayList<LocalDate>()
        var cur = LocalDate(marks.first().substring(0, 4).toInt(), marks.first().substring(5, 7).toInt(), 1)
        var guard = 0
        while (cur.ymKey() < nowYm && guard++ < 120) { cur = cur.firstOfNextMonth(); months.add(cur) }
        var swept = 0L
        val ended = ArrayList<Ended>()
        for (m in months) {
            val (after, r) = processMonth(next, m, clock, ids)
            next = after; swept += r.sweptTotal; ended.addAll(r.endedNow)
        }
        val (afterIncome, deposits) = Income.runCatchUp(next, clock, ids)
        val summary = CatchUpSummary(months.size, swept, ended, deposits)
        // §3.9: the card after a real absence, and §3.11's notices on it whenever there are any.
        // Stored, so it "persists until dismissed" across a restart; an unread card accumulates,
        // and nothing here ever clears it -- only the person's dismissal does.
        next = if (months.size > 1 || ended.isNotEmpty()) afterIncome.copy(settings = record(afterIncome.settings, summary)) else afterIncome
        return next to summary
    }

    private const val FIELD = '\t'

    private fun record(s: Settings, run: CatchUpSummary): Settings {
        val base = if (s.catchUpSummaryPending) s else s.copy(catchUpMonths = 0, catchUpSwept = 0, catchUpDepositCount = 0, catchUpDepositTotal = 0, catchUpEnded = "")
        val lines = run.endedNow.map { "${it.name.replace(FIELD, ' ').replace('\n', ' ')}$FIELD${it.note.replace('\n', ' ')}" }
        return base.copy(
            catchUpSummaryPending = true,
            catchUpMonths = base.catchUpMonths + run.monthsReplayed,
            catchUpSwept = base.catchUpSwept + run.sweptTotal,
            catchUpDepositCount = base.catchUpDepositCount + run.deposits.count,
            catchUpDepositTotal = base.catchUpDepositTotal + run.deposits.total,
            catchUpEnded = (base.catchUpEnded.lines().filter { it.isNotEmpty() } + lines).joinToString("\n"),
        )
    }

    /** The unread card, from what [runCatchUp] stored; null once dismissed. */
    fun persistedSummary(s: Settings): CatchUpSummary? {
        if (!s.catchUpSummaryPending) return null
        val ended = s.catchUpEnded.lines().filter { it.isNotEmpty() }.map { l -> Ended(l.substringBefore(FIELD), l.substringAfter(FIELD, "")) }
        return CatchUpSummary(s.catchUpMonths, s.catchUpSwept, ended, CatchUp(s.catchUpDepositCount, s.catchUpDepositTotal))
    }

    /** Dismissal: the flag and every figure behind it, so the next card starts from nothing. */
    fun dismissed(s: Settings) =
        s.copy(catchUpSummaryPending = false, catchUpMonths = 0, catchUpSwept = 0, catchUpDepositCount = 0, catchUpDepositTotal = 0, catchUpEnded = "")
}
