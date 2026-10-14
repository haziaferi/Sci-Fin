@file:OptIn(ExperimentalComposeUiApi::class)

package app.scifin

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density as UnitDensity
import androidx.compose.ui.use
import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Tab
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.model.AppState
import app.scifin.ui.SciFinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** The four phone screens of the type mockup, as built (2026-09-26), at 100% and 150% text. Gated by SCIFIN_MOCKUP=1. */
class TypeAfterRenderTest {
    @Test fun `render the built type system`() {
        if (System.getenv("SCIFIN_MOCKUP") != "1") return
        val dir = File("build/mockup-type").apply { mkdirs() }
        for (fs in listOf(1f, 1.5f)) for ((tab, sub) in listOf(Tab.BUDGET to "categories", Tab.ACTIVITY to null, Tab.REPORTS to "buffer", Tab.SETTINGS to null)) {
            val db = buildInMemorySciFinDatabase()
            val p = Fixture.profile.let { it.copy(settings = it.settings.copy(goalPromptDismissed = true)) }
            runBlocking { db.dao().save(p); db.dao().upsertAppState(AppState(activeProfileId = p.profileId)) }
            val s = AppStore(AppSession(db, RecordingScheduler(), FakeClock(Fixture.simDate), Ids("a")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
            runBlocking { s.start() }; s.selectTab(tab); sub?.let(s::selectSub)
            ImageComposeScene(width = 360, height = 780, density = UnitDensity(1f, fontScale = fs)).use { scene ->
                scene.setContent { SciFinApp(s, targetDp = 48, systemDark = false) }
                File(dir, "built-${(fs * 100).toInt()}-${tab.name.lowercase()}.png").writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }
}
