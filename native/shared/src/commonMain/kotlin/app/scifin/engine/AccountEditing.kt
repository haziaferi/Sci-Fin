package app.scifin.engine

import app.scifin.model.Account
import app.scifin.model.AccountType
import app.scifin.model.ProfileData

/**
 * §7.2's editor over the engine: a new Account's non-zero balance logs an opening row (§5.3,
 * or a forward replay shows it as €0 for every month it existed); an edit's balance change
 * is a logged correction; auto-update cannot be on without the Loan inputs it computes
 * from; a linked expense is linked both ways.
 */
object AccountEditing {

    fun save(d: ProfileData, draft: Account, clock: Clock, ids: () -> String): ProfileData {
        val name = draft.name.trim(); if (name.isEmpty()) return d
        val existing = d.accounts.firstOrNull { it.id == draft.id }
        val cleaned = draft.copy(
            name = name,
            autoUpdateFromPayments = draft.autoUpdateFromPayments && Accounts.loanInputsComplete(draft),
            originationDate = draft.originationDate ?: if (draft.type == AccountType.LOAN) clock.today() else null,
        )
        var next = if (existing == null) {
            var n = d.copy(accounts = d.accounts + cleaned.copy(balance = 0))
            if (cleaned.balance != 0L) n = Accounts.balanceEdit(n, cleaned.id, cleaned.balance, Accounts.OPENING_BALANCE, clock, ids())
            n
        } else {
            var n = d
            if (cleaned.balance != existing.balance) n = Accounts.balanceEdit(n, existing.id, cleaned.balance, Writes.CORRECTION, clock, ids())
            n.copy(accounts = n.accounts.map { if (it.id == existing.id) cleaned else it })
        }
        // both ways, and only one way at a time: an expense this account no longer links to
        // loses its back-link, or deleting it later would take the account with it (§7.2's cascade)
        val eid = cleaned.linkedRecurringExpenseId
        next = next.copy(recurringExpenses = next.recurringExpenses.map {
            when {
                it.id == eid -> it.copy(linkedAccountId = cleaned.id)
                it.linkedAccountId == cleaned.id -> it.copy(linkedAccountId = null)
                else -> it
            }
        }, accounts = next.accounts.map { if (eid != null && it.id != cleaned.id && it.linkedRecurringExpenseId == eid) it.copy(linkedRecurringExpenseId = null) else it })
        return next
    }

    /** §7.2: a fresh draft for the editor -- Credit by default, with the type's own glyph. */
    fun fresh(id: String, profileId: String) = Account(id, profileId, "", "credit-card", AccountType.CREDIT)
}
