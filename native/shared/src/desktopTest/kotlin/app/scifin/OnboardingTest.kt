package app.scifin

import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Tab
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.model.Theme
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.scifin.app.SettingsPage
import app.scifin.app.TUTORIAL_STOPS
import app.scifin.ui.screens.appearanceSections

/** §11.1 screen 1 and the first open (Decided 2026-09-14): nothing is created until Continue, and a later open lands on Budget. */
class OnboardingTest {
    private val clock = FakeClock(LocalDate(2026, 9, 14))
    private val ids = Ids("o")

    @Test fun `a fresh install opens on screen 1 with no Profile and creates the first one from name and theme`() = runTest {
        val db = buildInMemorySciFinDatabase(); val sched = RecordingScheduler()
        val store = AppStore(AppSession(db, sched, clock, ids::next), db, this)
        store.start()
        val s0 = store.state.value
        assertTrue(s0.needsFirstProfile); assertNull(s0.data); assertEquals(Route.Onboarding(1), s0.route); assertEquals(Tab.BUDGET, s0.tab)
        assertTrue(db.dao().profiles().isEmpty())
        store.previewTheme(Theme.DAYLIGHT)
        assertEquals(Theme.DAYLIGHT, store.state.value.settings.theme)

        store.createProfile("  Ana ", Theme.TALLOW_TOME, forNewProfile = false).join()
        val s1 = store.state.value
        assertFalse(s1.needsFirstProfile); assertEquals("Ana", s1.data!!.profile.name); assertEquals(Theme.TALLOW_TOME, s1.data.settings.theme)
        assertFalse(s1.data.settings.onboardingComplete)       // steps 2-5 still ahead of the first Profile
        assertNull(s1.previewTheme); assertEquals(Route.Onboarding(2), s1.route)
        // step 2: the scheduler says GRANTED, which is recorded on the Profile, then step 4, then home
        store.requestNotificationPermissionAndContinue().join()
        assertEquals(true, store.state.value.data!!.settings.notificationPermissionGranted)
        assertEquals(0, store.state.value.tutorialStop); assertEquals(Route.TabRoute(Tab.BUDGET, "envelopes"), store.state.value.route)   // step 3: the tour, on stop 1's screen
        repeat(8) { store.tutorialNext() }                                                                                         // ten stops since the split (2026-10-09)
        assertEquals(8, store.state.value.tutorialStop); assertEquals(Route.Settings(SettingsPage.APPEARANCE), store.state.value.route)
        store.tutorialNext()                                                                                                       // stop 10: Profiles, on the hub (§11.6, §8 shipped)
        assertEquals(9, store.state.value.tutorialStop); assertEquals(Route.TabRoute(Tab.SETTINGS), store.state.value.route)
        store.tutorialNext()                                                                                                       // Done on the last stop
        assertNull(store.state.value.tutorialStop); assertEquals(Route.Onboarding(4), store.state.value.route)
        store.completeOnboarding().join()
        assertTrue(store.state.value.data!!.settings.onboardingComplete); assertEquals(Route.TabRoute(Tab.BUDGET), store.state.value.route)
        assertEquals(store.state.value.data, db.dao().load(s1.data.profileId)); assertEquals(s1.data.profileId, db.dao().appState()!!.activeProfileId)
        assertEquals(1, sched.syncs)                                             // create; the answer and the completion move nothing in the schedule

        // the next open lands on Budget with the Profile, no screen 1
        val store2 = AppStore(AppSession(db, sched, clock, ids::next), db, this)
        store2.start()
        assertEquals("Ana", store2.state.value.data!!.profile.name); assertEquals(Route.TabRoute(Tab.BUDGET), store2.state.value.route)
    }

    @Test fun `a later Profile is onboarded by screen 1 alone, so it is born complete and becomes active`() = runTest {
        val db = buildInMemorySciFinDatabase()
        val store = AppStore(AppSession(db, RecordingScheduler(), clock, ids::next), db, this)
        store.start(); store.createProfile("Ana", Theme.SWISS_BLUE, forNewProfile = false).join()
        val first = store.state.value.data!!.profileId
        store.push(Route.Onboarding(1, forNewProfile = true))
        store.createProfile("Household", Theme.DUSK, forNewProfile = true).join()
        val s = store.state.value
        assertEquals("Household", s.data!!.profile.name); assertTrue(s.data.settings.onboardingComplete)
        assertEquals(Route.Onboarding(4, forNewProfile = true), s.route)          // §8.3: screen 1, then the Currency landing only
        store.completeOnboarding().join(); assertEquals(Route.TabRoute(Tab.BUDGET), store.state.value.route)
        assertEquals(2, s.profiles.size); assertEquals(s.data.profileId, db.dao().appState()!!.activeProfileId)
        assertEquals("Ana", db.dao().load(first)!!.profile.name)
    }
}

/** §11 step 4 (Decided 2026-09-15): the landing leads with the currency question, so the Currency row comes first there and only there. */
class CurrencyLandingOrderTest {
    @Test fun `the landing puts Currency before Look and the settings page keeps Look first`() {
        assertEquals("currency", appearanceSections(currencyFirst = true).first())
        assertEquals(listOf("look", "mode", "currency", "density", "motion", "distraction"), appearanceSections(currencyFirst = false))
        assertEquals(appearanceSections(false).toSet(), appearanceSections(true).toSet())
    }
}

/**
 * §11.6 stop 3 (Decided 2026-09-15) was one window with two lines; split 2026-10-09 (owner: "Split it, same words") into
 * a Goals stop on the Goals section and a Recurring stop on the Recurring section, each line word for word, each window
 * still short enough to read on a phone.
 */
class TutorialStopThreeTest {
    @Test fun `the split stops keep stop 3's words, one line each, on their own sections`() {
        val goals = TUTORIAL_STOPS[1]; val recurring = TUTORIAL_STOPS[3]
        assertEquals("goals-section", goals.anchor); assertEquals("recurring-section", recurring.anchor)
        assertEquals(listOf("Goals: near or far. Add to one whenever you like, like a piggy bank."), goals.lines)
        assertEquals(listOf("Recurring Expenses: your flat-fee bills. What’s due this month leaves Savings on the 1st, up front; anything billed less often is set aside a little each month — no surprise charges."), recurring.lines)
        for (s in listOf(goals, recurring)) assertTrue(s.lines.sumOf { it.length } <= 260, "${s.anchor} is ${s.lines.sumOf { it.length }} characters; the window took ten lines at 320")
    }
}
