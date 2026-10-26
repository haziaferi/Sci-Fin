@file:OptIn(ExperimentalTime::class)
@file:UseSerializers(InstantMillisSerializer::class)

package app.scifin.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * §12's entity table, one class per row, annotated for Room so the engine's working types ARE
 * the stored types -- there is no second "DTO" shape to drift from the spec.
 *
 * §8.4's wrap point is a `profileId` column on every table rather than a database per
 * Profile: one file, one schema version, one backup (§4.7), and §8.4's "genuinely separate
 * pool" holds because every read and every write is scoped by that column. Entity ids are
 * UUID strings, unique across Profiles by construction, so `key: goal:<id>` can never collide
 * between two pools (§12).
 *
 * §12's "types of time": `date`-kind fields are kotlinx-datetime LocalDate (a calendar day, no
 * zone, stored as ISO text); `loggedAt` is a kotlin.time Instant (stored as epoch millis).
 */

@Serializable
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey val id: String,
    val name: String,
)

/** Exactly one per Profile (§3.1). `amount == null` is the fresh-install third state. */
@Serializable
@Entity(tableName = "savings")
data class Savings(
    @PrimaryKey val profileId: String,
    val amount: Money?,
)

@Serializable
@Entity(tableName = "goals", indices = [Index("profileId")])
data class Goal(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val icon: String? = null,
    val saved: Money = 0,
    /** null = §6.3's no-target mode: saved accumulates, nothing is ever "achieved". */
    val target: Money? = null,
    /** null = §6.3's no-date mode. */
    val targetDate: LocalDate? = null,
    val mode: GoalMode? = null,
    /** §6.1 "by an amount": the contribution the person chose. `months` is derived (§12). */
    val monthly: Money? = null,
)

@Serializable
@Entity(tableName = "categories", indices = [Index("profileId")])
data class Category(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val icon: String? = null,
    val planned: Money = 0,
    val spent: Money = 0,
    /** "YYYY-MM" of the last §3.9 reset, null before the first. */
    val lastResetMonth: String? = null,
)

@Serializable
@Entity(tableName = "recurring_expenses", indices = [Index("profileId"), Index("linkedAccountId")])
data class RecurringExpense(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val icon: String? = null,
    val amount: Money,
    val cadence: Cadence,
    /** The floor for walking the chain backwards (§3.11): no occurrence predates it. */
    val firstDueDate: LocalDate,
    /** The live anchor: advances when the real date passes, never on sweep (§3.11). */
    val dueDate: LocalDate,
    /** Semimonthly only. */
    val dueDate2: LocalDate? = null,
    /** Fixed-date mode only: the immutable day-of-month a short month snaps back from. */
    val dueDateAnchorDay: Int? = null,
    /** Month-anchored cadences only (§3.11). */
    val dateMode: DateMode? = null,
    /** Interval mode only. */
    val intervalDays: Int? = null,
    /** Bimonthly/yearly only: the per-cycle share held, never recomputed from a shrinking divisor. */
    val apportionedShare: Money? = null,
    val reminderEnabled: Boolean = false,
    val reminderLeadDays: Int = 3,
    val lastSweptMonth: String? = null,
    val endDate: LocalDate? = null,
    /** Terminal marker: set once the final occurrence has passed; `dueDate` stops advancing. */
    val finishedAt: LocalDate? = null,
    val linkedAccountId: String? = null,
)

@Serializable
@Entity(tableName = "accounts", indices = [Index("profileId"), Index("linkedRecurringExpenseId")])
data class Account(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val icon: String? = null,
    val type: AccountType,
    val balance: Money = 0,
    /** Credit only. Stored as a fraction: 0.219 is 21.9 %. */
    val apr: Double? = null,
    val linkedRecurringExpenseId: String? = null,
    /** Loan only. */
    val autoUpdateFromPayments: Boolean = false,
    val principal: Money? = null,
    val interestRate: Double? = null,
    val termMonths: Int? = null,
    val rateType: RateType? = null,
    val originationDate: LocalDate? = null,
)

/**
 * §3.6 / §12. `amount` is the signed delta applied to the target named by [key]; the Savings
 * movement per key is §3.6's table, implemented in the engine's Ledger. `key` keeps the spec's
 * own spelling (`savings`, `goal:<id>`, `cat:<id>`, `rec:<id>`, `acct:<id>`,
 * `spend:<goalId>`) so the ledger a
 * person exports reads like the document that specified it.
 */
