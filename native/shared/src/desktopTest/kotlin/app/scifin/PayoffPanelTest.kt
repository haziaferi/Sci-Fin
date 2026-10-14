@file:OptIn(ExperimentalComposeUiApi::class)

package app.scifin

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density as UnitDensity
import androidx.compose.ui.use
import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Tab
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.model.AppState
import app.scifin.ui.FigureProbe
import app.scifin.ui.SciFinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse

/** §9.2's payoff panel (Decided 2026-09-26): an empty fixed-payment field projects nothing. */
class PayoffPanelTest {
    @Test fun `an empty fixed-payment field shows no every-month projection`() {
        val db = buildInMemorySciFinDatabase()
        runBlocking { db.dao().save(Fixture.profile); db.dao().upsertAppState(AppState(activeProfileId = Fixture.profile.profileId)) }
        val s = AppStore(AppSession(db, RecordingScheduler(), FakeClock(Fixture.simDate), Ids("p")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        runBlocking { s.start() }
        val card = Fixture.profile.accounts.first { it.name == "Credit Card" }
        s.selectTab(Tab.ACTIVITY); s.push(Route.AccountDetail(card.id))
        val seen = ArrayList<String>()
        FigureProbe.sink = { seen += it }
        ImageComposeScene(width = 360, height = 2000, density = UnitDensity(1f)).use { it.setContent { SciFinApp(s, targetDp = 48, systemDark = false) }; it.render() }
        FigureProbe.sink = null
        // before 2026-09-26 the empty field was floored to 100 minor units: "€1 every month, never at this rate"
        assertFalse(seen.any { it == "€1" }, "figures shown: $seen")
    }
}
