package app.scifin.engine

import app.scifin.i18n.tr
import app.scifin.i18n.trf
import app.scifin.model.AccountType
import app.scifin.model.Money
import app.scifin.model.ProfileData
import kotlinx.datetime.LocalDate

/**
 * §3.6's entry: one sentence for every editable element, opened by each row's + (on that element) or by the floating + (on the last area and target
 * used). The area word comes first and every underlined word is its own control:
 *
 *   Savings  – Add / Income € [amount] today, from [who or where]
 *   Goal     – Add € [amount] to [goal] today, for [what]      ·  Spent € [amount] from [goal] today, at [where]
 *   Category – Spent € [amount] on [category] today, at [where]
 *   Account  – Charged € [amount] on [card] today, at [where]  ·  Paid off € [amount] on [account] today, also / not from Savings
 *              (a Loan: Paid off / Increased)
 *
 * Savings has no Money out (§3.6): every expense is spent on a Category, from a Goal or on an Account, so nothing
 * leaves untracked. Categories are reduced only in their own editor; a Goal is reduced there or by Spent, which
 * spends at most what it holds.
 *
 * A target is a key: `savings`, `goal:<id>`, `cat:<id>`, `acct:<id>`. Wording here is the single source; the sheet
 * renders it and the tests read it.
 */
object Entry {
    enum class Area {
        SAVINGS, GOAL, CATEGORY, ACCOUNT;

        val word: String get() = when (this) { SAVINGS -> tr("Savings"); GOAL -> tr("Goal"); CATEGORY -> tr("Category"); ACCOUNT -> tr("Account") }
    }

    fun areaOf(target: String): Area = when {
        target.startsWith("goal:") -> Area.GOAL
        target.startsWith("cat:") -> Area.CATEGORY
        target.startsWith("acct:") -> Area.ACCOUNT
        else -> Area.SAVINGS
    }

    /** The targets in an area, in the order the picker lists them. */
    fun targetsIn(d: ProfileData, area: Area): List<Pair<String, String>> = when (area) {
        Area.SAVINGS -> listOf("savings" to tr("Savings"))
        Area.GOAL -> d.goals.map { "goal:${it.id}" to it.name }
        Area.CATEGORY -> d.categories.map { "cat:${it.id}" to it.name }
        Area.ACCOUNT -> d.accounts.map { "acct:${it.id}" to it.name }
    }

    fun exists(d: ProfileData, target: String): Boolean = targetsIn(d, areaOf(target)).any { it.first == target }

    fun nameOf(d: ProfileData, target: String): String = targetsIn(d, areaOf(target)).firstOrNull { it.first == target }?.second ?: tr("Savings")

    /** Before anything has been logged: the first Category (where most entries go), else Savings. */
    fun defaultTarget(d: ProfileData): String = d.categories.firstOrNull()?.let { "cat:${it.id}" } ?: "savings"

    /** The direction words; null where the target has one direction, so there is no control to show. */
    fun directions(d: ProfileData, target: String): List<Pair<String, String>>? = when (areaOf(target)) {
        Area.SAVINGS -> if (d.settings.incomeEnabled) listOf("in" to tr("Add"), "income" to tr("Income")) else null
        Area.GOAL -> listOf("in" to tr("Add"), "spend" to tr("Spent"))
        Area.CATEGORY -> null
        Area.ACCOUNT -> if (account(d, target)?.type == AccountType.CREDIT) listOf("charge" to tr("Charged"), "payment" to tr("Paid off"))
            else listOf("payment" to tr("Paid off"), "increase" to tr("Increased"))
    }

    fun firstDirection(d: ProfileData, target: String): String = directions(d, target)?.first()?.first ?: when (areaOf(target)) {
        Area.CATEGORY -> "spend"; else -> "in"
    }

    /** The verb when there is no control: a Category's "Spent", Savings' "Add" while Income is off. */
    fun fixedVerb(target: String): String = if (areaOf(target) == Area.CATEGORY) tr("Spent") else tr("Add")

    /** The word before the target; Savings has none (the area word already names it). */
    fun preposition(target: String, direction: String): String? = when (areaOf(target)) {
        Area.SAVINGS -> null
        Area.GOAL -> if (direction == "spend") tr("from") else tr("to")
        Area.CATEGORY, Area.ACCOUNT -> tr("on")
    }

    /** The note's word, or null where the log already says it ("Payment to <account>"). */
    fun noteWord(target: String, direction: String): String? = when (areaOf(target)) {
        Area.SAVINGS -> tr("from")
        Area.GOAL -> if (direction == "spend") tr("at") else tr("for")
        Area.CATEGORY -> tr("at")
        Area.ACCOUNT -> if (direction == "payment") null else tr("at")
    }

    /** [word] is [noteWord]'s, already in the current language. */
    fun noteHint(word: String): String = when (word) { tr("from") -> tr("who or where"); tr("for") -> tr("what"); else -> tr("where") }

