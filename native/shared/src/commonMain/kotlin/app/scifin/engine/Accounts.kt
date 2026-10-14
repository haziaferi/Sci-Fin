package app.scifin.engine

import app.scifin.model.Account
import app.scifin.model.AccountType
import app.scifin.model.Cadence
import app.scifin.model.DateMode
import app.scifin.model.Goal
import app.scifin.model.GoalMode
import app.scifin.model.Money
import app.scifin.model.ProfileData
import app.scifin.model.RateType
import app.scifin.model.RecurringExpense
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

/*
 * §7.2 -- Accounts (Credit and Loan): balance edits, quick-add directions, the paired
 * Savings outflow, retype clearing, cascade-on-delete. §9.1 -- the Loan calculator's three
 * rate types and its two hand-offs. §9.2 -- Credit's payoff projections and the fixed-payment
 * commit. Accounts are informational and Savings-vs-Owed-only (§7.1): their rows never move
 * Savings.
 */

/** §9.1's loan draft: the prototype's starting values. */
data class LoanDraft(val principal: Money = moneyOf(12000.0), val rate: Double = 0.065, val termMonths: Int = 48, val rateType: RateType = RateType.FIXED)

/** §9.1's arithmetic for one set of inputs. Rates are fractions (0.065 = 6.5 %). */
data class LoanMath(val monthly: Money, val totalRepaid: Money, val totalInterest: Money, val principalRemaining: Money, val months: Int)

/** §9.2: months to clear a balance, or null when the payment never clears it. */
data class Payoff(val months: Int?, val interest: Money?)

data class MinimumPayoff(val months: Int?, val interest: Money?, val first: Money)

object Accounts {

    /** §12's tunables: the minimum-payment model. A stated assumption, named in the panel's copy. */
    const val MIN_PAYMENT_PCT = 0.02
    val MIN_PAYMENT_FLOOR: Money = moneyOf(25.0)

    // ---- §9.1 --------------------------------------------------------------------------------

    /** Interest-only pays interest and nothing else for the term; the other two amortise. */
    fun loanMath(principal: Money, annualRate: Double, termMonths: Int, rateType: RateType): LoanMath {
        val p = maxOf(0L, principal).toDouble()
        val n = maxOf(1, termMonths)
        val i = maxOf(0.0, annualRate) / 12.0
        if (rateType == RateType.INTEREST_ONLY) {
            val monthly = (p * i).roundToLong()
            return LoanMath(monthly, monthly * n, monthly * n, principal, n)
        }
        val monthly = if (i == 0.0) ceil(p / n).toLong() else (p * i / (1 - (1 + i).pow(-n))).roundToLong()
        return LoanMath(monthly, monthly * n, monthly * n - principal, 0, n)
    }

    /** "Save for it": a by-date Goal for the principal over the term, nothing else created. */
    fun saveForItGoal(principal: Money, math: LoanMath, name: String, today: LocalDate, id: String, profileId: String): Goal =
        Goals.rederive(Goal(id, profileId, name.trim().ifEmpty { "Savings goal" }, "piggy-bank", saved = 0, target = principal,
            targetDate = today.plusMonthsClamped(math.months), mode = GoalMode.DATE), today)

    /**
     * "Get loan": a Loan Account plus its payment as a Recurring Expense -- obligatory and
     * atomic, linked both ways, the payment first due one month after origination, `endDate`
     * at the term's end, and §5.3's opening row on the Account.
     */
    fun getLoan(
        d: ProfileData, principal: Money, annualRate: Double, termMonths: Int, rateType: RateType,
        accountName: String, expenseName: String, autoUpdate: Boolean, clock: Clock, ids: () -> String,
    ): ProfileData {
        val m = loanMath(principal, annualRate, termMonths, rateType)
        val today = clock.today()
        val anchorDay = today.day
        val firstDue = today.plusMonthsClamped(1, anchorDay)
        val acctId = ids(); val expId = ids()
        val acct = Account(
            acctId, d.profileId, accountName.trim().ifEmpty { "Loan" }, "landmark", AccountType.LOAN, balance = principal,
            linkedRecurringExpenseId = expId, autoUpdateFromPayments = autoUpdate, principal = principal,
            interestRate = annualRate, termMonths = m.months, rateType = rateType, originationDate = today,
        )
        val exp = RecurringExpense(
            expId, d.profileId, expenseName.trim().ifEmpty { "Loan payment" }, "landmark", m.monthly, Cadence.MONTHLY,
            firstDueDate = firstDue, dueDate = firstDue, dueDateAnchorDay = anchorDay, dateMode = DateMode.FIXED_DATE,
            lastSweptMonth = today.ymKey(), endDate = today.plusMonthsClamped(m.months, anchorDay), linkedAccountId = acctId,
        )
        var next = d.copy(accounts = d.accounts + acct, recurringExpenses = d.recurringExpenses + exp)
        next = Ledger.logAccountEdit(next, acct.id, acct.name, principal, OPENING_BALANCE, clock, ids())
        return next
    }

