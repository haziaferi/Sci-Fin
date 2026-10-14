@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)

package app.scifin

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density as UnitDensity
import androidx.compose.ui.use
import app.scifin.app.AppSession
import app.scifin.app.UiState
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Sheet
import app.scifin.app.Tab
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.Recurring
import app.scifin.model.AccountType
import app.scifin.model.Account
import app.scifin.model.AppState
import app.scifin.model.Goal
import app.scifin.model.GoalMode
import app.scifin.model.ProfileData
import app.scifin.ui.FigureProbe
import app.scifin.ui.SciFinApp
import app.scifin.ui.formatMoney
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lane 5 of the 2026-10-10 cleanup: editor, sheet and list defects, driven through the semantics tree. */
class CleanupScreensBTest {
    private val pid = Fixture.profile.profileId
    private val goal = Goal("g-cleanup", pid, "Trip", saved = 0, target = 120_000, mode = GoalMode.AMOUNT, monthly = 10_000)

    private fun store(p: ProfileData = Fixture.profile): AppStore {
        val q = p.copy(settings = p.settings.copy(goalPromptDismissed = true))
        val db = buildInMemorySciFinDatabase()
        runBlocking { db.dao().save(q); db.dao().upsertAppState(AppState(activeProfileId = q.profileId)) }
        return AppStore(AppSession(db, RecordingScheduler(), FakeClock(Fixture.simDate), Ids("c")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)).also { runBlocking { it.start() } }
    }

