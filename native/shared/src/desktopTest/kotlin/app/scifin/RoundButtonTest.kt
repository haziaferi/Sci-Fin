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
import app.scifin.app.Sheet
import app.scifin.app.Tab
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.model.AppState
import app.scifin.ui.LocalProbe
import app.scifin.ui.SciFinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * §2.5's small-control rule on the round icon buttons (restored 2026-09-26): drawn at 30dp so they set no row's
 * height, answering over the 48dp platform minimum grown leftward, and each drawn circle answers for its own row.
 * Since 2026-10-09 the goals' + buttons, each opening the entry on its own Goal, and the floating + on the last area.
 */
class RoundButtonTest {
    @Test fun `the goals' + draw small, answer wide, open their own Goal, and the floating + opens the last area`() {
        val db = buildInMemorySciFinDatabase()
        val p = Fixture.profile.let { it.copy(settings = it.settings.copy(incomeEnabled = true, goalPromptDismissed = true)) }
        runBlocking { db.dao().save(p); db.dao().upsertAppState(AppState(activeProfileId = p.profileId)) }
        val store = AppStore(AppSession(db, RecordingScheduler(), FakeClock(Fixture.simDate), Ids("r")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        runBlocking { store.start() }
        store.selectTab(Tab.BUDGET)
        val at = HashMap<String, Map<String, Float>>()
        ImageComposeScene(width = 360, height = 1400, density = UnitDensity(1f)).use { scene ->
            scene.setContent {
                CompositionLocalProvider(LocalProbe provides { n, v -> if (n.startsWith("round:") || n == "el:fab") at.putIfAbsent(n, v) }) { SciFinApp(store, targetDp = 48, systemDark = false) }
            }
            scene.render()
            fun tap(x: Float, y: Float): Sheet? {
                store.closeSheet(); scene.render()
                val o = Offset(x, y)
                scene.sendPointerEvent(PointerEventType.Press, o); scene.sendPointerEvent(PointerEventType.Release, o); scene.render()
                return store.state.value.sheet
            }
            val goals = p.goals.take(2)
            val plus = goals.map { g -> at.getValue("round:Add to ${g.name}") }
            for (b in plus) { assertEquals(30f, b.getValue("w")); assertEquals(30f, b.getValue("h")) }
            goals.zip(plus).forEach { (g, b) ->
                val y = b.getValue("y") + 15f
                assertEquals(Sheet.Entry("goal:${g.id}"), tap(b.getValue("x") + 15f, y))          // the circle
                assertEquals(Sheet.Entry("goal:${g.id}"), tap(b.getValue("x") - 15f, y))          // the widened area to its left
            }
            val fab = at.getValue("el:fab")
            assertEquals(Sheet.Entry(), tap(fab.getValue("x") + fab.getValue("w") / 2, fab.getValue("y") + fab.getValue("h") / 2))
        }
    }
}
