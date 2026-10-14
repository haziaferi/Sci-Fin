@file:OptIn(ExperimentalComposeUiApi::class)

package app.scifin

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density as UnitDensity
import androidx.compose.ui.use
import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Tab
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.moneyText
import app.scifin.model.Account
import app.scifin.model.AccountType
import app.scifin.model.AppState
import app.scifin.model.ProfileData
import app.scifin.model.RateType
import app.scifin.ui.FigureProbe
import app.scifin.ui.SciFinApp
import app.scifin.ui.screens.goalPromptShows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Lane 4 of the 2026-10-10 cleanup: Activity, Settings, Budget, Reports and the calculators. */
class CleanupScreensATest {
    private val quiet = Fixture.profile.let { it.copy(settings = it.settings.copy(goalPromptDismissed = true)) }

    private fun store(p: ProfileData = quiet): AppStore {
        val db = buildInMemorySciFinDatabase()
        runBlocking { db.dao().save(p); db.dao().upsertAppState(AppState(activeProfileId = p.profileId)) }
        return AppStore(AppSession(db, RecordingScheduler(), FakeClock(Fixture.simDate), Ids("c")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)).also { runBlocking { it.start() } }
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
    private fun ImageComposeScene.texts(): List<String> = nodes().flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
    private fun ImageComposeScene.fields(): List<SemanticsNode> = nodes().filter { SemanticsProperties.EditableText in it.config }
    private fun SemanticsNode.value(): String = config[SemanticsProperties.EditableText].text
    private fun SemanticsNode.typeAll(text: String) { assertTrue(config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(text))) }
    private fun ImageComposeScene.settle() { render(); render() }

    private fun scene(st: AppStore, width: Int = 360): ImageComposeScene =
        ImageComposeScene(width = width, height = 2000, density = UnitDensity(1f)).also { it.setContent { SciFinApp(st, targetDp = 48, systemDark = false) }; it.settle() }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(500) { if (cond()) return; Thread.sleep(10) }
        error("timed out waiting for $what")
    }

    private val loan = Account(
        "loan-1", Fixture.PROFILE, "Car loan", null, AccountType.LOAN, balance = 500_000,
        autoUpdateFromPayments = true, principal = 600_000, interestRate = 0.059, termMonths = 1, rateType = RateType.FIXED,
    )

    @Test fun `the goal prompt is pending only on Budget home, so Activity keeps its floating +`() {
        val p = Fixture.profile.let { it.copy(goals = emptyList(), settings = it.settings.copy(onboardingComplete = true, goalPromptDismissed = false, catchUpSummaryPending = false)) }
        val st = store(p)
        st.selectTab(Tab.BUDGET)
        assertTrue(goalPromptShows(st.state.value), "the fixture should raise the prompt")
        st.selectTab(Tab.ACTIVITY)          // the tab bar is outside the prompt's scrim
        assertFalse(goalPromptShows(st.state.value))
    }

    @Test fun `a second account editor at the same place starts from its own draft`() {
        val st = store()
        val card = quiet.accounts.first { it.name == "Credit Card" }
        st.selectTab(Tab.ACTIVITY); st.push(Route.AccountDetail(card.id)); st.push(Route.AccountEditor(card.id))
        scene(st, width = 1280).use { sc ->
            assertTrue(sc.fields().any { it.value() == card.name }, "the editor opens on ${card.name}")
            st.push(Route.AccountEditor(null)); sc.settle()
            assertFalse(sc.fields().any { it.value() == card.name }, "New account still holds ${card.name}'s draft")
        }
    }

    @Test fun `the APR field keeps what was typed after it is committed`() {
        val card = quiet.accounts.first { it.name == "Credit Card" }
        val p = quiet.copy(accounts = quiet.accounts.map { if (it.id == card.id) it.copy(apr = null) else it })
        val st = store(p)
        st.selectTab(Tab.ACTIVITY); st.push(Route.AccountDetail(card.id))
        scene(st).use { sc ->
            sc.fields().single().typeAll("21.95")
            waitFor("the APR to be stored") { st.state.value.data!!.accounts.first { it.id == card.id }.apr == 21.95 / 100.0 }
            sc.settle()
            assertEquals("21.95", sc.fields().first().value())
        }
    }

    @Test fun `a one-month term reads singular, and clearing the rate takes the linked-expense chips with the toggle`() {
        val st = store(quiet.copy(accounts = quiet.accounts + loan))
        st.selectTab(Tab.ACTIVITY); st.push(Route.AccountDetail(loan.id))
        scene(st).use { sc ->
            assertTrue("1 month" in sc.texts(), "texts: ${sc.texts()}")
            st.push(Route.AccountEditor(loan.id)); sc.settle()
            assertTrue("Linked recurring expense" in sc.texts())
            sc.fields().first { it.value() == "5.90" }.typeAll("")
            sc.settle()
            assertFalse("Auto-update from payments" in sc.texts())
            assertFalse("Linked recurring expense" in sc.texts())
        }
    }

    @Test fun `Budget says one recurring expense, and a goal's figure is whole for a spaced currency symbol`() {
        val p = quiet.copy(recurringExpenses = quiet.recurringExpenses.take(1), settings = quiet.settings.copy(currency = "CHF"))
        val g = p.goals.first { it.target != null }
        val st = store(p)
        st.selectTab(Tab.BUDGET)
        scene(st).use { sc ->
            val texts = sc.texts()
            assertTrue("1 recurring expense" in texts, "texts: $texts")
            assertFalse("CHF" in texts, "a figure cut at the symbol's space")
            assertTrue(moneyText(abs(g.saved - g.target!!), "CHF") in texts, "texts: $texts")
        }
    }

    @Test fun `the purchase calculator projects nothing until a monthly amount is typed`() {
        val st = store()
        val floor = moneyText(100, quiet.settings.currency)
        st.selectTab(Tab.ACTIVITY); st.push(Route.PurchaseCalc)
        val seen = ArrayList<String>()
        FigureProbe.sink = { seen += it }
        try { scene(st).close() } finally { FigureProbe.sink = null }
        assertFalse(floor in seen, "figures shown: $seen")
    }
}
