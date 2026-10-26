package app.scifin.engine

import app.scifin.model.IncomeCadence
import app.scifin.model.Money
import app.scifin.model.ProfileData
import app.scifin.model.Settings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/*
 * §3.1 / §3.3 -- Savings' unset state, Income's cadence math, auto-deposit's catch-up, the
 * derived `incomeReceivedThisMonth`, and the "no income logged in the last X days" check.
 */

/** What a catch-up run deposited, for §3.9's summary card. */
data class CatchUp(val count: Int, val total: Money)

object Income {

    /**
     * §3.3: derived on read, never stored -- the sum of `isIncome` rows dated in the current
     * calendar month and on or after `incomeEnabledDate`. Deriving it is what keeps §3.9's two
     * triggers order-independent.
     */
    fun receivedThisMonth(d: ProfileData, today: LocalDate): Money {
        val ym = today.ymKey()
        val floor = d.settings.incomeEnabledDate
        return d.transactions
            .filter { it.isIncome && it.date.ymKey() == ym && (floor == null || it.date >= floor) }
            .sumOf { it.amount }
    }

    /** §3.3's anchor-date math: the first payday on or after [from], or null with no anchor. */
    fun nextOccurrence(s: Settings, from: LocalDate): LocalDate? {
        val anchor = s.incomePayDate ?: return null
        return when (s.incomeCadence) {
            IncomeCadence.SEMIMONTHLY -> {
                // §3.3: both dates or none; an unchosen second payday is not a monthly one
                val day2 = s.incomePayDate2?.day ?: return null
                val candidates = (-1..1).flatMap { off ->
                    val base = from.plusMonthsClamped(off, 1)
                    listOf(dateWithDay(base.year, base.month.number, anchor.day), dateWithDay(base.year, base.month.number, day2))
                }.sorted()
                candidates.firstOrNull { it >= from } ?: candidates.last()
            }
            IncomeCadence.MONTHLY -> monthlyNext(anchor.day, from)
            IncomeCadence.WEEKLY, IncomeCadence.BIWEEKLY -> {
                val step = if (s.incomeCadence == IncomeCadence.WEEKLY) 7 else 14
                var d = anchor
                var guard = 0
                while (d < from && guard++ < 4000) d = d.plusDays(step)
                d
            }
        }
    }

    private fun monthlyNext(anchorDay: Int, from: LocalDate): LocalDate {
        val d = dateWithDay(from.year, from.month.number, anchorDay)
        return if (d < from) from.firstOfNextMonth().let { dateWithDay(it.year, it.month.number, anchorDay) } else d
    }

    /** Semimonthly only (§3.3): the anchor that most recently passed, for the no-income trigger. */
    fun mostRecentlyPassedAnchor(s: Settings, asOf: LocalDate): LocalDate? {
        if (s.incomeCadence != IncomeCadence.SEMIMONTHLY) return null
        val anchor = s.incomePayDate ?: return null
        val day2 = s.incomePayDate2?.day ?: return null
        return (-1..0).flatMap { off ->
            val base = asOf.plusMonthsClamped(off, 1)
            listOf(dateWithDay(base.year, base.month.number, anchor.day), dateWithDay(base.year, base.month.number, day2))
        }.filter { it <= asOf }.maxOrNull()
    }

    /**
     * §3.9's second trigger, on its own clock. Replays EVERY elapsed payday, one real historical
     * date at a time, never collapsing a gap into a lump sum. The replay starts the day after the
     * last deposit, or -- with none yet -- at `incomePayDate` itself (§3.3), never at today.
     */
    fun runCatchUp(d: ProfileData, clock: Clock, ids: () -> String): Pair<ProfileData, CatchUp> {
        val s = d.settings
        if (!s.incomeEnabled || !s.incomeAutoDeposit || s.income <= 0 || Hourly.isHourly(s)) return d to CatchUp(0, 0)
        var cur = d
        var count = 0
        var total = 0L
        var guard = 0
        while (guard++ < 400) {
            val st = cur.settings
            val from = st.lastIncomeDepositDate?.plusDays(1) ?: st.incomePayDate ?: clock.today()
            val scheduled = nextOccurrence(st, from) ?: break
            if (scheduled > clock.today()) break
            if (st.lastIncomeDepositDate != null && scheduled <= st.lastIncomeDepositDate) break
            cur = Ledger.logAndMove(cur, Movement(st.income, TransactionKey.SavingsKey, "Savings", Ledger.AUTO_DEPOSIT_PAYEE, isIncome = true, date = scheduled), clock, ids()).first
            cur = cur.copy(settings = cur.settings.copy(lastIncomeDepositDate = scheduled))
            count++; total += st.income
        }
        // recorded on §3.9's card by NewMonth.runCatchUp, only when a card is shown
        return cur to CatchUp(count, total)
    }

