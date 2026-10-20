package app.scifin

import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.COPIES_KEPT
import app.scifin.app.CopyEvery
import app.scifin.app.CopyFolder
import app.scifin.app.CopyOutcome
import app.scifin.app.CopyPlace
import app.scifin.app.Files
import app.scifin.app.PickedFile
import app.scifin.app.WindowsCopyPlace
import app.scifin.data.SciFinDatabase
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.BackupCrypto
import app.scifin.model.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §4.7's automatic copy (owner, 2026-10-19/20): weekly by default, 14 kept, a password required with the PIN on. */
class AutoCopyTest {

    /** Folders in memory; sealing is reversible and marked, standing in for a key store. */
    private class Place : CopyPlace {
        val folders = HashMap<String, MutableMap<String, String>>()
        var writable = true
        override fun write(folder: String, name: String, text: String): Boolean {
            val f = folders[folder] ?: return false
            if (!writable) return false
            f[name] = text; return true
        }
        override fun list(folder: String): List<String>? = folders[folder]?.keys?.toList()
        override fun delete(folder: String, name: String) = folders[folder]?.remove(name) != null
        override fun seal(secret: String) = "sealed:$secret"
        override fun unseal(sealed: String) = sealed.removePrefix("sealed:")
    }

    private class Picker(val folder: CopyFolder?) : Files {
        var saved: String? = null
        override suspend fun open(extensions: List<String>): PickedFile? = null
        override suspend fun save(suggestedName: String, text: String): Boolean { saved = text; return true }
        override suspend fun pickFolder() = folder
    }

    private val clock = FakeClock(Fixture.simDate)
    private val place = Place().apply { folders["F"] = mutableMapOf("notes.txt" to "the person's own file") }
    private val sched = RecordingNotices()

    class RecordingNotices : RecordingScheduler() {
        val notices = ArrayList<String>()
        override fun notifyNow(key: String, title: String, body: String) { notices += body }
    }

    private fun db(): SciFinDatabase = buildInMemorySciFinDatabase().also { db ->
        runBlocking { db.dao().save(Fixture.profile); db.dao().upsertAppState(AppState(activeProfileId = Fixture.profile.profileId)) }
    }

    private fun session(db: SciFinDatabase) = AppSession(db, sched, clock, Ids("c")::next, place)

