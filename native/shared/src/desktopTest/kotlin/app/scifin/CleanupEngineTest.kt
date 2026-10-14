@file:OptIn(ExperimentalTime::class)

package app.scifin

import app.scifin.engine.Backup
import app.scifin.engine.BackupResult
import app.scifin.engine.Entry
import app.scifin.engine.Ledger
import app.scifin.engine.Writes
import app.scifin.model.Cadence
import app.scifin.model.Density
import app.scifin.model.Goal
import app.scifin.model.IncomeCadence
import app.scifin.model.ProfileData
import app.scifin.model.RecurringExpense
import app.scifin.model.Transaction
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import app.scifin.model.Account
import app.scifin.model.AccountType

/** Lane 2 of the 2026-10-10 cleanup: one test per engine fix, each failing on the code before it. */
class CleanupEngineTest {
    private val clock = FakeClock(LocalDate(2026, 10, 10))
    private val ids = Ids()
    private val t0 = Instant.parse("2026-10-10T10:00:00Z")

    private fun tx(id: String, key: String, amount: Double, at: Instant, date: LocalDate, payee: String? = null, isIncome: Boolean = false) =
        Transaction(id, "p-1", date, at, payee, moneyOf(amount), key, "x", isIncome)

    // ---- Ledger.deletePair keeps §3.3's resets ----

    @Test fun `deleting an unrelated pair leaves both income markers where the toggle and switch put them`() {
        val enabled = LocalDate(2026, 10, 1)
        val d0 = profileWith(categories = listOf(category("c"))).let {
            it.copy(
                transactions = listOf(
                    tx("dep", "savings", 1450.0, t0 - 4000.hours, LocalDate(2026, 3, 27), Ledger.AUTO_DEPOSIT_PAYEE, isIncome = true),
                    tx("a", "cat:c", 5.0, t0, LocalDate(2026, 10, 10)),
                    tx("b", "cat:c", -5.0, t0 + 1.hours, LocalDate(2026, 10, 10)),
                ),
                settings = it.settings.copy(
                    incomeEnabled = true, incomeEnabledDate = enabled, incomeAutoDeposit = true, income = moneyOf(1450.0),
                    incomeCadence = IncomeCadence.MONTHLY, incomePayDate = LocalDate(2026, 3, 27),
                    lastIncomeLoggedDate = null, lastIncomeDepositDate = LocalDate(2026, 10, 9),
                ),
            )
        }
        val d1 = Ledger.deletePair(d0, "a", "b")
        assertEquals(LocalDate(2026, 10, 9), d1.settings.lastIncomeDepositDate)      // was the March deposit: catch-up replayed April-October
        assertNull(d1.settings.lastIncomeLoggedDate)                                 // was March: the no-income note fired at once
    }

    @Test fun `deleting an income pair recomputes the logged marker from rows on or after the enable date`() {
        val d0 = profileWith().let {
            it.copy(
                transactions = listOf(
                    tx("old", "savings", 50.0, t0 - 4000.hours, LocalDate(2026, 3, 2), isIncome = true),
                    tx("m", "savings", 50.0, t0, LocalDate(2026, 10, 10), isIncome = true),
                    tx("c", "savings", -50.0, t0 + 1.hours, LocalDate(2026, 10, 10)),
                ),
                settings = it.settings.copy(incomeEnabled = true, incomeEnabledDate = LocalDate(2026, 10, 1), lastIncomeLoggedDate = LocalDate(2026, 10, 10)),
            )
        }
        assertEquals(LocalDate(2026, 10, 1), Ledger.deletePair(d0, "m", "c").settings.lastIncomeLoggedDate)
    }

    // ---- Ledger.logAndMove: a backdated income never moves the marker back ----

    @Test fun `a backdated income entry keeps the later logged marker`() {
        val d0 = profileWith().let { it.copy(settings = it.settings.copy(incomeEnabled = true, lastIncomeLoggedDate = LocalDate(2026, 10, 5))) }
        val d1 = Writes.savingsQuickAdd(d0, moneyOf(100.0), "Bonus", true, clock, ids.next(), date = LocalDate(2026, 9, 20))
        assertEquals(LocalDate(2026, 10, 5), d1.settings.lastIncomeLoggedDate)
    }

