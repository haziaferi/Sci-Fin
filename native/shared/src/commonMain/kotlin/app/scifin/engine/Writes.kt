package app.scifin.engine

import app.scifin.model.Money
import app.scifin.model.ProfileData
import kotlinx.datetime.LocalDate

/*
 * §3.6's entry points, each a thin wrapper over [Ledger.logAndMove]. Quick-add and the full
 * editor are two doors into the same mechanism, not two mechanisms: a direct edit logs the
 * signed DELTA (never the new absolute value -- an absolute row would be a second competing
 * statement of truth in a log §12 defines as history only, and would break §5.1's lot
 * arithmetic permanently), and moves Savings by the inverse of it exactly as quick-add does.
 */
object Writes {

    /** Money into a Goal; Savings moves by the inverse (§3.6). */
    fun goalQuickAdd(d: ProfileData, goalId: String, amount: Money, payee: String?, clock: Clock, txId: String, date: LocalDate? = null): ProfileData {
        val g = d.goals.first { it.id == goalId }
        val next = d.copy(goals = d.goals.map { if (it.id == goalId) it.copy(saved = it.saved + amount) else it })
        return Ledger.logAndMove(next, Movement(amount, TransactionKey.GoalKey(goalId), g.name, payee ?: "Toward ${g.name}", date = date), clock, txId).first
    }

    /** §3.6: spent from a Goal, at most what it holds; Savings is untouched (the money left it
     *  when it went into the Goal). */
    fun goalSpend(d: ProfileData, goalId: String, amount: Money, payee: String?, clock: Clock, txId: String, date: LocalDate? = null): ProfileData {
        val g = d.goals.first { it.id == goalId }
        require(amount in 1..g.saved) { "a Goal can spend at most what it holds" }
        val next = d.copy(goals = d.goals.map { if (it.id == goalId) it.copy(saved = it.saved - amount) else it })
        return Ledger.logAndMove(next, Movement(-amount, TransactionKey.GoalSpendKey(goalId), g.name, payee ?: "Spent from ${g.name}", date = date), clock, txId).first
    }

    fun categoryQuickAdd(d: ProfileData, categoryId: String, amount: Money, payee: String?, clock: Clock, txId: String, date: LocalDate? = null): ProfileData {
        val c = d.categories.first { it.id == categoryId }
        val next = d.copy(categories = d.categories.map { if (it.id == categoryId) it.copy(spent = it.spent + amount) else it })
        return Ledger.logAndMove(next, Movement(amount, TransactionKey.CategoryKey(categoryId), c.name, payee ?: c.name, date = date), clock, txId).first
    }

    /** [signed] is positive from the entry (Add/Income) and import, and negative only for an Account payment that also
     *  leaves Savings (§7.2). `isIncome` comes from the Income verb (§3.6), never a checkbox. */
    fun savingsQuickAdd(d: ProfileData, signed: Money, payee: String?, isIncome: Boolean, clock: Clock, txId: String, date: LocalDate? = null): ProfileData =
        Ledger.logAndMove(d, Movement(signed, TransactionKey.SavingsKey, "Savings", payee, isIncome, date), clock, txId).first

    /** §3.6's Savings full editor: logs the delta, never tags income; a no-op delta still
     *  ends the null state by writing the figure. */
    fun savingsDirectEdit(d: ProfileData, newValue: Money, payee: String?, clock: Clock, txId: String): ProfileData {
        val delta = newValue - d.savingsAmount
        if (delta == 0L) return d.copy(savings = d.savings.copy(amount = newValue))
        return Ledger.logAndMove(d, Movement(delta, TransactionKey.SavingsKey, "Savings", payee ?: CORRECTION), clock, txId).first
    }

    /** Direct edit of `Goal.saved`: any delta, either direction, moves Savings by the inverse. */
    fun goalSavedEdit(d: ProfileData, goalId: String, newValue: Money, payee: String?, clock: Clock, txId: String): ProfileData {
        val g = d.goals.first { it.id == goalId }
        require(newValue >= 0) { "a Goal cannot hold less than nothing" }
        val delta = newValue - g.saved
        val next = d.copy(goals = d.goals.map { if (it.id == goalId) it.copy(saved = newValue) else it })
        if (delta == 0L) return next
        return Ledger.logAndMove(next, Movement(delta, TransactionKey.GoalKey(goalId), g.name, payee ?: CORRECTION), clock, txId).first
    }

    /** Direct edit of `Category.spent`: same rule, same mechanism. */
    fun categorySpentEdit(d: ProfileData, categoryId: String, newValue: Money, payee: String?, clock: Clock, txId: String): ProfileData {
        val c = d.categories.first { it.id == categoryId }
        val delta = newValue - c.spent
        val next = d.copy(categories = d.categories.map { if (it.id == categoryId) it.copy(spent = newValue) else it })
        if (delta == 0L) return next
        return Ledger.logAndMove(next, Movement(delta, TransactionKey.CategoryKey(categoryId), c.name, payee ?: CORRECTION), clock, txId).first
    }

    const val CORRECTION = "Correction"
    const val STARTING_BALANCE = "Starting balance"
}
