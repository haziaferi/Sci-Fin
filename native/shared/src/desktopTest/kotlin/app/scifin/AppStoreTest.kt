package app.scifin

import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Tab
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.Writes
import app.scifin.model.moneyOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import app.scifin.engine.plusDays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import app.scifin.model.Cadence
import app.scifin.model.RecurringExpense
import app.scifin.model.Theme

/** The store: what opening loads, how the route stack moves, and that a dispatch is saved whole and re-arms reminders. */
class AppStoreTest {
    private val clock = FakeClock(LocalDate(2026, 9, 14))
    private val ids = Ids("s")

    @Test fun `start loads the active Profile and lands on Budget`() = runTest {
        val db = buildInMemorySciFinDatabase(); val sched = RecordingScheduler()
        val store = AppStore(AppSession(db, sched, clock, ids::next), db, this)
        assertFalse(store.state.value.ready)
        store.start(); store.createProfile("Mine", Theme.SWISS_BLUE, false).join(); store.completeOnboarding().join()
        val s = store.state.value
        assertTrue(s.ready); assertEquals(Tab.BUDGET, s.tab); assertEquals(1, s.profiles.size)
        assertEquals(s.profiles.single().id, s.data!!.profileId)
    }

    @Test fun `tabs replace the stack, pushes stack on it, back pops and reports when there is nothing to pop`() = runTest {
        val db = buildInMemorySciFinDatabase()
        val store = AppStore(AppSession(db, RecordingScheduler(), clock, ids::next), db, this)
        store.start(); store.createProfile("Mine", Theme.SWISS_BLUE, false).join(); store.completeOnboarding().join()
        store.selectTab(Tab.REPORTS)
        assertEquals(listOf(Route.TabRoute(Tab.REPORTS)), store.state.value.routes)
        store.selectSub("spending")
        assertEquals(Route.TabRoute(Tab.REPORTS, "spending"), store.state.value.route)
        store.push(Route.TabRoute(Tab.SETTINGS))          // any Route; the pushes proper arrive with their screens
        assertEquals(2, store.state.value.routes.size)
        assertTrue(store.back()); assertEquals(1, store.state.value.routes.size)
        assertFalse(store.back())
        store.selectTab(Tab.BUDGET)
        assertEquals(Route.TabRoute(Tab.BUDGET), store.state.value.route)
    }

    @Test fun `dispatch saves the Profile whole and re-syncs the reminder schedule only when it moved`() = runTest {
        val db = buildInMemorySciFinDatabase(); val sched = RecordingScheduler()
        val store = AppStore(AppSession(db, sched, clock, ids::next), db, this)
        store.start(); store.createProfile("Mine", Theme.SWISS_BLUE, false).join(); store.completeOnboarding().join()
        val syncsAfterOpen = sched.syncs
        store.dispatch { Writes.savingsQuickAdd(it, moneyOf(120.0), null, false, clock, ids.next()) }.join()
        val s = store.state.value.data!!
        assertEquals(moneyOf(120.0), s.savings.amount)
        assertEquals(1, s.transactions.size)
        assertEquals(s, db.dao().load(s.profileId))
        assertEquals(syncsAfterOpen, sched.syncs)                                // Savings moved, the schedule did not (audit 2026-09-25)
        val due = clock.today().plusDays(10)
        store.dispatch { it.copy(recurringExpenses = it.recurringExpenses + RecurringExpense("r", it.profileId, "Rent", null, moneyOf(900.0),
            Cadence.MONTHLY, firstDueDate = due, dueDate = due, reminderEnabled = true)) }.join()
        assertEquals(syncsAfterOpen + 1, sched.syncs)
        // an identity write neither saves nor syncs
        store.dispatch { it }.join()
        assertEquals(syncsAfterOpen + 1, sched.syncs)
    }
}
