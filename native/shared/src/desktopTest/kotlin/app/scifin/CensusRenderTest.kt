@file:OptIn(ExperimentalComposeUiApi::class)

package app.scifin

import androidx.compose.runtime.CompositionLocalProvider
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
import app.scifin.ui.LocalProbe
import app.scifin.ui.SciFinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * §4.8's co-visibility census, re-run on every view the three unscoped destinations have
 * (§13.1 item 3, 2026-09-26): money figures formatted and progress bars laid out in one frame of
 * each view, over the parity fixture, at phone width so each view is alone. The Reports rows are
 * the calibration: the 2026-09-24 census counted Spending 12 figures / 11 bars and Income 2 / 12.
 * Writes build/measure/census.tsv. Gated by SCIFIN_CENSUS=1.
 */
class CensusRenderTest {
    private val clock = FakeClock(Fixture.simDate)

    private fun store(): AppStore {
        val db = buildInMemorySciFinDatabase()
        val p = Fixture.profile.let { it.copy(settings = it.settings.copy(goalPromptDismissed = true)) }
        runBlocking { db.dao().save(p); db.dao().upsertAppState(AppState(activeProfileId = p.profileId)) }
        val s = AppStore(AppSession(db, RecordingScheduler(), clock, Ids("c")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        runBlocking { s.start() }
        return s
    }

    @Test fun `census of every view`() {
        if (System.getenv("SCIFIN_CENSUS") != "1") return
        val out = File("build/measure").apply { mkdirs() }
        val rows = ArrayList<String>()
        fun count(view: String, w: Int, setup: (AppStore) -> Unit) {
            val s = store(); setup(s)
            val figures = ArrayList<String>(); var bars = 0; var columnCharts = 0
            FigureProbe.sink = { figures += it }
            ImageComposeScene(width = w, height = 2400, density = UnitDensity(1f)).use { scene ->
                scene.setContent {
                    CompositionLocalProvider(LocalProbe provides { n, _ -> if (n == "bar") bars++ else if (n == "month-columns") columnCharts++ }) { SciFinApp(s, targetDp = 40, systemDark = false) }
                }
                val png = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
                FigureProbe.sink = null                          // first composition only: a later recomposition would count twice
                File(out, "census-$view.png").writeBytes(png)
            }
            val months = s.state.value.data!!.settings.trendMonths
            rows += listOf(view, w, figures.size, bars + columnCharts * months * 2, figures.joinToString(" ")).joinToString("\t")
        }
        val acct = Fixture.profile.accounts
        count("reports-buffer", 360) { it.selectTab(Tab.REPORTS); it.selectSub("buffer") }
        count("reports-spending", 360) { it.selectTab(Tab.REPORTS); it.selectSub("spending") }
        count("reports-income", 360) { it.selectTab(Tab.REPORTS); it.selectSub("income") }
        count("budget-envelopes", 360) { it.selectTab(Tab.BUDGET); it.selectSub("envelopes") }
        count("budget-categories", 360) { it.selectTab(Tab.BUDGET); it.selectSub("categories") }
        count("budget-two-columns", 1084) { it.selectTab(Tab.BUDGET) }
        count("activity", 360) { it.selectTab(Tab.ACTIVITY) }
        for (a in acct) count("account-${a.name.replace(' ', '-')}", 360) { it.selectTab(Tab.ACTIVITY); it.push(Route.AccountDetail(a.id)) }
        count("ledger", 360) { it.selectTab(Tab.ACTIVITY); it.push(Route.Ledger) }
        count("import", 360) { it.selectTab(Tab.ACTIVITY); it.push(Route.Import) }
        count("learn", 360) { it.selectTab(Tab.LEARN) }
        count("loan-calc", 360) { it.selectTab(Tab.LEARN); it.push(Route.LoanCalc) }
        count("purchase-calc", 360) { it.selectTab(Tab.LEARN); it.push(Route.PurchaseCalc) }
        count("settings", 360) { it.selectTab(Tab.SETTINGS) }
        for (pg in SettingsPage.entries) count("settings-${pg.name.lowercase()}", 360) { it.selectTab(Tab.SETTINGS); it.push(Route.Settings(pg)) }
        File(out, "census.tsv").writeText("view\twidth\tfigures\tbars\tamounts\n" + rows.joinToString("\n") + "\n")
    }
}
