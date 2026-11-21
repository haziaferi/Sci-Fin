@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package app.scifin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density as UnitDensity
import androidx.compose.ui.use
import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.SettingsPage
import app.scifin.app.Tab
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.model.AppState
import app.scifin.model.ProfileData
import app.scifin.ui.LocalProbe
import app.scifin.ui.SciFinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The owner's layout rulings of 2026-11-21 (§2.5, §3.6, §4.7), read off the rendered app through its probes. */
class LayoutDecisionsTest {
    private fun store(p: ProfileData = Fixture.profile.let { it.copy(settings = it.settings.copy(goalPromptDismissed = true)) }): AppStore {
        val db = buildInMemorySciFinDatabase()
        runBlocking { db.dao().save(p); db.dao().upsertAppState(AppState(activeProfileId = p.profileId)) }
        return AppStore(AppSession(db, RecordingScheduler(), FakeClock(Fixture.simDate), Ids("l")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)).also { runBlocking { it.start() } }
    }

    /** Renders [st] and returns every probe report, the last one per name; [tap] names a probe whose centre is clicked first. */
    private fun probes(st: AppStore, w: Int = 360, h: Int = 780, tap: String? = null, scroll: Pair<Offset, Float>? = null): Map<String, Map<String, Float>> {
        val seen = HashMap<String, Map<String, Float>>()
        ImageComposeScene(width = w, height = h, density = UnitDensity(1f)).use { scene ->
            scene.setContent { CompositionLocalProvider(LocalProbe provides { n, v -> seen[n] = v }) { SciFinApp(st, targetDp = 48, systemDark = false) } }
            scene.render(); scene.render(16_000_000)
            if (tap != null) {
                val b = assertNotNull(seen[tap], "no probe $tap")
                val at = Offset(b.getValue("x") + b.getValue("w") / 2, b.getValue("y") + b.getValue("h") / 2)
                scene.sendPointerEvent(PointerEventType.Press, at); scene.sendPointerEvent(PointerEventType.Release, at)
                scene.render(32_000_000); scene.render(48_000_000)
            }
            if (scroll != null) {
                scene.sendPointerEvent(PointerEventType.Scroll, scroll.first, scrollDelta = Offset(0f, scroll.second))
                scene.render(64_000_000); scene.render(80_000_000)
            }
        }
        return seen
    }

    @Test fun `the PIN switch stays off while the fields are open, and is on once a PIN is set`() {
        val st = store()
        st.selectTab(Tab.SETTINGS); st.push(Route.Settings(SettingsPage.SECURITY))
        val name = "switch:Lock with a PIN"
        assertEquals(0f, probes(st)[name]?.get("on"))
        assertEquals(0f, probes(st, tap = name)[name]?.get("on"))        // the fields open; the lock is not on
        runBlocking { st.setPin("4826", "").join() }
        assertEquals(1f, probes(st)[name]?.get("on"))
    }

    @Test fun `a setting's line shows only while it is off`() {
        val st = store()
        st.selectTab(Tab.SETTINGS); st.push(Route.Settings(SettingsPage.INCOME))
        val on = Fixture.profile.settings.incomeEnabled
        assertEquals(if (on) 0f else 1f, probes(st)["setting:Income"]?.get("described"))
        runBlocking { st.setIncomeEnabled(!on).join() }
        assertEquals(if (on) 1f else 0f, probes(st)["setting:Income"]?.get("described"))
    }

    @Test fun `on a wide window nothing scrolls under the desktop's Add, and New is a line, not a full-width row`() {
        val st = store()
        st.selectTab(Tab.BUDGET)
        val before = probes(st, w = 1280, h = 800)
        val add = assertNotNull(before["el:add"], "Add is drawn")
        val top = assertNotNull(before["page:scroll"]).getValue("y")
        assertTrue(add.getValue("y") + add.getValue("h") <= top + 0.5f, "Add ends above the scrolling column (${add.getValue("y") + add.getValue("h")} vs $top)")
        val scrolled = probes(st, w = 1280, h = 800, scroll = Offset(640f, 400f) to 6f)
        assertEquals(top, scrolled["page:scroll"]?.get("y"), "the title band stays put while the page scrolls")
        val new = before.entries.first { it.key.startsWith("el:add:") }.value
        assertTrue(new.getValue("w") < 300f, "+ New is a line of its own width (${new.getValue("w")} dp), not a full-width row")
    }
}
