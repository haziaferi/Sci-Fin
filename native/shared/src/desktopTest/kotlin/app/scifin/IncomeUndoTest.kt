package app.scifin

import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Notice
import app.scifin.data.SciFinDatabase
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.Income
import app.scifin.engine.Trash
import app.scifin.i18n.I18n
import app.scifin.i18n.Language
import app.scifin.i18n.elideItalian
import app.scifin.model.AppState
import app.scifin.model.IncomeCadence
import app.scifin.model.ProfileData
import androidx.compose.ui.use
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Income off (owner, 2026-11-21): Undo for a few seconds, and the setup kept in the Trash for 30 days. */
class IncomeUndoTest {
    private val clock = FakeClock(Fixture.simDate)
    private val ids = Ids("u")::next
    private val today = Fixture.simDate

    private val d0: ProfileData get() = Fixture.profile.let { d ->
        d.copy(shifts = emptyList(), settings = d.settings.copy(
            incomeEnabled = true, income = 145_000, incomeCadence = IncomeCadence.BIWEEKLY,
            incomePayDate = today.minus(DatePeriod(days = 20)), incomeAutoDeposit = true,
            incomeEnabledDate = today.minus(DatePeriod(days = 60)), lastIncomeDepositDate = today.minus(DatePeriod(days = 6)),
            goalPromptDismissed = true,
        ))
    }

    private fun db(d: ProfileData = d0): SciFinDatabase = buildInMemorySciFinDatabase().also { db ->
        runBlocking { db.dao().save(d); db.dao().upsertAppState(AppState(activeProfileId = d.profileId)) }
    }

