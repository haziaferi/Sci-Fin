package app.scifin.engine

import app.scifin.model.Category
import app.scifin.model.Money
import app.scifin.model.ProfileData

/** §3.6 / §4.3's Category editor over the engine: `spent` is directly editable and any change moves Savings by the inverse, logged. */
object CategoryEditing {

    data class Draft(val id: String? = null, val name: String = "", val icon: String? = null, val planned: Money = 0, val spent: Money = 0) {
        companion object { fun of(c: Category) = Draft(c.id, c.name, c.icon, c.planned, c.spent) }
    }

    fun save(d: ProfileData, draft: Draft, clock: Clock, ids: () -> String): ProfileData {
        val name = draft.name.trim(); if (name.isEmpty()) return d
        return if (draft.id == null) {
            val c = Category(ids(), d.profileId, name, draft.icon, planned = draft.planned, spent = 0, lastResetMonth = clock.today().ymKey())
            var next = d.copy(categories = d.categories + c)
            if (draft.spent > 0) next = Writes.categorySpentEdit(next, c.id, draft.spent, Writes.STARTING_BALANCE, clock, ids())
            next
        } else {
            val old = d.categories.firstOrNull { it.id == draft.id } ?: return d
            var next = d.copy(categories = d.categories.map { if (it.id == old.id) it.copy(name = name, icon = draft.icon, planned = draft.planned) else it })
            if (draft.spent != old.spent) next = Writes.categorySpentEdit(next, old.id, draft.spent, Writes.CORRECTION, clock, ids())
            next
        }
    }

    /** §3.6: deleting a Category returns nothing and writes no Transaction -- its `spent` left in the real world. */
    fun delete(d: ProfileData, id: String): ProfileData = d.copy(categories = d.categories.filter { it.id != id })
}
