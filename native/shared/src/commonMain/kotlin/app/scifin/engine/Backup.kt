package app.scifin.engine

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import app.scifin.data.SciFinDatabase
import app.scifin.model.Account
import app.scifin.model.AppState
import app.scifin.model.Category
import app.scifin.model.Density
import app.scifin.model.Goal
import app.scifin.model.ImportState
import app.scifin.model.Profile
import app.scifin.model.ProfileData
import app.scifin.model.RecurringExpense
import app.scifin.model.SCHEMA_VERSION
import app.scifin.model.Savings
import app.scifin.model.Settings
import app.scifin.model.Shift
import app.scifin.model.Theme
import app.scifin.model.ThemeMode
import app.scifin.model.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/*
 * §4.7 -- Data & backup. One JSON file, every Profile, replace-not-merge. `schemaVersion`
 * travels in the file (§12); a reader refuses a NEWER file whole rather than half-reading it;
 * a file with no version predates the field and reads as 1. Validation returns a reason and
 * imports nothing for anything structurally wrong; three cosmetics are repaired rather than
 * refused (an unknown theme becomes Swiss Blue, an unknown mode System, an unknown density
 * Comfortable). Absent fields take their defaults; a present field of the wrong shape, a
 * transaction key the ledger cannot parse, or an id used twice is damage, and refuses.
 * An absent `app` field is allowed (older files); a different one is not this app's file.
 */

@Serializable
data class BackupProfile(
    val id: String,
    val name: String,
    val savings: Long? = null,
    val settings: Settings,
    val importState: ImportState = ImportState(profileId = ""),
    val goals: List<Goal> = emptyList(),
    val categories: List<Category> = emptyList(),
    val recurringExpenses: List<RecurringExpense> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    /** §3.10's unpaid hours; absent from files before format 9, which read as none. */
    val shifts: List<Shift> = emptyList(),
)

@Serializable
data class BackupFile(
    val app: String = "sci-fin",
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: String,
    val activeProfileId: String,
    val profiles: List<BackupProfile>,
)

/** What a validated file becomes: ready to replace the store, with a summary for the review card. */
data class ValidatedBackup(val profiles: List<ProfileData>, val activeProfileId: String, val exportedAt: LocalDate?, val version: Int) {
    val transactionCount: Int get() = profiles.sumOf { it.transactions.size }
}

sealed class BackupResult {
    data class Ok(val backup: ValidatedBackup) : BackupResult()
    data class Refused(val reason: String) : BackupResult()
}

object Backup {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun toProfile(d: ProfileData) = BackupProfile(
        d.profile.id, d.profile.name, d.savings.amount, d.settings, d.importState,
        d.goals, d.categories, d.recurringExpenses, d.accounts, d.transactions, d.shifts,
    )