@Serializable
@Entity(tableName = "transactions", indices = [Index("profileId"), Index("date"), Index("loggedAt")])
data class Transaction(
    @PrimaryKey val id: String,
    val profileId: String,
    /** The editable calendar day; governs the ledger and every report (§3.6). */
    val date: LocalDate,
    /** Real creation time; governs §7.3's pair rules and same-date ordering. */
    val loggedAt: Instant,
    val payee: String? = null,
    val amount: Money,
    val key: String,
    /** Snapshot of the target's name at logging time. */
    val targetLabel: String,
    val isIncome: Boolean = false,
    /** §3.10's optional metadata. */
    val hoursWorked: Double? = null,
    val rate: Money? = null,
)

/** §12's Settings row, one per Profile (§8.4 wraps appearance too). Defaults are the spec's. */
@Serializable
@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val profileId: String,
    val theme: Theme = Theme.SWISS_BLUE,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val density: Density = Density.COMFORTABLE,
    val reduceMotion: Boolean = false,
    val lowDistraction: Boolean = false,
    val currency: String = "EUR",
    /** §4.6's trend window: 3, 6, 12 or 24 months. The default is declared to the column as well,
     *  because migration 2->3 adds it NOT NULL to existing rows and Room compares the schemas. */
    @ColumnInfo(defaultValue = "6") val trendMonths: Int = 6,
    val incomeEnabled: Boolean = false,
    /** Per-paycheck amount (§3.3). */
    val income: Money = 0,
    val incomeCadence: IncomeCadence = IncomeCadence.BIWEEKLY,
    val incomePayDate: LocalDate? = null,
    val incomePayDate2: LocalDate? = null,
    val incomeAutoDeposit: Boolean = false,
    val incomeEnabledDate: LocalDate? = null,
    val lastIncomeDepositDate: LocalDate? = null,
    val lastIncomeLoggedDate: LocalDate? = null,
    val newMonthNotificationEnabled: Boolean = true,
    /** null until the platform has answered (§11.5). */
    val notificationPermissionGranted: Boolean? = null,
    val savingsVsOwedEnabled: Boolean = false,
    val onboardingComplete: Boolean = false,
    /**
     * The step of §11.1 this Profile reached, while [onboardingComplete] is still false; null
     * once the flow is done or was never entered. Screen 1's Continue creates the Profile, so
     * without this an app killed between screen 1 and step 5 left a Profile that could never
     * finish onboarding and for which §11.3's prompt could never become eligible.
     */
    val onboardingStep: Int? = null,
    val goalPromptDismissed: Boolean = false,
    val catchUpSummaryPending: Boolean = false,
    /**
     * §3.9's card, stored so it "persists until dismissed" across a restart and not only a
     * background: the deposits, the months replayed, what they swept, and the
     * §3.11 notices, accumulated while the card is unread and cleared by its dismissal.
     */
    val catchUpDepositCount: Int = 0,
    val catchUpDepositTotal: Money = 0,
    @ColumnInfo(defaultValue = "0") val catchUpMonths: Int = 0,
    @ColumnInfo(defaultValue = "0") val catchUpSwept: Money = 0,
    /** The notices, one per line as `name<TAB>note`. */
    @ColumnInfo(defaultValue = "''") val catchUpEnded: String = "",
    /** §3.10 (owner, 2026-10-26): salaried keeps §3.3's amount and cadence; hourly logs shifts and confirms each payday. */
    @ColumnInfo(defaultValue = "'salaried'") val incomeType: IncomeType = IncomeType.SALARIED,
    /** Hourly only: the rate a new shift starts from, per hour. */
    @ColumnInfo(defaultValue = "0") val hourlyRate: Money = 0,
    /** The last payday whose prompt was answered (hourly) or whose entry was offered (salaried, manual). */
    val lastPaydayHandled: LocalDate? = null,
    /** The payday notification, sent only while income is not recorded by itself (owner, 2026-10-26). */
    @ColumnInfo(defaultValue = "1") val paydayNotificationEnabled: Boolean = true,
    /** §2.7: the language this Profile is shown in (`en`, `it`); null follows the device. */
    val language: String? = null,
)

/**
 * §3.10's logged hours (owner, 2026-10-26): a shift moves nothing; the payday that covers its date turns it into
 * income once the person confirms what arrived. Carried by a backup, like every other thing the person entered.
 */