    /** §7.2: a payment also leaves Savings by default only when no recurring expense already moves it. */
    fun pairDefault(d: ProfileData, target: String): Boolean = account(d, target)?.let { Accounts.pairedOutflowDefault(it) } ?: false

    /** What will move, for the readout ("Holidays +€50 · Savings −€50"); null while nothing can be written. */
    fun moves(d: ProfileData, target: String, direction: String, amount: Money?, pair: Boolean): List<Pair<String, Money>>? {
        if (amount == null || amount <= 0 || !exists(d, target)) return null
        val name = nameOf(d, target)
        return when (areaOf(target)) {
            Area.SAVINGS -> listOf(tr("Savings") to amount)
            Area.GOAL -> if (direction == "spend") (if (amount > goalHolds(d, target)) null else listOf(name to -amount))
                else listOf(name to amount, tr("Savings") to -amount)
            Area.CATEGORY -> listOf(trf("{0} spent", name) to amount, tr("Savings") to -amount)
            Area.ACCOUNT -> {
                if (directions(d, target)?.none { it.first == direction } != false) return null
                val a = account(d, target)!!
                val owed = if (a.type == AccountType.CREDIT) trf("{0} owed", name) else trf("{0} balance", name)
                if (direction == "payment") listOfNotNull(owed to -amount, if (pair) tr("Savings") to -amount else null) else listOf(owed to amount)
            }
        }
    }

    /** §3.6's captions under the readout; wording is the owner's. */
    fun captions(d: ProfileData, target: String, direction: String, amount: Money?, note: String, pair: Boolean, money: (Money) -> String): List<String> {
        val out = mutableListOf<String>()
        if (areaOf(target) == Area.GOAL && direction == "spend" && amount != null && amount > goalHolds(d, target))
            out += trf("{0} holds {1}, so this can spend up to that.", nameOf(d, target), money(goalHolds(d, target)))
        if (areaOf(target) == Area.SAVINGS && direction == "income") out += tr("Counts toward this month’s income received.")
        val n = note.trim().lowercase()
        val spending = areaOf(target) == Area.CATEGORY || (areaOf(target) == Area.GOAL && direction == "spend")
        if (spending && n.length > 2) d.recurringExpenses.firstOrNull { val name = it.name.lowercase(); n.contains(name) || name.contains(n) }?.let {
            out += trf("{0} is a recurring expense and is taken on its own date. Log it here only if this is an extra payment.", it.name)
        }
        if (areaOf(target) == Area.ACCOUNT && direction == "payment") {
            val linked = account(d, target)?.linkedRecurringExpenseId?.let { id -> d.recurringExpenses.firstOrNull { it.id == id }?.name }
            out += when {
                linked != null && pair -> trf("{0} already takes this from Savings on its date, so this would count it twice.", linked)
                linked != null -> trf("Savings stays: {0} already takes this from Savings on its date.", linked)
                pair -> tr("Savings goes down by the same amount: nothing else moves it for this account.")
                else -> tr("Savings stays as it is.")
            }
        }
        return out
    }

    /** The write, over the engine's own functions; callers check [moves] first. */
    fun write(d: ProfileData, target: String, direction: String, amount: Money, note: String?, date: LocalDate, pair: Boolean, clock: Clock, ids: () -> String): ProfileData {
        val id = target.substringAfter(':')
        val payee = note?.trim()?.ifEmpty { null }
        return when (areaOf(target)) {
            Area.SAVINGS -> Writes.savingsQuickAdd(d, amount, payee, direction == "income", clock, ids(), date)
            Area.GOAL -> if (direction == "spend") Writes.goalSpend(d, id, amount, payee, clock, ids(), date)
                else Writes.goalQuickAdd(d, id, amount, payee, clock, ids(), date)
            Area.CATEGORY -> Writes.categoryQuickAdd(d, id, amount, payee, clock, ids(), date)
            // with no note, the row is named by the sentence's verb rather than the direct edit's "Correction"
            Area.ACCOUNT -> Accounts.quickAdd(
                d, id, amount,
                when (direction) { "payment" -> Accounts.Direction.PAYMENT; "charge" -> Accounts.Direction.CHARGE; "increase" -> Accounts.Direction.INCREASE; else -> error("unknown account direction: $direction") },
                payee, direction == "payment" && pair, clock, date,
                rowLabel = directions(d, target)?.firstOrNull { it.first == direction }?.let { ROW_LABELS[it.first] }, ids = ids,
            )
        }
    }

    /** The row's stored name stays English (the ledger displays it through tr), whatever language the sentence read in. */
    private val ROW_LABELS = mapOf("charge" to "Charged", "payment" to "Paid off", "increase" to "Increased")

    private fun account(d: ProfileData, target: String) = if (areaOf(target) == Area.ACCOUNT) d.accounts.firstOrNull { it.id == target.removePrefix("acct:") } else null
    private fun goalHolds(d: ProfileData, target: String): Money = d.goals.firstOrNull { it.id == target.removePrefix("goal:") }?.saved ?: 0
}