    // ---- §9.2 --------------------------------------------------------------------------------

    /** Months to clear at a constant payment, walked so the final partial payment is real. */
    fun payoffAtFixedPayment(balance: Money, monthlyRate: Double, payment: Money): Payoff {
        if (balance <= 0) return Payoff(0, 0)
        if (monthlyRate <= 0) return Payoff(ceil(balance.toDouble() / payment).toInt(), 0)
        if (payment.toDouble() <= balance * monthlyRate) return Payoff(null, null)
        var bal = balance.toDouble(); var interest = 0.0; var months = 0
        while (bal > 0.5 && months < 1200) {
            val int = bal * monthlyRate
            interest += int
            bal = bal + int - minOf(payment.toDouble(), bal + int)
            months++
        }
        if (bal > 0.5) return Payoff(null, null)
        return Payoff(months, interest.roundToLong())
    }

    /** The minimum is a percentage of a moving balance, so it is walked; there is no closed form. */
    fun payoffAtMinimum(balance: Money, monthlyRate: Double): MinimumPayoff {
        if (balance <= 0) return MinimumPayoff(0, 0, 0)
        val first = maxOf(MIN_PAYMENT_FLOOR.toDouble(), balance * MIN_PAYMENT_PCT).roundToLong()
        var bal = balance.toDouble(); var interest = 0.0; var months = 0
        while (bal > 0.5 && months < 1200) {
            val int = bal * monthlyRate
            val pay = minOf(bal + int, maxOf(MIN_PAYMENT_FLOOR.toDouble(), bal * MIN_PAYMENT_PCT))
            if (pay <= int) return MinimumPayoff(null, null, first)
            interest += int
            bal = bal + int - pay
            months++
        }
        if (bal > 0.5) return MinimumPayoff(null, null, first)
        return MinimumPayoff(months, interest.roundToLong(), first)
    }

    /** §9.2's Surface 1 commit: a fixed payment becomes a monthly Recurring Expense linked both ways, no auto-update. */
    fun commitFixedPayment(d: ProfileData, accountId: String, amount: Money, name: String, clock: Clock, expId: String): ProfileData {
        val a = d.accounts.first { it.id == accountId }
        val today = clock.today()
        val firstDue = today.plusMonthsClamped(1, today.day)
        val e = RecurringExpense(
            expId, d.profileId, name.trim().ifEmpty { "${a.name} payment" }, "credit-card", amount, Cadence.MONTHLY,
            firstDueDate = firstDue, dueDate = firstDue, dueDateAnchorDay = today.day, dateMode = DateMode.FIXED_DATE,
            lastSweptMonth = today.ymKey(), linkedAccountId = a.id,
        )
        // one link at a time: an expense the account linked before loses its back-link, or deleting it would take the account
        return d.copy(
            recurringExpenses = d.recurringExpenses.map { if (it.linkedAccountId == a.id) it.copy(linkedAccountId = null) else it } + e,
            accounts = d.accounts.map { if (it.id == a.id) it.copy(linkedRecurringExpenseId = e.id, autoUpdateFromPayments = false) else it },
        )
    }

    // ---- §7.2 --------------------------------------------------------------------------------

    /** Direct balance edit: logs the delta as an `acct:` row; Savings untouched. */
    fun balanceEdit(d: ProfileData, accountId: String, newBalance: Money, payee: String?, clock: Clock, txId: String, date: LocalDate? = null): ProfileData {
        val a = d.accounts.first { it.id == accountId }
        val delta = newBalance - a.balance
        val next = d.copy(accounts = d.accounts.map { if (it.id == accountId) it.copy(balance = newBalance) else it })
        if (delta == 0L) return next
        return Ledger.logAccountEdit(next, a.id, a.name, delta, payee ?: Writes.CORRECTION, clock, txId, date)
    }

    /** §3.6: CHARGE (Credit) and INCREASE (Loan) raise the balance, PAYMENT lowers it. */
    enum class Direction { CHARGE, INCREASE, PAYMENT }

    /** §7.2: the paired Savings outflow defaults on for a manual payment unless a linked expense already sweeps it. */
    fun pairedOutflowDefault(a: Account): Boolean = a.linkedRecurringExpenseId == null

    /**
     * Account quick-add. On a payment with [pairSavings] the matching Savings outflow is
     * written too: the `acct:` row moved the tracked balance and no Savings; this is the other half.
     */
    fun quickAdd(d: ProfileData, accountId: String, amount: Money, direction: Direction, payee: String?, pairSavings: Boolean, clock: Clock, date: LocalDate? = null, rowLabel: String? = null, ids: () -> String): ProfileData {
        val a = d.accounts.first { it.id == accountId }
        val signed = if (direction == Direction.PAYMENT) -amount else amount
        // [rowLabel] names the account's own row when there is no note (§3.6's entry passes its verb); the paired
        // Savings row keeps "Payment to <account>"
        var next = balanceEdit(d, accountId, a.balance + signed, payee ?: rowLabel, clock, ids(), date)
        if (direction == Direction.PAYMENT && pairSavings) {
            next = Writes.savingsQuickAdd(next, -amount, payee ?: "Payment to ${a.name}", false, clock, ids(), date)
        }
        return next
    }

