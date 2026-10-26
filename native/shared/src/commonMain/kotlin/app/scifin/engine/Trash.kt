package app.scifin.engine

import app.scifin.model.Account
import app.scifin.model.Category
import app.scifin.model.Goal
import app.scifin.model.ImportState
import app.scifin.model.Money
import app.scifin.model.Profile
import app.scifin.model.ProfileData
import app.scifin.model.RecurringExpense
import app.scifin.model.Savings
import app.scifin.model.Settings
import app.scifin.model.Shift
import app.scifin.model.Transaction
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * §3.6 / §3.11 / §7.2 / §7.3 / §8.3: the Trash (owner, 2026-10-19/20). A delete still happens at once and still
 * asks the money question when it does -- a goal's saved money, this period's sweep -- but what it removed is kept
 * for 30 days, and Restore puts it back. While in the Trash an item is inert: it is out of ProfileData, so it
 * sweeps nothing, reminds of nothing and counts towards no figure.
 *
 * Restore never rewrites history. Any money the delete moved is moved back by a new row through the same engine
 * writers ("Restored from the Trash"), so the ledger shows the delete and the restore both, and every stored
 * figure -- Savings, a goal's saved, an account's balance -- is what it was.
 */

/** What one delete removed, and the money it moved, so Restore can undo exactly that. */
@Serializable
data class TrashPayload(
    val goals: List<Goal> = emptyList(),
    val categories: List<Category> = emptyList(),
    val expenses: List<RecurringExpense> = emptyList(),
    val accounts: List<Account> = emptyList(),
    /** §7.3's cancelled pair. */
    val transactions: List<Transaction> = emptyList(),
    /** Goal id -> the saved money returned to Savings by the delete. */
    val goalReturned: Map<String, Long> = emptyMap(),
    /** Expense id -> this period's sweep reversed back to Savings by the delete. */
    val reversed: Map<String, Long> = emptyMap(),
    /** §8.3: a whole Profile. */
    val profile: TrashedProfile? = null,
)

/** A Profile's whole world, in serialisable pieces. */
@Serializable
data class TrashedProfile(
    val profile: Profile, val savings: Savings, val settings: Settings, val importState: ImportState,
    val goals: List<Goal>, val categories: List<Category>, val expenses: List<RecurringExpense>,
    val accounts: List<Account>, val transactions: List<Transaction>,
    val shifts: List<Shift> = emptyList(),
) {
    fun data() = ProfileData(profile, savings, settings, importState, goals, categories, expenses, accounts, transactions, shifts)

    companion object {
        fun of(d: ProfileData) = TrashedProfile(d.profile, d.savings, d.settings, d.importState, d.goals, d.categories, d.recurringExpenses, d.accounts, d.transactions, d.shifts)
    }
}

object Trash {
    const val KEPT_DAYS = 30
    /** The notice that items are about to go for good (owner, 2026-10-20). */
    const val WARN_DAYS = 20
    const val RESTORED = "Restored from the Trash"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    fun encode(p: TrashPayload) = json.encodeToString(TrashPayload.serializer(), p)
    fun decode(s: String): TrashPayload? = runCatching { json.decodeFromString(TrashPayload.serializer(), s) }.getOrNull()

    fun goesOn(deletedOn: LocalDate): LocalDate = deletedOn.plus(DatePeriod(days = KEPT_DAYS))
    fun expired(deletedOn: LocalDate, today: LocalDate) = deletedOn.daysUntil(today) >= KEPT_DAYS
    fun warnDue(deletedOn: LocalDate, today: LocalDate) = deletedOn.daysUntil(today) >= WARN_DAYS

    // ---- the deletes, each returning the state after it and what it removed --------------------------

    fun deleteGoal(d: ProfileData, id: String, returnSaved: Boolean, clock: Clock, ids: () -> String): Pair<ProfileData, TrashPayload>? {
        val g = d.goals.firstOrNull { it.id == id } ?: return null
        val after = GoalEditing.delete(d, id, returnSaved, clock, ids)
        return after to TrashPayload(goals = listOf(g), goalReturned = if (returnSaved && g.saved > 0) mapOf(id to g.saved) else emptyMap())
    }

    fun deleteCategory(d: ProfileData, id: String): Pair<ProfileData, TrashPayload>? {
        val c = d.categories.firstOrNull { it.id == id } ?: return null
        return CategoryEditing.delete(d, id) to TrashPayload(categories = listOf(c))
    }

