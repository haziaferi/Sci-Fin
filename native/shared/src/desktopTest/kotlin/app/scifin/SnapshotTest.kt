package app.scifin

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.scifin.data.Snapshot
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §12's pre-migration snapshot. The case that matters is the one the file layout creates: with
 * WAL on, the main database file can hold a header and nothing else, so a copy would capture
 * nothing. These tests write the data, leave it un-checkpointed, and require the snapshot to
 * contain it anyway.
 */
class SnapshotTest {
    private val driver = BundledSQLiteDriver()

    /** A database at [version] holding one row, deliberately left with its data in the WAL. */
    private fun seed(dir: File, version: Int, rows: Int = 3): File {
        val db = File(dir, "scifin.db")
        val c = driver.open(db.absolutePath)
        c.execSQL("PRAGMA journal_mode=WAL")
        c.execSQL("CREATE TABLE settings (profileId TEXT PRIMARY KEY, currency TEXT NOT NULL)")
        for (i in 0 until rows) c.execSQL("INSERT INTO settings VALUES ('p$i', 'EUR')")
        c.execSQL("PRAGMA user_version = $version")
        c.close()
        return db
    }

    private fun rowCount(db: File): Int {
        val c = driver.open(db.absolutePath)
        try {
            val s = c.prepare("SELECT count(*) FROM settings")
            s.step()
            return s.getInt(0).also { s.close() }
        } finally { c.close() }
    }

    @Test fun `the version is read from the file header, with no database connection`() {
        val dir = createTempDirectory("snap-ver").toFile(); val db = seed(dir, 2)
        assertEquals(2, Snapshot.storedVersion(db, driver))
        dir.deleteRecursively()
    }

    @Test fun `a missing or truncated file reads as unknown rather than as a version`() {
        val dir = createTempDirectory("snap-missing").toFile()
        assertNull(Snapshot.storedVersion(File(dir, "absent.db")))
        File(dir, "short.db").writeBytes(ByteArray(10))
        assertNull(Snapshot.storedVersion(File(dir, "short.db")))
        dir.deleteRecursively()
    }

    @Test fun `a database behind the current version is snapshotted whole, WAL included`() {
        val dir = createTempDirectory("snap-behind").toFile()
        val db = File(dir, "scifin.db")
        val a = driver.open(db.absolutePath)
        a.execSQL("PRAGMA journal_mode=WAL")
        a.execSQL("PRAGMA wal_autocheckpoint=0")
        a.execSQL("CREATE TABLE settings (profileId TEXT PRIMARY KEY, currency TEXT NOT NULL)")
        for (i in 0 until 5) a.execSQL("INSERT INTO settings VALUES ('p$i', 'EUR')")
        a.execSQL("PRAGMA user_version = 2")
        // Measured 2026-09-24: this driver checkpoints when ANY connection closes, not only the
        // last, so the populated-WAL state cannot be reached by closing the writer first. It is
        // reached by not closing it -- which is also the shape the owner's real file was found in:
        // 4KB in the main database against 350KB of write-ahead log.
        assertTrue(File(dir, "scifin.db-wal").length() > 0, "expected an un-checkpointed WAL")
        assertTrue(db.length() <= 4096, "expected the main file to hold no rows yet, was ${db.length()}")

        val out = Snapshot.before(db, current = 3, driver = driver, device = "TEST-HOST")
        assertNotNull(out, "expected a snapshot")
        assertTrue(out.isFile && out.length() > 0)
        assertEquals("snapshots", out.parentFile.name)
        assertEquals("scifin-v2-TEST-HOST.db", out.name)

        // one file, not three: nothing beside it to keep consistent under sync
        assertTrue(!File(out.parentFile, out.name + "-wal").exists())
        assertTrue(!File(out.parentFile, out.name + "-shm").exists())

        // and it holds the data that was only ever in the WAL
        assertEquals(5, rowCount(out))
        a.close()
        dir.deleteRecursively()
    }

    @Test fun `a database already at the current version is left alone`() {
        val dir = createTempDirectory("snap-current").toFile(); val db = seed(dir, 3)
        assertNull(Snapshot.before(db, current = 3, driver = driver, device = "TEST-HOST"))
        assertTrue(!File(dir, "snapshots").exists())
        dir.deleteRecursively()
    }

    @Test fun `a first run has nothing to protect and writes nothing`() {
        val dir = createTempDirectory("snap-first").toFile()
        assertNull(Snapshot.before(File(dir, "scifin.db"), 3, driver, "TEST-HOST"))
        assertTrue(!File(dir, "snapshots").exists())
        dir.deleteRecursively()
    }

    @Test fun `names carry version and device, and no timestamp, so they are written once`() {
        assertEquals("scifin-v2-DESKTOP.db", Snapshot.nameFor(2, "DESKTOP"))
        assertEquals("scifin-v2-OnePlus9Pro.db", Snapshot.nameFor(2, "OnePlus 9 Pro"))
        assertEquals("scifin-vunknown-A.db", Snapshot.nameFor(null, "A"))
    }

    @Test fun `the version is read through the write-ahead log, not from the stale file header`() {
        val dir = createTempDirectory("snap-wal-ver").toFile()
        val db = File(dir, "scifin.db")
        val a = driver.open(db.absolutePath)
        a.execSQL("PRAGMA journal_mode=WAL")
        a.execSQL("PRAGMA wal_autocheckpoint=0")
        a.execSQL("CREATE TABLE t (x TEXT)")
        a.execSQL("PRAGMA user_version = 7")
        // measured 2026-09-24: the header still reads 0 while the pragma sits in the log
        assertEquals(0, Snapshot.storedVersion(db))
        assertEquals(7, Snapshot.storedVersion(db, driver))
        a.close()
        dir.deleteRecursively()
    }

    @Test fun `an existing snapshot for a version is never rewritten`() {
        val dir = createTempDirectory("snap-once").toFile(); val db = seed(dir, 2)
        val first = Snapshot.before(db, 3, driver, "TEST-HOST")!!
        val stamp = first.lastModified()
        val again = Snapshot.before(db, 3, driver, "TEST-HOST")!!
        assertEquals(first, again)
        assertEquals(stamp, again.lastModified(), "a second pass must not rewrite the snapshot")
        assertEquals(1, File(dir, "snapshots").listFiles()!!.size)
        dir.deleteRecursively()
    }
}
