package app.scifin.engine

import app.scifin.model.Money
import app.scifin.model.ProfileData
import kotlinx.datetime.LocalDate

/*
 * §5 -- the four Reports, every one derived from the ledger and nothing else.
 */

/** §5.2: one slice of this month's outflow. */
data class Slice(val key: String, val label: String, val icon: String?, val amount: Money)

/** §5.2: `plainList` when a share chart would describe nothing (a negative slice, or net ≤ 0). */
data class SpendingComposition(val slices: List<Slice>, val net: Money) {
    val anyNegative: Boolean get() = slices.any { it.amount < 0 }
    val plainList: Boolean get() = anyNegative || net <= 0
}

data class MonthPoint(val month: LocalDate, val value: Money)
data class IncomeExpensePoint(val month: LocalDate, val income: Money, val expenses: Money)

object Reports {

    // ---- §5.1 Buffer / Age of Money ----------------------------------------------------

    /**
     * FIFO lot consumption on the Savings effect (§3.6), Income on or off alike: every
     * Savings-increasing row opens a lot dated to itself, every decreasing row consumes the
     * oldest lots first, and the age is the days since the oldest lot still holding money.
     * §5.1's invariant -- unconsumed lots sum to Savings at every step -- holds through an
     * overshoot: the excess is carried as a deficit the next deposit absorbs before it becomes
     * a lot, and the figure is 0 while the replayed balance is at or below zero.
     */
    fun ageOfMoney(d: ProfileData, asOf: LocalDate): Int {
        val lots = ArrayList<Pair<LocalDate, Money>>()
        var deficit = 0L
        val rows = d.transactions.filter { it.date <= asOf }.sortedWith(compareBy({ it.date }, { it.loggedAt }))
        for (t in rows) {
            val effect = Ledger.savingsEffect(t)
            if (effect > 0) {
                val absorbed = minOf(deficit, effect)
                deficit -= absorbed
                if (effect > absorbed) lots.add(t.date to (effect - absorbed))
            } else if (effect < 0) {
                var toConsume = -effect
                for (i in lots.indices) {
                    if (toConsume <= 0) break
                    val (day, rem) = lots[i]
                    val take = minOf(rem, toConsume)
                    lots[i] = day to (rem - take)
                    toConsume -= take
                }
                deficit += toConsume
            }
        }
        if (deficit > 0) return 0
        val oldest = lots.firstOrNull { it.second > 0 } ?: return 0
        return maxOf(0, oldest.first.daysTo(asOf))
    }

    /**
     * §5.1: one point per month, each as of that month's FINAL day -- the current month's is the
     * live value. A month that ended before the opening lot has no age to report, so it is left
     * out rather than drawn as zero (§3.1); a zero after it -- a month Savings ended below zero --
     * is a real reading and stays.
     */
    fun ageOfMoneyTrend(d: ProfileData, today: LocalDate, months: Int = 6): List<MonthPoint> {
        val opening = d.transactions.filter { Ledger.savingsEffect(it) > 0 }.minOfOrNull { it.date }
        return (months - 1 downTo 0).mapNotNull { i ->
            val p = today.plusMonthsClamped(-i, 1)
            val asOf = if (i == 0) today else p.firstOfNextMonth().minusDays(1)
            if (i > 0 && (opening == null || asOf < opening)) null
            else MonthPoint(p, ageOfMoney(d, asOf).toLong())
        }
    }

    /** §5.1's prospective window: the trailing three COMPLETE calendar months, never a straddling 30 days. */
    data class Window(val start: LocalDate, val end: LocalDate) { val days: Int get() = maxOf(1, start.daysTo(end)) }

    fun trailingSpendWindow(today: LocalDate): Window {
        val end = today.firstOfMonth()
        return Window(end.plusMonthsClamped(-3, 1), end)
    }

