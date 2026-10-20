package app.scifin

import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.data.SciFinDatabase
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.Backup
import app.scifin.engine.BackupResult
import app.scifin.engine.Ledger
import app.scifin.engine.NewMonth
import app.scifin.engine.Recurring
import app.scifin.engine.Trash
import app.scifin.engine.ymKey
import app.scifin.engine.savingsAmount
import app.scifin.model.AppState
import app.scifin.model.ProfileData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Trash (owner, 2026-10-19/20): money decided at the delete, undone by Restore; inert; 20-day notice; 30 days. */
class TrashTest {
    private val clock = FakeClock(Fixture.simDate)
    private val ids = Ids("t")::next
    private val d0: ProfileData get() = Fixture.profile

    /** The stored figures a restore must bring back exactly. */
    private fun figures(d: ProfileData) = listOf(d.savingsAmount, d.goals.sortedBy { it.id }, d.categories.sortedBy { it.id }, d.accounts.sortedBy { it.id })

    @Test fun `a goal deleted with its money returned comes back with it, and Savings as it was`() {
        val g = d0.goals.first { it.saved > 0 }
        val (after, p) = assertNotNull(Trash.deleteGoal(d0, g.id, returnSaved = true, clock, ids))
        assertEquals(d0.savingsAmount + g.saved, after.savingsAmount)
        assertEquals(g.saved, Trash.moneyMoved(p))
        val back = Trash.restore(after, p, clock, ids)
        assertEquals(figures(d0), figures(back))
        assertTrue(back.transactions.any { it.payee == Trash.RESTORED }, "the restore is a logged row, not a rewrite")
        assertTrue(back.transactions.size > d0.transactions.size, "and the delete's own row stays in the log")
    }

    @Test fun `a goal deleted alone comes back as it was, with no money moved`() {
        val g = d0.goals.first { it.saved > 0 }
        val (after, p) = assertNotNull(Trash.deleteGoal(d0, g.id, returnSaved = false, clock, ids))
        assertEquals(d0.savingsAmount, after.savingsAmount)
        assertEquals(figures(d0), figures(Trash.restore(after, p, clock, ids)))
    }

    @Test fun `a payment and its account come back together, the reversal undone and the balance logged back on`() {
        val e = d0.recurringExpenses.firstOrNull { it.linkedAccountId != null && Recurring.reversalAmount(d0, it, clock.today()) > 0 }
            ?: d0.recurringExpenses.first { it.linkedAccountId != null }
        val (after, p) = assertNotNull(Trash.deleteExpense(d0, e.id, reverse = true, clock, ids))
        assertNull(after.accounts.firstOrNull { it.id == e.linkedAccountId })
        val back = Trash.restore(after, p, clock, ids)
        assertEquals(d0.savingsAmount, back.savingsAmount)
        assertEquals(d0.accounts.sortedBy { it.id }, back.accounts.sortedBy { it.id })
        assertNotNull(back.recurringExpenses.firstOrNull { it.id == e.id })
    }

    @Test fun `an account takes its payment to the Trash and brings it back`() {
        val a = d0.accounts.first { it.linkedRecurringExpenseId != null }
        val (after, p) = assertNotNull(Trash.deleteAccount(d0, a.id, reverse = false, clock, ids))
        assertNull(after.recurringExpenses.firstOrNull { it.id == a.linkedRecurringExpenseId })
        val back = Trash.restore(after, p, clock, ids)
        assertEquals(figures(d0), figures(back))
        assertEquals(d0.recurringExpenses.map { it.id }.sorted(), back.recurringExpenses.map { it.id }.sorted())
    }

    @Test fun `a category and a cancelled pair come back as they were`() {
        val c = d0.categories.first()
        val (afterC, pc) = assertNotNull(Trash.deleteCategory(d0, c.id))
        assertEquals(figures(d0), figures(Trash.restore(afterC, pc, clock, ids)))

        val (withPair, _) = Ledger.logAndMove(d0, app.scifin.engine.Movement(1250, app.scifin.engine.TransactionKey.SavingsKey, "Savings", "slip"), clock, "x1")
        val (withBoth, _) = Ledger.logAndMove(withPair, app.scifin.engine.Movement(-1250, app.scifin.engine.TransactionKey.SavingsKey, "Savings", "slip"), clock, "x2")
        val (afterP, pp) = assertNotNull(Trash.deletePair(withBoth, "x1", "x2"))
        assertEquals(setOf("x1", "x2"), (Trash.restore(afterP, pp, clock, ids).transactions.map { it.id }.toSet() intersect setOf("x1", "x2")))
    }

