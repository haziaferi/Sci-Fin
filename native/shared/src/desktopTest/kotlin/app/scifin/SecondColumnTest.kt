@file:OptIn(ExperimentalComposeUiApi::class)

package app.scifin

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density as UnitDensity
import androidx.compose.ui.use
import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.SettingsPage
import app.scifin.app.Tab
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.model.AppState
import app.scifin.ui.FigureProbe
import app.scifin.ui.SciFinApp
import app.scifin.ui.screens.LEARN_TOPICS
import app.scifin.ui.shownDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** §4.8's second column for Activity, Learn and Settings (Decided 2026-09-26, §13.1 item 3). */
class SecondColumnTest {
    private val clock = FakeClock(Fixture.simDate)

    private fun store(): AppStore {
        val db = buildInMemorySciFinDatabase()
        val p = Fixture.profile.let { it.copy(settings = it.settings.copy(goalPromptDismissed = true)) }
        runBlocking { db.dao().save(p); db.dao().upsertAppState(AppState(activeProfileId = p.profileId)) }
        val s = AppStore(AppSession(db, RecordingScheduler(), clock, Ids("s")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        runBlocking { s.start() }
        return s
    }

    @Test fun `each destination rests on its chosen view, and Budget and Reports have none`() {
        val s = store()
        s.selectTab(Tab.ACTIVITY); assertEquals(Route.Ledger, shownDetail(s.state.value))
        s.selectTab(Tab.LEARN); assertEquals(Route.LearnTopic(LEARN_TOPICS.first().id), shownDetail(s.state.value))
        s.selectTab(Tab.SETTINGS); assertEquals(Route.Settings(SettingsPage.APPEARANCE), shownDetail(s.state.value))
        s.selectTab(Tab.BUDGET); assertNull(shownDetail(s.state.value))
        s.selectTab(Tab.REPORTS); assertNull(shownDetail(s.state.value))
    }

    @Test fun `a row replaces what the column shows, and Back returns it to rest`() {
        val s = store(); s.selectTab(Tab.SETTINGS)
        s.openDetail(Route.Settings(SettingsPage.INCOME)); s.openDetail(Route.Settings(SettingsPage.BACKUP))
        assertEquals(2, s.state.value.routes.size)                                   // replaced, not stacked
        assertEquals(Route.Settings(SettingsPage.BACKUP), shownDetail(s.state.value))
        s.back()
        assertEquals(Route.Settings(SettingsPage.APPEARANCE), shownDetail(s.state.value))
    }

    private fun figuresAt(width: Int, s: AppStore): Int {
        val seen = ArrayList<String>()
        FigureProbe.sink = { seen += it }
        ImageComposeScene(width = width, height = 900, density = UnitDensity(1f)).use { scene ->
            scene.setContent { SciFinApp(s, targetDp = 40, systemDark = false) }
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
            FigureProbe.sink = null
            File("build/second-column").apply { mkdirs() }.let { File(it, "activity-$width.png").writeBytes(png) }
        }
        return seen.size
    }

    @Test fun `at desktop width Activity draws the activity log beside itself, and a phone does not`() {
        // the census counts (2026-09-26): Activity 3 figures alone, the log 14 more beside it
        assertEquals(3, figuresAt(360, store().also { it.selectTab(Tab.ACTIVITY) }))
        assertEquals(17, figuresAt(1084, store().also { it.selectTab(Tab.ACTIVITY) }))
    }
}
