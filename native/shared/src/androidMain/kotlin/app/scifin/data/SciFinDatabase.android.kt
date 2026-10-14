package app.scifin.data

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.scifin.model.SCHEMA_VERSION

private const val DB_NAME = "scifin.db"

/**
 * Android bootstrap for the shared [SciFinDatabase].
 *
 * §12: the store is snapshotted before Room is handed the file, in app-private storage (survives
 * an upgrade, not an uninstall or Clear Data); what leaves the device is §4.7's JSON backup, through SAF.
 */
fun buildSciFinDatabase(context: Context, log: (String) -> Unit = {}): SciFinDatabase {
    val app = context.applicationContext
    // before ANY open through AndroidSQLiteDriver: its default handler deletes a corrupt file (Integrity)
    Integrity.quarantineIfDamaged(app.getDatabasePath(DB_NAME), ::quickCheck, deviceName(), log = log)
    Snapshot.before(app.getDatabasePath(DB_NAME), SCHEMA_VERSION, AndroidSQLiteDriver(), deviceName(), log = log)
    return Room.databaseBuilder<SciFinDatabase>(context = app, name = DB_NAME)
        .finishBuilding(AndroidSQLiteDriver())
}

/** The damage notice for the store [buildSciFinDatabase] opens. */
fun sciFinDamageNotice(context: Context) = Integrity.notice(context.applicationContext.getDatabasePath(DB_NAME))

/**
 * `PRAGMA quick_check` through an open that cannot delete: a handler that does nothing means the
 * framework's retry after a corruption report throws instead of reopening an emptied file.
 */
private fun quickCheck(path: String): List<String> {
    val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING, DatabaseErrorHandler { })
    try {
        db.rawQuery("PRAGMA quick_check", null).use { c -> return buildList { while (c.moveToNext()) add(c.getString(0)) } }
    } finally {
        db.close()
    }
}

/** Names a snapshot so two devices writing into one folder cannot collide. */
private fun deviceName(): String = (Build.MODEL ?: Build.DEVICE ?: "android").take(24)
