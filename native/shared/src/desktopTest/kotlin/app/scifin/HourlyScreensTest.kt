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
import app.scifin.model.IncomeCadence
import app.scifin.model.IncomeType
import app.scifin.model.ProfileData
import app.scifin.model.Shift
import app.scifin.model.moneyOf
import app.scifin.ui.LocalProbe
import app.scifin.ui.SciFinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §3.10 on screen (owner, 2026-10-26): the payday card, the shift list, the hours entry and the salaried payday entry. Renders go to build/hourly. */
class HourlyScreensTest {
    private val out = File("build/hourly").apply { mkdirs() }

    private fun store(today: LocalDate, edit: (ProfileData) -> ProfileData): AppStore {
        val db = buildInMemorySciFinDatabase()
        val p = edit(Fixture.profile.let { it.copy(settings = it.settings.copy(goalPromptDismissed = true)) })
        runBlocking { db.dao().save(p); db.dao().upsertAppState(AppState(activeProfileId = p.profileId)) }
        return AppStore(AppSession(db, RecordingScheduler(), FakeClock(today), Ids("s")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
            .also { runBlocking { it.start() } }
    }

    private fun hourly(p: ProfileData) = p.copy(
        settings = p.settings.copy(
            incomeEnabled = true, incomeEnabledDate = LocalDate(2026, 11, 1), incomeCadence = IncomeCadence.BIWEEKLY, incomePayDate = LocalDate(2026, 11, 13),
            incomeAutoDeposit = false, incomeType = IncomeType.HOURLY, hourlyRate = moneyOf(20.0), lastPaydayHandled = LocalDate(2026, 10, 31),
        ),
        shifts = listOf(Shift("sh-1", p.profileId, LocalDate(2026, 11, 3), 6.0, moneyOf(20.0)), Shift("sh-2", p.profileId, LocalDate(2026, 11, 10), 8.5, moneyOf(20.0))),
    )

    private fun frame(st: AppStore, name: String, w: Int = 360, h: Int = 1600): Map<String, Map<String, Float>> {
        val heard = HashMap<String, Map<String, Float>>()
        ImageComposeScene(width = w, height = h, density = UnitDensity(1f)).use { scene ->
            scene.setContent { CompositionLocalProvider(LocalProbe provides { n, v -> heard[n] = v }) { SciFinApp(st, targetDp = 48, systemDark = false) } }
            scene.render(); scene.render(16_000_000)
            File(out, "$name.png").writeBytes(scene.render(32_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        return heard
    }

    @Test fun `before payday Budget lists the shifts with an estimate, and on payday the card asks what arrived`() {
        val before = store(LocalDate(2026, 11, 12), ::hourly)
        before.selectTab(Tab.BUDGET)
        val b = frame(before, "budget-before-payday")
        assertNotNull(b["el:shift-summary"]); assertNotNull(b["el:log-hours"]); assertNull(b["el:payday-card"])
        val on = store(LocalDate(2026, 11, 13), ::hourly)
        on.selectTab(Tab.BUDGET)
        assertNotNull(frame(on, "budget-payday")["el:payday-card"])
        runBlocking { on.confirmPayday(moneyOf(250.0)).join() }
        val d = on.state.value.data!!
        assertTrue(d.shifts.isEmpty()); assertEquals(LocalDate(2026, 11, 13), d.transactions.last().date); assertEquals(14.5, d.transactions.last().hoursWorked)
        assertNull(frame(on, "budget-after-payday")["el:payday-card"])
    }

    @Test fun `the Income verb on hourly logs hours at a rate and moves nothing`() {
        val st = store(LocalDate(2026, 11, 12), ::hourly)
        st.selectTab(Tab.BUDGET); st.openSheet(Sheet.Entry("savings", income = true))
        assertNotNull(frame(st, "entry-hours")["el:word-rate"])
        val savings = st.state.value.data!!.savings
        runBlocking { st.logShift(LocalDate(2026, 11, 12), 4.0, moneyOf(22.0)).join() }
        assertEquals(savings, st.state.value.data!!.savings)
        assertEquals(3, st.state.value.data!!.shifts.size)
    }

    @Test fun `salaried by hand, opening the app on payday brings up the Income entry once`() {
        fun manual(p: ProfileData) = p.copy(settings = p.settings.copy(
            incomeEnabled = true, incomeEnabledDate = LocalDate(2026, 11, 1), income = moneyOf(1450.0), incomeAutoDeposit = false,
            incomeCadence = IncomeCadence.MONTHLY, incomePayDate = LocalDate(2026, 11, 10), lastIncomeLoggedDate = null,
        ))
        val st = store(LocalDate(2026, 11, 10), ::manual)
        assertEquals(Sheet.Entry("savings", income = true), st.state.value.sheet)
        frame(st, "salaried-payday-entry")
        assertEquals(LocalDate(2026, 11, 10), st.state.value.data!!.settings.lastPaydayHandled)
    }
}
