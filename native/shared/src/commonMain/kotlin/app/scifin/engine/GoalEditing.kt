package app.scifin.engine

import app.scifin.i18n.stored
import app.scifin.model.Goal
import app.scifin.model.GoalMode
import app.scifin.model.Money
import app.scifin.model.ProfileData
import app.scifin.model.ceilDiv
import kotlinx.datetime.LocalDate

/**
 * §6.4's editor draft: what the person types, and the two derived figures kept in step
 * (§6.1: by date, the monthly rounds up; by amount, the months round up). The date is the
 * anchor that is stored in by-date mode; in by-amount mode the rate is the anchor and the
 * date is derived on read (§12), so only one of the two is ever persisted.
 */
data class GoalDraft(
    val id: String? = null,
    val name: String = "",
    val icon: String? = null,
    val noTarget: Boolean = false,
    val saved: Money = 0,
    val target: Money? = null,
    val mode: GoalMode = GoalMode.DATE,
    val months: Int? = 6,
    val monthly: Money? = null,
    /**
     * The stored deadline and the months it read as when the editor opened. A save that did not
     * touch the months keeps the stored date, so a passed deadline keeps counting (§6.2) and a
     * rename does not re-anchor it.
     */
    val storedDate: LocalDate? = null,
    val storedMonths: Int? = null,
) {
    val remaining: Money get() = maxOf(0L, (target ?: 0) - saved)

    /** Re-derive the figure the changed one governs. */
    fun synced(changed: GoalMode): GoalDraft {
        if (noTarget || target == null || target <= 0) return copy(monthly = null)
        return if (changed == GoalMode.AMOUNT) {
            val m = maxOf(0L, monthly ?: 0)
            copy(monthly = m, months = if (m > 0) remaining.ceilDiv(m).toInt() else null)
        } else {
            val n = maxOf(1, months ?: 1)
            copy(months = n, monthly = remaining.ceilDiv(n.toLong()))
        }
    }

    companion object {
        /** Months are re-derived from the stored date every time the editor opens (§6.1, §12). */
        fun of(g: Goal, today: LocalDate): GoalDraft {
            val derived = Goals.derive(g, today)
            return GoalDraft(
                id = g.id, name = g.name, icon = g.icon, noTarget = g.target == null, saved = g.saved, target = g.target,
                mode = g.mode ?: GoalMode.DATE, months = derived.months ?: 6, monthly = derived.monthly,
                storedDate = g.targetDate, storedMonths = derived.months ?: 6,
            )
        }
    }
}

object GoalEditing {

    /** §6.4 Save: a new Goal, or an edit whose `saved` change moves Savings by the inverse (§3.6) and is logged. */
    fun save(d: ProfileData, draft: GoalDraft, clock: Clock, ids: () -> String): ProfileData {
        val name = draft.name.trim(); if (name.isEmpty()) return d
        val today = clock.today()
        // an empty or zero target is a running tally, as if "no target" were ticked
        val noTarget = draft.noTarget || (draft.target ?: 0L) <= 0L
        val target = if (noTarget) null else draft.target
        val mode = if (noTarget) null else draft.mode
        val targetDate = when {
            noTarget || mode != GoalMode.DATE -> null
            draft.storedDate != null && draft.months == draft.storedMonths -> draft.storedDate
            else -> draft.months?.let { today.plusMonthsClamped(it) }
        }
        val monthly = if (noTarget || mode != GoalMode.AMOUNT) null else draft.monthly
        return if (draft.id == null) {
            val g = Goal(ids(), d.profileId, name, draft.icon, saved = 0, target = target, targetDate = targetDate, mode = mode, monthly = monthly)
            var next = d.copy(goals = d.goals + g)
            if (draft.saved > 0) next = Writes.goalSavedEdit(next, g.id, draft.saved, Writes.STARTING_BALANCE, clock, ids())
            next.copy(goals = next.goals.map { if (it.id == g.id) Goals.rederive(it, today) else it })
        } else {
            val old = d.goals.firstOrNull { it.id == draft.id } ?: return d
            var next = d.copy(goals = d.goals.map { if (it.id == old.id) it.copy(name = name, icon = draft.icon, target = target, targetDate = targetDate, mode = mode, monthly = monthly) else it })
            if (draft.saved != old.saved) next = Writes.goalSavedEdit(next, old.id, draft.saved, Writes.CORRECTION, clock, ids())
            next.copy(goals = next.goals.map { if (it.id == old.id) Goals.rederive(it, today) else it })
        }
    }

    /** §3.6 Delete: only on accept is `saved` returned to Savings, logged; the Goal's history stays in the ledger either way. */
    fun delete(d: ProfileData, goalId: String, returnSaved: Boolean, clock: Clock, ids: () -> String): ProfileData {
        val g = d.goals.firstOrNull { it.id == goalId } ?: return d
        var next = d
        if (returnSaved && g.saved > 0) next = Writes.goalSavedEdit(next, goalId, 0, stored("Goal deleted — returned to Savings"), clock, ids())
        return next.copy(goals = next.goals.filter { it.id != goalId })
    }
}
