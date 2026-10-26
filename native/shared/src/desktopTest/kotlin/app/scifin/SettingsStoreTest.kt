package app.scifin

import app.scifin.engine.PAYDAY_REMINDER_KEY
import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Files
import app.scifin.app.PickedFile
import app.scifin.app.Route
import app.scifin.app.SettingsPage
import app.scifin.app.Tab
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.Writes
import app.scifin.model.IncomeCadence
import app.scifin.model.Theme
import app.scifin.model.moneyOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.scifin.data.SciFinDatabase
import app.scifin.notify.NotificationPermission

/** A Files that hands back what it was last given, so export → restore can be exercised without a dialog. */
private class MemoryFiles : Files {
    var saved: String? = null
    var toOpen: PickedFile? = null
    override suspend fun open(extensions: List<String>) = toOpen
    override suspend fun save(suggestedName: String, text: String): Boolean { saved = text; return true }
}

/** §4.7's writes through the store: settings, Income's engine paths, backup out and back, and §8.3's Profile operations. */
class SettingsStoreTest {
    private val clock = FakeClock(LocalDate(2026, 9, 14))
    private val ids = Ids("st")

    private suspend fun fresh(files: Files = Files.None, db: SciFinDatabase = buildInMemorySciFinDatabase(), sched: RecordingScheduler = RecordingScheduler(), scope: kotlinx.coroutines.CoroutineScope): AppStore {
        val store = AppStore(AppSession(db, sched, clock, ids::next), db, scope, files)
        store.start(); store.createProfile("Ana", Theme.SWISS_BLUE, false).join(); store.completeOnboarding().join()
        return store
    }

    @Test fun `appearance and the inline toggle persist, and the notification permission is read on open`() = runTest {
        val db = buildInMemorySciFinDatabase()
        val store = fresh(db = db, scope = this)
        store.updateSettings { it.copy(theme = Theme.DAYLIGHT, currency = "USD") }.join()
        store.updateSettings { it.copy(savingsVsOwedEnabled = true) }.join()
        val saved = db.dao().load(store.state.value.data!!.profileId)!!.settings
        assertEquals(Theme.DAYLIGHT, saved.theme); assertEquals("USD", saved.currency); assertTrue(saved.savingsVsOwedEnabled)
        assertEquals(NotificationPermission.GRANTED, store.state.value.notificationPermission)
    }

    @Test fun `income on, cadence, then off clears the set through the engine, and the payday notification follows it`() = runTest {
        val sched = RecordingScheduler()
        val store = fresh(sched = sched, scope = this)
        val before = sched.syncs
        store.setIncomeEnabled(true).join()
        store.updateSettings { it.copy(income = moneyOf(1450.0), incomePayDate = LocalDate(2026, 9, 25)) }.join()
        // §3.10 (owner, 2026-10-26): income recorded by hand is notified on payday
        assertEquals(LocalDate(2026, 9, 25), sched.reminders.single { it.expenseId == PAYDAY_REMINDER_KEY }.dueDate)
        store.setIncomeCadence(IncomeCadence.SEMIMONTHLY).join()
        var s = store.state.value.data!!.settings
        assertTrue(s.incomeEnabled); assertEquals(IncomeCadence.SEMIMONTHLY, s.incomeCadence); assertEquals(LocalDate(2026, 9, 14), s.incomeEnabledDate)
        store.setIncomeEnabled(false).join()
        s = store.state.value.data!!.settings
        assertFalse(s.incomeEnabled); assertEquals(0L, s.income); assertNull(s.incomePayDate); assertNull(s.incomeEnabledDate)
        assertTrue(sched.syncs > before && sched.reminders.none { it.expenseId == PAYDAY_REMINDER_KEY }, "off takes the payday notification with it")
    }

    @Test fun `export writes one file holding every Profile, and restoring it replaces the device`() = runTest {
        val files = MemoryFiles(); val db = buildInMemorySciFinDatabase()
        val store = fresh(files, db, scope = this)
        store.dispatch { Writes.savingsQuickAdd(it, moneyOf(300.0), "opening", false, clock, ids.next()) }.join()
        store.push(Route.Onboarding(1, forNewProfile = true)); store.createProfile("Household", Theme.TALLOW_TOME, true).join()
        store.exportBackup().join()
        assertNotNull(files.saved); assertEquals(LocalDate(2026, 9, 14), store.state.value.appState!!.lastBackupExportDate)
        val exported = files.saved!!

        // wreck the device, then restore: the file wins whole
        store.deleteProfile(store.state.value.profiles.first { it.name == "Ana" }.id).join()
        assertEquals(1, store.state.value.profiles.size)
        files.toOpen = PickedFile("b.json", exported)
        store.pickBackup().join()
        val pend = store.state.value.pendingBackup
        assertNotNull(pend); assertEquals(2, pend.profiles.size); assertNull(store.state.value.backupError)
        store.confirmBackup().join()
        val s = store.state.value
        assertEquals(setOf("Ana", "Household"), s.profiles.map { it.name }.toSet())
        assertEquals("Household", s.data!!.profile.name)                      // the file's active Profile
        assertEquals(moneyOf(300.0), db.dao().load(s.profiles.first { it.name == "Ana" }.id)!!.savings.amount)
        assertEquals(LocalDate(2026, 9, 14), s.appState!!.lastBackupImportDate)
        assertEquals(listOf(Route.TabRoute(Tab.SETTINGS), Route.Settings(SettingsPage.BACKUP)), s.routes)

        files.toOpen = PickedFile("bad.json", "{\"schemaVersion\": 99, \"profiles\": []}")
        store.pickBackup().join()
        assertNull(store.state.value.pendingBackup); assertNotNull(store.state.value.backupError)
    }

    @Test fun `switch, rename and delete keep exactly one active Profile and never delete the last`() = runTest {
        val db = buildInMemorySciFinDatabase()
        val store = fresh(db = db, scope = this)
        val ana = store.state.value.data!!.profileId
        store.push(Route.Onboarding(1, forNewProfile = true)); store.createProfile("Household", Theme.DUSK, true).join()
        val house = store.state.value.data!!.profileId
        store.switchProfile(ana).join()
        assertEquals(ana, store.state.value.data!!.profileId); assertEquals(ana, db.dao().appState()!!.activeProfileId)
        store.renameProfile(house, "  Joint  ").join()
        assertEquals("Joint", db.dao().profile(house)!!.name); assertEquals("Ana", store.state.value.data!!.profile.name)
        store.deleteProfile(ana).join()                                        // deleting the active one
        assertEquals(house, store.state.value.data!!.profileId); assertEquals(1, store.state.value.profiles.size)
        store.deleteProfile(house).join()                                      // the last one stays
        assertEquals(1, store.state.value.profiles.size); assertNotNull(db.dao().profile(house))
    }
}
