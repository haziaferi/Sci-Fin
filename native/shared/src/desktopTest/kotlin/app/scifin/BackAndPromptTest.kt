@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package app.scifin

import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Sheet
import app.scifin.app.Tab
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.model.AppState
import app.scifin.ui.screens.goalPromptShows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density as UnitDensity
import androidx.compose.ui.use
import app.scifin.ui.LocalProbe
import app.scifin.ui.SciFinApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.scifin.app.SettingsPage
import app.scifin.engine.Recurring
import app.scifin.model.ProfileData

/** Two defects the 2026-10-10 device walk found: Back never closed a sheet, and the floating + covered the goal prompt. */
class BackAndPromptTest {
    private fun store(p: ProfileData = Fixture.profile): AppStore {
        val db = buildInMemorySciFinDatabase()
        runBlocking { db.dao().save(p); db.dao().upsertAppState(AppState(activeProfileId = p.profileId)) }
        return AppStore(AppSession(db, RecordingScheduler(), FakeClock(Fixture.simDate), Ids("b")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)).also { runBlocking { it.start() } }
    }

    @Test fun `Back closes a sheet before it pops a screen, and leaves a sheet that must be answered`() {
        val st = store()
        st.selectTab(Tab.ACTIVITY); st.push(Route.Ledger); st.openSheet(Sheet.Entry())
        assertTrue(st.handlesBack(st.state.value))
        assertTrue(st.back()); assertNull(st.state.value.sheet); assertEquals(Route.Ledger, st.state.value.route)   // the sheet, not the screen
        assertTrue(st.back()); assertEquals(listOf(Route.TabRoute(Tab.ACTIVITY)), st.state.value.routes)              // then the screen
        assertTrue(st.back()); assertEquals(Tab.BUDGET, st.state.value.tab)                                            // then the page opened before
        assertFalse(st.handlesBack(st.state.value))                                                                     // and at the first, the host's
        st.openSheet(Sheet.Reconcile(Recurring.Reconcile(Fixture.profile.recurringExpenses.first().id, 0, Fixture.simDate)))
        assertFalse(st.closesOnBack(st.state.value))
    }

    @Test fun `Back on Settings returns to the page it was opened from, after its own sub-pages`() {
        val st = store()
        st.selectTab(Tab.BUDGET); st.selectSub("categories")
        val before = st.state.value.routes
        st.selectTab(Tab.SETTINGS); st.push(Route.Settings(SettingsPage.APPEARANCE))
        assertTrue(st.back()); assertEquals(Tab.SETTINGS, st.state.value.tab)                  // the sub-page first
        assertTrue(st.handlesBack(st.state.value))
        assertTrue(st.back()); assertEquals(before, st.state.value.routes)                      // then Budget, on Categories
        assertTrue(st.back()); assertEquals(listOf(Route.TabRoute(Tab.BUDGET)), st.state.value.routes)   // then Envelopes, opened before it
        assertFalse(st.handlesBack(st.state.value))                                              // and from the first page, the host's
        st.selectTab(Tab.REPORTS); st.selectTab(Tab.SETTINGS); st.selectTab(Tab.SETTINGS)        // reselecting is not a new page
        assertTrue(st.back()); assertEquals(Tab.REPORTS, st.state.value.tab)
    }

    /** Owner, 2026-10-28: Back returns to the last page opened -- a tab or its sub-page -- never to a screen pushed on it. */
    @Test fun `Back returns to the last page opened, not the screen that was open on it`() {
        val st = store()
        st.selectTab(Tab.ACTIVITY); st.push(Route.Ledger)
        st.selectTab(Tab.LEARN)
        assertTrue(st.handlesBack(st.state.value))
        assertTrue(st.back()); assertEquals(listOf(Route.TabRoute(Tab.ACTIVITY)), st.state.value.routes)   // Activity, not its ledger
        st.selectTab(Tab.REPORTS); st.selectSub("spending"); st.selectTab(Tab.LEARN)
        assertTrue(st.back()); assertEquals(Route.TabRoute(Tab.REPORTS, "spending"), st.state.value.route)
        assertTrue(st.back()); assertEquals(Route.TabRoute(Tab.REPORTS), st.state.value.route)              // a sub-page is a page too
        assertTrue(st.back()); assertEquals(Tab.ACTIVITY, st.state.value.tab)
    }

    /** Owner, 2026-10-28: the floating + is hidden for the whole tour, a stop's window included. */
    @Test fun `the floating + is not drawn over a tour stop's window`() {
        val st = store(Fixture.profile.let { it.copy(settings = it.settings.copy(goalPromptDismissed = true)) })
        fun fabDrawn(): Boolean {
            val seen = HashSet<String>()
            ImageComposeScene(width = 360, height = 780, density = UnitDensity(1f)).use { scene ->
                scene.setContent { CompositionLocalProvider(LocalProbe provides { n, _ -> seen += n }) { SciFinApp(st, targetDp = 48, systemDark = false) } }
                scene.render(); scene.render(16_000_000)
            }
            return "el:fab" in seen
        }
        st.selectTab(Tab.BUDGET)
        assertTrue(fabDrawn())                     // Budget's root shows it
        st.showStop(3)                             // Recurring Expenses, on Budget
        assertFalse(fabDrawn())
        st.closeStop()
        assertTrue(fabDrawn())
    }

    @Test fun `while the goal prompt is up the floating + is not drawn over it`() {
        val p = Fixture.profile.let { it.copy(goals = emptyList(), settings = it.settings.copy(onboardingComplete = true, goalPromptDismissed = false, catchUpSummaryPending = false)) }
        val st = store(p)
        st.selectTab(Tab.BUDGET)
        assertTrue(goalPromptShows(st.state.value), "the fixture should raise the prompt")
        fun fabDrawn(): Boolean {
            val seen = HashSet<String>()
            ImageComposeScene(width = 360, height = 900, density = UnitDensity(1f)).use { scene ->
                scene.setContent { CompositionLocalProvider(LocalProbe provides { n, _ -> seen += n }) { SciFinApp(st, targetDp = 48, systemDark = false) } }
                scene.render()
            }
            return "el:fab" in seen
        }
        assertFalse(fabDrawn())
        runBlocking { st.dispatch { it.copy(settings = it.settings.copy(goalPromptDismissed = true)) }.join() }
        assertFalse(goalPromptShows(st.state.value)); assertTrue(fabDrawn())        // and back once it is answered
    }
}
