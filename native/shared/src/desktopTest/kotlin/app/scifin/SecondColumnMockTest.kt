@file:OptIn(ExperimentalComposeUiApi::class)

package app.scifin

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.SettingsPage
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

/**
 * §13.1 item 3 as built (2026-09-26): the real frame at the default window's 1084 × 721, over the
 * parity fixture -- each destination at rest and with a row chosen. Written to build/mockup-item3.
 * Gated by SCIFIN_MOCKUP=1.
 */
class SecondColumnMockTest {
    private val clock = FakeClock(Fixture.simDate)

    private fun store(): AppStore {
        val db = buildInMemorySciFinDatabase()
        val p = Fixture.profile.let { it.copy(settings = it.settings.copy(goalPromptDismissed = true)) }
        runBlocking { db.dao().save(p); db.dao().upsertAppState(AppState(activeProfileId = p.profileId)) }
        val s = AppStore(AppSession(db, RecordingScheduler(), clock, Ids("k")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        runBlocking { s.start() }
        return s
    }

    @Test fun `render the built second columns`() {
        if (System.getenv("SCIFIN_MOCKUP") != "1") return
        val dir = File("build/mockup-item3").apply { mkdirs() }
        fun shot(name: String, tab: Tab, chosen: Route? = null) {
            val s = store(); s.selectTab(tab); chosen?.let(s::openDetail)
            ImageComposeScene(width = 1084, height = 721).use { scene ->
                scene.setContent { SciFinApp(s, targetDp = 40, systemDark = false) }
                File(dir, "$name.png").writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
        val card = Fixture.profile.accounts.first { it.name == "Credit Card" }
        shot("built-activity-rest", Tab.ACTIVITY)
        shot("built-activity-account", Tab.ACTIVITY, Route.AccountDetail(card.id))
        shot("built-learn-rest", Tab.LEARN)
        shot("built-learn-credit", Tab.LEARN, Route.LearnTopic("credit-score"))
        shot("built-settings-rest", Tab.SETTINGS)
        shot("built-settings-income", Tab.SETTINGS, Route.Settings(SettingsPage.INCOME))
    }
}
