package app.scifin

import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Files
import app.scifin.app.PickedFile
import app.scifin.data.SciFinDatabase
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.Backup
import app.scifin.engine.BackupResult
import app.scifin.engine.Writes
import app.scifin.model.Theme
import app.scifin.model.moneyOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** A file dialog whose provider fails: a revoked URI, a cloud folder that will not write. */
private class BrokenFiles : Files {
    override suspend fun open(extensions: List<String>): PickedFile? = throw java.io.IOException("revoked")
    override suspend fun save(suggestedName: String, text: String): Boolean = throw SecurityException("no write")
}

/** The failure paths of the store's file and restore steps (audit 2026-09-25): a line on screen, never a crash, never half a restore. */
class FailurePathTest {
    private val clock = FakeClock(LocalDate(2026, 9, 14))
    private val ids = Ids("fp")

    private suspend fun fresh(files: Files, db: SciFinDatabase, scope: CoroutineScope): AppStore {
        val store = AppStore(AppSession(db, RecordingScheduler(), clock, ids::next), db, scope, files)
        store.start(); store.createProfile("Ana", Theme.SWISS_BLUE, false).join(); store.completeOnboarding().join()
        return store
    }

    @Test fun `an unreadable statement says so on the import screen`() = runTest {
        val store = fresh(BrokenFiles(), buildInMemorySciFinDatabase(), this)
        store.pickStatement().join()
        assertNotNull(store.state.value.importError); assertNull(store.state.value.importReview)
    }

    @Test fun `an unreadable backup and an unwritable export say so on the backup page and change nothing`() = runTest {
        val store = fresh(BrokenFiles(), buildInMemorySciFinDatabase(), this)
        store.pickBackup().join()
        assertNotNull(store.state.value.backupError); assertNull(store.state.value.pendingBackup)
        store.exportBackup().join()
        assertTrue(store.state.value.backupError!!.startsWith("Could not save"))
        assertNull(store.state.value.appState!!.lastBackupExportDate)
    }

    @Test fun `a restore that fails part-way leaves every Profile as it was`() = runTest {
        val db = buildInMemorySciFinDatabase()
        val store = fresh(Files.None, db, this)
        store.dispatch { Writes.savingsQuickAdd(it, moneyOf(300.0), "opening", false, clock, ids.next()) }.join()
        val before = db.dao().load(store.state.value.data!!.profileId)!!

        // a backup whose one Profile is the same id with different money
        val other = before.copy(savings = before.savings.copy(amount = moneyOf(9999.0)))
        val text = Backup.encode(Backup.build(listOf(other), other.profileId, clock.today()))
        val b = (Backup.validate(text) as? BackupResult.Ok)?.backup ?: fail("the backup should validate")

        try {
            Backup.apply(db, b, clock.today(), afterProfiles = { throw IllegalStateException("process died") })
            fail("apply should have thrown")
        } catch (_: IllegalStateException) {}

        val after = db.dao().load(before.profileId)!!
        assertEquals(before.savings.amount, after.savings.amount)
        assertNull(db.dao().appState()!!.lastBackupImportDate)
    }
}
