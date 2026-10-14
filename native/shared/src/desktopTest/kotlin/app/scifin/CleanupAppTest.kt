package app.scifin

import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Files
import app.scifin.app.PickedFile
import app.scifin.app.Route
import app.scifin.app.SettingsPage
import app.scifin.app.Sheet
import app.scifin.app.TUTORIAL_STOPS
import app.scifin.app.Tab
import app.scifin.data.Integrity
import app.scifin.data.SciFinDatabase
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.Recurring
import app.scifin.model.AppState
import app.scifin.model.ProfileData
import app.scifin.model.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files as NioFiles
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lane 3 of the 2026-10-10 cleanup: Back, Profile changes, onboarding resume and the store's quarantine. */
class CleanupAppTest {
    private val other = ProfileData.fresh("p-2", "Other")

    private class OneCsv(val text: String) : Files {
        override suspend fun open(extensions: List<String>) = PickedFile("sept.csv", text)
        override suspend fun save(suggestedName: String, text: String) = false
    }

    private fun db(vararg profiles: ProfileData, active: String? = profiles.firstOrNull()?.profileId): SciFinDatabase {
        val db = buildInMemorySciFinDatabase()
        runBlocking {
            profiles.forEach { db.dao().save(it) }
            active?.let { db.dao().upsertAppState(AppState(activeProfileId = it)) }
        }
        return db
    }

    private fun store(db: SciFinDatabase, files: Files = Files.None): AppStore =
        AppStore(AppSession(db, RecordingScheduler(), FakeClock(Fixture.simDate), Ids("c")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), files)
            .also { runBlocking { it.start() } }

    @Test fun `Back on a fresh install's screen 1 is taken and does nothing`() {
        val st = store(db())
        assertEquals(Route.Onboarding(1), st.state.value.route)
        assertTrue(st.handlesBack(st.state.value))
        assertTrue(st.back())
        assertEquals(Route.Onboarding(1), st.state.value.route)
    }

    @Test fun `Back with a Reconcile sheet open leaves both the sheet and the screen under it`() {
        val st = store(db(Fixture.profile))
        st.selectTab(Tab.BUDGET); st.push(Route.RecurringList)
        st.openSheet(Sheet.Reconcile(Recurring.Reconcile(Fixture.profile.recurringExpenses.first().id, 0, Fixture.simDate)))
        assertTrue(st.back())
        assertEquals(Route.RecurringList, st.state.value.route)
        assertTrue(st.state.value.sheet is Sheet.Reconcile)
    }

    @Test fun `Back during the tour steps to the previous stop, and before the first it ends the tour`() {
        val st = store(db(Fixture.profile))
        st.startTutorial(fromOnboarding = false)
        st.tutorialNext(); assertEquals(1, st.state.value.tutorialStop)
        st.back(); assertEquals(0, st.state.value.tutorialStop)
        repeat(TUTORIAL_APPEARANCE) { st.tutorialNext() }
        assertTrue(st.state.value.routes.size > 1, "the Appearance stop is a pushed Settings page")
        assertTrue(st.back())
        assertEquals(TUTORIAL_APPEARANCE - 1, st.state.value.tutorialStop)       // the stop, not the pushed page under it
        repeat(TUTORIAL_APPEARANCE - 1) { st.back() }
        assertEquals(0, st.state.value.tutorialStop)
        assertTrue(st.handlesBack(st.state.value)); assertTrue(st.back())
        assertEquals(null, st.state.value.tutorialStop); assertEquals(listOf<Route>(Route.TabRoute(Tab.SETTINGS)), st.state.value.routes)  // a replay returns to Settings
    }

    @Test fun `a Profile switch leaves Settings' Back on the origin's tab, not the old Profile's screen`() {
        val st = store(db(Fixture.profile, other))
        st.selectTab(Tab.ACTIVITY); st.push(Route.AccountDetail("old-account"))
        st.selectTab(Tab.SETTINGS)
        runBlocking { st.switchProfile(other.profileId).join() }
        assertEquals(other.profileId, st.state.value.data!!.profileId)
        assertTrue(st.back())
        assertEquals(listOf<Route>(Route.TabRoute(Tab.ACTIVITY)), st.state.value.routes)
    }

    @Test fun `a Profile switch drops an import review annotated against the old Profile`() {
        val csv = "Date;Description;Amount\n2026-09-10;SUPERMARKET;-42,50\n"
        val st = store(db(Fixture.profile, other), OneCsv(csv))
        runBlocking { st.pickStatement().join() }
        assertNotNull(st.state.value.importReview)
        runBlocking { st.switchProfile(other.profileId).join() }
        assertNull(st.state.value.importReview)
    }

    @Test fun `a first Profile left unfinished with no step stored resumes at step 2`() {
        val p = Fixture.profile.let { it.copy(settings = it.settings.copy(onboardingComplete = false, onboardingStep = null)) }
        val st = store(db(p))
        assertEquals(Route.Onboarding(2), st.state.value.route)
    }

    @Test fun `the first Profile is born with its step, in the one save`() {
        val db = db()
        val made = runBlocking { AppSession(db, RecordingScheduler(), FakeClock(Fixture.simDate), Ids("f")::next).createProfile("Ana", Theme.SWISS_BLUE, first = true) }
        assertEquals(2, runBlocking { db.dao().load(made.profileId) }!!.settings.onboardingStep)
    }

    @Test fun `opening falls back to the first Profile and makes it the active one`() {
        val db = db(Fixture.profile, active = "gone")
        val st = store(db)
        assertEquals(Fixture.profile.profileId, st.state.value.data!!.profileId)
        assertEquals(Fixture.profile.profileId, runBlocking { db.dao().appState() }!!.activeProfileId)
    }

    private fun tempStore(): File = NioFiles.createTempDirectory("scifin-cleanup").toFile().let { File(it, "scifin.db") }

    @Test fun `a store moved aside owes its notice even when its write-ahead log could not follow`() {
        val file = tempStore()
        file.writeBytes(ByteArray(8192) { 1 }); File(file.path + "-wal").writeBytes(ByteArray(64) { 2 })
        val now = LocalDateTime.of(2026, 10, 10, 12, 0, 0)
        // the -wal's destination already exists as a non-empty folder, so neither the rename nor the copy can land
        val blocked = File(File(file.parentFile, "snapshots"), "scifin-damaged-20261010-120000-TEST.db-wal").apply { mkdirs() }
        File(blocked, "x").writeText("x")
        Integrity.quarantineIfDamaged(file, { listOf("page 2: corrupt") }, "TEST", now)
        assertFalse(file.exists())
        assertEquals("scifin-damaged-20261010-120000-TEST.db", Integrity.notice(file).keptAs)
    }

    @Test fun `a store another process holds locked is not taken for damage`() {
        val file = tempStore()
        file.writeBytes(ByteArray(8192) { 1 })
        assertNull(Integrity.quarantineIfDamaged(file, { throw IllegalStateException("Error code: 5, message: database is locked") }, "TEST"))
        assertTrue(file.isFile)
        assertNull(Integrity.notice(file).keptAs)
    }

    private companion object {
        /** The Appearance stop's index in §11.6's tour. */
        val TUTORIAL_APPEARANCE = TUTORIAL_STOPS.indexOfFirst { it.settingsPage == SettingsPage.APPEARANCE }
    }
}
