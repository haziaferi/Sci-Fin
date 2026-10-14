package app.scifin.data

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import java.io.RandomAccessFile

/**
 * §12: the database is snapshotted before Room opens it at a new version.
 *
 * The live store is three files -- `scifin.db` plus `-wal` and `-shm` -- and the main file can hold
 * almost nothing: 4KB against a 350KB write-ahead log. So this does not copy.
 * `VACUUM INTO` folds the log in and emits **one** complete, consistent database, through raw
 * SQLite, which means it works on a schema Room would refuse to validate -- exactly the case a
 * pre-migration snapshot exists for.
 *
 * Snapshots are immutable and device-qualified, in a `snapshots/` subfolder. One file rather than
 * three, written once and never modified, named so two machines cannot collide: those are the
 * properties that make them safe to sit next to file sync. They are excluded from sync by default
 * (`/snapshots` in a Syncthing `.stignore`); the artefact meant to travel between devices is
 * §4.7's JSON backup, not this.
 */
object Snapshot {

    /**
     * The database's `user_version`, read **through SQLite** so an un-checkpointed write-ahead log
     * is taken into account, with the file header as a fallback.
     *
     * Not just the header read: a
     * `PRAGMA user_version = 2` can still be sitting in the WAL, leaving byte 60 of the main file
     * reading 0. The header is therefore **at or behind the truth, never ahead**. Behind is mostly
     * harmless -- it snapshots when it need not, which [nameFor]'s write-once naming bounds -- but
     * it is not harmless in one direction: on a *downgrade*, a header reading the current version
     * while the log holds a newer one would skip the snapshot, and the destructive fallback would
     * then drop exactly what this exists to protect.
     *
     * Raw SQLite opens a schema Room would refuse, so this still works in the case that matters.
     * Null means unknown -- absent, unreadable, or not a database -- and [before] treats unknown as
     * "snapshot anyway" rather than "nothing to do".
     */
    fun storedVersion(db: File, driver: SQLiteDriver? = null): Int? {
        if (!db.isFile || db.length() < 64) return null
        driver?.let { d ->
            runCatching {
                val c = d.open(db.absolutePath)
                try {
                    val s = c.prepare("PRAGMA user_version")
                    try { if (s.step()) s.getInt(0) else null } finally { s.close() }
                } finally { c.close() }
            }.getOrNull()?.let { return it }
        }
        return runCatching {
            RandomAccessFile(db, "r").use { f ->
                f.seek(60)
                val b = ByteArray(4)
                f.readFully(b)
                ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or
                    ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
            }
        }.getOrNull()
    }

    /**
     * `scifin-v2-DESKTOP.db` -- version and device, and deliberately **no timestamp**.
     *
     * One snapshot per version per device, written once and never rewritten. That bounds them (a
     * stale header read would otherwise write one on every launch, forever) and it means a good
     * snapshot is never replaced by a later, possibly worse one. Device-qualified so two machines
     * writing into a shared folder can never collide.
     */
    internal fun nameFor(version: Int?, device: String): String =
        "scifin-v${version ?: "unknown"}-${device.filter { it.isLetterOrDigit() || it == '-' }.take(24)}.db"

    /**
     * Snapshot [db] if its stored version is not [current]. Returns the file written, or null when
     * there was nothing to protect or nothing to do.
     *
     * Never throws: a snapshot that fails must not stop the app from opening, since the failure it
     * guards against is rarer than the failure of refusing to start. It reports through [log].
     */
    fun before(
        db: File,
        current: Int,
        driver: SQLiteDriver,
        device: String,
        log: (String) -> Unit = {},
    ): File? {
        if (!db.isFile) return null                       // a first run has nothing to protect
        val stored = storedVersion(db, driver)
        if (stored == current) return null                // already at this version: no migration coming
        val dir = File(db.parentFile, "snapshots")
        val out = File(dir, nameFor(stored, device))
        return runCatching {
            dir.mkdirs()
            if (out.exists()) return@runCatching out      // immutable: never rewrite one
            val conn = driver.open(db.absolutePath)
            try {
                conn.execSQL("VACUUM INTO '" + out.absolutePath.replace("'", "''") + "'")
            } finally {
                conn.close()
            }
            log("snapshot v$stored -> ${out.name} (${out.length()} bytes) before opening at v$current")
            out
        }.getOrElse { e ->
            log("snapshot before v$current failed: $e")
            null
        }
    }
}
