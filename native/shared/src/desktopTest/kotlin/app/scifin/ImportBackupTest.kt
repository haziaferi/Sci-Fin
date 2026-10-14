package app.scifin

import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.Backup
import app.scifin.engine.BackupResult
import app.scifin.engine.Import
import app.scifin.engine.ImportStatus
import app.scifin.model.Cadence
import app.scifin.model.DateMode
import app.scifin.model.ProfileData
import app.scifin.model.RecurringExpense
import app.scifin.model.SCHEMA_VERSION
import app.scifin.model.Theme
import app.scifin.model.ThemeMode
import app.scifin.model.moneyOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFails
import app.scifin.model.AccountType
import app.scifin.model.Account
import app.scifin.model.Goal
import app.scifin.engine.ReviewRow
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.scifin.model.AppState

/** §4.3's import and §4.7's backup: the exact cases verified in the prototype on the 6th and 14th. */
class ImportBackupTest {
    private val today = LocalDate(2026, 9, 14)

    private val csv = """
        Data;Descrizione;Importo
        02/09/2026;STIPENDIO SETTEMBRE;1.450,00
        03/09/2026;SPAR ROMA;-12,40
        05/09/2026;AFFITTO RENT;-950,00
        07/09/2026;NETFLIX;-14,00
        08/09/2026;PRELIEVO BANCOMAT;-100,00
        10/09/2026;TRENITALIA;-1.234,56
        11/09/2026;SPAR ROMA;-8,30
    """.trimIndent()

    private val ofx = """
        <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
        <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260903120000</DTPOSTED><TRNAMT>-12.40</TRNAMT><FITID>A1</FITID><NAME>SPAR ROMA</NAME></STMTTRN>
        <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260907</DTPOSTED><TRNAMT>-14.00</TRNAMT><FITID>A2</FITID><NAME>NETFLIX</NAME></STMTTRN>
        <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260912</DTPOSTED><TRNAMT>-30.00</TRNAMT><FITID>A3</FITID><MEMO>FARMACIA</MEMO></STMTTRN>
        </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
    """.trimIndent()

    private fun world() = profileWith(savings = 3067.0, categories = listOf(category("groceries", "Groceries", planned = 300.0, spent = 50.0))).let {
        it.copy(recurringExpenses = listOf(RecurringExpense("rent", "p-1", "Rent", null, moneyOf(950.0), Cadence.MONTHLY,
            firstDueDate = LocalDate(2026, 1, 1), dueDate = LocalDate(2026, 10, 1), dueDateAnchorDay = 1, dateMode = DateMode.FIXED_DATE, lastSweptMonth = "2026-09")))
    }

    // ---- amounts and dates -------------------------------------------------------------------

    @Test fun `locale-aware amounts`() {
        assertEquals(moneyOf(-1234.56), Import.amount("-1.234,56")); assertEquals(moneyOf(1234.56), Import.amount("1,234.56"))
        assertEquals(moneyOf(-12.40), Import.amount("-12,40")); assertEquals(moneyOf(1450.0), Import.amount("1.450,00"))
        assertEquals(moneyOf(1450.0), Import.amount("1,450")); assertEquals(moneyOf(-12.40), Import.amount("(12,40)"))
        assertEquals(moneyOf(9.5), Import.amount("€ 9.5")); assertNull(Import.amount("abc")); assertNull(Import.amount(""))
    }

    @Test fun `day-first dates, ISO dates, two-digit years`() {
        assertEquals(LocalDate(2026, 4, 3), Import.date("03/04/2026")); assertEquals(LocalDate(2026, 4, 3), Import.date("2026-04-03"))
        assertEquals(LocalDate(2026, 4, 3), Import.date("03.04.26")); assertNull(Import.date("yesterday")); assertNull(Import.date("31/02/2026"))
    }

    // ---- parsers -----------------------------------------------------------------------------

    @Test fun `semicolon CSV with Italian headers parses every row`() {
        val rows = Import.parseCSV(csv)
        assertEquals(7, rows.size)
        assertEquals(moneyOf(1450.0), rows[0].amount); assertEquals("STIPENDIO SETTEMBRE", rows[0].description)
        assertEquals(moneyOf(-1234.56), rows[5].amount); assertEquals(LocalDate(2026, 9, 10), rows[5].date)
        assertTrue(rows.all { it.fitid == null })
    }

    @Test fun `OFX rows carry FITID and fall back to MEMO`() {
        val rows = Import.parseOFX(ofx)
        assertEquals(3, rows.size); assertEquals("A1", rows[0].fitid); assertEquals(LocalDate(2026, 9, 3), rows[0].date); assertEquals("FARMACIA", rows[2].description)
    }

    @Test fun `a CSV with no usable header parses nothing`() {
        assertTrue(Import.parseCSV("a;b;c\n1;2;3").isEmpty())
    }

    // ---- annotate + confirm ------------------------------------------------------------------

