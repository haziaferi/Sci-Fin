package app.scifin

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.scifin.data.SCIFIN_MIGRATIONS
import app.scifin.data.buildSciFinDatabase
import app.scifin.model.SCHEMA_VERSION
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * §12's migration path, tested against the exported schemas rather than against memory.
 *
 * The guard is the cheap half: it fails when a `SCHEMA_VERSION` bump arrives with no migration,
 * which is the failure no amount of care reliably prevents. The second test is the expensive half
 * and the one that matters: it builds a real v2 database from `2.json`, puts data in it, hands it
 * to Room, and requires the data to still be there afterwards.
 */
class MigrationTest {
    private val driver = BundledSQLiteDriver()

    private fun schemaDir(): File =
        listOf("schemas", "shared/schemas", "../shared/schemas")
            .map { File(it, "app.scifin.data.SciFinDatabase") }
            .firstOrNull { it.isDirectory }
            ?: error("exported schemas not found from ${File(".").absolutePath}")

    private fun schema(v: Int) =
        Json.parseToJsonElement(File(schemaDir(), "$v.json").readText()).jsonObject["database"]!!.jsonObject

    // ---------------------------------------------------------------- the guard

    @Test fun `every shipped version has a migration path to the current one`() {
        val exported = schemaDir().listFiles { f: File -> f.name.endsWith(".json") }
            .orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.sorted()
        assertTrue(exported.isNotEmpty(), "no exported schemas")
        assertTrue(
            SCHEMA_VERSION in exported,
            "SCHEMA_VERSION is $SCHEMA_VERSION but no $SCHEMA_VERSION.json is exported -- build the module before trusting this",
        )

        val edges = SCIFIN_MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        for (v in exported.min() until SCHEMA_VERSION) {
            assertTrue(
                (v to v + 1) in edges,
                "no migration from $v to ${v + 1}. §12 (2026-09-24): every bump ships one, because the " +
                    "condition the destructive policy was written against -- nothing real stored -- expired.",
            )
        }
    }

    @Test fun `no migration claims a version that was never shipped`() {
        val exported = schemaDir().listFiles { f: File -> f.name.endsWith(".json") }
            .orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.toSet()
        for (m in SCIFIN_MIGRATIONS) {
            assertTrue(m.startVersion in exported, "migration from ${m.startVersion}, which has no schema")
            assertTrue(m.endVersion in exported, "migration to ${m.endVersion}, which has no schema")
        }
    }

    // ------------------------------------------------- the one that proves data survives

