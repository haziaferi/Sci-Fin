package app.scifin

import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Sheet
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.savingsAmount
import app.scifin.model.AppState
import app.scifin.model.Cadence
import app.scifin.model.DateMode
import app.scifin.model.RecurringExpense
import app.scifin.model.moneyOf
import app.scifin.ui.screens.freshExpense
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.scifin.model.ProfileData

/** §3.11's editor through the store: creation branches, the reconcile prompt after an edit, the reversal on delete; and §3.9's card surviving a background. */
class RecurringStoreTest {
    private val ids = Ids("re")

    private suspend fun storeOn(day: LocalDate, seed: (ProfileData) -> ProfileData = { it }, scope: kotlinx.coroutines.CoroutineScope): Pair<AppStore, FakeClock> {
        val db = buildInMemorySciFinDatabase(); val clock = FakeClock(day)
        val d = seed(profileWith(savings = 5000.0).copy(categories = listOf(category("c1", "Groceries", spent = 120.0).copy(lastResetMonth = "2026-08"))))
        db.dao().save(d); db.dao().upsertAppState(AppState(activeProfileId = d.profileId))
        val store = AppStore(AppSession(db, RecordingScheduler(), clock, ids::next), db, scope)
        store.start()
        return store to clock
    }

    @Test fun `a new expense due later this month sweeps now, and an edit after the sweep asks once before moving money`() = runTest {
        val (store, clock) = storeOn(LocalDate(2026, 9, 14), scope = this)
        val draft = freshExpense("gym", "p-1", clock.today()).copy(name = "Gym", amount = moneyOf(40.0), dueDate = LocalDate(2026, 9, 20))
        store.saveExpense(draft, isNew = true).join()
        var d = store.state.value.data!!
        assertEquals(moneyOf(4960.0), d.savingsAmount); assertEquals("2026-09", d.recurringExpenses.single().lastSweptMonth)   // branch 1
        assertNull(store.state.value.sheet)
        // amount 40 → 55 with the month already swept: the reconcile sheet opens, nothing has moved yet
        store.saveExpense(d.recurringExpenses.single().copy(amount = moneyOf(55.0)), isNew = false).join()
        val sheet = assertIs<Sheet.Reconcile>(store.state.value.sheet)
        assertEquals(moneyOf(15.0), sheet.reconcile.delta)
        assertEquals(moneyOf(4960.0), store.state.value.data!!.savingsAmount)
        store.resolveReconcile(sheet.reconcile, null, null).join()
        d = store.state.value.data!!
        assertEquals(moneyOf(4945.0), d.savingsAmount); assertNull(store.state.value.sheet); assertEquals(moneyOf(55.0), d.recurringExpenses.single().amount)
        // delete: the current period's sweep (55) is offered back
        store.push(Route.RecurringEditor("gym"))
        store.deleteExpense("gym", reverse = true).join()
        assertTrue(store.state.value.data!!.recurringExpenses.isEmpty()); assertEquals(moneyOf(5000.0), store.state.value.data!!.savingsAmount)
        assertTrue(store.state.value.routes.none { it is Route.RecurringEditor })
    }

    @Test fun `a three-month absence shows the catch-up card until dismissed, and it survives a reopen`() = runTest {
        val rent = RecurringExpense("rent", "p-1", "Rent", null, moneyOf(950.0), Cadence.MONTHLY, firstDueDate = LocalDate(2026, 1, 1), dueDate = LocalDate(2026, 9, 1), dueDateAnchorDay = 1, dateMode = DateMode.FIXED_DATE, lastSweptMonth = "2026-08")
        val (store, _) = storeOn(LocalDate(2026, 11, 14), seed = { it.copy(recurringExpenses = listOf(rent)) }, scope = this)
        val s = store.state.value
        val card = s.catchUp; assertNotNull(card); assertEquals(3, card.monthsReplayed); assertEquals(moneyOf(2850.0), card.sweptTotal)
        assertTrue(s.data!!.settings.catchUpSummaryPending)
        store.dismissCatchUpCard().join()
        assertNull(store.state.value.catchUp); assertEquals(false, store.state.value.data!!.settings.catchUpSummaryPending)
    }
}
