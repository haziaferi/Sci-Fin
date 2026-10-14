@file:OptIn(ExperimentalComposeUiApi::class)

package app.scifin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density as UnitDensity
import androidx.compose.ui.use
import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Tab
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.model.AppState
import app.scifin.ui.BAR_TABS
import app.scifin.ui.LocalProbe
import app.scifin.ui.SciFinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Decided 2026-10-10, with the owner: on a phone Settings leaves the bar for a glyph in the tab pages' title row. */
class SettingsCornerTest {
    private fun store(): AppStore {
        val db = buildInMemorySciFinDatabase()
        val p = Fixture.profile.let { it.copy(settings = it.settings.copy(goalPromptDismissed = true)) }
        runBlocking { db.dao().save(p); db.dao().upsertAppState(AppState(activeProfileId = p.profileId)) }
        return AppStore(AppSession(db, RecordingScheduler(), FakeClock(Fixture.simDate), Ids("s")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)).also { runBlocking { it.start() } }
    }

    @Test fun `the phone bar has four tabs and each tab page's corner opens Settings, which has none`() {
        assertEquals(listOf(Tab.BUDGET, Tab.ACTIVITY, Tab.LEARN, Tab.REPORTS), BAR_TABS)
        val st = store()
        for (tab in BAR_TABS) {
            st.selectTab(tab)
            val at = HashMap<String, Map<String, Float>>()
            ImageComposeScene(width = 360, height = 900, density = UnitDensity(1f)).use { scene ->
                scene.setContent { CompositionLocalProvider(LocalProbe provides { n, v -> at[n] = v }) { SciFinApp(st, targetDp = 48, systemDark = false) } }
                scene.render()
                val c = at["el:settings-corner"] ?: error("no Settings glyph on $tab")
                assertTrue(c.getValue("x") > 250f, "$tab: the glyph sits at the right")
                val o = Offset(c.getValue("x") + c.getValue("w") / 2, c.getValue("y") + c.getValue("h") / 2)
                scene.sendPointerEvent(PointerEventType.Press, o); scene.sendPointerEvent(PointerEventType.Release, o); scene.render()
                assertEquals(Tab.SETTINGS, st.state.value.tab, "$tab's corner opens Settings")
                at.clear(); scene.render()
                assertFalse("el:settings-corner" in at, "Settings has no corner glyph")
            }
        }
    }

    @Test fun `a desktop window keeps Settings in the rail and draws no corner glyph`() {
        val st = store(); st.selectTab(Tab.BUDGET)
        val seen = HashSet<String>()
        ImageComposeScene(width = 1280, height = 900, density = UnitDensity(1f)).use { scene ->
            scene.setContent { CompositionLocalProvider(LocalProbe provides { n, _ -> seen += n }) { SciFinApp(st, targetDp = 40, systemDark = false) } }
            scene.render()
        }
        assertFalse("el:settings-corner" in seen)
    }
}