    /**
     * §5.1's coverage figure, or null when there is no answer to give: Savings null, fewer than
     * three complete months of ledger, or a zero trailing outflow. Floored at 0 when Savings is
     * at or below zero -- −€5 and −€5,000 answer alike.
     */
    fun coverageDaysOrNull(d: ProfileData, today: LocalDate): Int? {
        if (d.savings.amount == null) return null
        val w = trailingSpendWindow(today)
        val earliest = d.transactions.minOfOrNull { it.date } ?: return null
        if (earliest > w.start) return null
        val outflow = d.transactions.filter { it.date >= w.start && it.date < w.end }.sumOf { t -> Ledger.savingsEffect(t).let { if (it < 0) -it else 0 } }
        if (outflow <= 0) return null
        if (d.savingsAmount <= 0) return 0
        return (d.savingsAmount.toDouble() / (outflow.toDouble() / w.days)).let { kotlin.math.round(it).toInt() }
    }

    // ---- §5.2 Spending --------------------------------------------------------------------

    /**
     * This month's outflow by key, netting sweeps against reversals; Goals as one combined
     * slice; Savings-decreasing `savings` rows as Uncategorised; `acct:` rows never.
     */
    fun spendingComposition(d: ProfileData, today: LocalDate): SpendingComposition {
        val ym = today.ymKey()
        val slices = LinkedHashMap<String, Slice>()
        fun bump(key: String, label: String, icon: String?, delta: Money) {
            val cur = slices[key]
            slices[key] = Slice(key, label, icon, (cur?.amount ?: 0) + delta)
        }
        for (t in d.transactions) {
            if (t.date.ymKey() != ym) continue
            val eff = Ledger.savingsEffect(t)
            if (eff == 0L) continue
            val out = -eff
            when (val k = TransactionKey.parse(t.key)) {
                is TransactionKey.RecurringKey -> d.recurringExpenses.firstOrNull { it.id == k.id }.let { bump(t.key, it?.name ?: t.targetLabel, it?.icon, out) }
                is TransactionKey.CategoryKey -> d.categories.firstOrNull { it.id == k.id }.let { bump(t.key, it?.name ?: t.targetLabel, it?.icon, out) }
                is TransactionKey.GoalKey -> bump("goals", "Goals", "piggy-bank", out)
                TransactionKey.SavingsKey -> if (out > 0) bump("uncat", "Uncategorised", "banknote", out)
                is TransactionKey.AccountKey, is TransactionKey.GoalSpendKey -> Unit
            }
        }
        val rows = slices.values.filter { it.amount != 0L }.sortedByDescending { it.amount }
        return SpendingComposition(rows, rows.sumOf { it.amount })
    }

    // ---- §5.3 Savings vs Owed -------------------------------------------------------------

    /** Unallocated Savings minus every Account balance; money held in Goals is not counted. */
    fun savingsVsOwedNow(d: ProfileData): Money = d.savingsAmount - d.accounts.sumOf { it.balance }

    /** Ledger-derived history: a forward replay from zero over both series, as of each month's end. */
    fun savingsVsOwedTrend(d: ProfileData, today: LocalDate, months: Int = 6): List<MonthPoint> =
        (months - 1 downTo 0).map { i ->
            val p = today.plusMonthsClamped(-i, 1)
            val asOf = p.firstOfNextMonth().minusDays(1)
            var savings = 0L; var owed = 0L
            for (t in d.transactions) {
                if (t.date > asOf) continue
                savings += Ledger.savingsEffect(t)
                if (t.key.startsWith("acct:")) owed += t.amount
            }
            MonthPoint(p, savings - owed)
        }

    // ---- §5.4 Income vs Expenses ----------------------------------------------------------

    /** Present only while Income is on (§5.4). Goal money counts once, when it is spent from the Goal: a contribution
     *  is money kept, not spent, and a Goal spend leaves Savings untouched, so neither shows in the Savings effect. */
    fun incomeVsExpenses(d: ProfileData, today: LocalDate, months: Int = 6): List<IncomeExpensePoint>? {
        if (!d.settings.incomeEnabled) return null
        return (months - 1 downTo 0).map { i ->
            val p = today.plusMonthsClamped(-i, 1)
            val ym = p.ymKey()
            val inc = d.transactions.filter { it.isIncome && it.date.ymKey() == ym }.sumOf { it.amount }
            val month = d.transactions.filter { it.date.ymKey() == ym }
            val exp = month.filter { !it.key.startsWith("goal:") }.sumOf { t -> Ledger.savingsEffect(t).let { if (it < 0) -it else 0 } } +
                month.filter { it.key.startsWith("spend:") }.sumOf { -it.amount }
            IncomeExpensePoint(p, inc, exp)
        }
    }
}