@Serializable
@Entity(tableName = "shifts", indices = [Index("profileId")])
data class Shift(
    @PrimaryKey val id: String,
    val profileId: String,
    /** The day the hours were worked; a payday covers every shift dated on or before it. */
    val date: LocalDate,
    val hours: Double,
    /** Per hour, editable per shift so a differently paid one needs no concept of its own. */
    val rate: Money,
)

/** §4.3's ImportState, one per Profile (§12). */
@Serializable
@Entity(tableName = "import_state")
data class ImportState(
    @PrimaryKey val profileId: String,
    /** The dedup ledger: `fit:<FITID>` and `syn:date|amount|description`, both recorded. */
    val importedKeys: List<String> = emptyList(),
    /** Normalised payee -> category id, learned by assigning. */
    val payeeRules: Map<String, String> = emptyMap(),
    val importReminderEnabled: Boolean = false,
    val importReminderDay: Int = 1,
    val lastImportDate: LocalDate? = null,
)

/** App-level (above the wrap): which Profile is open, the storage version, backup dates. */
@Serializable
@Entity(tableName = "app_state")
data class AppState(
    @PrimaryKey val id: Int = 1,
    val schemaVersion: Int = SCHEMA_VERSION,
    val activeProfileId: String,
    val lastBackupExportDate: LocalDate? = null,
    val lastBackupImportDate: LocalDate? = null,
)

/**
 * §4.7's app lock, one row while a PIN is set and none otherwise. Device-only: not @Serializable, so it can
 * never reach a backup file, and outside `app_state`, which a restore rewrites (Backup.apply). The PIN is
 * kept only as a salted PBKDF2-HMAC-SHA256 hash ([app.scifin.engine.Pin]).
 */
@Entity(tableName = "app_lock")
data class AppLock(
    @PrimaryKey val id: Int = 1,
    /** Base64 of the 16 random bytes and of the derived 32-byte hash. */
    val salt: String,
    val hash: String,
    /** Stored so the cost can be raised later without locking anyone out. */
    val iterations: Int,
    /** The digit count, so the gate checks once, on the last digit, rather than on every partial entry. */
    val pinLength: Int,
    /** The person's own hint, optional, never the PIN itself (engine/Lock.kt). */
    val hint: String? = null,
    /** [app.scifin.engine.LockAfter]'s token. */
    val lockAfter: String = "one_minute",
    /** Wrong PINs since the last right one; kept so restarting the app cannot skip a wait. */
    val failedAttempts: Int = 0,
)

/**
 * §4.7's automatic copy, one row once it has been set up. Device-only like [AppLock]: a folder on this device and
 * a password only this device's key store can read are meaningless anywhere else.
 */
@Entity(tableName = "copy_schedule")
data class CopySchedule(
    @PrimaryKey val id: Int = 1,
    /** [app.scifin.app.CopyEvery]'s token. */
    val every: String = "off",
    /** The platform's handle on the chosen folder (an Android tree URI, a Windows path) and the name to show for it. */
    val folder: String? = null,
    val folderLabel: String? = null,
    /** The copies' password, sealed by the platform's key store (Android Keystore, Windows DPAPI); null for plain copies. */
    val sealedPassword: String? = null,
    /** Required with a password (owner, 2026-10-20); travels in each file unencrypted. */
    val hint: String? = null,
    /** The day of the last copy written, ISO; null before the first. */
    val lastCopy: String? = null,
    /** Why the last attempt failed; null after a success or before any attempt. */
    val lastError: String? = null,
    /** The failure notice was shown for the current run of failures; cleared by the next success. */
    val failureNotified: Boolean = false,
)

/**
 * One deleted thing in the Trash (owner, 2026-10-19/20), kept 30 days. [payload] is [app.scifin.engine.TrashPayload]
 * as JSON: what the delete removed and the money it moved. Device-only, like the lock: a backup carries what
 * exists, not what was deleted, and restoring one empties the Trash.
 */
@Entity(tableName = "trash", indices = [Index("profileId")])
data class TrashItem(
    @PrimaryKey val id: String,
    /** The Profile the item belonged to; for a deleted Profile, its own id. */
    val profileId: String,
    /** goal, category, expense, account, pair, profile. */
    val kind: String,
    val name: String,
    /** ISO day of the delete; it goes for good 30 days on. */
    val deletedOn: String,
    val payload: String,
    /** The 20-day notice has been given for it. */
    val warned: Boolean = false,
)

/** §12's storage version, carried by the Room database and the backup file. Every bump has a Migration (data/Migrations.kt); the history is in the spec. */
const val SCHEMA_VERSION = 10
