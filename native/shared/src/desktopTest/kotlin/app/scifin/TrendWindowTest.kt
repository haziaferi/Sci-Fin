@file:OptIn(ExperimentalComposeUiApi::class)

package app.scifin

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Tab
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.Reports
import app.scifin.model.AppState
import app.scifin.ui.SciFinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * §4.6's trend window (Decided, 2026\-09\-24). The engine always took a `months` parameter; what
 * was missing was anyone passing anything but its default, which is what made §4.8's withdrawn
 * width clause wrong -- six fixed points have no more resolution to reveal.
 */
class TrendWindowTest {
    private val clock = FakeClock(Fixture.simDate)

    private fun store(months: Int): AppStore {
        val db = buildInMemorySciFinDatabase()
        val p = Fixture.profile.let { it.copy(settings = it.settings.copy(trendMonths = months)) }
        runBlocking { db.dao().save(p); db.dao().upsertAppState(AppState(activeProfileId = p.profile.id)) }
        val s = AppStore(AppSession(db, RecordingScheduler(), clock, Ids("w")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        runBlocking { s.start(); s.selectTab(Tab.REPORTS) }
        return s
    }

    @Test fun `the window the person chooses is the window the trends are computed over`() {
        val d = Fixture.profile
        val today = clock.today()
        for (m in listOf(3, 6, 12, 24)) {
            // §5.1 leaves out months that ended before the opening lot: the fixture's ledger opens on 1 March, seven months back
            assertEquals(minOf(m, 7), Reports.ageOfMoneyTrend(d, today, m).size, "§5.1 at $m months")
            assertEquals(m, Reports.savingsVsOwedTrend(d, today, m).size, "§5.3 at $m months")
            assertEquals(m, Reports.incomeVsExpenses(d, today, m)?.size, "§5.4 at $m months")
        }
    }

    @Test fun `the default is six, so nothing changes for someone who never touches it`() {
        assertEquals(6, Fixture.profile.settings.trendMonths)
    }

    @Test fun `the stored choice survives a write and a reload`() {
        val s = store(6)
        runBlocking { s.updateSettings { it.copy(trendMonths = 24) }.join() }
        assertEquals(24, s.state.value.data!!.settings.trendMonths)
    }

    @Test fun `reports renders at every offered window`() {
        val out = File("build/trend-window").apply { mkdirs() }
        for (m in listOf(3, 6, 12, 24)) {
            ImageComposeScene(width = 1904, height = 1000).use { scene ->
                scene.setContent { SciFinApp(store(m), targetDp = 40, systemDark = false) }
                val bytes = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
                File(out, "buffer-$m.png").writeBytes(bytes)
                assertTrue(bytes.size > 1000, "nothing painted at $m months")
            }
        }
    }
}