    private fun store(db: SciFinDatabase, files: Files = Picker(CopyFolder("F", "Sync"))) =
        AppStore(session(db), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), files, hashing = EmptyCoroutineContext)
            .also { runBlocking { it.start() } }

    private fun copies() = place.folders["F"]!!.keys.filter { it.startsWith("sci-fin-copy-") }.sorted()

    @Test fun `turning it on asks for a folder, then copies weekly, catching up on the next run`() {
        val db = db()
        val s = store(db)
        assertNull(s.state.value.copy)
        runBlocking { s.setCopyEvery(CopyEvery.WEEKLY).join() }
        assertEquals("Sync", s.state.value.copy?.folderLabel); assertEquals(CopyEvery.WEEKLY, s.state.value.copy?.every)

        val session = session(db)
        assertIs<CopyOutcome.Written>(runBlocking { session.runCopy() })
        assertEquals(listOf("sci-fin-copy-${Fixture.simDate}.json"), copies())
        clock.today = Fixture.simDate.plus(DatePeriod(days = 6))
        assertEquals(CopyOutcome.NotDue, runBlocking { session.runCopy() })
        clock.today = Fixture.simDate.plus(DatePeriod(days = 9))
        assertIs<CopyOutcome.Written>(runBlocking { session.runCopy() }, "a week missed is caught up on the next run")
        assertEquals(2, copies().size)
        assertEquals("the person's own file", place.folders["F"]!!["notes.txt"])
    }

    @Test fun `a folder the person picks no folder for stays off`() {
        val s = store(db(), Picker(null))
        runBlocking { s.setCopyEvery(CopyEvery.DAILY).join() }
        assertTrue(s.state.value.copy == null || s.state.value.copy?.every == CopyEvery.OFF)
    }

    @Test fun `the newest fourteen are kept, and nothing else in the folder is touched`() {
        val db = db()
        runBlocking { store(db).setCopyEvery(CopyEvery.DAILY).join() }
        val session = session(db)
        repeat(COPIES_KEPT + 3) { i ->
            clock.today = Fixture.simDate.plus(DatePeriod(days = i))
            assertIs<CopyOutcome.Written>(runBlocking { session.runCopy() })
        }
        assertEquals(COPIES_KEPT, copies().size)
        assertEquals("sci-fin-copy-${Fixture.simDate.plus(DatePeriod(days = 3))}.json", copies().first())
        assertTrue("notes.txt" in place.folders["F"]!!)
    }

    @Test fun `with a password every copy is encrypted and carries its hint`() {
        val db = db()
        val s = store(db)
        runBlocking { s.setCopyEvery(CopyEvery.WEEKLY).join(); s.setCopyPassword("correct horse", "the stable").join() }
        assertTrue(s.state.value.copy!!.hasPassword)
        assertEquals("sealed:correct horse", runBlocking { db.dao().copySchedule() }!!.sealedPassword, "only the sealed form is stored")
        runBlocking { s.copyNow().join() }
        val text = place.folders["F"]!![copies().single()]!!
        assertEquals("the stable", BackupCrypto.locked(text)?.hint)
        assertIs<BackupCrypto.Opened.Plain>(BackupCrypto.decrypt(text, "correct horse"))
    }

    @Test fun `turning the PIN on pauses plain copies and asks for a password at once`() {
        val db = db()
        val s = store(db)
        runBlocking { s.setCopyEvery(CopyEvery.WEEKLY).join(); s.setPin("4821", "").join() }
        assertTrue(s.state.value.copy!!.paused); assertTrue(s.state.value.copyPasswordAsked)
        assertEquals(CopyOutcome.Paused, runBlocking { session(db).runCopy(force = true) })
        assertTrue(copies().isEmpty(), "nothing plain is written while the PIN is on")

        runBlocking { s.setCopyPassword("correct horse", "the stable").join() }
        assertFalse(s.state.value.copy!!.paused); assertFalse(s.state.value.copyPasswordAsked)
        runBlocking { s.removeCopyPassword().join() }
        assertTrue(s.state.value.copy!!.hasPassword, "the password cannot be removed while the PIN is on")
        assertIs<CopyOutcome.Written>(runBlocking { session(db).runCopy() })
    }

    @Test fun `a failure is recorded and said once, until a copy succeeds again`() {
        val db = db()
        runBlocking { store(db).setCopyEvery(CopyEvery.DAILY).join() }
        val session = session(db)
        place.writable = false
        assertIs<CopyOutcome.Failed>(runBlocking { session.runCopy() })
        clock.today = Fixture.simDate.plus(DatePeriod(days = 1))
        assertIs<CopyOutcome.Failed>(runBlocking { session.runCopy() })
        assertEquals(1, sched.notices.size, "one notice per run of failures")
        assertNotNull(runBlocking { db.dao().copySchedule() }!!.lastError)

        place.writable = true
        assertIs<CopyOutcome.Written>(runBlocking { session.runCopy() })
        assertNull(runBlocking { db.dao().copySchedule() }!!.lastError)
        place.writable = false
        clock.today = Fixture.simDate.plus(DatePeriod(days = 2))
        runBlocking { session.runCopy() }
        assertEquals(2, sched.notices.size, "a new run of failures is said again")
    }

    @Test fun `an export can use the copies' password`() {
        val db = db()
        val picker = Picker(CopyFolder("F", "Sync"))
        val s = store(db, picker)
        runBlocking { s.setCopyEvery(CopyEvery.WEEKLY).join(); s.setCopyPassword("correct horse", "the stable").join() }
        runBlocking { s.exportWithCopiesPassword().join() }
        val text = assertNotNull(picker.saved)
        assertEquals("the stable", BackupCrypto.locked(text)?.hint)
        assertIs<BackupCrypto.Opened.Plain>(BackupCrypto.decrypt(text, "correct horse"))
    }

    @Test fun `Windows seals the password with DPAPI and reads it back`() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        val sealed = assertNotNull(WindowsCopyPlace.seal("correct horse, ünïcode €"))
        assertFalse("correct horse" in sealed)
        assertEquals("correct horse, ünïcode €", WindowsCopyPlace.unseal(sealed))
        assertNull(WindowsCopyPlace.unseal("bm90IHNlYWxlZA=="), "anything not sealed here reads as nothing")
    }
}