    /**
     * §3.3's "No income logged in the last X days" -- manual path only. Returns X, the real
     * elapsed days since the reference point (today minus the reference, the same figure the
     * trigger is tested against), or null when the note does not fire.
     */
    fun noIncomeLoggedDays(d: ProfileData, today: LocalDate): Int? {
        val s = d.settings
        // hourly: the payday prompt does this job (owner, 2026-10-26)
        if (!s.incomeEnabled || s.incomeAutoDeposit || Hourly.isHourly(s)) return null
        val ref = s.lastIncomeLoggedDate ?: s.incomeEnabledDate ?: return null
        val elapsed = ref.daysTo(today)
        return when (s.incomeCadence) {
            IncomeCadence.SEMIMONTHLY -> {
                val passed = mostRecentlyPassedAnchor(s, today) ?: return null
                if (ref >= passed) null else elapsed.takeIf { it > 0 }
            }
            IncomeCadence.MONTHLY -> {
                val due = nextOccurrence(s, ref.plusDays(1)) ?: return null
                if (due > today) null else elapsed.takeIf { it > 0 }
            }
            else -> {
                val threshold = if (s.incomeCadence == IncomeCadence.WEEKLY) 7 else 14
                elapsed.takeIf { it > threshold }
            }
        }
    }

    /** §3.3's toggle. Off clears the whole set -- cleared, not remembered; on stamps the date. */
    fun setEnabled(d: ProfileData, enabled: Boolean, today: LocalDate): ProfileData {
        val s = d.settings
        // hourly's unpaid shifts are settled by the switch prompt first; off clears what is left, like the rest
        return d.copy(shifts = if (!enabled) emptyList() else d.shifts, settings = if (!enabled) s.copy(
            incomeEnabled = false, income = 0, incomeAutoDeposit = false,
            incomePayDate = null, incomePayDate2 = null, incomeEnabledDate = null,
            lastIncomeLoggedDate = null, lastIncomeDepositDate = null,
            incomeType = app.scifin.model.IncomeType.SALARIED, hourlyRate = 0, lastPaydayHandled = null,
        ) else s.copy(
            incomeEnabled = true, incomeEnabledDate = today,
            incomePayDate = s.incomePayDate ?: today,
        ))
    }

    /**
     * §3.3's auto-deposit switch: turning it back ON after
     * an earlier on-period starts from today -- the stale marker would otherwise replay every
     * payday the switch was off as real, dated income, the invented money §3.3 forbids. A first
     * switch-on (no marker yet) keeps §3.3's rule and replays from the chosen payday.
     */
    fun setAutoDeposit(d: ProfileData, on: Boolean, today: LocalDate): ProfileData {
        val s = d.settings
        if (s.incomeAutoDeposit == on) return d
        return d.copy(settings = s.copy(
            incomeAutoDeposit = on,
            lastIncomeDepositDate = if (on && s.lastIncomeDepositDate != null) today.minusDays(1) else s.lastIncomeDepositDate,
        ))
    }

    /**
     * §3.3: a cadence change re-asks the anchor (a date that anchored a biweekly cycle means
     * something different under a semimonthly one), clears the second date the new cadence may
     * not use, and resets the deposit marker so no cycle is invented across the change.
     */
    fun setCadence(d: ProfileData, cadence: IncomeCadence): ProfileData {
        if (d.settings.incomeCadence == cadence) return d
        return d.copy(settings = d.settings.copy(
            incomeCadence = cadence,
            incomePayDate = null,
            incomePayDate2 = if (cadence == IncomeCadence.SEMIMONTHLY) d.settings.incomePayDate2 else null,
            lastIncomeDepositDate = null,
        ))
    }
}