    fun build(profiles: List<ProfileData>, activeProfileId: String, exportedAt: LocalDate): BackupFile =
        BackupFile(exportedAt = exportedAt.toString(), activeProfileId = activeProfileId, profiles = profiles.map(::toProfile))

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** Parses and validates whole; nothing is imported on any refusal. */
    fun validate(text: String): BackupResult {
        val root: JsonObject = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return BackupResult.Refused("That file could not be read as a backup. Nothing was changed.")
        root["app"].str()?.let { if (it != "sci-fin") return BackupResult.Refused("That file is not a Sci-Fin backup.") }
        val vEl = root["schemaVersion"]
        val v = when {
            vEl == null || vEl is JsonNull -> 1
            else -> (vEl as? JsonPrimitive)?.intOrNull ?: return BackupResult.Refused("That file is not a Sci-Fin backup.")
        }
        if (v < 1) return BackupResult.Refused("That file is not a Sci-Fin backup.")
        if (v > SCHEMA_VERSION) return BackupResult.Refused("That backup was made by a newer version of the app (format $v; this app reads up to $SCHEMA_VERSION). Nothing was changed.")
        val profilesEl = root["profiles"]
        if (profilesEl == null || profilesEl !is JsonArray || profilesEl.isEmpty()) return BackupResult.Refused("That backup contains no profiles. Nothing was changed.")

        val profiles = ArrayList<ProfileData>()
        // per table, across every Profile: Room's primary keys are global, so a repeat would overwrite
        val seen = HashMap<String, MutableSet<String>>()
        fun unique(table: String, ids: List<String>) = ids.forEach { require(seen.getOrPut(table) { HashSet() }.add(it)) { "$table id $it is used twice" } }
        for (pe in profilesEl.jsonArray) {
            val po = pe as? JsonObject ?: return BackupResult.Refused("That backup is damaged (a profile is missing its id, name or data). Nothing was changed.")
            val id = po["id"].str(); val name = po["name"].str()
            if (id == null || name == null) return BackupResult.Refused("That backup is damaged (a profile is missing its id, name or data). Nothing was changed.")
            for (list in listOf("transactions", "goals", "categories", "recurringExpenses", "accounts", "shifts")) {
                val el = po[list]
                if (el != null && el !is JsonNull && el !is JsonArray) return BackupResult.Refused("That backup is damaged (“$name” has no $list list). Nothing was changed.")
            }
            po["savings"]?.let { if (it !is JsonNull && (it as? JsonPrimitive)?.longOrNull == null) return BackupResult.Refused("That backup is damaged (“$name” has an unreadable Savings figure). Nothing was changed.") }
            // Repairs, not rejections: an unknown theme, mode or density is a cosmetic.
            val repaired = repairSettings(po)
            val bp = runCatching {
                json.decodeFromJsonElement<BackupProfile>(repaired).also { bp ->
                    bp.transactions.forEach { TransactionKey.parse(it.key) }      // every Savings figure parses these
                    unique("profile", listOf(id)); unique("goal", bp.goals.map { it.id }); unique("category", bp.categories.map { it.id })
                    unique("recurring expense", bp.recurringExpenses.map { it.id }); unique("account", bp.accounts.map { it.id })
                    unique("transaction", bp.transactions.map { it.id }); unique("shift", bp.shifts.map { it.id })
                }
            }.getOrElse {
                println("Sci-Fin: backup profile “$name” refused: ${it.message}")   // the reason stays in the log, not on screen
                return BackupResult.Refused("That backup is damaged (“$name” could not be read). Nothing was changed.")
            }
            profiles.add(ProfileData(
                profile = Profile(id, name.trim().ifEmpty { "Profile" }),
                savings = Savings(id, bp.savings),
                settings = bp.settings.copy(profileId = id),
                importState = bp.importState.copy(profileId = id),
                goals = bp.goals.map { it.copy(profileId = id) },
                categories = bp.categories.map { it.copy(profileId = id) },
                recurringExpenses = bp.recurringExpenses.map { it.copy(profileId = id) },
                accounts = bp.accounts.map { it.copy(profileId = id) },
                transactions = bp.transactions.map { it.copy(profileId = id) },
                shifts = bp.shifts.map { it.copy(profileId = id) },
            ))
        }
        val active = root["activeProfileId"].str()?.takeIf { a -> profiles.any { it.profileId == a } } ?: profiles.first().profileId
        val exportedAt = root["exportedAt"].str()?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return BackupResult.Ok(ValidatedBackup(profiles, active, exportedAt, v))
    }

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun repairSettings(po: JsonObject): JsonObject {
        val s = (po["settings"] as? JsonObject) ?: JsonObject(mapOf("profileId" to JsonPrimitive("")))
        val m = s.toMutableMap()
        if (m["profileId"] == null) m["profileId"] = JsonPrimitive("")
        // a retired theme token maps to its nearest successor (§2.5)
        m["theme"] = JsonPrimitive(Theme.of(m["theme"].str() ?: "").token)
        m["mode"] = JsonPrimitive(ThemeMode.of(m["mode"].str() ?: "").token)
        m["density"] = JsonPrimitive(Density.of(m["density"].str() ?: "").token)
        val out = po.toMutableMap(); out["settings"] = JsonObject(m)
        (po["importState"] as? JsonObject)?.let { io ->
            val im = io.toMutableMap(); if (im["profileId"] == null) im["profileId"] = JsonPrimitive(""); out["importState"] = JsonObject(im)
        }
        return JsonObject(out)
    }

    /**
     * Write everything first, then load: the store is replaced whole, in one transaction, so a
     * failure part-way leaves the device exactly as it was -- §4.7's "replace, not merge" holds
     * even then. [afterProfiles] exists for the test that proves it.
     */
    suspend fun apply(db: SciFinDatabase, b: ValidatedBackup, today: LocalDate, afterProfiles: suspend () -> Unit = {}) {
        db.useWriterConnection { conn ->
            conn.immediateTransaction {
                val dao = db.dao()
                val existing = dao.profiles().map { it.id }
                for (p in b.profiles) dao.save(p)
                afterProfiles()
                for (id in existing) if (b.profiles.none { it.profileId == id }) dao.deleteProfileWhole(id)
                // the Trash holds what was deleted from the data now replaced; none of it can come back into this
                dao.emptyTrash()
                val prev = dao.appState()
                dao.upsertAppState(AppState(activeProfileId = b.activeProfileId, lastBackupExportDate = prev?.lastBackupExportDate, lastBackupImportDate = today))
            }
        }
    }
}
