@file:OptIn(ExperimentalComposeUiApi::class)

package app.scifin

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Sheet
import app.scifin.app.Tab
import app.scifin.app.Files
import app.scifin.app.PickedFile
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.Backup
import app.scifin.model.AppState
import app.scifin.ui.SciFinApp
import app.scifin.ui.SciFinTheme
import app.scifin.ui.components.DeleteWithReturnSheet
import app.scifin.ui.screens.LearnScreen
import app.scifin.ui.tokensFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import app.scifin.app.SettingsPage
import app.scifin.engine.LoanDraft
import app.scifin.model.AccountType

/**
 * Screens over the parity fixture: the numbers on screen are the prototype's numbers. Each
 * case writes a PNG under build/screen-renders and fails only if nothing painted; what they
 * look like is checked by eye from the contact sheet.
 */
class ScreenRenderTest {
    private val clock = FakeClock(Fixture.simDate)

    /** A store whose database already holds the fixture's Profile, opened as any other day would open it. */
    private fun fixtureStore(): AppStore {
        val db = buildInMemorySciFinDatabase()
        runBlocking { db.dao().save(Fixture.profile); db.dao().upsertAppState(AppState(activeProfileId = Fixture.profile.profileId)) }
        val store = AppStore(AppSession(db, RecordingScheduler(), clock, Ids("r")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        runBlocking { store.start() }
        return store
    }

    private fun render(name: String, store: AppStore, w: Int = 360, h: Int = 900) {
        val out = File("build/screen-renders").apply { mkdirs() }
        ImageComposeScene(width = w, height = h).use { scene ->
            scene.setContent { SciFinApp(store, targetDp = 48, systemDark = false) }
            val bytes = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, "$name.png").writeBytes(bytes)
            assertTrue(bytes.size > 1000, "$name rendered nothing")
        }
    }

    /** §11.1's screen 1 on a fresh install: the restore line under Continue, and §4.7's review in its place. */
    @Test fun `onboarding screen 1 and the restore it now offers`() {
        val db = buildInMemorySciFinDatabase()
        val text = Backup.encode(Backup.build(listOf(Fixture.profile), Fixture.profile.profileId, LocalDate(2026, 9, 22)))
        val files = object : Files {
            override suspend fun open(extensions: List<String>) = PickedFile("sci-fin-backup-2026-09-22.json", text)
            override suspend fun save(suggestedName: String, text: String) = false
        }
        val st = AppStore(AppSession(db, RecordingScheduler(), clock, Ids("b")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), files)
        runBlocking { st.start() }
        render("onboarding-screen1", st, h = 1100)
        runBlocking { st.pickBackup().join() }
        render("onboarding-restore-review", st, h = 1100)
    }

    /** §9.3's topic open, and its country list open inside it — the two states the store cannot drive. */
    @Test fun `the credit topic and its country list`() {
        val st = fixtureStore()
        val out = File("build/screen-renders").apply { mkdirs() }
        for ((name, listOpen, h) in listOf(Triple("learn-credit-topic", false, 1400), Triple("learn-credit-resources", true, 2600))) {
            ImageComposeScene(width = 360, height = h).use { scene ->
                scene.setContent {
                    SciFinTheme(tokensFor(Fixture.profile.settings, systemDark = false, targetDp = 48)) {
                        LearnScreen(st.state.value, st, initialOpen = "credit-score", initialListOpen = listOpen)
                    }
                }
                val bytes = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
                File(out, "$name.png").writeBytes(bytes)
                assertTrue(bytes.size > 1000, "$name rendered nothing")
            }
        }
    }

    @Test fun `Budget Envelopes over the fixture, then the goal editor, the recurring list and the delete sheet`() {
        val st = fixtureStore()
        render("budget-envelopes", st, h = 1400)
        st.push(Route.RecurringList); render("recurring-list", st)
        st.selectTab(Tab.BUDGET); st.push(Route.GoalEditor(Fixture.profile.goals.first().id)); render("goal-editor", st, h = 1400)
        st.openSheet(Sheet.DeleteGoal(Fixture.profile.goals.first().id)); render("goal-delete", st)
        st.closeSheet(); st.selectTab(Tab.BUDGET); st.push(Route.GoalEditor(null)); render("goal-new", st, h = 1200)
    }

    @Test fun `the recurring editor, its delete prompt, and the catch-up card`() {
        val st = fixtureStore()
        val e = Fixture.profile.recurringExpenses.first()
        st.push(Route.RecurringList); st.push(Route.RecurringEditor(e.id)); render("recurring-editor", st, h = 1500)
        st.openSheet(Sheet.DeleteExpense(e.id)); render("recurring-delete", st)
        st.closeSheet(); st.selectTab(Tab.BUDGET); st.push(Route.RecurringEditor(null)); render("recurring-new", st, h = 1300)
        // a backdated open: three months replayed, the card at the top of Budget home
        val db = buildInMemorySciFinDatabase()
        runBlocking { db.dao().save(Fixture.profile); db.dao().upsertAppState(AppState(activeProfileId = Fixture.profile.profileId)) }
        val late = AppStore(AppSession(db, RecordingScheduler(), FakeClock(LocalDate(2026, 12, 14)), Ids("l")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        runBlocking { late.start() }
        render("budget-catchup", late, h = 900)
    }

    @Test fun `the delete prompt's confirmation step, both paths, at 360dp`() {
        // §3.6 (2026-09-15): after "Delete and add back" or "Delete only" the sheet restates the consequence with Go back / Delete
        val out = File("build/screen-renders").apply { mkdirs() }
        for ((name, pending) in listOf("delete-confirm-add-back" to true, "delete-confirm-only" to false)) {
            ImageComposeScene(width = 360, height = 600).use { scene ->
                scene.setContent {
                    SciFinTheme(tokensFor(Fixture.profile.settings, systemDark = false, targetDp = 48)) {
                        DeleteWithReturnSheet(
                            title = "Delete goal", question = "", hasReturn = true,
                            confirmAddBack = "€1,050 goes back to Savings and Emergency fund is deleted. Its past history stays in the log.",
                            confirmDeleteOnly = "Emergency fund is deleted. Savings stays as it is.",
                            onDismiss = {}, onDelete = {}, initialPending = pending,
                        )
                    }
                }
                val bytes = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
                File(out, "$name.png").writeBytes(bytes)
                assertTrue(bytes.size > 1000, "$name rendered nothing")
            }
        }
    }

    @Test fun `Reports over the fixture - all four sub-views`() {
        val st = fixtureStore()
        st.selectTab(Tab.REPORTS); render("reports-buffer", st, h = 800)
        st.selectSub("spending"); render("reports-spending", st, h = 1500)
        st.selectSub("income"); render("reports-income", st, h = 700)
        runBlocking { st.updateSettings { it.copy(savingsVsOwedEnabled = true) }.join() }
        st.selectSub("savingsVsOwed"); render("reports-savings-vs-owed", st, h = 700)
    }

    @Test fun `at 1100dp the paned tabs render list-detail and Reports stays single`() {
        val st = fixtureStore()
        render("wide-budget-empty", st, w = 1100, h = 760)
        st.push(Route.GoalEditor(Fixture.profile.goals.first().id)); render("wide-budget-goal", st, w = 1100, h = 760)
        st.selectTab(Tab.ACTIVITY); st.push(Route.Ledger); render("wide-activity-ledger", st, w = 1100, h = 760)
        st.selectTab(Tab.SETTINGS); st.push(Route.Settings(SettingsPage.APPEARANCE)); render("wide-settings-appearance", st, w = 1100, h = 760)
        st.selectTab(Tab.REPORTS); render("wide-reports", st, w = 1100, h = 760)
    }

    @Test fun `Learn, its two calculators and the loan hand-off sheets`() {
        val st = fixtureStore()
        st.selectTab(Tab.LEARN); render("learn", st, h = 900)
        st.push(Route.LoanCalc); render("loan-calc", st, h = 1400)
        st.openSheet(Sheet.LoanBorrow(LoanDraft())); render("loan-borrow", st, h = 900)
        st.closeSheet(); st.selectTab(Tab.LEARN); st.push(Route.PurchaseCalc); render("purchase-calc", st, h = 1200)
        st.back(); st.push(Route.TimeWorthCalc); render("time-worth-calc", st, h = 1200)
    }

    @Test fun `low-distraction mode removes the entity icons and nothing else`() {
        val st = fixtureStore()
        val out = File("build/screen-renders")
        render("budget-envelopes-icons", st, h = 1400)
        runBlocking { st.updateSettings { it.copy(lowDistraction = true) }.join() }
        render("budget-envelopes-low-distraction", st, h = 1400)
        assertTrue(!File(out, "budget-envelopes-icons.png").readBytes().contentEquals(File(out, "budget-envelopes-low-distraction.png").readBytes()), "the mode changed nothing")
        st.selectTab(Tab.SETTINGS); st.showStop(8); render("tutorial-stop9", st)
    }

    @Test fun `Budget Categories over the fixture, the editor and its delete sheet`() {
        val st = fixtureStore()
        st.selectSub("categories"); render("budget-categories", st, h = 1300)
        val c = Fixture.profile.categories.first()
        st.push(Route.CategoryEditor(c.id)); render("category-editor", st, h = 1100)
        st.openSheet(Sheet.DeleteCategory(c.id)); render("category-delete", st)
    }

    @Test fun `Activity over the fixture, the card's payoff outlook, the loan detail, the editor, the ledger, the import page`() {
        val st = fixtureStore()
        st.selectTab(Tab.ACTIVITY); render("activity", st, h = 1100)
        val card = Fixture.profile.accounts.first { it.type == AccountType.CREDIT }
        val loan = Fixture.profile.accounts.first { it.type == AccountType.LOAN }
        st.push(Route.AccountDetail(card.id)); render("account-credit", st, h = 1500)
        st.selectTab(Tab.ACTIVITY); st.push(Route.AccountDetail(loan.id)); render("account-loan", st)
        st.selectTab(Tab.ACTIVITY); st.push(Route.AccountEditor(loan.id)); render("account-editor-loan", st, h = 1300)
        st.selectTab(Tab.ACTIVITY); st.push(Route.Ledger); render("ledger", st, h = 1400)
        st.openSheet(Sheet.Entry("acct:" + card.id)); render("entry-account", st)
        // §3.6's entry (2026-10-09): one sentence per area, from each row's + and from the floating +
        val goal = Fixture.profile.goals.first(); val cat = Fixture.profile.categories.first()
        st.closeSheet(); st.selectTab(Tab.BUDGET); st.openSheet(Sheet.Entry("goal:" + goal.id)); render("entry-goal", st)
        st.openSheet(Sheet.Entry("cat:" + cat.id)); render("entry-category", st)
        st.openSheet(Sheet.Entry("savings")); render("entry-savings", st)
        st.openSheet(Sheet.Entry()); render("entry-floating", st)
        st.closeSheet(); render("budget-desktop", st, w = 1280, h = 900)
        st.openSheet(Sheet.Entry("acct:" + loan.id)); render("entry-loan-desktop", st, w = 1280, h = 900)
        st.closeSheet(); st.selectTab(Tab.ACTIVITY); st.push(Route.Import); render("import", st)
    }
}
