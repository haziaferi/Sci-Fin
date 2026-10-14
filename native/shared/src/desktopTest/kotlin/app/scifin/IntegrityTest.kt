package app.scifin

import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.DamageNotice
import app.scifin.app.Files
import app.scifin.app.PickedFile
import app.scifin.data.Integrity
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.data.buildSciFinDatabase
import app.scifin.data.quickCheck
import app.scifin.engine.Backup
import app.scifin.model.Theme
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files as NioFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A marker held in memory, so the store's side can be read without a disk. */
private class HeldNotice(override var keptAs: String?) : DamageNotice {
    var cleared = false
    override fun clear() { keptAs = null; cleared = true }
}

/** §12 (2026-09-25): an unreadable store is moved aside before anything opens it, and the person is asked what next. */
class IntegrityTest {
    private val clock = FakeClock(LocalDate(2026, 9, 25))

    private fun tempStore(): File = NioFiles.createTempDirectory("scifin-integrity").toFile().let { File(it, "scifin.db") }

    private suspend fun healthy(file: File) {
        val db = buildSciFinDatabase(file)
        AppSession(db, RecordingScheduler(), clock, Ids("h")::next).createProfile("Ana", Theme.SWISS_BLUE, first = true)
        db.close()
    }

    @Test fun `a healthy store is left where it is`() = runTest {
        val file = tempStore()
        healthy(file)
        assertNull(Integrity.quarantineIfDamaged(file, ::quickCheck, "TEST"))
        assertTrue(file.isFile)
        assertNull(Integrity.notice(file).keptAs)
    }

    @Test fun `a file that is not a database is moved aside, and the app still opens`() = runTest {
        val file = tempStore()
        file.writeBytes(ByteArray(8192) { (it * 31).toByte() })
        val db = buildSciFinDatabase(file)                           // threw before 2026-09-25
        assertEquals(0, db.dao().profiles().size)
        db.close()
        val kept = Integrity.notice(file).keptAs
        assertNotNull(kept)
        assertTrue(kept.startsWith("scifin-damaged-"))
        val copy = File(File(file.parentFile, "snapshots"), kept)
        assertEquals(8192L, copy.length())                        // moved whole, not truncated
    }

    @Test fun `a damaged page with a valid header is caught by quick_check`() = runTest {
        val file = tempStore()
        healthy(file)
        assertTrue(file.length() > 4096 * 3, "needs more than the header page")
        RandomAccessFile(file, "rw").use { f -> f.seek(4096L + 16); f.write(ByteArray(2048) { 0x5A }) }
        val kept = Integrity.quarantineIfDamaged(file, ::quickCheck, "TEST")
        assertNotNull(kept)
        assertFalse(file.exists())
        assertEquals(kept.name, Integrity.notice(file).keptAs)
    }

    @Test fun `the screen is owed while nothing exists, and Start fresh answers it`() = runTest {
        val db = buildInMemorySciFinDatabase()
        val notice = HeldNotice("scifin-damaged-20260925-TEST.db")
        val store = AppStore(AppSession(db, RecordingScheduler(), clock, Ids("d")::next), db, this, Files.None, notice)
        store.start()
        assertEquals("scifin-damaged-20260925-TEST.db", store.state.value.damagedCopy)
        store.startFresh()
        assertNull(store.state.value.damagedCopy)
        assertTrue(notice.cleared)
    }

    @Test fun `restoring a backup from the screen answers it too`() = runTest {
        val source = buildInMemorySciFinDatabase()
        val made = AppSession(source, RecordingScheduler(), clock, Ids("s")::next).createProfile("Ana", Theme.SWISS_BLUE, first = true)
        val text = Backup.encode(Backup.build(listOf(made), made.profile.id, clock.today()))
        val db = buildInMemorySciFinDatabase()
        val notice = HeldNotice("kept.db")
        val files = object : Files {
            override suspend fun open(extensions: List<String>) = PickedFile("b.json", text)
            override suspend fun save(suggestedName: String, text: String) = false
        }
        val store = AppStore(AppSession(db, RecordingScheduler(), clock, Ids("r")::next), db, this, files, notice)
        store.start()
        store.pickBackup().join()
        store.confirmBackup().join()
        assertNull(store.state.value.damagedCopy)
        assertTrue(notice.cleared)
        assertEquals("Ana", store.state.value.data?.profile?.name)
    }

    @Test fun `a store that already holds a Profile never shows it`() = runTest {
        val db = buildInMemorySciFinDatabase()
        AppSession(db, RecordingScheduler(), clock, Ids("p")::next).createProfile("Ana", Theme.SWISS_BLUE, first = true)
        val notice = HeldNotice("stale.db")
        val store = AppStore(AppSession(db, RecordingScheduler(), clock, Ids("q")::next), db, this, Files.None, notice)
        store.start()
        assertNull(store.state.value.damagedCopy)
        assertTrue(notice.cleared)
    }
}