    private fun store(db: SciFinDatabase) =
        AppStore(AppSession(db, RecordingScheduler(), clock, ids), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), hashing = EmptyCoroutineContext)
            .also { runBlocking { it.start() } }

    private fun income(d: ProfileData?) = d!!.settings.let { listOf(it.incomeEnabled, it.income, it.incomeCadence, it.incomePayDate, it.incomeAutoDeposit, it.incomeEnabledDate, it.lastIncomeDepositDate) }

    @Test fun `turning Income off keeps its setup in the Trash and offers Undo`() {
        val db = db(); val s = store(db)
        runBlocking { s.setIncomeEnabled(false).join() }
        assertFalse(s.state.value.data!!.settings.incomeEnabled)
        assertEquals(0, s.state.value.data!!.settings.income, "§3.3's clearing still happens")
        val n = assertNotNull(s.state.value.notice as? Notice.IncomeOff)
        val row = s.state.value.trash.single()
        assertEquals("income", row.kind)
        assertEquals(row.id, n.trashId)
    }

    @Test fun `Undo puts every field back as it was, markers included, and empties the Trash`() {
        val db = db(); val s = store(db)
        val before = income(s.state.value.data)
        runBlocking { s.setIncomeEnabled(false).join(); s.undoIncomeOff().join() }
        assertEquals(before, income(s.state.value.data))
        assertNull(s.state.value.notice)
        assertTrue(s.state.value.trash.isEmpty())
        assertTrue(runBlocking { db.dao().trash() }.isEmpty())
    }

    @Test fun `only the latest setup is kept`() {
        val db = db(); val s = store(db)
        runBlocking {
            s.setIncomeEnabled(false).join()
            s.setIncomeEnabled(true).join(); s.updateSettings { it.copy(income = 90_000) }.join()
            s.setIncomeEnabled(false).join()
        }
        val row = s.state.value.trash.single { it.kind == "income" }
        val saved = assertNotNull(Trash.decode(runBlocking { db.dao().trashItem(row.id) }!!.payload)?.income)
        assertEquals(90_000, saved.income)
    }

    @Test fun `a restore days later starts from today, and adds no payday that passed while it was off`() {
        val db = db(); val s = store(db)
        runBlocking { s.setIncomeEnabled(false).join() }
        s.dismissNotice(s.state.value.notice!!.id)
        clock.today = today.plus(DatePeriod(days = 29))
        val row = s.state.value.trash.single()
        runBlocking { s.restoreFromTrash(row.id).join() }
        val st = s.state.value.data!!.settings
        assertTrue(st.incomeEnabled); assertEquals(145_000, st.income); assertTrue(st.incomeAutoDeposit)
        assertEquals(clock.today, st.incomeEnabledDate)
        val (after, _) = Income.runCatchUp(s.state.value.data!!, clock, ids)
        assertTrue(after.transactions.filter { it.isIncome && it.date > today }.all { it.date >= clock.today }, "no deposit dated in the off period")
        assertTrue(s.state.value.trash.isEmpty())
    }

    @Test fun `a setup in the Trash is not restored over Income that is on again`() {
        val db = db(); val s = store(db)
        runBlocking { s.setIncomeEnabled(false).join(); s.setIncomeEnabled(true).join() }
        val row = s.state.value.trash.single()
        runBlocking { s.restoreFromTrash(row.id).join() }
        assertEquals(row.id, s.state.value.trash.single().id, "the row stays until Income is off")
        assertEquals(0, s.state.value.data!!.settings.income)
    }

    @Test fun `with nothing set up there is no Trash row, only the notice`() {
        val bare = d0.let { it.copy(settings = it.settings.copy(income = 0, hourlyRate = 0)) }
        val db = db(bare); val s = store(db)
        runBlocking { s.setIncomeEnabled(false).join() }
        assertNull((s.state.value.notice as Notice.IncomeOff).trashId)
        assertTrue(s.state.value.trash.isEmpty())
    }

    @Test fun `a notice dismissed by id leaves a newer one alone`() {
        val db = db(); val s = store(db)
        runBlocking { s.setIncomeEnabled(false).join() }
        val first = s.state.value.notice!!.id
        s.dismissNotice("an older one")
        assertEquals(first, s.state.value.notice?.id)
        s.dismissNotice(first)
        assertNull(s.state.value.notice)
    }

    @Test fun `on a phone the notice sits above the tab bar and never over the +`() {
        val db = db(); val s = store(db)
        runBlocking { s.setIncomeEnabled(false).join() }
        s.closeSheet(); s.selectTab(app.scifin.app.Tab.BUDGET)
        val seen = HashMap<String, Map<String, Float>>()
        androidx.compose.ui.ImageComposeScene(width = 412, height = 915, density = androidx.compose.ui.unit.Density(1f)).use { scene ->
            scene.setContent { androidx.compose.runtime.CompositionLocalProvider(app.scifin.ui.LocalProbe provides { n, v -> seen[n] = v }) { app.scifin.ui.SciFinApp(s, targetDp = 48, systemDark = false) } }
            scene.render()
        }
        val n = assertNotNull(seen["el:notice"], "the notice is drawn")
        val fab = assertNotNull(seen["el:fab"], "the + is drawn")
        assertTrue(n.getValue("y") + n.getValue("h") <= fab.getValue("y"), "notice $n ends above the + $fab")
        assertTrue(seen.getValue("el:notice-action").getValue("h") >= 48f, "Undo keeps the 48dp target")
    }

    @Test fun `Italian elides the article before 1, 8 and 11, and nowhere else`() {
        assertEquals("eliminato l’11 novembre · eliminazione definitiva l’1 dicembre", elideItalian("eliminato il 11 novembre · eliminazione definitiva il 1 dicembre"))
        assertEquals("un giorno prima dell’8 marzo, dall’11 al 18", elideItalian("un giorno prima del 8 marzo, dal 11 al 18"))
        assertEquals("il 18 maggio, il 1,5 %, il 11.000, il 80", elideItalian("il 18 maggio, il 1,5 %, il 11.000, il 80"))
        val was = I18n.current
        try {
            I18n.current = Language.IT
            assertEquals("Entrate disattivate l’11 nov · eliminazione definitiva l’11 dic", app.scifin.i18n.trf("Income turned off {0} · goes for good on {1}", "11 nov", "11 dic"))
        } finally { I18n.current = was }
    }
}