    @Test fun `review - income to Savings, rent skipped as swept, cash withdrawal is an ordinary expense`() {
        val d = world()
        val review = Import.annotate(d, Import.parseCSV(csv))
        val byDesc = review.associateBy { it.row.description }
        assertEquals(ImportStatus.INCOMING, byDesc["STIPENDIO SETTEMBRE"]!!.status)
        assertEquals(ImportStatus.RECURRING, byDesc["AFFITTO RENT"]!!.status); assertEquals(false, byDesc["AFFITTO RENT"]!!.include)
        assertEquals(ImportStatus.READY, byDesc["PRELIEVO BANCOMAT"]!!.status); assertTrue(byDesc["PRELIEVO BANCOMAT"]!!.include)
        assertNull(byDesc["SPAR ROMA"]!!.target)                                        // never guessed on first sight
        assertEquals(6, review.count { it.include })
    }

    @Test fun `confirm writes through the quick-add paths with the statement date and learns the assignment`() {
        val clock = FakeClock(today)
        val ids = Ids()
        val d = world()
        val review = Import.annotate(d, Import.parseCSV(csv)).map { if (it.row.description == "SPAR ROMA") it.copy(target = "cat:groceries") else it }.assigned()
        val after = Import.confirm(d, review, clock, ids::next)
        // 3067 + 1450 − (12.40 + 14 + 100 + 1234.56 + 8.30) = 3147.74
        assertEquals(moneyOf(3147.74), after.savings.amount)
        assertEquals(moneyOf(50.0 + 12.40 + 14 + 100 + 1234.56 + 8.30), after.categories[0].spent)
        assertEquals("groceries", after.importState.payeeRules["spar roma"])
        assertEquals(LocalDate(2026, 9, 2), after.transactions.first { it.payee == "STIPENDIO SETTEMBRE" }.date)
        assertEquals(6, after.importState.importedKeys.size)
        assertEquals(today, after.importState.lastImportDate)
        // Re-importing the same file: every row arrives as a duplicate.
        val again = Import.annotate(after, Import.parseCSV(csv))
        assertEquals(0, again.count { it.include })
        assertEquals(6, again.count { it.status == ImportStatus.DUPLICATE })
    }

    @Test fun `a learned rule pre-fills the category on the next import, and cross-format duplicates are caught`() {
        val clock = FakeClock(today)
        val ids = Ids()
        var d = world()
        d = Import.confirm(d, Import.annotate(d, Import.parseCSV(csv)).map { if (it.row.description == "SPAR ROMA") it.copy(target = "cat:groceries") else it }.assigned(), clock, ids::next)
        val review = Import.annotate(d, Import.parseOFX(ofx))
        val byId = review.associateBy { it.row.fitid }
        assertEquals(ImportStatus.DUPLICATE, byId["A1"]!!.status)                       // CSV then OFX of the same row: matched on syn:
        assertEquals(ImportStatus.DUPLICATE, byId["A2"]!!.status)
        assertEquals(ImportStatus.READY, byId["A3"]!!.status)
        d = Import.confirm(d, review.assigned(), clock, ids::next)
        assertTrue("fit:A3" in d.importState.importedKeys && d.importState.importedKeys.any { it.startsWith("syn:2026-09-12|-30.00|farmacia") })
        assertEquals(0, Import.annotate(d, Import.parseOFX(ofx)).count { it.include })
    }

    /** §4.3 (Decided 2026-10-09): every included outflow needs a target; here the rest go to Groceries. */
    private fun List<ReviewRow>.assigned() = map { if (it.include && it.row.amount < 0 && it.target == null) it.copy(target = "cat:groceries") else it }

    @Test fun `an outflow with no target blocks confirm, and one can be spent from a Goal, charged or paid off`() {
        val clock = FakeClock(today)
        val ids = Ids()
        val d = world().let { w ->
            w.copy(goals = listOf(Goal("cash", w.profileId, "Cash", null, saved = moneyOf(110.0))),
                accounts = listOf(Account("cc", w.profileId, "Visa", null, AccountType.CREDIT, balance = moneyOf(300.0))))
        }
        val review = Import.annotate(d, Import.parseCSV(csv))
        assertEquals(review.count { it.include && it.row.amount < 0 }, Import.unassigned(d, review).size)   // nothing leaves Savings untracked
        assertFails { Import.confirm(d, review, clock, ids::next) }
        val atm = review.first { it.row.description == "PRELIEVO BANCOMAT" }
        // the ATM withdrawal (100) from the Cash goal; a Goal holding less than an outflow is not offered for it
        assertTrue("spend:cash" in Import.outflowTargets(d, -atm.row.amount).map { it.first })
        assertTrue("spend:cash" !in Import.outflowTargets(d, moneyOf(1234.56)).map { it.first })
        val chosen = review.map { r ->
            when (r.row.description) {
                "PRELIEVO BANCOMAT" -> r.copy(target = "spend:cash")
                "SPAR ROMA" -> r.copy(target = "charge:cc")
                else -> r
            }
        }.assigned()
        assertTrue(Import.unassigned(d, chosen).isEmpty())
        val after = Import.confirm(d, chosen, clock, ids::next)
        assertEquals(moneyOf(10.0), after.goals.single().saved)
        assertEquals(moneyOf(300.0 + 12.40 + 8.30), after.accounts.single().balance)            // both SPAR ROMA rows
        assertEquals(moneyOf(3067.0 + 1450.0 - 14.0 - 1234.56), after.savings.amount)           // the Goal and the card leave Savings alone
        // two rows that together overdraw the Goal (100 + 14 > 110) are caught before confirm: the second, in write order
        val twice = chosen.map { if (it.row.description == "NETFLIX") it.copy(target = "spend:cash") else it }
        assertEquals(listOf("NETFLIX"), Import.unassigned(d, twice).map { it.row.description })
    }