    @Test fun `a payment in the Trash is inert, and comes back without catching up the months it missed`() {
        val e = d0.recurringExpenses.first { it.finishedAt == null && it.linkedAccountId == null }
        val (after, p) = assertNotNull(Trash.deleteExpense(d0, e.id, reverse = false, clock, ids))
        val later = FakeClock(Fixture.simDate.plus(DatePeriod(months = 2)))
        val (aged, _) = NewMonth.runCatchUp(after, later, ids)
        assertTrue(aged.transactions.none { it.key == "rec:${e.id}" && it.date > Fixture.simDate }, "nothing swept while in the Trash")
        val back = Trash.restore(aged, p, later, ids)
        assertEquals(later.today().ymKey(), back.recurringExpenses.first { it.id == e.id }.lastSweptMonth)
        val (again, _) = NewMonth.runCatchUp(back, later, ids)
        assertTrue(again.transactions.none { it.key == "rec:${e.id}" && it.date > Fixture.simDate }, "and restoring does not sweep the missed months")
    }

    // ---- the store and the session ------------------------------------------------------------------

    private class Notices : RecordingScheduler() {
        val bodies = ArrayList<String>()
        override fun notifyNow(key: String, title: String, body: String) { bodies += body }
    }

    private fun db(): SciFinDatabase = buildInMemorySciFinDatabase().also { db ->
        runBlocking { db.dao().save(d0); db.dao().upsertAppState(AppState(activeProfileId = d0.profileId)) }
    }

    private fun store(db: SciFinDatabase, sched: RecordingScheduler = RecordingScheduler()) =
        AppStore(AppSession(db, sched, clock, ids), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), hashing = EmptyCoroutineContext)
            .also { runBlocking { it.start() } }

    @Test fun `every delete lands in the Trash, and Restore and Delete forever empty it`() {
        val db = db(); val s = store(db)
        val g = d0.goals.first { it.saved > 0 }
        runBlocking { s.deleteGoal(g.id, returnSaved = true).join(); s.deleteCategory(d0.categories.first().id).join() }
        assertEquals(listOf("category", "goal"), s.state.value.trash.map { it.kind }.sorted())
        val goal = s.state.value.trash.first { it.kind == "goal" }
        assertEquals(g.saved, goal.money)
        assertEquals(Fixture.simDate.plus(DatePeriod(days = 30)), goal.goesOn)

        runBlocking { s.restoreFromTrash(goal.id).join() }
        assertEquals(g.saved, s.state.value.data!!.goals.first { it.id == g.id }.saved)
        assertEquals(d0.savingsAmount, s.state.value.data!!.savingsAmount)
        runBlocking { s.deleteForever(s.state.value.trash.single().id).join() }
        assertTrue(s.state.value.trash.isEmpty())
        assertTrue(runBlocking { db.dao().trash() }.isEmpty())
    }

    @Test fun `a deleted profile waits whole in the Trash and comes back as one more profile`() {
        val db = db(); val s = store(db)
        runBlocking { s.createProfile("Second", d0.settings.theme, forNewProfile = true).join() }
        val second = s.state.value.data!!.profileId
        runBlocking { s.switchProfile(d0.profileId).join(); s.deleteProfile(second).join() }
        assertEquals(1, s.state.value.profiles.size)
        val entry = s.state.value.trash.single { it.kind == "profile" }
        runBlocking { s.restoreFromTrash(entry.id).join() }
        assertEquals(setOf(d0.profileId, second), s.state.value.profiles.map { it.id }.toSet())
        assertEquals(d0.profileId, s.state.value.data!!.profileId, "the restored profile is not opened")
    }

    @Test fun `one notice at twenty days, and gone at thirty`() {
        val db = db(); val sched = Notices(); val s = store(db, sched)
        runBlocking { s.deleteCategory(d0.categories.first().id).join() }
        val session = AppSession(db, sched, clock, ids)
        clock.today = Fixture.simDate.plus(DatePeriod(days = 19)); runBlocking { session.tidyTrash() }
        assertTrue(sched.bodies.isEmpty())
        clock.today = Fixture.simDate.plus(DatePeriod(days = 20)); runBlocking { session.tidyTrash(); session.tidyTrash() }
        assertEquals(1, sched.bodies.size, "said once, not every day")
        assertTrue("10 days" in sched.bodies.single())
        clock.today = Fixture.simDate.plus(DatePeriod(days = 30)); assertEquals(1, runBlocking { session.tidyTrash() })
        assertTrue(runBlocking { db.dao().trash() }.isEmpty())
    }

    @Test fun `restoring a backup empties the Trash`() {
        val db = db(); val s = store(db)
        runBlocking { s.deleteCategory(d0.categories.first().id).join() }
        val text = Backup.encode(Backup.build(listOf(d0), d0.profileId, Fixture.simDate))
        runBlocking { Backup.apply(db, (Backup.validate(text) as BackupResult.Ok).backup, Fixture.simDate) }
        assertTrue(runBlocking { db.dao().trash() }.isEmpty())
    }
}
