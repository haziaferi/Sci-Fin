package app.scifin

import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Files
import app.scifin.app.LocalCopies
import app.scifin.app.PickedFile
import app.scifin.app.Route
import app.scifin.data.SciFinDatabase
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.Backup
import app.scifin.engine.BackupResult
import app.scifin.engine.LockAfter
import app.scifin.engine.Pin
import app.scifin.engine.hintProblem
import app.scifin.model.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** §4.7's app lock (owner, 2026-10-19): the PIN rules, the gate, the wait, leaving and returning, and the erase. */
class LockTest {

    // ---- the rules ----------------------------------------------------------------------------------------

    @Test fun `a PIN is four to six digits, and a field keeps only digits`() {
        assertTrue(Pin.valid("1234")); assertTrue(Pin.valid("123456"))
        assertFalse(Pin.valid("123")); assertFalse(Pin.valid("1234567")); assertFalse(Pin.valid("12a4"))
        assertEquals("123456", Pin.clean("1 2-3a4567"))
    }

    @Test fun `five wrong PINs are free, then the wait starts at 30 seconds and doubles to at most an hour`() {
        assertEquals(listOf(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO), (1..4).map(Pin::waitAfter))
        assertEquals(listOf(30.seconds, 1.minutes, 2.minutes, 4.minutes), (5..8).map(Pin::waitAfter))
        assertEquals(1.hours, Pin.waitAfter(20))
        assertEquals(1.hours, Pin.waitAfter(Int.MAX_VALUE))
    }

    @Test fun `the PIN is kept only as a salted hash, and checked against it`() {
        val a = Pin.lockFor("482100", "the first flat", LockAfter.ONE_MINUTE)
        val b = Pin.lockFor("482100", null, LockAfter.ONE_MINUTE)
        assertFalse("482100" in a.hash + a.salt)
        assertTrue(a.salt != b.salt && a.hash != b.hash, "the same PIN must not hash the same twice")
        assertTrue(Pin.matches(a, "482100")); assertFalse(Pin.matches(a, "482101")); assertFalse(Pin.matches(a, "4821"))
        assertEquals(6, a.pinLength); assertEquals("the first flat", a.hint)
    }

    @Test fun `a hint is optional and may never be the secret itself`() {
        assertNull(hintProblem("4821", "", "PIN")); assertNull(hintProblem("4821", "   ", "PIN"))
        assertNull(hintProblem("4821", "grandad's door", "PIN"))
        assertEquals("The hint can’t be the PIN itself.", hintProblem("4821", " 4821 ", "PIN"))
        assertEquals("The hint can’t be the password itself.", hintProblem("Lisbon2026", "lisbon2026", "password"))
    }

    // ---- the store ----------------------------------------------------------------------------------------

    private val time = TestTimeSource()

    private class Copies : LocalCopies { var erased = 0; override fun eraseAll(): Int { erased++; return 3 } }

    private fun db(): SciFinDatabase = buildInMemorySciFinDatabase().also { db ->
        runBlocking { db.dao().save(Fixture.profile); db.dao().upsertAppState(AppState(activeProfileId = Fixture.profile.profileId)) }
    }

