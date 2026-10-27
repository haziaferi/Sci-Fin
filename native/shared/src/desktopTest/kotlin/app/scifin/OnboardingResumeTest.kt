package app.scifin

import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Files
import app.scifin.app.PickedFile
import app.scifin.app.Route
import app.scifin.app.Tab
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.Backup
import app.scifin.model.Theme
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.scifin.data.SciFinDatabase

/**
 * §11.1's two 2026-09-23 decisions, both found by walking the app on the device rather than by
 * any test: an onboarding interrupted between screen 1 and step 5 used to leave a Profile that
 * could never finish, and a fresh install had no way to a restore except through a Profile
 * invented to be overwritten.
 */
class OnboardingResumeTest {
    private val clock = FakeClock(LocalDate(2026, 9, 23))
    private val ids = Ids("r")

    private fun store(db: SciFinDatabase, scope: kotlinx.coroutines.CoroutineScope, files: Files = Files.None) =
        AppStore(AppSession(db, RecordingScheduler(), clock, ids::next), db, scope, files)

    @Test fun `an onboarding killed mid-flow resumes at the step it reached`() = runTest {
        val db = buildInMemorySciFinDatabase()
        val first = store(db, this)
        first.start()
        first.createProfile("Ana", Theme.SWISS_BLUE, forNewProfile = false).join()
        val id = first.state.value.data!!.profileId
        assertEquals(2, db.dao().load(id)!!.settings.onboardingStep, "screen 1's Continue records the step to come back to")

        // killed at step 2. A second store over the same database is the next cold open.
        val second = store(db, this)
        second.start()
        assertEquals(Route.Onboarding(2), second.state.value.route)
        assertFalse(second.state.value.data!!.settings.onboardingComplete)

        second.requestNotificationPermissionAndContinue().join()
        assertEquals(0, second.state.value.tourSlide, "step 3 is the tour")
        assertEquals(3, db.dao().load(id)!!.settings.onboardingStep)

        // killed during the tour: the next open resumes into it, at its first slide rather than nowhere
        val third = store(db, this)
        third.start()
        assertEquals(0, third.state.value.tourSlide)
        third.tourFinish().join()
        assertEquals(Route.Onboarding(4), third.state.value.route)
        assertEquals(4, db.dao().load(id)!!.settings.onboardingStep)

        // killed at the Currency landing: the next open is still there, not on Budget home
        val fourth = store(db, this)
        fourth.start()
        assertEquals(Route.Onboarding(4), fourth.state.value.route)
        fourth.completeOnboarding().join()
        assertTrue(db.dao().load(id)!!.settings.onboardingComplete)
        assertNull(db.dao().load(id)!!.settings.onboardingStep, "the step is cleared once the flow is done")

        // and from there a cold open is Budget home, as it always was
        val fifth = store(db, this)
        fifth.start()
        assertEquals(Route.TabRoute(Tab.BUDGET), fifth.state.value.route)
    }

    @Test fun `screen 1 restores a backup without a Profile having to be invented first`() = runTest {
        val src = Fixture.profile
        val text = Backup.encode(Backup.build(listOf(src), src.profileId, LocalDate(2026, 9, 22)))

        val db = buildInMemorySciFinDatabase()
        val s = store(db, this, OneFile("sci-fin-backup-2026-09-22.json", text))
        s.start()
        assertEquals(Route.Onboarding(1), s.state.value.route)
        assertTrue(s.state.value.needsFirstProfile)

        s.pickBackup().join()
        assertNotNull(s.state.value.pendingBackup, "a valid file lands on the review card, nothing written yet")
        assertTrue(db.dao().profiles().isEmpty(), "and nothing is written until Confirm")

        s.confirmBackup().join()
        assertEquals(Route.TabRoute(Tab.BUDGET), s.state.value.route, "the restored Profile's home replaces the flow")
        assertNull(s.state.value.pendingBackup)
        assertEquals(listOf(src.profile.name), db.dao().profiles().map { it.name })
        assertEquals(src.profileId, s.state.value.data!!.profileId)
        assertFalse(s.state.value.needsFirstProfile)
    }

    @Test fun `a file the validator refuses leaves screen 1 exactly where it was`() = runTest {
        val db = buildInMemorySciFinDatabase()
        val s = store(db, this, OneFile("not-a-backup.json", """{"profiles":"nope"}"""))
        s.start()
        s.pickBackup().join()
        assertNull(s.state.value.pendingBackup)
        assertNotNull(s.state.value.backupError, "screen 1 has no Settings page to say it on, so it says it here")
        assertEquals(Route.Onboarding(1), s.state.value.route)
        assertTrue(db.dao().profiles().isEmpty())
    }
}

/** A picker that always hands back the same file, and never saves. */
private class OneFile(val name: String, val text: String) : Files {
    override suspend fun open(extensions: List<String>) = PickedFile(name, text)
    override suspend fun save(suggestedName: String, text: String) = false
}
