@file:OptIn(ExperimentalTime::class)

package app.scifin

import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.model.Account
import app.scifin.model.AccountType
import app.scifin.model.AppState
import app.scifin.model.Cadence
import app.scifin.model.Category
import app.scifin.model.DateMode
import app.scifin.model.Goal
import app.scifin.model.GoalMode
import app.scifin.model.ImportState
import app.scifin.model.ProfileData
import app.scifin.model.RateType
import app.scifin.model.RecurringExpense
import app.scifin.model.SCHEMA_VERSION
import app.scifin.model.Savings
import app.scifin.model.Theme
import app.scifin.model.Transaction
import app.scifin.model.moneyOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** §12 as a store: every entity survives a write and a read on the bundled JVM driver. */
class SchemaRoundTripTest {

    private fun sample(): ProfileData {
        val id = "p-1"
        return ProfileData.fresh(id, "Mine").copy(
            savings = Savings(id, moneyOf(3067.0)),
            goals = listOf(
                Goal("g-1", id, "Emergency fund", "wallet", saved = 0, target = moneyOf(2200.0), mode = GoalMode.AMOUNT, monthly = moneyOf(150.0)),
                Goal("g-2", id, "Someday fund", "piggy-bank"),
            ),
            categories = listOf(Category("c-1", id, "Groceries", "cart", planned = moneyOf(300.0), spent = moneyOf(62.0))),
            recurringExpenses = listOf(
                RecurringExpense(
                    "e-1", id, "Rent", "wallet", moneyOf(950.0), Cadence.MONTHLY,
                    firstDueDate = LocalDate(2026, 1, 1), dueDate = LocalDate(2026, 10, 1),
                    dueDateAnchorDay = 1, dateMode = DateMode.FIXED_DATE, reminderEnabled = true, reminderLeadDays = 3,
                ),
                RecurringExpense(
                    "e-2", id, "Car insurance", "shield", moneyOf(600.0), Cadence.YEARLY,
                    firstDueDate = LocalDate(2026, 3, 1), dueDate = LocalDate(2027, 3, 1),
                    dateMode = DateMode.FIXED_DATE, dueDateAnchorDay = 1, apportionedShare = moneyOf(50.0),
                ),
            ),
            accounts = listOf(
                Account("a-1", id, "Credit Card", "credit-card", AccountType.CREDIT, balance = moneyOf(420.0), apr = 0.219),
                Account(
                    "a-2", id, "Car Loan", "landmark", AccountType.LOAN, balance = moneyOf(8100.0),
                    linkedRecurringExpenseId = "e-1", autoUpdateFromPayments = true, principal = moneyOf(9800.0),
                    interestRate = 0.059, termMonths = 48, rateType = RateType.FIXED, originationDate = LocalDate(2024, 8, 15),
                ),
            ),
            transactions = listOf(
                Transaction("t-1", id, LocalDate(2026, 9, 1), Instant.fromEpochMilliseconds(1_788_000_000_000), "Stipendio", moneyOf(1450.0), "savings", "Savings", isIncome = true),
                Transaction("t-2", id, LocalDate(2026, 9, 3), Instant.fromEpochMilliseconds(1_788_100_000_000), "SPAR", moneyOf(12.0), "cat:c-1", "Groceries"),
            ),
            importState = ImportState(id, importedKeys = listOf("fit:abc", "syn:2026-09-03|-12.00|spar roma"), payeeRules = mapOf("spar roma" to "c-1"), importReminderEnabled = true),
        ).let { it.copy(settings = it.settings.copy(theme = Theme.DUSK, incomeEnabled = true, income = moneyOf(1450.0), incomePayDate = LocalDate(2026, 9, 15))) }
    }

    @Test
    fun everyEntitySurvivesARoundTrip() = runTest {
        val db = buildInMemorySciFinDatabase()
        try {
            val d = sample()
            db.dao().save(d)
            db.dao().upsertAppState(AppState(activeProfileId = d.profileId))
            val back = db.dao().load(d.profileId)!!
            assertEquals(d.profile, back.profile)
            assertEquals(d.savings, back.savings)
            assertEquals(d.settings, back.settings)
            assertEquals(d.importState, back.importState)
            assertEquals(d.goals.sortedBy { it.id }, back.goals.sortedBy { it.id })
            assertEquals(d.categories, back.categories)
            assertEquals(d.recurringExpenses.sortedBy { it.id }, back.recurringExpenses.sortedBy { it.id })
            assertEquals(d.accounts.sortedBy { it.id }, back.accounts.sortedBy { it.id })
            assertEquals(d.transactions, back.transactions)   // ordered by date, loggedAt
            assertEquals(SCHEMA_VERSION, db.dao().appState()!!.schemaVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun saveReplacesListsWhole() = runTest {
        val db = buildInMemorySciFinDatabase()
        try {
            val d = sample()
            db.dao().save(d)
            db.dao().save(d.copy(goals = d.goals.take(1), transactions = emptyList()))
            val back = db.dao().load(d.profileId)!!
            assertEquals(1, back.goals.size)
            assertEquals(0, back.transactions.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun twoProfilesDoNotSeeEachOther() = runTest {
        val db = buildInMemorySciFinDatabase()
        try {
            val a = sample()
            val b = ProfileData.fresh("p-2", "Trust")
            db.dao().save(a); db.dao().save(b)
            val backB = db.dao().load("p-2")!!
            assertNull(backB.savings.amount)              // §3.1's fresh third state
            assertEquals(0, backB.transactions.size)
            assertEquals(0, backB.importState.importedKeys.size)   // §8.4: ImportState is inside the wrap
            db.dao().deleteProfileWhole("p-1")
            assertNull(db.dao().load("p-1"))
            assertEquals(1, db.dao().profiles().size)
        } finally {
            db.close()
        }
    }
}
