@file:OptIn(ExperimentalTime::class)

package app.scifin.engine

import app.scifin.model.Money
import app.scifin.model.ProfileData
import app.scifin.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * §3.6 -- THE ONE SAVINGS-MOVING MECHANISM, and §7.3 -- pair deletion.
 *
 * Every real money movement against Savings (quick-add, direct edit, sweep, auto-deposit)
 * funnels through [Ledger.logAndMove]. Account edits are the one exception (§7.1): they log
 * a Transaction for their own record but never move Savings, so they are excluded from every
 * Savings-derived figure (thresholds, feasibility, Age of Money).
 */

/** §3.6's `key`, parsed once. The stored form is the spec's own spelling. */
sealed class TransactionKey(val stored: String) {
    object SavingsKey : TransactionKey("savings")
    data class GoalKey(val id: String) : TransactionKey("goal:$id")
    data class CategoryKey(val id: String) : TransactionKey("cat:$id")
    data class RecurringKey(val id: String) : TransactionKey("rec:$id")
    data class AccountKey(val id: String) : TransactionKey("acct:$id")
    /** §3.6: money spent out of a Goal. The Goal goes down; Savings does not move. */
    data class GoalSpendKey(val id: String) : TransactionKey("spend:$id")

    companion object {
        fun parse(s: String): TransactionKey {
            if (s == "savings") return SavingsKey
            val i = s.indexOf(':')
            require(i > 0) { "not a transaction key: $s" }
            val id = s.substring(i + 1)
            return when (s.substring(0, i)) {
                "goal" -> GoalKey(id)
                "cat" -> CategoryKey(id)
                "rec" -> RecurringKey(id)
                "acct" -> AccountKey(id)
                "spend" -> GoalSpendKey(id)
                else -> throw IllegalArgumentException("not a transaction key: $s")
            }
        }
    }
}

/** §12's tunable, single-sourced: pair deletion's window, measured on `loggedAt`. */
val PAIR_WINDOW = 48.hours

/** What a write path hands the ledger. `amount` is always the delta to the target (§3.6). */
data class Movement(
    val amount: Money,
    val key: TransactionKey = TransactionKey.SavingsKey,
    val targetLabel: String = "Savings",
    val payee: String? = null,
    val isIncome: Boolean = false,
    /** null = today (device-local day, §12). */
    val date: LocalDate? = null,
)

/** The engine's clock, injected so every rule about "now" is testable. */
interface Clock {
    fun today(): LocalDate
    fun now(): Instant
}

object Ledger {

    /**
     * §3.6's sign table, in one place and in one direction: the Savings movement is derived
     * from the key, never stored. `acct:` rows are not part of the pool (§7.1).
     */
    fun savingsEffect(tx: Transaction): Money = when (TransactionKey.parse(tx.key)) {
        TransactionKey.SavingsKey -> tx.amount
        is TransactionKey.AccountKey -> 0
        is TransactionKey.GoalSpendKey -> 0      // counted when it went into the Goal
        else -> -tx.amount
    }

    /**
     * The one writer for every path that both logs and moves money. Returns the new state and
     * the row it wrote. Any movement that touches Savings (or any `savings` row) ends §3.1's null
     * state, treating the starting figure as 0.
     */
    fun logAndMove(d: ProfileData, m: Movement, clock: Clock, id: String): Pair<ProfileData, Transaction> {
        val tx = Transaction(
            id = id,
            profileId = d.profileId,
            date = m.date ?: clock.today(),
            loggedAt = clock.now(),
            payee = m.payee?.takeIf { it.isNotBlank() },
            amount = m.amount,
            key = m.key.stored,
            targetLabel = m.targetLabel,
            isIncome = m.isIncome,
        )
        val effect = savingsEffect(tx)
        var next = d.copy(transactions = d.transactions + tx)
        if (effect != 0L || m.key == TransactionKey.SavingsKey) {
            next = next.copy(savings = next.savings.copy(amount = (next.savings.amount ?: 0) + effect))
        }
        if (m.isIncome) {
            // a backdated entry never moves the marker back
            val last = next.settings.lastIncomeLoggedDate
            next = next.copy(settings = next.settings.copy(lastIncomeLoggedDate = if (last == null || tx.date > last) tx.date else last))
        }
        return next to tx
    }

    /** §7.1: Account-only movement -- logged, structurally never touching Savings. */
    fun logAccountEdit(d: ProfileData, accountId: String, accountName: String, delta: Money, payee: String?, clock: Clock, id: String, date: LocalDate? = null): ProfileData =
        logAndMove(d, Movement(delta, TransactionKey.AccountKey(accountId), accountName, payee, date = date), clock, id).first

    /**
     * §7.3's five conditions, evaluated ONCE against history as it stands. Returns each
     * eligible row's counterpart id (both directions). Ordering is by `loggedAt`, not `date`
     * -- ordering by date would reopen the backdating loophole the 48-hour rule closes.
     */
    fun pairEligibility(transactions: List<Transaction>): Map<String, String> {
        val pairs = HashMap<String, String>()
        transactions
            .filter { !it.key.startsWith("rec:") }                       // condition 5
            .groupBy { it.key }
            .values
            .forEach { rows ->
                val sorted = rows.sortedWith(compareBy({ it.loggedAt }, { it.id }))   // condition 2
                var i = 0
                while (i < sorted.size - 1) {
                    val a = sorted[i]; val b = sorted[i + 1]
                    // conditions 1 and 4; 3 holds by the skip below, and the sort keeps the gap non-negative
                    val ok = a.amount != 0L && a.amount + b.amount == 0L && b.loggedAt - a.loggedAt <= PAIR_WINDOW
                    if (ok) {
                        pairs[a.id] = b.id; pairs[b.id] = a.id
                        i += 2                                                      // neither row can pair again
                    } else i += 1
                }
            }
        return pairs
    }

    /**
     * §7.3: delete a pair. The two rows net to zero on their target and on Savings, so removing
     * them IS reversing their recorded effect. Eligibility is not re-derived here; the caller
     * drops the two ids from the map it holds.
     *
     * The logged-income marker is recomputed only when a removed row was income, from the income
     * rows on or after `incomeEnabledDate`, so §3.3's resets (toggle, auto-deposit, cadence)
     * survive. The deposit marker is never lowered: re-opening a processed payday would let the
     * next catch-up deposit it again and silently undo the deletion.
     */
    fun deletePair(d: ProfileData, id: String, otherId: String): ProfileData {
        val ids = setOf(id, otherId)
        val survivors = d.transactions.filter { it.id !in ids }
        val s = d.settings
        val floor = s.incomeEnabledDate
        val logged = if (!s.incomeEnabled || d.transactions.none { it.id in ids && it.isIncome }) s.lastIncomeLoggedDate
            else survivors.filter { it.isIncome && (floor == null || it.date >= floor) }.maxOfOrNull { it.date } ?: floor
        return d.copy(transactions = survivors, settings = s.copy(lastIncomeLoggedDate = logged))
    }

    /** §3.3's auto-deposit rows are recognised by this payee, in the ledger and in §7.3. */
    const val AUTO_DEPOSIT_PAYEE = "Auto-deposit"
}

/** §3.6: the arithmetic reading of Savings -- null (§3.1's third state) reads as 0 here only. */
val ProfileData.savingsAmount: Money get() = savings.amount ?: 0