    /** A payment, with the account it was created with (§7.2: they go, and come back, as one). */
    fun deleteExpense(d: ProfileData, id: String, reverse: Boolean, clock: Clock, ids: () -> String): Pair<ProfileData, TrashPayload>? {
        val e = d.recurringExpenses.firstOrNull { it.id == id } ?: return null
        val back = if (reverse) Recurring.reversalAmount(d, e, clock.today()) else 0L
        val acct = e.linkedAccountId?.let { a -> d.accounts.firstOrNull { it.id == a } }
        val after = Recurring.delete(d, id, reverse, clock, ids)
        return after to TrashPayload(expenses = listOf(e), accounts = listOfNotNull(acct), reversed = if (back > 0) mapOf(id to back) else emptyMap())
    }

    fun deleteAccount(d: ProfileData, id: String, reverse: Boolean, clock: Clock, ids: () -> String): Pair<ProfileData, TrashPayload>? {
        val a = d.accounts.firstOrNull { it.id == id } ?: return null
        val linked = a.linkedRecurringExpenseId?.let { e -> d.recurringExpenses.firstOrNull { it.id == e } }
        val back = if (reverse && linked != null) Recurring.reversalAmount(d, linked, clock.today()) else 0L
        val after = Accounts.deleteCascade(d, id, clock, reverse, ids)
        return after to TrashPayload(accounts = listOf(a), expenses = listOfNotNull(linked), reversed = if (back > 0 && linked != null) mapOf(linked.id to back) else emptyMap())
    }

    fun deletePair(d: ProfileData, id: String, otherId: String): Pair<ProfileData, TrashPayload>? {
        val rows = d.transactions.filter { it.id == id || it.id == otherId }
        if (rows.size != 2) return null
        return Ledger.deletePair(d, id, otherId) to TrashPayload(transactions = rows)
    }

    // ---- Restore ----------------------------------------------------------------------------------------

    /**
     * Puts [p]'s items back into [d] and moves back any money their delete moved. Anything whose id is already
     * present again is left as it is. A payment resumes from today: it is marked swept for this month whatever
     * it did before, so the months it spent in the Trash are not caught up (owner, 2026-10-20: inert).
     */
    fun restore(d: ProfileData, p: TrashPayload, clock: Clock, ids: () -> String): ProfileData {
        var n = d
        val today = clock.today()
        for (g in p.goals) {
            if (n.goals.any { it.id == g.id }) continue
            val returned = p.goalReturned[g.id] ?: 0L
            n = n.copy(goals = n.goals + g.copy(profileId = n.profileId, saved = g.saved - returned))
            if (returned > 0) n = Writes.goalSavedEdit(n, g.id, g.saved, RESTORED, clock, ids())
        }
        for (c in p.categories) if (n.categories.none { it.id == c.id }) n = n.copy(categories = n.categories + c.copy(profileId = n.profileId))
        for (a in p.accounts) {
            if (n.accounts.any { it.id == a.id }) continue
            n = n.copy(accounts = n.accounts + a.copy(profileId = n.profileId))
            // §5.3's closing row took the balance off; this row puts it back on, Savings untouched (acct: rows)
            if (a.balance != 0L) n = Ledger.logAccountEdit(n, a.id, a.name, a.balance, RESTORED, clock, ids())
        }
        for (e in p.expenses) {
            if (n.recurringExpenses.any { it.id == e.id }) continue
            val swept = maxOf(e.lastSweptMonth ?: "", today.ymKey())
            n = n.copy(recurringExpenses = n.recurringExpenses + e.copy(profileId = n.profileId, lastSweptMonth = swept))
            p.reversed[e.id]?.takeIf { it > 0 }?.let { back ->
                n = Ledger.logAndMove(n, Movement(back, TransactionKey.RecurringKey(e.id), e.name, RESTORED), clock, ids()).first
            }
        }
        if (p.expenses.isNotEmpty()) n = Recurring.advanceDueDates(n, today)
        val missing = p.transactions.filter { t -> n.transactions.none { it.id == t.id } }
        if (missing.isNotEmpty()) {
            n = n.copy(transactions = (n.transactions + missing.map { it.copy(profileId = n.profileId) }).sortedWith(compareBy({ it.date }, { it.loggedAt })))
            val s = n.settings
            val income = missing.filter { it.isIncome }.maxOfOrNull { it.date }
            if (s.incomeEnabled && income != null && (s.lastIncomeLoggedDate == null || income > s.lastIncomeLoggedDate)) {
                n = n.copy(settings = s.copy(lastIncomeLoggedDate = income))
            }
        }
        return n
    }

    /** What the Trash row says of an item, money included: what restoring will move. */
    fun moneyMoved(p: TrashPayload): Money = p.goalReturned.values.sum() + p.reversed.values.sum()
}