    /** §7.2: a Loan with null inputs cannot auto-update -- the decrement has nothing to compute from. */
    fun loanInputsComplete(a: Account): Boolean = a.type == AccountType.LOAN && a.interestRate != null

    /** §7.2's retype rule: retyping clears the state belonging to the type it LEAVES. */
    fun retype(a: Account, next: AccountType): Account {
        if (a.type == next) return a
        return if (next == AccountType.CREDIT) a.copy(
            type = next, icon = "credit-card", autoUpdateFromPayments = false,
            principal = null, interestRate = null, termMonths = null, rateType = null, originationDate = null,
        ) else a.copy(type = next, icon = "landmark", apr = null)
    }

    /** §7.2: a Loan is finished when its linked expense is. */
    fun finished(d: ProfileData, a: Account): Boolean {
        if (a.type != AccountType.LOAN || a.linkedRecurringExpenseId == null) return false
        return d.recurringExpenses.firstOrNull { it.id == a.linkedRecurringExpenseId }?.finishedAt != null
    }

    /**
     * §7.2's cascade from the Account side: a closing row, the Account goes, and so does the
     * linked expense -- with §3.11's reversal when the person took it in the same prompt
     * ([reverse]), exactly as deleting from the expense's side offers.
     */
    fun deleteCascade(d: ProfileData, accountId: String, clock: Clock, reverse: Boolean = false, ids: () -> String): ProfileData {
        val a = d.accounts.firstOrNull { it.id == accountId } ?: return d
        val linked = a.linkedRecurringExpenseId?.let { eid -> d.recurringExpenses.firstOrNull { it.id == eid } }
        var next = if (reverse && linked != null) Recurring.applyReversal(d, linked, clock, ids) else d
        next = close(next, a, clock, ids)
        a.linkedRecurringExpenseId?.let { eid -> next = next.copy(recurringExpenses = next.recurringExpenses.filter { it.id != eid }) }
        return next
    }

    /**
     * §7.2's retype cascade: a Loan becoming Credit takes its linked payment with it -- a Credit
     * account cannot keep a Loan's tracked figure honest -- with §3.11's reversal if accepted;
     * the account itself stays, re-typed, and nothing links to it afterwards.
     */
    fun retypeToCreditCascade(d: ProfileData, draft: Account, reverse: Boolean, clock: Clock, ids: () -> String): ProfileData {
        val before = d.accounts.firstOrNull { it.id == draft.id } ?: return d
        val linked = before.linkedRecurringExpenseId?.let { eid -> d.recurringExpenses.firstOrNull { it.id == eid } }
        var next = if (reverse && linked != null) Recurring.applyReversal(d, linked, clock, ids) else d
        if (linked != null) next = next.copy(recurringExpenses = next.recurringExpenses.filter { it.id != linked.id })
        return AccountEditing.save(next, draft.copy(linkedRecurringExpenseId = null), clock, ids)
    }

    /** §5.3: an Account leaves with a closing row for what it still showed. */
    internal fun close(d: ProfileData, a: Account, clock: Clock, ids: () -> String): ProfileData {
        val next = if (a.balance != 0L) Ledger.logAccountEdit(d, a.id, a.name, -a.balance, ACCOUNT_CLOSED, clock, ids()) else d
        return next.copy(accounts = next.accounts.filter { it.id != a.id })
    }

    /** Whether saving [draft] over what is stored is §7.2's Loan-to-Credit retype with a payment to cascade. */
    fun retypeNeedsCascade(d: ProfileData, draft: Account): Boolean {
        val before = d.accounts.firstOrNull { it.id == draft.id } ?: return false
        return before.type == AccountType.LOAN && draft.type == AccountType.CREDIT &&
            before.linkedRecurringExpenseId?.let { eid -> d.recurringExpenses.any { it.id == eid } } == true
    }

    const val OPENING_BALANCE = "Opening balance"
    const val ACCOUNT_CLOSED = "Account closed"
}

/** §9.2's copy for a months figure: "never at this rate", "cleared at once", "2 years 3 months". */
fun monthsLabel(m: Int?): String = when {
    m == null -> "never at this rate"
    m == 0 -> "cleared at once"
    m < 12 -> "$m month" + (if (m == 1) "" else "s")
    else -> {
        val y = m / 12; val r = m % 12
        "$y year" + (if (y == 1) "" else "s") + (if (r > 0) " $r month" + (if (r == 1) "" else "s") else "")
    }
}
