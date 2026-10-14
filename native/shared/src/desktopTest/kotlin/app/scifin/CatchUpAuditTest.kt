package app.scifin

import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.data.SciFinDatabase
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.Income
import app.scifin.engine.NewMonth
import app.scifin.model.AppState
import app.scifin.model.Cadence
import app.scifin.model.DateMode
import app.scifin.model.IncomeCadence
import app.scifin.model.ProfileData
import app.scifin.model.RecurringExpense
import app.scifin.model.moneyOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §3.9's card and §3.3's income switch, as the 2026-09-25 audit left them (decisions of that day). */
class CatchUpAuditTest {
    private val ids = Ids("cu")

    private fun rent(swept: String) = RecurringExpense("rent", "p-1", "Rent", null, moneyOf(950.0), Cadence.MONTHLY, firstDueDate = LocalDate(2026, 1, 1),
        dueDate = LocalDate(2026, 9, 1), dueDateAnchorDay = 1, dateMode = DateMode.FIXED_DATE, lastSweptMonth = swept)

    private suspend fun seeded(db: SciFinDatabase, d: ProfileData) { db.dao().save(d); db.dao().upsertAppState(AppState(activeProfileId = d.profileId)) }

    private suspend fun open(db: SciFinDatabase, day: LocalDate, scope: CoroutineScope): AppStore =
        AppStore(AppSession(db, RecordingScheduler(), FakeClock(day), ids::next), db, scope).also { it.start() }

    @Test fun `the card survives a restart, and dismissing it frees the goal prompt`() = runTest {
        val db = buildInMemorySciFinDatabase()
        seeded(db, profileWith(savings = 5000.0).copy(categories = listOf(category("c1").copy(lastResetMonth = "2026-08")), recurringExpenses = listOf(rent("2026-08"))))
        open(db, LocalDate(2026, 11, 14), this)                          // the open that ran the catch-up, then the process dies
        val again = open(db, LocalDate(2026, 11, 14), this)              // nothing left to replay on this open
        val card = again.state.value.catchUp
        assertNotNull(card); assertEquals(3, card.monthsReplayed); assertEquals(moneyOf(2850.0), card.sweptTotal)
        again.dismissCatchUpCard().join()
        assertNull(again.state.value.catchUp)
        assertEquals(false, db.dao().load("p-1")!!.settings.catchUpSummaryPending)
    }

    @Test fun `an ordinary month boundary never clears an unread card, and a second absence adds to it`() {
        val w = profileWith(savings = 5000.0).copy(categories = listOf(category("c1").copy(lastResetMonth = "2026-08")), recurringExpenses = listOf(rent("2026-08")))
        val (afterAbsence, _) = NewMonth.runCatchUp(w, FakeClock(LocalDate(2026, 10, 14))) { ids.next() }
        assertEquals(2, NewMonth.persistedSummary(afterAbsence.settings)!!.monthsReplayed)
        val (afterBoundary, _) = NewMonth.runCatchUp(afterAbsence, FakeClock(LocalDate(2026, 11, 3))) { ids.next() }
        assertEquals(2, NewMonth.persistedSummary(afterBoundary.settings)!!.monthsReplayed)
        val (afterSecond, _) = NewMonth.runCatchUp(afterBoundary, FakeClock(LocalDate(2027, 1, 3))) { ids.next() }
        assertEquals(4, NewMonth.persistedSummary(afterSecond.settings)!!.monthsReplayed)
    }

    @Test fun `turning auto-deposit back on starts from today and deposits no missed payday`() {
        val base = profileWith(savings = 1000.0)
        val off = base.copy(settings = base.settings.copy(incomeEnabled = true, income = moneyOf(1450.0), incomeCadence = IncomeCadence.MONTHLY,
            incomePayDate = LocalDate(2026, 5, 5), incomeAutoDeposit = false, lastIncomeDepositDate = LocalDate(2026, 6, 5)))
        val today = LocalDate(2026, 9, 14)
        val on = Income.setAutoDeposit(off, true, today)
        val (d, c) = Income.runCatchUp(on, FakeClock(today)) { ids.next() }
        assertEquals(0, c.count); assertEquals(moneyOf(1000.0), d.savings.amount)
        val (later, c2) = Income.runCatchUp(d, FakeClock(LocalDate(2026, 10, 5))) { ids.next() }
        assertEquals(1, c2.count); assertEquals(LocalDate(2026, 10, 5), later.transactions.single().date)
    }

    @Test fun `a first switch-on still replays from the chosen payday`() {
        val base = profileWith(savings = 1000.0)
        val d = base.copy(settings = base.settings.copy(incomeEnabled = true, income = moneyOf(100.0), incomeCadence = IncomeCadence.MONTHLY, incomePayDate = LocalDate(2026, 8, 5)))
        val (after, c) = Income.runCatchUp(Income.setAutoDeposit(d, true, LocalDate(2026, 9, 14)), FakeClock(LocalDate(2026, 9, 14))) { ids.next() }
        assertEquals(2, c.count); assertEquals(moneyOf(1200.0), after.savings.amount)
    }

    @Test fun `semimonthly with no second payday chosen deposits nothing yet`() {
        val base = profileWith()
        val s = base.settings.copy(incomeEnabled = true, incomeCadence = IncomeCadence.SEMIMONTHLY, incomePayDate = LocalDate(2026, 9, 1), incomePayDate2 = null)
        assertNull(Income.nextOccurrence(s, LocalDate(2026, 9, 14)))
    }

    @Test fun `switching to a Profile last open months ago runs its catch-up`() = runTest {
        val db = buildInMemorySciFinDatabase()
        seeded(db, profileWith(savings = 5000.0).copy(categories = listOf(category("c1").copy(lastResetMonth = "2026-09"))))
        val stale = ProfileData.fresh("p-2", "Household").let { it.copy(categories = listOf(category("h1").copy(profileId = "p-2", spent = moneyOf(80.0), lastResetMonth = "2026-06"))) }
        db.dao().save(stale)
        val store = open(db, LocalDate(2026, 9, 14), this)
        store.switchProfile("p-2").join()
        val d = store.state.value.data!!
        assertEquals("p-2", d.profileId)
        assertEquals("2026-09", d.categories.single().lastResetMonth); assertEquals(0L, d.categories.single().spent)
        assertTrue(store.state.value.catchUp != null)                     // three months replayed on that Profile: its own card
    }
}
