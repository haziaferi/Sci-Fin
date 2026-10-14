package app.scifin.data

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * §12: every `SCHEMA_VERSION` the app has shipped has a path forward.
 *
 * Each migration is written against the exported schema in `shared/schemas/`; `SchemaGuardTest`
 * fails the build if a bump arrives without one.
 */
val SCIFIN_MIGRATIONS: Array<Migration> = arrayOf(

    /** 1 → 2: §11.1's resumable onboarding needed the step it stopped at. Nullable, no default. */
    object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE settings ADD COLUMN onboardingStep INTEGER")
        }
    },

    /**
     * 2 → 3: §4.6's trend window, which the person chooses.
     *
     * `NOT NULL` needs a default for the rows that already exist, and the entity declares the same
     * default through `@ColumnInfo` so Room's validator sees one schema rather than two.
     */
    object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE settings ADD COLUMN trendMonths INTEGER NOT NULL DEFAULT 6")
        }
    },

    /**
     * 3 → 4, two changes in one bump:
     *
     * - §3.9's catch-up card, stored whole so it survives a restart. An existing row has no
     *   unread card, which is exactly what the defaults say.
     * - `recurring_expenses.lastReminderFiredFor` removed (§3.11): nothing
     *   ever wrote it, and Windows cannot, since a scheduled toast fires with no process of
     *   ours alive. The reminder's own fire time is the guard. A table rebuild rather than
     *   `DROP COLUMN`, because Android's framework SQLite on API 30 predates 3.35.
     */
    object : Migration(3, 4) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE settings ADD COLUMN catchUpMonths INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE settings ADD COLUMN catchUpSwept INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE settings ADD COLUMN catchUpEnded TEXT NOT NULL DEFAULT ''")

            val cols = "`id`, `profileId`, `name`, `icon`, `amount`, `cadence`, `firstDueDate`, `dueDate`, `dueDate2`, `dueDateAnchorDay`, " +
                "`dateMode`, `intervalDays`, `apportionedShare`, `reminderEnabled`, `reminderLeadDays`, `lastSweptMonth`, `endDate`, `finishedAt`, `linkedAccountId`"
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `recurring_expenses_new` (`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT, " +
                    "`amount` INTEGER NOT NULL, `cadence` TEXT NOT NULL, `firstDueDate` TEXT NOT NULL, `dueDate` TEXT NOT NULL, `dueDate2` TEXT, " +
                    "`dueDateAnchorDay` INTEGER, `dateMode` TEXT, `intervalDays` INTEGER, `apportionedShare` INTEGER, `reminderEnabled` INTEGER NOT NULL, " +
                    "`reminderLeadDays` INTEGER NOT NULL, `lastSweptMonth` TEXT, `endDate` TEXT, `finishedAt` TEXT, `linkedAccountId` TEXT, PRIMARY KEY(`id`))"
            )
            connection.execSQL("INSERT INTO `recurring_expenses_new` ($cols) SELECT $cols FROM `recurring_expenses`")
            connection.execSQL("DROP TABLE `recurring_expenses`")
            connection.execSQL("ALTER TABLE `recurring_expenses_new` RENAME TO `recurring_expenses`")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_profileId` ON `recurring_expenses` (`profileId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_linkedAccountId` ON `recurring_expenses` (`linkedAccountId`)")
        }
    },

    /**
     * 4 → 5: `settings.scale` removed (§2.5: the platform's text size is the only one). A table
     * rebuild, as 3 → 4's, for Android's pre-3.35 framework SQLite.
     */
    object : Migration(4, 5) {
        override fun migrate(connection: SQLiteConnection) {
            val cols = "`profileId`, `theme`, `mode`, `density`, `reduceMotion`, `lowDistraction`, `currency`, `trendMonths`, `incomeEnabled`, `income`, `incomeCadence`, `incomePayDate`, `incomePayDate2`, `incomeAutoDeposit`, `incomeEnabledDate`, `lastIncomeDepositDate`, `lastIncomeLoggedDate`, `newMonthNotificationEnabled`, `notificationPermissionGranted`, `savingsVsOwedEnabled`, `onboardingComplete`, `onboardingStep`, `goalPromptDismissed`, `catchUpSummaryPending`, `catchUpDepositCount`, `catchUpDepositTotal`, `catchUpMonths`, `catchUpSwept`, `catchUpEnded`"
            connection.execSQL("CREATE TABLE IF NOT EXISTS `settings_new` (`profileId` TEXT NOT NULL, `theme` TEXT NOT NULL, `mode` TEXT NOT NULL, `density` TEXT NOT NULL, `reduceMotion` INTEGER NOT NULL, `lowDistraction` INTEGER NOT NULL, `currency` TEXT NOT NULL, `trendMonths` INTEGER NOT NULL DEFAULT 6, `incomeEnabled` INTEGER NOT NULL, `income` INTEGER NOT NULL, `incomeCadence` TEXT NOT NULL, `incomePayDate` TEXT, `incomePayDate2` TEXT, `incomeAutoDeposit` INTEGER NOT NULL, `incomeEnabledDate` TEXT, `lastIncomeDepositDate` TEXT, `lastIncomeLoggedDate` TEXT, `newMonthNotificationEnabled` INTEGER NOT NULL, `notificationPermissionGranted` INTEGER, `savingsVsOwedEnabled` INTEGER NOT NULL, `onboardingComplete` INTEGER NOT NULL, `onboardingStep` INTEGER, `goalPromptDismissed` INTEGER NOT NULL, `catchUpSummaryPending` INTEGER NOT NULL, `catchUpDepositCount` INTEGER NOT NULL, `catchUpDepositTotal` INTEGER NOT NULL, `catchUpMonths` INTEGER NOT NULL DEFAULT 0, `catchUpSwept` INTEGER NOT NULL DEFAULT 0, `catchUpEnded` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`profileId`))")
            connection.execSQL("INSERT INTO `settings_new` ($cols) SELECT $cols FROM `settings`")
            connection.execSQL("DROP TABLE `settings`")
            connection.execSQL("ALTER TABLE `settings_new` RENAME TO `settings`")
        }
    },
)