    private fun store(
        db: SciFinDatabase, files: Files = Files.None, copies: LocalCopies = LocalCopies.None, sched: RecordingScheduler = RecordingScheduler(),
    ): AppStore = AppStore(
        AppSession(db, sched, FakeClock(Fixture.simDate), Ids("l")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        files, copies = copies, time = time, hashing = EmptyCoroutineContext,
    ).also { runBlocking { it.start() } }

    private fun locked(db: SciFinDatabase, pin: String = "4821"): AppStore {
        runBlocking { store(db).setPin(pin, "").join() }
        return store(db)
    }

    @Test fun `off by default, and with a PIN every open starts locked and Back cannot get past the gate`() {
        val db = db()
        val first = store(db)
        assertNull(first.state.value.lock); assertFalse(first.state.value.locked)
        runBlocking { first.setPin("4821", "door").join() }
        assertEquals(4, first.state.value.lock?.pinLength); assertFalse(first.state.value.locked, "setting a PIN does not lock you out there and then")

        val reopened = store(db)
        assertTrue(reopened.state.value.locked)
        assertEquals("door", reopened.state.value.lock?.hint)
        reopened.push(Route.Ledger)
        assertFalse(reopened.handlesBack(reopened.state.value)); assertFalse(reopened.back())
    }

    @Test fun `the right PIN opens, and a wrong one says so and is counted, even across a restart`() {
        val db = db()
        val s = locked(db)
        runBlocking { s.unlock("1111").join() }
        assertTrue(s.state.value.locked); assertTrue(s.state.value.pinWrong)
        assertEquals(1, runBlocking { db.dao().appLock() }!!.failedAttempts)
        runBlocking { s.unlock("4821").join() }
        assertFalse(s.state.value.locked); assertFalse(s.state.value.pinWrong)
        assertEquals(0, runBlocking { db.dao().appLock() }!!.failedAttempts, "the right PIN clears the count")
    }

    @Test fun `after five wrong PINs nothing is checked until the wait has passed, and a restart owes it again`() {
        val db = db()
        val s = locked(db)
        runBlocking { repeat(5) { s.unlock("0000").join() } }
        assertNotNull(s.state.value.pinWaitEnds)
        runBlocking { s.unlock("4821").join() }
        assertTrue(s.state.value.locked, "the right PIN is not even checked during the wait")

        assertNotNull(store(db).state.value.pinWaitEnds, "restarting the app does not skip the wait")

        time += 31.seconds
        runBlocking { s.unlock("4821").join() }
        assertFalse(s.state.value.locked)
    }

    @Test fun `leaving locks after the chosen time, and never for the app's own file dialogs`() {
        val db = db()
        lateinit var s: AppStore
        val files = object : Files {
            override suspend fun open(extensions: List<String>): PickedFile? { s.appLeft(); time += 10.minutes; s.appReturned(); return null }
            override suspend fun save(suggestedName: String, text: String) = false
        }
        s = store(db, files)
        runBlocking { s.setPin("4821", "").join() }

        s.appLeft(); time += 30.seconds; s.appReturned()
        assertFalse(s.state.value.locked, "half a minute away is under the default minute")
        s.appLeft(); time += 61.seconds; s.appReturned()
        assertTrue(s.state.value.locked)
        runBlocking { s.unlock("4821").join() }

        runBlocking { s.pickBackup().join() }
        assertFalse(s.state.value.locked, "picking a file is not leaving the app")

        runBlocking { s.setLockAfter(LockAfter.IMMEDIATELY).join() }
        s.appLeft(); s.appReturned()
        assertTrue(s.state.value.locked)
        assertEquals("immediately", runBlocking { db.dao().appLock() }!!.lockAfter)
    }

    @Test fun `turning the lock off asks for the PIN, and a wrong one keeps it`() {
        val db = db()
        val s = store(db)
        runBlocking { s.setPin("4821", "").join(); s.turnOffLock("1234").join() }
        assertNotNull(s.state.value.lock); assertTrue(s.state.value.pinWrong)
        runBlocking { s.turnOffLock("4821").join() }
        assertNull(s.state.value.lock); assertNull(runBlocking { db.dao().appLock() })
        assertFalse(store(db).state.value.locked)
    }

    @Test fun `changing the PIN asks for the current one, keeps the lock-after choice, and can change only the hint`() {
        val db = db()
        val s = store(db)
        runBlocking { s.setPin("4821", "door").join(); s.setLockAfter(LockAfter.FIVE_MINUTES).join() }
        runBlocking { s.changePin("1111", "907316", "").join() }
        assertTrue(s.state.value.pinWrong); assertEquals(4, s.state.value.lock?.pinLength, "a wrong current PIN changes nothing")
        assertEquals(1, runBlocking { db.dao().appLock() }!!.failedAttempts, "and it counts towards the wait")

        runBlocking { s.changePin("4821", "907316", "the old bus").join() }
        val lock = runBlocking { db.dao().appLock() }!!
        assertTrue(Pin.matches(lock, "907316")); assertFalse(Pin.matches(lock, "4821"))
        assertEquals(LockAfter.FIVE_MINUTES, s.state.value.lock?.after); assertEquals("the old bus", lock.hint); assertEquals(0, lock.failedAttempts)

        runBlocking { s.changePin("907316", "907316", "").join() }
        assertNull(runBlocking { db.dao().appLock() }!!.hint, "the same PIN again changes only the hint")
        runBlocking { s.changePin("907316", "1234", "1234").join() }
        assertTrue(Pin.matches(runBlocking { db.dao().appLock() }!!, "907316"), "a hint equal to the new PIN is refused")
    }

    @Test fun `the lock never travels in a backup, and a restore leaves it in place`() {
        val db = db()
        runBlocking { store(db).setPin("4821", "door").join() }
        val text = runBlocking { Backup.encode(Backup.build(listOf(db.dao().load(Fixture.profile.profileId)!!), Fixture.profile.profileId, Fixture.simDate)) }
        val lock = runBlocking { db.dao().appLock() }!!
        assertFalse(lock.hash in text || lock.salt in text || "door" in text || "app_lock" in text)

        val backup = (Backup.validate(text) as BackupResult.Ok).backup
        runBlocking { Backup.apply(db, backup, Fixture.simDate) }
        assertEquals(lock, runBlocking { db.dao().appLock() })
    }

    @Test fun `a forgotten PIN erases everything on the device and opens as new`() {
        val db = db()
        val copies = Copies(); val sched = RecordingScheduler()
        runBlocking { store(db).setPin("4821", "").join() }
        val s = store(db, copies = copies, sched = sched)
        assertTrue(s.state.value.locked)

        runBlocking { s.eraseEverything().join() }
        val dao = db.dao()
        runBlocking {
            assertTrue(dao.profiles().isEmpty()); assertNull(dao.appState()); assertNull(dao.appLock())
            assertTrue(dao.transactions(Fixture.profile.profileId).isEmpty())
        }
        assertEquals(1, copies.erased, "the kept snapshots go too")
        assertTrue(sched.reminders.isEmpty()); assertEquals(false, sched.newMonth)
        with(s.state.value) {
            assertFalse(locked); assertNull(lock); assertNull(data)
            assertEquals(Route.Onboarding(1), route, "the app opens on screen 1, which offers a restore")
        }
        assertFalse(store(db).state.value.locked)
    }
}
