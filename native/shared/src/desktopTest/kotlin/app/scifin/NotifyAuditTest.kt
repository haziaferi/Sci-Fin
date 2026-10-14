package app.scifin

import app.scifin.app.AppSession
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.model.AppState
import app.scifin.model.Cadence
import app.scifin.model.DateMode
import app.scifin.model.RecurringExpense
import app.scifin.model.moneyOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import app.scifin.app.AppStore
import app.scifin.model.Theme
import app.scifin.notify.NotificationPermission

/** §11.5's headless top-up (audit 2026-09-25): it re-arms from state as it stands today. */
class NotifyAuditTest {

    @Test fun `the top-up two months after the last open still registers the next reminder`() = runTest {
        val db = buildInMemorySciFinDatabase()
        val rent = RecurringExpense("rent", "p-1", "Rent", null, moneyOf(950.0), Cadence.MONTHLY, firstDueDate = LocalDate(2026, 1, 30),
            dueDate = LocalDate(2026, 9, 30), dueDateAnchorDay = 30, dateMode = DateMode.FIXED_DATE, reminderEnabled = true, reminderLeadDays = 3)
        val d = profileWith().copy(recurringExpenses = listOf(rent))
        db.dao().save(d); db.dao().upsertAppState(AppState(activeProfileId = d.profileId))
        val sched = RecordingScheduler()
        AppSession(db, sched, FakeClock(LocalDate(2026, 11, 14)), Ids("n")::next).restoreAfterBoot()
        assertEquals(LocalDate(2026, 11, 27), sched.reminders.single().fireAt.date)
        assertEquals(LocalDate(2026, 9, 30), db.dao().load("p-1")!!.recurringExpenses.single().dueDate)   // nothing saved: that is the next open's job
    }

    @Test fun `on Android 13+ a denial reads as not-asked, and once the question was asked the store calls it a denial`() = runTest {
        val db = buildInMemorySciFinDatabase()
        val sched = object : RecordingScheduler() { override fun permissionState() = NotificationPermission.NOT_ASKED }
        val store = AppStore(AppSession(db, sched, FakeClock(LocalDate(2026, 9, 14)), Ids("p")::next), db, this)
        store.start(); store.createProfile("Ana", Theme.SWISS_BLUE, false).join()
        store.refreshPermission()
        assertEquals(NotificationPermission.NOT_ASKED, store.state.value.notificationPermission)   // never asked: step 2 still may
        store.updateSettings { it.copy(notificationPermissionGranted = false) }.join()
        store.refreshPermission()
        assertEquals(NotificationPermission.DENIED, store.state.value.notificationPermission)      // §4.7's line now shows
    }

    @Test fun `the top-up falls back to the first Profile when the active one is gone`() = runTest {
        val db = buildInMemorySciFinDatabase()
        val d = profileWith()
        db.dao().save(d); db.dao().upsertAppState(AppState(activeProfileId = "gone"))
        val sched = RecordingScheduler()
        AppSession(db, sched, FakeClock(LocalDate(2026, 11, 14)), Ids("n")::next).restoreAfterBoot()
        assertEquals(true, sched.newMonth)
    }

    @Test fun `typing an Income amount reaches the OS scheduler not once`() = runTest {
        // §4.7's Income field commits on every keystroke. On Windows each scheduler call was a
        // PowerShell process; the session now hands over only what changed, and an amount moves
        // no reminder -- so the per-keystroke commit costs a row write and nothing else.
        val db = buildInMemorySciFinDatabase()
        var newMonthCalls = 0
        val sched = object : RecordingScheduler() { override fun setNewMonthNotification(enabled: Boolean) { newMonthCalls++; super.setNewMonthNotification(enabled) } }
        val store = AppStore(AppSession(db, sched, FakeClock(LocalDate(2026, 9, 14)), Ids("k")::next), db, this)
        store.start(); store.createProfile("Ana", Theme.SWISS_BLUE, false).join(); store.completeOnboarding().join()
        store.setIncomeEnabled(true).join()
        val syncs = sched.syncs; val months = newMonthCalls
        for (typed in listOf("1", "15", "150", "1500", "1500.5")) store.updateSettings { it.copy(income = moneyOf(typed.toDouble())) }.join()
        assertEquals(syncs, sched.syncs)
        assertEquals(months, newMonthCalls)
    }
}
