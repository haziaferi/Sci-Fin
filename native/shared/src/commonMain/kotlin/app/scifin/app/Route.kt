package app.scifin.app

import app.scifin.engine.LoanDraft
import app.scifin.engine.Recurring
import app.scifin.model.Account

/**
 * §4.1's five destinations, in the order the spec fixes them. The container that shows them
 * changes with width (§4.8 rule 1); the destinations, their order and their labels never do.
 */
enum class Tab(val label: String) {
    BUDGET("Budget"), ACTIVITY("Activity"), LEARN("Learn"), REPORTS("Reports"), SETTINGS("Settings");
}

/**
 * Where the app is. A route stack rather than a navigation library: none is in the offline
 * cache, and §4.8's pane rule ("what was a pushed screen renders in the right pane at 840dp")
 * needs the stack readable anyway. The first entry is always a [TabRoute]; pushes stack on it;
 * Back pops.
 */
sealed interface Route {
    /** One of the five tabs, with its own sub-view where the tab has one (§4.3's segmented control, §4.6's sub-views). */
    data class TabRoute(val tab: Tab, val sub: String? = null) : Route

    /** §11.1's flow; [forNewProfile] is §8.3's "Add profile" running screen 1 and the Currency landing only. */
    data class Onboarding(val step: Int, val forNewProfile: Boolean = false) : Route

    /** §4.7's spokes off the hub. */
    data class Settings(val page: SettingsPage) : Route

    /** §6.4's calculator: one screen for create (null) and edit. */
    data class GoalEditor(val id: String?) : Route

    /** §4.3's Recurring Expenses drill-in list. */
    data object RecurringList : Route

    /** §4.2: the Account detail screen, host of §9.2's payoff outlook. */
    data class AccountDetail(val id: String) : Route

    /** §7.2's editor: create (null) or edit. */
    data class AccountEditor(val id: String?) : Route

    /** §7.3's ledger. */
    data object Ledger : Route

    /** §4.3's statement import. */
    data object Import : Route

    /** §3.6 / §4.3's Category editor: create (null) or edit. */
    data class CategoryEditor(val id: String?) : Route

    /** §3.11's Recurring Expense editor: create (null) or edit. */
    data class RecurringEditor(val id: String?) : Route

    /** §4.5's topic, read in Learn's second column (§4.8); on a phone topics open in place. */
    data class LearnTopic(val id: String) : Route

    /** §9.1's loan calculator and §9.2's purchase calculator, off Learn. */
    data object LoanCalc : Route
    data object PurchaseCalc : Route
}

enum class SettingsPage(val title: String) {
    APPEARANCE("Appearance"), INCOME("Income"), NOTIFICATIONS("Notifications"), BACKUP("Data & backup"), SECURITY("Security & lock"), TRASH("Trash"), PROFILES("Profiles"), REPLAY("The tour, condensed");
}

/**
 * The forms that open over a screen: bottom sheets below 600dp, centred dialogs at 600dp and
 * above (§4.8 rule 3). Their drafts are local to the form; only confirming reaches the store.
 */
sealed interface Sheet {
    /** §3.6's entry on a target key (`savings`, `goal:<id>`, `cat:<id>`, `acct:<id>`); null is the floating + and
     *  Ctrl+N, which open the last area and target used. */
    data class Entry(val target: String? = null) : Sheet
    /** §3.6's Savings full editor: the typed-UPDATE direct edit. */
    data object SavingsEditor : Sheet
    /** §4.4: the accounts note, opened from its one line on Activity. */
    data object AccountsNote : Sheet
    /** §8.3: rename a Profile. */
    data class RenameProfile(val id: String) : Sheet
    /** §8.3: delete a Profile -- one of §4.8 rule 3's delete confirmations. */
    data class DeleteProfile(val id: String) : Sheet
    /** §3.6: delete a Goal, offering its `saved` back. */
    data class DeleteGoal(val id: String) : Sheet
    /** §7.2: delete an Account (and the payment created as its pair, if any). */
    data class DeleteAccount(val id: String) : Sheet
    /** §7.2: a Loan re-typed to Credit takes its linked payment with it -- asked before the save. */
    data class RetypeCascade(val draft: Account) : Sheet
    /** §7.3: remove a pair of rows that cancelled each other within two days. */
    data class DeletePair(val id: String, val otherId: String) : Sheet
    /** §9.2: the fixed card payment becomes a linked Recurring Expense. */
    data class PayoffCommit(val accountId: String, val amount: Long) : Sheet
    /** §3.6: delete a Category -- nothing is un-spent. */
    data class DeleteCategory(val id: String) : Sheet
    /** §3.11: delete a Recurring Expense, offering back what this period actually swept. */
    data class DeleteExpense(val id: String) : Sheet
    /** §3.11's edit-and-reconcile: this month was swept at the old settings. */
    data class Reconcile(val reconcile: Recurring.Reconcile) : Sheet
    /** §9.1's two hand-offs, each behind a confirm. */
    data class LoanSave(val draft: LoanDraft) : Sheet
    data class LoanBorrow(val draft: LoanDraft) : Sheet
}
