package app.scifin

import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Files
import app.scifin.app.PickedFile
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.BackupCrypto
import app.scifin.model.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §4.7: a backup file with a password (owner, 2026-10-19/20). */
class BackupCryptoTest {
    private val fast = 100_000

    @Test fun `a file opens with its password and with nothing else, and keeps its hint and date in the clear`() {
        val text = BackupCrypto.encrypt("""{"app":"sci-fin","profiles":[]}""", "correct horse", "the stable", "2026-10-20", fast)
        assertFalse("profiles" in text, "the content is not readable")
        val l = assertNotNull(BackupCrypto.locked(text))
        assertEquals("the stable", l.hint); assertEquals("2026-10-20", l.exportedAt)
        assertEquals(BackupCrypto.Opened.WrongPassword, BackupCrypto.decrypt(text, "correct hors"))
        assertEquals("""{"app":"sci-fin","profiles":[]}""", (BackupCrypto.decrypt(text, "correct horse") as BackupCrypto.Opened.Plain).text)
        assertNull(BackupCrypto.locked("""{"app":"sci-fin","profiles":[]}"""), "a plain file is not asked for a password")
    }

    @Test fun `the hint and the date cannot be altered without the file refusing to open`() {
        val text = BackupCrypto.encrypt("x", "correct horse", "the stable", "2026-10-20", fast)
        val o = Json.parseToJsonElement(text).jsonObject.toMutableMap()
        o["hint"] = JsonPrimitive("it is: correct horse")
        assertEquals(BackupCrypto.Opened.WrongPassword, BackupCrypto.decrypt(JsonObject(o).toString(), "correct horse"))
        o["hint"] = JsonPrimitive("the stable"); o["iterations"] = JsonPrimitive(50)
        assertIs<BackupCrypto.Opened.Refused>(BackupCrypto.decrypt(JsonObject(o).toString(), "correct horse"), "a cost below the floor is not this app's file")
    }

    @Test fun `a file password is at least eight characters, typed twice, with a hint that is not the password`() {
        assertEquals("At least 8 characters.", BackupCrypto.passwordProblem("short", "short", "h"))
        assertEquals("Type the password again.", BackupCrypto.passwordProblem("longenough", "", "h"))
        assertEquals("The two don’t match yet.", BackupCrypto.passwordProblem("longenough", "longenougH", "h"))
        assertNotNull(BackupCrypto.passwordProblem("longenough", "longenough", "  "), "the hint is required for a file")
        assertEquals("The hint can’t be the password itself.", BackupCrypto.passwordProblem("longenough", "longenough", "LongEnough"))
        assertNull(BackupCrypto.passwordProblem("longenough", "longenough", "the usual, twice"))
    }

    // ---- the store ----------------------------------------------------------------------------------------

    private class Disk : Files {
        var saved: String? = null
        override suspend fun open(extensions: List<String>) = saved?.let { PickedFile("b.json", it) }
        override suspend fun save(suggestedName: String, text: String): Boolean { saved = text; return true }
    }

    private fun store(disk: Disk): AppStore {
        val db = buildInMemorySciFinDatabase()
        runBlocking { db.dao().save(Fixture.profile); db.dao().upsertAppState(AppState(activeProfileId = Fixture.profile.profileId)) }
        return AppStore(
            AppSession(db, RecordingScheduler(), FakeClock(Fixture.simDate), Ids("e")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            disk, hashing = EmptyCoroutineContext,
        ).also { runBlocking { it.start() } }
    }

    @Test fun `an exported file with a password is restored only through it, and shows its hint first`() {
        val disk = Disk(); val s = store(disk)
        runBlocking { s.exportBackup("correct horse", "the stable").join() }
        val text = assertNotNull(disk.saved)
        assertNotNull(BackupCrypto.locked(text))
        assertFalse(Fixture.profile.profile.name in text)

        runBlocking { s.pickBackup().join() }
        assertEquals("the stable", s.state.value.lockedBackup?.hint); assertNull(s.state.value.pendingBackup)
        runBlocking { s.openLockedBackup("wrong password").join() }
        assertTrue(s.state.value.backupPasswordWrong); assertNull(s.state.value.pendingBackup)
        runBlocking { s.openLockedBackup("correct horse").join() }
        assertNull(s.state.value.lockedBackup)
        assertEquals(Fixture.profile.profile.name, s.state.value.pendingBackup?.profiles?.single()?.profile?.name)
    }

    @Test fun `while the PIN is on a file is never written without a password, nor with a weak one`() {
        val disk = Disk(); val s = store(disk)
        runBlocking { s.setPin("4821", "").join() }
        runBlocking { s.exportBackup().join() }
        assertNull(disk.saved, "no plain file while the PIN is on")
        runBlocking { s.exportBackup("short", "h").join(); s.exportBackup("longenough", "").join() }
        assertNull(disk.saved, "nor one with a short password or without a hint")
        runBlocking { s.exportBackup("longenough", "the usual").join() }
        assertNotNull(BackupCrypto.locked(assertNotNull(disk.saved)))
    }
}