    // ---- Entry: a note naming a recurring expense ----

    @Test fun `a note that names a recurring expense gets the caption`() {
        val e = RecurringExpense("n", "p-1", "Netflix", amount = moneyOf(12.0), cadence = Cadence.MONTHLY,
            firstDueDate = LocalDate(2026, 9, 3), dueDate = LocalDate(2026, 11, 3))
        val d = profileWith(categories = listOf(category("c"))).copy(recurringExpenses = listOf(e))
        val caps = Entry.captions(d, "cat:c", "spend", moneyOf(12.0), "Netflix monthly", false) { "" }
        assertTrue(caps.any { it.startsWith("Netflix is a recurring expense") }, "$caps")
    }

    // ---- Entry: an unknown account direction is refused, not read as Increase ----

    @Test fun `an unknown account direction moves nothing and writes nothing`() {
        val d = profileWith().copy(accounts = listOf(Account("cc", "p-1", "Card", null, AccountType.CREDIT, balance = moneyOf(100.0))))
        assertNull(Entry.moves(d, "acct:cc", "in", moneyOf(10.0), false))
        assertFails { Entry.write(d, "acct:cc", "in", moneyOf(10.0), null, clock.today(), false, clock, ids::next) }
    }

    // ---- Writes.goalSavedEdit: a Goal never holds less than nothing ----

    @Test fun `a negative saved figure is refused`() {
        val d = profileWith(goals = listOf(Goal("g", "p-1", "Trip", saved = moneyOf(10.0))))
        assertFails { Writes.goalSavedEdit(d, "g", moneyOf(-5.0), null, clock, ids.next()) }
    }

    // ---- Backup.validate ----

    private fun text(vararg ps: ProfileData) = Backup.encode(Backup.build(ps.toList(), ps.first().profileId, clock.today()))

    @Test fun `a transaction key the ledger cannot parse refuses the file`() {
        val d = profileWith().copy(transactions = listOf(tx("w", "wallet:1", 5.0, t0, clock.today())))
        assertIs<BackupResult.Refused>(Backup.validate(text(d)))
    }

    @Test fun `an id used twice refuses the file`() {
        val a = profileWith(goals = listOf(Goal("g", "p-1", "Trip")))
        val b = ProfileData.fresh("p-2", "Other").copy(goals = listOf(Goal("g", "p-2", "Car")))
        assertIs<BackupResult.Refused>(Backup.validate(text(a, b)))
        assertIs<BackupResult.Refused>(Backup.validate(text(profileWith(), profileWith())))
        assertIs<BackupResult.Ok>(Backup.validate(text(a, ProfileData.fresh("p-2", "Other"))))
    }

    @Test fun `a fractional Savings figure is refused as a Savings figure`() {
        val r = Backup.validate("""{"profiles":[{"id":"p","name":"Mine","settings":{"profileId":"p"},"savings":12.5}]}""")
        assertTrue(assertIs<BackupResult.Refused>(r).reason.contains("Savings figure"))
    }

    @Test fun `an unknown density is repaired, not refused`() {
        val r = Backup.validate("""{"profiles":[{"id":"p","name":"Mine","settings":{"profileId":"p","density":"roomy"}}]}""")
        assertEquals(Density.COMFORTABLE, assertIs<BackupResult.Ok>(r).backup.profiles.single().settings.density)
    }

    @Test fun `another app's file is refused, and a file with no app field is still read`() {
        val profile = """"profiles":[{"id":"p","name":"Mine","settings":{"profileId":"p"}}]"""
        assertTrue(assertIs<BackupResult.Refused>(Backup.validate("""{"app":"other",$profile}""")).reason.contains("not a Sci-Fin backup"))
        assertIs<BackupResult.Ok>(Backup.validate("""{$profile}"""))
    }
}
