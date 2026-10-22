package app.scifin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
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
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §3.6 (owner, 2026-10-22): the desktop keeps every row's + and gains one labelled Add, fixed at the top right of the
 * content -- the right-most column's edge, not the window's -- on Budget and Activity. Renders go to build/desktop-add.
 */
class DesktopAddTest {
    private val out = File("build/desktop-add").apply { mkdirs() }

    private fun store(): AppStore {
        val db = buildInMemorySciFinDatabase()
        val p = Fixture.profile.let { it.copy(settings = it.settings.copy(goalPromptDismissed = true)) }
        runBlocking { db.dao().save(p); db.dao().upsertAppState(AppState(activeProfileId = p.profileId)) }
        return AppStore(AppSession(db, RecordingScheduler(), FakeClock(Fixture.simDate), Ids("d")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
            .also { runBlocking { it.start() } }
    }

    /** Every probe heard in one frame, by name; two frames, so the edges the first one measured are used. */
    private fun frame(st: AppStore, w: Int, h: Int, target: Int, name: String): Map<String, Map<String, Float>> {
        val heard = HashMap<String, Map<String, Float>>()
        ImageComposeScene(width = w, height = h, density = UnitDensity(1f)).use { scene ->
            scene.setContent { CompositionLocalProvider(LocalProbe provides { n, v -> heard[n] = v }) { SciFinApp(st, targetDp = target, systemDark = false) } }
            scene.render(); scene.render(16_000_000)
            File(out, "$name.png").writeBytes(scene.render(32_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        return heard
    }

    private fun Map<String, Float>.right() = getValue("x") + getValue("w")

    @Test fun `the desktop Add sits at the content's right edge, on Budget and Activity only`() {
        val st = store()
        for ((w, h) in listOf(1084 to 721, 1520 to 900)) {
            st.selectTab(Tab.BUDGET)
            val b = frame(st, w, h, 40, "budget-$w")
            val add = assertNotNull(b["el:add"], "Budget has the Add at $w")
            val rowPlus = b.keys.filter { it.startsWith("round:Add to ") || it.startsWith("round:Spent on ") }
            assertTrue(rowPlus.isNotEmpty(), "the rows keep their + at $w")
            // aligned with the content: its right edge is that of the right-most row control, not the window's
            // the row + is drawn inside its row's padding, so the content's edge (the rows' own) lies a little beyond it
            val plusRight = rowPlus.maxOf { b.getValue(it).right() }
            assertTrue(add.right() >= plusRight && add.right() <= plusRight + 24f, "at $w: Add ends at ${add.right()}, the row + at $plusRight")
            // and it clears what lies beneath it at the top of the right-most column
            val below = b.filterKeys { it.startsWith("el:") && it != "el:add" }.values
                .filter { v -> v.getValue("x") < add.right() && v.right() > add.getValue("x") && v.getValue("y") >= add.getValue("y") }
                .minOfOrNull { it.getValue("y") }
            println("desktop add at $w: x=${add.getValue("x")} y=${add.getValue("y")} w=${add.getValue("w")} h=${add.getValue("h")} below=$below")
            if (below != null) assertTrue(add.getValue("y") + add.getValue("h") <= below, "at $w: Add overlaps what is under it (${add.getValue("y") + add.getValue("h")} > $below)")
            if (w == 1520) assertTrue(w - add.right() > 100f, "on a wide window Add stays by the content, not in the corner (${add.right()})")
            assertTrue(add.getValue("y") < 60f, "Add sits at the top at $w")

            st.selectTab(Tab.ACTIVITY)
            assertNotNull(frame(st, w, h, 40, "activity-$w")["el:add"], "Activity has the Add at $w")
            st.selectTab(Tab.REPORTS)
            assertNull(frame(st, w, h, 40, "reports-$w")["el:add"], "Reports has none at $w")
        }
    }

    @Test fun `the desktop Add opens the last area and target, and the phone keeps its floating +`() {
        val st = store()
        st.selectTab(Tab.BUDGET)
        assertNull(frame(st, 360, 800, 48, "budget-phone")["el:add"], "no labelled Add on a phone")
        st.openSheet(Sheet.Entry())
        assertEquals(Sheet.Entry(null), st.state.value.sheet)
    }
}
