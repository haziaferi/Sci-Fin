package app.scifin.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.scifin.model.SCHEMA_VERSION
import java.io.File

/**
 * Desktop bootstrap: a file path, the bundled SQLite driver. Tests pass a temp file.
 *
 * §12: the store is snapshotted here, before Room is handed the file,
 * so a migration that goes wrong leaves the previous database intact beside it.
 */
fun buildSciFinDatabase(dbFile: File, log: (String) -> Unit = {}): SciFinDatabase {
    dbFile.parentFile?.mkdirs()
    // BundledSQLiteDriver deletes nothing, but an unreadable file threw before any window existed (Integrity)
    Integrity.quarantineIfDamaged(dbFile, ::quickCheck, deviceName(), log = log)
    Snapshot.before(dbFile, SCHEMA_VERSION, BundledSQLiteDriver(), deviceName(), log = log)
    return Room.databaseBuilder<SciFinDatabase>(name = dbFile.absolutePath)
        .finishBuilding(BundledSQLiteDriver())
}

/** `PRAGMA quick_check`'s rows; throws when SQLite cannot open the file at all. */
internal fun quickCheck(path: String): List<String> {
    val c = BundledSQLiteDriver().open(path)
    try {
        val s = c.prepare("PRAGMA quick_check")
        try { return buildList { while (s.step()) add(s.getText(0)) } } finally { s.close() }
    } finally {
        c.close()
    }
}

/** Names a snapshot so two machines writing into one folder cannot collide. */
private fun deviceName(): String =
    (System.getenv("COMPUTERNAME") ?: System.getProperty("user.name") ?: "desktop").take(24)

/** In-memory database for tests: nothing touches disk. */
fun buildInMemorySciFinDatabase(): SciFinDatabase =
    Room.inMemoryDatabaseBuilder<SciFinDatabase>().finishBuilding(BundledSQLiteDriver())
