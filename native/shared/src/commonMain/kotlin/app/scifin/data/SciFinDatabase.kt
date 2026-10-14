package app.scifin.data

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Transaction as RoomTransaction
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.sqlite.SQLiteDriver
import app.scifin.model.Account
import app.scifin.model.AppState
import app.scifin.model.Category
import app.scifin.model.Goal
import app.scifin.model.ImportState
import app.scifin.model.Profile
import app.scifin.model.ProfileData
import app.scifin.model.RecurringExpense
import app.scifin.model.SCHEMA_VERSION
import app.scifin.model.Savings
import app.scifin.model.Settings
import app.scifin.model.Transaction
import kotlinx.coroutines.Dispatchers

/**
 * §12's store. `version` is [SCHEMA_VERSION] -- the same integer the backup file carries
 * (§4.7) -- and `exportSchema` writes `shared/schemas/<version>.json`, because a migration
 * can only ever be generated from a version whose JSON exists on disk.
 *
 * Migration policy (§12): every shipped version has a path, with the
 * destructive fallback kept underneath and the pre-open snapshot covering it -- see
 * [finishBuilding].
 */
@Database(
    entities = [
        Profile::class, Savings::class, Goal::class, Category::class, RecurringExpense::class,
        Account::class, Transaction::class, Settings::class, ImportState::class, AppState::class,
    ],
    version = SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
@ConstructedBy(SciFinDatabaseConstructor::class)
abstract class SciFinDatabase : RoomDatabase() {
    abstract fun dao(): SciFinDao
}

@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object SciFinDatabaseConstructor : RoomDatabaseConstructor<SciFinDatabase> {
    override fun initialize(): SciFinDatabase
}

/** Shared tail of both platform builders. */
fun RoomDatabase.Builder<SciFinDatabase>.finishBuilding(driver: SQLiteDriver): SciFinDatabase =
    setDriver(driver)
        .setQueryCoroutineContext(Dispatchers.IO)
        // §12: the destructive fallback stays under the migrations: no ordinary route reaches it,
        // the pre-open snapshot holds anything it drops, and without it an unforeseen gap would
        // crash before any window exists (store.start() runs before application {} on the desktop).
        .addMigrations(*SCIFIN_MIGRATIONS)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

/**
 * One DAO, because the unit of work is a whole [ProfileData]: the engine reads a Profile in
 * full and writes it back in full, and §8.4's swap is exactly that. Per-entity queries exist
 * for the few places (Profiles list, active id) that do not need the whole world.
 */
@Dao
interface SciFinDao {
    @Query("SELECT * FROM profiles ORDER BY name") suspend fun profiles(): List<Profile>
    @Query("SELECT * FROM app_state WHERE id = 1") suspend fun appState(): AppState?
    @Upsert suspend fun upsertAppState(s: AppState)

    @Query("SELECT * FROM profiles WHERE id = :id") suspend fun profile(id: String): Profile?
    @Query("SELECT * FROM savings WHERE profileId = :id") suspend fun savings(id: String): Savings?
    @Query("SELECT * FROM settings WHERE profileId = :id") suspend fun settings(id: String): Settings?
    @Query("SELECT * FROM import_state WHERE profileId = :id") suspend fun importState(id: String): ImportState?
    @Query("SELECT * FROM goals WHERE profileId = :id") suspend fun goals(id: String): List<Goal>
    @Query("SELECT * FROM categories WHERE profileId = :id") suspend fun categories(id: String): List<Category>
    @Query("SELECT * FROM recurring_expenses WHERE profileId = :id") suspend fun recurringExpenses(id: String): List<RecurringExpense>
    @Query("SELECT * FROM accounts WHERE profileId = :id") suspend fun accounts(id: String): List<Account>
    @Query("SELECT * FROM transactions WHERE profileId = :id ORDER BY date, loggedAt") suspend fun transactions(id: String): List<Transaction>

    @Upsert suspend fun upsertProfile(p: Profile)
    @Upsert suspend fun upsertSavings(s: Savings)
    @Upsert suspend fun upsertSettings(s: Settings)
    @Upsert suspend fun upsertImportState(s: ImportState)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGoals(g: List<Goal>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(c: List<Category>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRecurringExpenses(e: List<RecurringExpense>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAccounts(a: List<Account>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransactions(t: List<Transaction>)

    @Query("DELETE FROM goals WHERE profileId = :id") suspend fun deleteGoals(id: String)
    @Query("DELETE FROM categories WHERE profileId = :id") suspend fun deleteCategories(id: String)
    @Query("DELETE FROM recurring_expenses WHERE profileId = :id") suspend fun deleteRecurringExpenses(id: String)
    @Query("DELETE FROM accounts WHERE profileId = :id") suspend fun deleteAccounts(id: String)
    @Query("DELETE FROM transactions WHERE profileId = :id") suspend fun deleteTransactions(id: String)
    @Query("DELETE FROM profiles WHERE id = :id") suspend fun deleteProfile(id: String)
    @Query("DELETE FROM savings WHERE profileId = :id") suspend fun deleteSavings(id: String)
    @Query("DELETE FROM settings WHERE profileId = :id") suspend fun deleteSettings(id: String)
    @Query("DELETE FROM import_state WHERE profileId = :id") suspend fun deleteImportState(id: String)

    /** The whole world of one Profile, or null if there is no such Profile. */
    @RoomTransaction
    suspend fun load(id: String): ProfileData? {
        val p = profile(id) ?: return null
        return ProfileData(
            profile = p,
            savings = savings(id) ?: Savings(id, null),
            settings = settings(id) ?: Settings(profileId = id),
            importState = importState(id) ?: ImportState(profileId = id),
            goals = goals(id),
            categories = categories(id),
            recurringExpenses = recurringExpenses(id),
            accounts = accounts(id),
            transactions = transactions(id),
        )
    }

    /** Write first, then prune: the lists are replaced whole, inside one transaction. */
    @RoomTransaction
    suspend fun save(d: ProfileData) {
        val id = d.profileId
        upsertProfile(d.profile)
        upsertSavings(d.savings.copy(profileId = id))
        upsertSettings(d.settings.copy(profileId = id))
        upsertImportState(d.importState.copy(profileId = id))
        deleteGoals(id); insertGoals(d.goals)
        deleteCategories(id); insertCategories(d.categories)
        deleteRecurringExpenses(id); insertRecurringExpenses(d.recurringExpenses)
        deleteAccounts(id); insertAccounts(d.accounts)
        deleteTransactions(id); insertTransactions(d.transactions)
    }

    @RoomTransaction
    suspend fun deleteProfileWhole(id: String) {
        deleteGoals(id); deleteCategories(id); deleteRecurringExpenses(id); deleteAccounts(id)
        deleteTransactions(id); deleteSavings(id); deleteSettings(id); deleteImportState(id)
        deleteProfile(id)
    }
}