    // ---- backup ------------------------------------------------------------------------------

    private fun sample(): ProfileData = Fixture.profile.let { it.copy(settings = it.settings.copy(theme = Theme.DUSK), importState = it.importState.copy(importedKeys = listOf("fit:x"))) }

    @Test fun `export then validate round-trips every profile, and the file carries the schema version`() {
        val d = sample()
        val text = Backup.encode(Backup.build(listOf(d), d.profileId, today))
        assertTrue(text.contains("\"schemaVersion\": $SCHEMA_VERSION")); assertTrue(text.contains("\"theme\": \"dusk\""))
        val ok = assertIs<BackupResult.Ok>(Backup.validate(text)).backup
        assertEquals(1, ok.profiles.size); assertEquals(d, ok.profiles[0]); assertEquals(today, ok.exportedAt); assertEquals(112, ok.transactionCount)
    }

    @Test fun `a newer format, non-JSON, no profiles, and damaged profiles are refused whole`() {
        val text = Backup.encode(Backup.build(listOf(sample()), Fixture.PROFILE, today))
        // relative to the app's own version, so the next schema bump does not quietly make this case valid
        val newer = text.replace("\"schemaVersion\": $SCHEMA_VERSION", "\"schemaVersion\": ${SCHEMA_VERSION + 1}")
        assertTrue(assertIs<BackupResult.Refused>(Backup.validate(newer)).reason.contains("newer version"))
        assertTrue(assertIs<BackupResult.Refused>(Backup.validate("{not json")).reason.contains("could not be read"))
        assertTrue(assertIs<BackupResult.Refused>(Backup.validate("""{"schemaVersion":1,"profiles":[]}""")).reason.contains("no profiles"))
        assertTrue(assertIs<BackupResult.Refused>(Backup.validate("""{"schemaVersion":1,"profiles":[{"name":"x"}]}""")).reason.contains("missing its id"))
        assertTrue(assertIs<BackupResult.Refused>(Backup.validate("""{"schemaVersion":1,"profiles":[{"id":"p","name":"Mine","settings":{"profileId":"p"},"goals":"oops"}]}""")).reason.contains("no goals list"))
        assertTrue(assertIs<BackupResult.Refused>(Backup.validate("""{"schemaVersion":1,"profiles":[{"id":"p","name":"Mine","settings":{"profileId":"p"},"savings":"lots"}]}""")).reason.contains("Savings figure"))
    }

    @Test fun `a backup from before the 2026-10-07 themes keeps its choice as the nearest new theme`() {
        for ((old, now) in listOf("slate" to Theme.SWISS_BLUE, "mauve" to Theme.DUSK, "wabi" to Theme.DAYLIGHT, "tide" to Theme.SWISS_BLUE)) {
            val ok = assertIs<BackupResult.Ok>(Backup.validate("""{"profiles":[{"id":"p","name":"Mine","settings":{"profileId":"p","theme":"$old"}}]}""")).backup
            assertEquals(now, ok.profiles[0].settings.theme, old)
        }
    }

    @Test fun `unknown theme and mode are repaired, absent fields take defaults, no version reads as 1`() {
        val ok = assertIs<BackupResult.Ok>(Backup.validate("""{"profiles":[{"id":"p","name":"Mine","settings":{"profileId":"p","theme":"neon","mode":"sepia"}}]}""")).backup
        assertEquals(1, ok.version)
        assertEquals(Theme.SWISS_BLUE, ok.profiles[0].settings.theme); assertEquals(ThemeMode.SYSTEM, ok.profiles[0].settings.mode)
        assertNull(ok.profiles[0].savings.amount); assertTrue(ok.profiles[0].goals.isEmpty()); assertTrue(ok.profiles[0].importState.importedKeys.isEmpty())
    }

    @Test fun `apply replaces the store whole - a profile not in the file is gone`() = runTest {
        val db = buildInMemorySciFinDatabase()
        try {
            db.dao().save(sample()); db.dao().save(ProfileData.fresh("p-2", "Trust"))
            db.dao().upsertAppState(AppState(activeProfileId = "p-2"))
            val text = Backup.encode(Backup.build(listOf(sample()), Fixture.PROFILE, today))
            Backup.apply(db, assertIs<BackupResult.Ok>(Backup.validate(text)).backup, today)
            assertEquals(listOf(Fixture.PROFILE), db.dao().profiles().map { it.id })
            assertEquals(Fixture.PROFILE, db.dao().appState()!!.activeProfileId); assertEquals(today, db.dao().appState()!!.lastBackupImportDate)
            assertEquals(112, db.dao().load(Fixture.PROFILE)!!.transactions.size)
        } finally { db.close() }
    }
}