    /** Builds a real database at [v] from its exported schema, exactly as Room would have left it. */
    private fun buildAt(v: Int, dir: File): File {
        val db = File(dir, "scifin.db")
        val s = schema(v)
        val c = driver.open(db.absolutePath)
        for (e in s["entities"]!!.jsonArray) {
            val o = e.jsonObject
            c.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", o["tableName"]!!.jsonPrimitive.content))
            o["indices"]?.jsonArray?.forEach { i ->
                c.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", o["tableName"]!!.jsonPrimitive.content))
            }
        }
        // Room validates against this on open; a hand-built database without it is not a v2 database.
        c.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        c.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '${s["identityHash"]!!.jsonPrimitive.content}')")
        c.execSQL("PRAGMA user_version = $v")
        c.close()
        return db
    }

    /** Every NOT NULL column filled with a placeholder of the right affinity -- enough to be a row. */
    private fun insertRow(db: File, table: String, v: Int, overrides: Map<String, String>) {
        val fields = schema(v)["entities"]!!.jsonArray.map { it.jsonObject }
            .first { it["tableName"]!!.jsonPrimitive.content == table }["fields"]!!.jsonArray
            .map { it.jsonObject }
            .filter { it["notNull"]?.jsonPrimitive?.content == "true" }
        val cols = fields.map { it["columnName"]!!.jsonPrimitive.content }
        val vals = fields.map { f ->
            val name = f["columnName"]!!.jsonPrimitive.content
            overrides[name] ?: if (f["affinity"]!!.jsonPrimitive.content == "TEXT") "'x'" else "0"
        }
        val c = driver.open(db.absolutePath)
        c.execSQL("INSERT INTO $table (${cols.joinToString(",")}) VALUES (${vals.joinToString(",")})")
        c.close()
    }

    @Test fun `a real v2 database migrates to the current version with its data intact`() {
        val dir = createTempDirectory("migrate").toFile()
        val file = buildAt(2, dir)
        insertRow(file, "profiles", 2, mapOf("id" to "'p1'", "name" to "'Fixture'"))
        insertRow(file, "settings", 2, mapOf("profileId" to "'p1'", "currency" to "'EUR'"))

        // handing it to Room is what runs the migration and validates the result
        val room = buildSciFinDatabase(file)
        val profiles = runBlocking { room.dao().profiles() }
        assertEquals(1, profiles.size, "the profile did not survive the migration")
        assertEquals("Fixture", profiles.first().name)
        room.close()

        // and the new column arrived with 4.6's default rather than as null or zero
        val c = driver.open(file.absolutePath)
        val st = c.prepare("SELECT currency, trendMonths FROM settings WHERE profileId = 'p1'")
        assertTrue(st.step(), "the settings row did not survive the migration")
        assertEquals("EUR", st.getText(0))
        assertEquals(6, st.getInt(1), "§4.6's trend window should default to six months")
        st.close(); c.close()

        // and 12's snapshot was taken on the way in, because the stored version was behind
        val snaps = File(dir, "snapshots").listFiles().orEmpty()
        assertEquals(1, snaps.size, "expected exactly one snapshot, got ${snaps.map { it.name }}")
        assertTrue(snaps.first().name.startsWith("scifin-v2-"), "got ${snaps.first().name}")
        assertNotNull(snaps.first())

        dir.deleteRecursively()
    }

    /** 3 → 4 rebuilds `recurring_expenses` to drop a column: every row and every other value must come through. */
    @Test fun `a v3 recurring expense survives the table rebuild, without lastReminderFiredFor`() {
        val dir = createTempDirectory("migrate3").toFile()
        val file = buildAt(3, dir)
        insertRow(file, "profiles", 3, mapOf("id" to "'p1'", "name" to "'Fixture'"))
        insertRow(file, "settings", 3, mapOf("profileId" to "'p1'", "currency" to "'EUR'"))
        insertRow(file, "recurring_expenses", 3, mapOf(
            "id" to "'rent'", "profileId" to "'p1'", "name" to "'Rent'", "amount" to "95000", "cadence" to "'monthly'",
            "firstDueDate" to "'2026-01-01'", "dueDate" to "'2026-10-01'", "reminderEnabled" to "1", "reminderLeadDays" to "3",
        ))

        val room = buildSciFinDatabase(file)
        assertEquals(1, runBlocking { room.dao().profiles() }.size)            // opening is what migrates and validates
        room.close()

        // read raw: the fixture's placeholder settings are not valid enums, and this is about the table
        val c = driver.open(file.absolutePath)
        c.prepare("SELECT name, amount, reminderLeadDays, firstDueDate FROM recurring_expenses WHERE id = 'rent'").use { st ->
            assertTrue(st.step(), "the recurring row did not survive the rebuild")
            assertEquals("Rent", st.getText(0)); assertEquals(95000L, st.getLong(1)); assertEquals(3, st.getInt(2)); assertEquals("2026-01-01", st.getText(3))
        }
        val cols = c.prepare("PRAGMA table_info(recurring_expenses)").use { st -> buildList { while (st.step()) add(st.getText(1)) } }
        assertTrue("lastReminderFiredFor" !in cols, "the column should be gone, got $cols")
        val idx = c.prepare("PRAGMA index_list(recurring_expenses)").use { st -> buildList { while (st.step()) add(st.getText(1)) } }
        assertTrue(idx.containsAll(listOf("index_recurring_expenses_profileId", "index_recurring_expenses_linkedAccountId")), "indices lost: $idx")
        c.close()
        dir.deleteRecursively()
    }
}
