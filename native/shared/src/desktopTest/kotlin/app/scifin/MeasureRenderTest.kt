@file:OptIn(ExperimentalComposeUiApi::class)

package app.scifin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density as UnitDensity
import androidx.compose.ui.use
import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Tab
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.model.AppState
import app.scifin.model.Density
import app.scifin.ui.LocalProbe
import app.scifin.ui.SciFinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import app.scifin.ui.ChartShapes

/**
 * The measurement pass for §13.1 items 1, 5 and 6 (2026-09-26): the real app rendered over the
 * parity fixture at phone and desktop sizes, every probed component's laid-out size written to
 * build/measure/measure.jsonl, one line per report. Density 1, so px = dp. Scoring happens
 * outside, from this file -- nothing here decides anything.
 *
 * Gated by SCIFIN_MEASURE=1: it renders ~100 frames and is not part of the suite's claim.
 */
class MeasureRenderTest {
    private val clock = FakeClock(Fixture.simDate)
    private val out = File("build/measure").apply { mkdirs() }

    private fun store(density: Density): AppStore {
        val db = buildInMemorySciFinDatabase()
        val p = Fixture.profile.let { it.copy(settings = it.settings.copy(density = density, goalPromptDismissed = true)) }   // the prompt would cover what is being compared
        runBlocking { db.dao().save(p); db.dao().upsertAppState(AppState(activeProfileId = p.profileId)) }
        val s = AppStore(AppSession(db, RecordingScheduler(), clock, Ids("m")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        runBlocking { s.start() }
        return s
    }

    private fun frame(ctx: Map<String, Any>, store: AppStore, w: Int, h: Int, fontScale: Float, targetDp: Int, lines: MutableList<String>) {
        val heard = LinkedHashMap<String, Map<String, Float>>()          // last report per name+value set wins
        val seen = ArrayList<Pair<String, Map<String, Float>>>()
        ImageComposeScene(width = w, height = h, density = UnitDensity(1f, fontScale = fontScale)).use { scene ->
            scene.setContent {
                CompositionLocalProvider(LocalProbe provides { name, v -> seen += name to v; heard[name] = v }) {
                    SciFinApp(store, targetDp = targetDp, systemDark = false)
                }
            }
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, ctx.values.joinToString("_") + ".png").writeBytes(png)
        }
        val head = ctx.entries.joinToString(",") { (k, v) -> "\"$k\":" + if (v is String) "\"$v\"" else "$v" }
        for ((name, v) in seen.distinctBy { it.first + it.second }) {
            lines += "{" + head + ",\"name\":\"$name\"," + v.entries.joinToString(",") { "\"${it.key}\":${it.value}" } + "}"
        }
    }

    @Test fun `measure the probed components at phone and desktop sizes`() {
        if (System.getenv("SCIFIN_MEASURE") != "1") return
        val lines = ArrayList<String>()
        val views = listOf(Triple(Tab.BUDGET, "categories", "budget"), Triple(Tab.REPORTS, "buffer", "buffer"),
            Triple(Tab.REPORTS, "spending", "spending"), Triple(Tab.REPORTS, "income", "income"))
        // phones: the width range §2.5's phone rule covers, at the OS text sizes Android offers
        for (w in listOf(320, 360, 412, 480)) for (fs in listOf(1.0f, 1.3f, 1.5f)) for (dn in Density.entries) for ((tab, sub, view) in views) {
            val s = store(dn); s.selectTab(tab); s.selectSub(sub)
            frame(mapOf("platform" to "phone", "win" to w, "fs" to fs, "density" to dn.token, "view" to view), s, w, 900, fs, 48, lines)
        }
        // desktop client widths: 1084 and 1100 are §13.1 item 6's two readings; 932 and 948 are the
        // same windows' Page widths under a 240dp rail (152 more than today's 88), rendered with the 88 rail
        for (w in listOf(932, 948, 1084, 1100, 1264, 1520)) for (fs in listOf(1.0f, 1.5f)) for (dn in Density.entries) for ((tab, sub, view) in views) {
            val s = store(dn); s.selectTab(tab); s.selectSub(sub)
            frame(mapOf("platform" to "desktop", "win" to w, "fs" to fs, "density" to dn.token, "view" to view), s, w, 760, fs, 40, lines)
        }
        File(out, "measure.jsonl").writeText(lines.joinToString("\n") + "\n")
        println("measure: ${lines.size} reports")
    }

    /** §13.1 item 1 as decided 2026-09-26: each chart keeps its phone-360 shape at phone and desktop widths. */
    @Test fun `the bar, sparkline and month columns keep their shape at every width`() {
        val want = mapOf("bar" to (ChartShapes.BAR to "budget"), "sparkline" to (ChartShapes.SPARKLINE to "buffer"),
            "month-columns" to (ChartShapes.MONTH_COLUMNS to "income"))
        val views = mapOf("budget" to (Tab.BUDGET to "categories"), "buffer" to (Tab.REPORTS to "buffer"), "income" to (Tab.REPORTS to "income"))
        for ((platform, w) in listOf("phone" to 320, "phone" to 480, "desktop" to 1084)) for ((name, pair) in want) {
            val (aspect, view) = pair
            val heard = ArrayList<Map<String, Float>>()
            val s = store(Density.COMFORTABLE); val (tab, sub) = views.getValue(view); s.selectTab(tab); s.selectSub(sub)
            ImageComposeScene(width = w, height = 900, density = UnitDensity(1f)).use { scene ->
                scene.setContent { CompositionLocalProvider(LocalProbe provides { n, v -> if (n == name) heard += v }) { SciFinApp(s, targetDp = if (platform == "phone") 48 else 40, systemDark = false) } }
                scene.render()
            }
            val outer = heard.maxBy { it.getValue("w") }
            // the sparkline's probe sits inside its 4dp side padding, so its outer width is 8 more
            val width = outer.getValue("w") + if (name == "sparkline") 8f else 0f
            // heights land on whole pixels, so the check is the height within one pixel of what the shape asks for
            kotlin.test.assertEquals(width / aspect, outer.getValue("h"), 1f, "$name at $platform $w")
        }
    }

    /**
     * §13.1 item 2 (2026-09-26): characters per line of measure-capped prose, on Android's wide
     * layouts (600dp and up -- Compose Desktop fixes fontScale at 1, so Windows never varies), at
     * every text scale Android offers. Activity's note is the longest capped paragraph on a root.
     */
    @Test fun `measure line lengths at Android text scales`() {
        if (System.getenv("SCIFIN_MEASURE") != "1") return
        val lines = ArrayList<String>()
        for (w in listOf(600, 858, 1280)) for (fs in listOf(1.0f, 1.15f, 1.3f, 1.5f, 1.8f, 2.0f)) {
            val heard = ArrayList<Map<String, Float>>()
            var columns = 0f
            for (tab in listOf(Tab.ACTIVITY, Tab.BUDGET)) {
                val s = store(Density.COMFORTABLE); s.selectTab(tab)
                ImageComposeScene(width = w, height = 900, density = UnitDensity(1f, fontScale = fs)).use { scene ->
                    scene.setContent {
                        CompositionLocalProvider(LocalProbe provides { n, v -> if (n == "note-text") heard += v else if (n == "page" && tab == Tab.BUDGET) columns = v.getValue("n") }) {
                            SciFinApp(s, targetDp = 48, systemDark = false)
                        }
                    }
                    scene.render()
                }
            }
            val v = heard.maxBy { it.getValue("len") }
            lines += listOf(w, fs, v["w"], v["lines"], v["chars0"], v["len"], columns).joinToString("\t")
        }
        File(out, "linelength.tsv").writeText("win\tfs\ttextW\tlines\tchars0\tlen\tbudgetColumns\n" + lines.joinToString("\n") + "\n")
    }

    /** §2.5 (Decided 2026-09-26): on a phone in landscape at 150%, every rail label is whole, on one line, at one size. */
    @Test fun `the rail keeps its labels whole in landscape at 150 percent`() {
        val heard = ArrayList<Map<String, Float>>()
        val s = store(Density.COMFORTABLE); s.selectTab(Tab.BUDGET)
        ImageComposeScene(width = 858, height = 411, density = UnitDensity(1f, fontScale = 1.5f)).use { scene ->
            scene.setContent { CompositionLocalProvider(LocalProbe provides { n, v -> if (n == "rail-label") heard += v }) { SciFinApp(s, targetDp = 48, systemDark = false) } }
            scene.render()
        }
        kotlin.test.assertTrue(heard.size >= 5, "the rail reported ${heard.size} labels")      // all five laid out, even past the screen
        kotlin.test.assertTrue(heard.all { it.getValue("lines") == 1f && it.getValue("overflow") == 0f }, "rail labels: $heard")
    }
}
