package app.scifin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density as UnitDensity
import androidx.compose.ui.use
import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.SettingsPage
import app.scifin.app.Sheet
import app.scifin.app.TOUR_SLIDES
import app.scifin.app.Tab
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.i18n.I18n
import app.scifin.i18n.Language
import app.scifin.model.AppState
import app.scifin.ui.LocalProbe
import app.scifin.ui.SciFinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** §2.7: the main screens in Italian at phone width, for the owner's review and to catch text that no longer fits. Renders go to build/italian. */
class ItalianRenderTest {
    private val out = File("build/italian").apply { mkdirs() }

    @AfterTest fun english() { I18n.preview = null; I18n.current = Language.EN }

    private fun store(): AppStore {
        val db = buildInMemorySciFinDatabase()
        val p = Fixture.profile.let { it.copy(settings = it.settings.copy(goalPromptDismissed = true)) }
        runBlocking { db.dao().save(p); db.dao().upsertAppState(AppState(activeProfileId = p.profileId)) }
        return AppStore(AppSession(db, RecordingScheduler(), FakeClock(Fixture.simDate), Ids("it")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
            .also { runBlocking { it.start() } }
    }

    private fun render(st: AppStore, name: String, w: Int = 360, h: Int = 1800): Set<String> {
        val seen = HashSet<String>()
        ImageComposeScene(width = w, height = h, density = UnitDensity(1f)).use { scene ->
            scene.setContent { CompositionLocalProvider(LocalProbe provides { n, _ -> seen += n }) { SciFinApp(st, targetDp = 48, systemDark = false) } }
            scene.render(); scene.render(16_000_000)
            File(out, "$name.png").writeBytes(scene.render(32_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        return seen
    }

    @Test fun `the main screens render in Italian`() {
        I18n.preview = Language.IT
        val st = store()
        val shots = listOf<Pair<String, (AppStore) -> Unit>>(
            "budget" to { it.selectTab(Tab.BUDGET) },
            "categories" to { it.selectTab(Tab.BUDGET); it.selectSub("categories") },
            "activity" to { it.selectTab(Tab.ACTIVITY) },
            "ledger" to { it.selectTab(Tab.ACTIVITY); it.push(Route.Ledger) },
            "reports" to { it.selectTab(Tab.REPORTS) },
            "learn" to { it.selectTab(Tab.LEARN) },
            "settings" to { it.selectTab(Tab.SETTINGS) },
            "settings-income" to { it.selectTab(Tab.SETTINGS); it.push(Route.Settings(SettingsPage.INCOME)) },
            "settings-backup" to { it.selectTab(Tab.SETTINGS); it.push(Route.Settings(SettingsPage.BACKUP)) },
            "loan-calc" to { it.selectTab(Tab.LEARN); it.push(Route.LoanCalc) },
            "entry" to { it.selectTab(Tab.BUDGET); it.openSheet(Sheet.Entry()) },
        )
        for ((name, go) in shots) {
            st.closeSheet(); while (st.back()) Unit
            go(st)
            assertTrue(render(st, name).isNotEmpty(), "$name drew nothing")
        }
    }

    /** Every Learn topic in Italian (§2.7 step 3), for the owner's review in place. */
    @Test fun `the Learn topics render in Italian`() {
        I18n.preview = Language.IT
        val st = store()
        for (id in listOf("name-your-goal", "why-categories", "why-no-red", "borrowing", "credit-score", "money-on-hand", "uneven-months", "well-being", "time-worth")) {
            st.closeSheet(); while (st.back()) Unit
            st.selectTab(Tab.LEARN); st.push(Route.LearnTopic(id))
            assertTrue(render(st, "learn-$id", h = 2600).isNotEmpty(), "$id drew nothing")
        }
    }

    /** The longest tour stop, Recurring Expenses, on a phone-sized frame from the Tour index; then every slide, as onboarding shows them. */
    @Test fun `the tour fits a phone in Italian`() {
        I18n.preview = Language.IT
        val st = store()
        st.showStop(3)
        assertTrue(render(st, "tour-recurring", h = 780).isNotEmpty())
        st.closeStop()
        for (i in TOUR_SLIDES.indices) {
            if (i == 0) st.startTour(fromOnboarding = false) else st.tourNext()
            assertTrue(render(st, "tour-slide${i + 1}", h = 780).isNotEmpty())
        }
    }
}
