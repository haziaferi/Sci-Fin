package app.scifin.data

import app.scifin.app.DamageNotice
import app.scifin.app.LocalCopies
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * §12: a store SQLite cannot
 * read is moved aside, whole, before anything else opens it.
 *
 * Why it has to come first: Room KMP's
 * `AndroidSQLiteDriver` opens through `SQLiteDatabase.openOrCreateDatabase(path, null)`, and a
 * null error handler is Android's `DefaultDatabaseErrorHandler`, which **deletes** a database that
 * reports corruption on open. [Snapshot.storedVersion] opens with that driver, so the snapshot
 * meant to protect the file would have been the thing that erased it. `BundledSQLiteDriver` on the
 * desktop deletes nothing, but it throws, and the first open runs before `application {}` -- the
 * process ended with no window. [probe] is each platform's open that cannot delete.
 *
 * What is moved: the main file and its `-wal`/`-shm` together, renamed as one trio so SQLite (or
 * a recovery tool) can still open them side by side. They land in `snapshots/` beside
 * [Snapshot]'s files. A marker beside the store names the copy until the person answers the
 * screen it raises, so a restart before answering still shows it.
 *
 * Not covered, and stated so it is not assumed: corruption that surfaces *while* the app is
 * running still meets Android's default handler inside Room's own connection. The driver takes no
 * handler, and the open-helper route would fight Room for `user_version`.
 */
object Integrity {

    private const val MARKER = "scifin.damaged"

    /**
     * Probe [db]; when SQLite cannot open it or `quick_check` reports anything but `ok`, move it
     * aside and leave the marker. Returns the kept copy, or null when there was nothing wrong
     * (or nothing there). Never throws: a failed check must not stop the app opening.
     */
    fun quarantineIfDamaged(
        db: File,
        probe: (String) -> List<String>,
        device: String,
        now: LocalDateTime = LocalDateTime.now(),
        log: (String) -> Unit = {},
    ): File? {
        if (!db.isFile) return null
        val problem = try {
            val rows = probe(db.absolutePath)
            if (rows == listOf("ok")) null else rows.take(3).joinToString("; ").ifEmpty { "quick_check returned nothing" }
        } catch (e: Exception) {
            // a lock is another process holding a healthy store (the headless --sync-notifications run beside the window)
            if (isLock(e)) { log("store busy, not checked: $e"); return null }
            e.toString()
        } ?: return null
        return runCatching {
            val dir = File(db.parentFile, "snapshots").apply { mkdirs() }
            val stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val kept = File(dir, "scifin-damaged-$stamp-${device.filter { it.isLetterOrDigit() || it == '-' }.take(24)}.db")
            for (suffix in listOf("", "-wal", "-shm")) {
                val from = File(db.path + suffix)
                if (!from.exists()) continue
                val to = File(kept.path + suffix)
                if (!from.renameTo(to)) { from.copyTo(to, overwrite = false); from.delete() }
                // owed as soon as the main file has gone: a later -wal/-shm failure must not leave it unannounced
                if (suffix == "") File(db.parentFile, MARKER).writeText(kept.name)
            }
            log("store unreadable ($problem): moved to ${kept.name}")
            kept
        }.getOrElse { e ->
            log("store unreadable ($problem) and could not be moved aside: $e")
            null
        }
    }

    /** SQLITE_BUSY / SQLITE_LOCKED, as the bundled driver and Android's framework report them. */
    private fun isLock(e: Exception): Boolean =
        e.javaClass.simpleName.contains("Locked") || e.message.orEmpty().let { m -> "database is locked" in m || "database table is locked" in m || "SQLITE_BUSY" in m || "SQLITE_LOCKED" in m }

    /** §4.7's erase: the `snapshots/` folder beside [db] (Snapshot's and this object's copies) and the marker. */
    fun copies(db: File): LocalCopies = object : LocalCopies {
        override fun eraseAll(): Int {
            var n = 0
            File(db.parentFile, "snapshots").listFiles().orEmpty().forEach { if (runCatching { it.delete() }.getOrDefault(false)) n++ }
            if (File(db.parentFile, MARKER).delete()) n++
            return n
        }
    }

    /** The marker as §12's screen reads it: the kept copy's name until the person answers. */
    fun notice(db: File): DamageNotice = object : DamageNotice {
        private val marker = File(db.parentFile, MARKER)
        override val keptAs: String? get() = runCatching { marker.readText().trim().ifEmpty { null } }.getOrNull()
        override fun clear() { marker.delete() }
    }
}