    private fun scene(st: AppStore, h: Int = 2400, body: ImageComposeScene.() -> Unit) =
        ImageComposeScene(width = 360, height = h, density = UnitDensity(1f)).use { s ->
            s.setContent { SciFinApp(st, targetDp = 48, systemDark = false) }
            s.render(); s.body()
        }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        val out = ArrayList<SemanticsNode>()
        fun walk(n: SemanticsNode) { out += n; n.children.forEach(::walk) }
        semanticsOwners.forEach { walk(it.rootSemanticsNode) }
        return out
    }
    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""
    private fun SemanticsNode.field() = config.getOrNull(SemanticsProperties.EditableText)?.text
    private fun ImageComposeScene.fields() = nodes().filter { SemanticsActions.SetText in it.config }
    private fun ImageComposeScene.button(label: String) = nodes().first { SemanticsActions.OnClick in it.config && it.text() == label }
    private fun ImageComposeScene.enabled(label: String) = SemanticsProperties.Disabled !in button(label).config
    private fun ImageComposeScene.click(label: String) { button(label).config[SemanticsActions.OnClick].action!!(); render() }
    private fun ImageComposeScene.type(node: SemanticsNode, s: String) { node.config[SemanticsActions.SetText].action!!(AnnotatedString(s)); render() }

    private fun <T> await(st: AppStore, read: (UiState) -> T, ok: (T) -> Boolean): T =
        runBlocking { withTimeoutOrNull(5_000) { st.state.first { ok(read(it)) } } }.let { read(st.state.value) }

    @Test fun `ticking no-target off and on again keeps the typed monthly contribution`() {
        val st = store(Fixture.profile.copy(goals = listOf(goal)))
        st.selectTab(Tab.BUDGET); st.push(Route.GoalEditor(goal.id))
        scene(st) {
            assertNotNull(fields().firstOrNull { it.field() == "100" }, "the monthly field starts on the stored €100")
            click("Track without a target amount or date"); click("Track without a target amount or date")
            click("Save")
        }
        // before: the draft's monthly fell to 0 on the round trip while the field still read 100
        val saved = await(st, { s -> s.data?.goals?.first { it.id == goal.id }?.monthly }) { it == 10_000L }
        assertEquals(10_000L, saved)
    }

    @Test fun `a figure that does not parse holds Save in the goal, category and recurring editors`() {
        val st = store(Fixture.profile.copy(goals = listOf(goal)))
        st.selectTab(Tab.BUDGET); st.push(Route.GoalEditor(goal.id))
        scene(st) {
            assertTrue(enabled("Save"))
            type(fields().first { it.field() == "" }, "abc")          // "Already saved"
            assertFalse(enabled("Save"), "before: saved as 0")
        }
        st.selectTab(Tab.BUDGET); st.push(Route.CategoryEditor(null))
        scene(st) {
            type(fields()[0], "Books"); type(fields()[2], "12.34.5")   // Name, then Spent ("12.345" is a valid grouped 12,345)
            assertFalse(enabled("Add category"))
            type(fields()[2], "12.34"); assertTrue(enabled("Add category"))
        }
        st.selectTab(Tab.BUDGET); st.push(Route.RecurringEditor(null))
        scene(st) {
            type(fields()[0], "Rent"); type(fields()[1], "abc")       // Name, then Amount
            assertFalse(enabled("Add expense"))
        }
    }

    @Test fun `the reconcile sheet refuses a negative or unreadable rate rather than storing or dropping it`() {
        val loan = Account("a-loan", pid, "Car loan", type = AccountType.LOAN, autoUpdateFromPayments = true, interestRate = 0.05)
        val e = Fixture.profile.recurringExpenses.first().copy(linkedAccountId = loan.id)
        val st = store(Fixture.profile.let { it.copy(accounts = it.accounts + loan, recurringExpenses = listOf(e) + it.recurringExpenses.drop(1)) })
        st.selectTab(Tab.BUDGET); st.openSheet(Sheet.Reconcile(Recurring.Reconcile(e.id, 500, Fixture.simDate)))
        scene(st) {
            assertTrue(enabled("Leave it as swept"))
            type(fields().last(), "-5"); assertFalse(enabled("Leave it as swept"))
            type(fields().last(), "abc"); assertFalse(enabled("Leave it as swept"))
            type(fields().last(), "4,5"); assertTrue(enabled("Leave it as swept"))
        }
    }

    @Test fun `the recurring total goes through formatMoney like every other figure`() {
        val st = store()
        val d = st.state.value.data!!
        val expected = formatMoney(Recurring.scheduledTotal(d, Fixture.simDate), d.settings.currency)
        st.selectTab(Tab.BUDGET); st.push(Route.RecurringList)
        val seen = ArrayList<String>()
        FigureProbe.sink = { seen += it }
        try { scene(st, h = 900) {} } finally { FigureProbe.sink = null }
        assertTrue(expected in seen, "expected $expected among $seen")
    }

    @Test fun `an entry sheet that replaces another starts empty`() {
        val st = store()
        val c = Fixture.profile.categories.first()
        st.selectTab(Tab.BUDGET); st.openSheet(Sheet.Entry())
        scene(st, h = 900) {
            type(fields().first(), "12")
            st.openSheet(Sheet.Entry("cat:${c.id}")); render()
            assertEquals("", fields().first().field(), "before: the first sheet's amount carried over")
        }
    }

    @Test fun `an editor that replaces another of its kind opens on its own record`() {
        val st = store()
        val (a, b) = Fixture.profile.categories.take(2)
        st.selectTab(Tab.BUDGET); st.push(Route.CategoryEditor(a.id))
        scene(st) {
            type(fields().first { it.field() == a.name }, "Changed")
            st.openDetail(Route.CategoryEditor(b.id)); render()
            assertEquals(b.name, fields().first().field(), "before: B opened on A's draft")
        }
    }

    @Test fun `keypad Enter writes the entry like the main Enter`() {
        val st = store()
        val c = Fixture.profile.categories.first()
        st.selectTab(Tab.BUDGET); st.openSheet(Sheet.Entry("cat:${c.id}"))
        scene(st, h = 900) {
            val amount = fields().first()
            type(amount, "5")
            fields().first().config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke(); render()
            sendKeyEvent(KeyEvent(Key.NumPadEnter, KeyEventType.KeyDown)); render()
        }
        assertNull(await(st, { it.sheet }) { it == null }, "the sheet wrote and closed")
    }
}
