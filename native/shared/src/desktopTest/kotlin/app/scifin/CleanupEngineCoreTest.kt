package app.scifin

import app.scifin.engine.Accounts
import app.scifin.engine.Goals
import app.scifin.engine.Import
import app.scifin.engine.ImportRow
import app.scifin.engine.ImportStatus
import app.scifin.engine.NewMonth
import app.scifin.engine.Recurring
import app.scifin.engine.savingsAmount
import app.scifin.model.Account
import app.scifin.model.AccountType
import app.scifin.model.Cadence
import app.scifin.model.DateMode
import app.scifin.model.Goal
import app.scifin.model.RateType
import app.scifin.model.RecurringExpense
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The engine-core cleanup of 2026-10-10: each case failed before its fix. */
class CleanupEngineCoreTest {

    // ---- §4.3 import ---------------------------------------------------------------------------

    @Test fun `debit amount and credit amount columns are the split columns, not the signed amount`() {
        val csv = """
            Transaction Date,Transaction Type,Sort Code,Account Number,Transaction Description,Debit Amount,Credit Amount,Balance
            03/09/2026,DEB,11-22-33,12345678,SPAR ROMA,12.40,,500.00
            04/09/2026,FPI,11-22-33,12345678,SALARY,,1450.00,1950.00
        """.trimIndent()
        val rows = Import.parseCSV(csv)
        assertEquals(listOf(moneyOf(-12.40), moneyOf(1450.0)), rows.map { it.amount })
    }

    @Test fun `a trailing minus is money out`() {
        assertEquals(moneyOf(-1234.56), Import.amount("1.234,56-"))
        assertEquals(moneyOf(-12.40), Import.amount("12,40-"))
    }

    @Test fun `escaped quotes inside a quoted field survive`() {
        assertEquals(listOf("\"Quoted\"", "x"), Import.splitCsvLine("\"\"\"Quoted\"\"\",x", ','))
    }

    @Test fun `a quoted field spanning lines stays one record`() {
        val csv = "Data;Descrizione;Importo\n02/09/2026;\"SPAR\nROMA\";-12,40\r\n03/09/2026;NETFLIX;-14,00"
        val rows = Import.parseCSV(csv)
        assertEquals(2, rows.size)
        assertEquals("SPAR ROMA", rows[0].description)
        assertEquals(moneyOf(-12.40), rows[0].amount)
        assertEquals("NETFLIX", rows[1].description)
    }

    @Test fun `recurring match is on whole words`() {
        val d = profileWith().copy(recurringExpenses = listOf(RecurringExpense("rent", "p-1", "Rent", null, moneyOf(950.0), Cadence.MONTHLY,
            firstDueDate = LocalDate(2026, 1, 1), dueDate = LocalDate(2026, 10, 1), dueDateAnchorDay = 1, dateMode = DateMode.FIXED_DATE)))
        val review = Import.annotate(d, listOf(
            ImportRow(LocalDate(2026, 9, 3), moneyOf(-12.0), "Parent teacher assoc"),
            ImportRow(LocalDate(2026, 9, 5), moneyOf(-950.0), "AFFITTO RENT"),
        ))
        assertEquals(ImportStatus.READY, review.single { it.row.description.startsWith("Parent") }.status)
        assertEquals(ImportStatus.RECURRING, review.single { it.row.description == "AFFITTO RENT" }.status)
    }

    @Test fun `payee normalisation keeps accented letters`() {
        assertEquals("café müller", Import.normalisePayee("Café Müller!"))
    }

    // ---- §3.11 / §3.9 ---------------------------------------------------------------------------

    private fun yearlyEndingAtDue() = RecurringExpense("ins", "p-1", "Insurance", null, moneyOf(1200.0), Cadence.YEARLY,
        firstDueDate = LocalDate(2026, 3, 15), dueDate = LocalDate(2027, 3, 15), dueDateAnchorDay = 15, dateMode = DateMode.FIXED_DATE,
        apportionedShare = moneyOf(100.0), lastSweptMonth = "2027-03", endDate = LocalDate(2027, 3, 15))

    @Test fun `an apportioned share stops after the last occurrence endDate allows`() {
        val e = yearlyEndingAtDue()
        assertEquals(moneyOf(100.0), Recurring.sweepAmountForMonth(e, 2027, 3))
        assertEquals(0L, Recurring.sweepAmountForMonth(e, 2027, 4))
        val d = profileWith().copy(recurringExpenses = listOf(e))
        val (after, r) = NewMonth.processMonth(d, LocalDate(2027, 4, 1), FakeClock(LocalDate(2027, 4, 1))) { "x" }
        assertEquals(0L, r.sweptTotal)
        assertEquals(1, r.endedNow.size)
        assertNotNull(after.recurringExpenses.single().finishedAt)
        assertEquals(0, after.transactions.size)
    }

    @Test fun `processMonth twice in one month pays a loan down once`() {
        val ids = Ids()
        val loan = Account("L", "p-1", "Car loan", null, AccountType.LOAN, balance = moneyOf(10000.0), linkedRecurringExpenseId = "pay",
            autoUpdateFromPayments = true, interestRate = 0.0, rateType = RateType.FIXED)
        val pay = RecurringExpense("pay", "p-1", "Car", null, moneyOf(100.0), Cadence.MONTHLY,
            firstDueDate = LocalDate(2026, 1, 5), dueDate = LocalDate(2026, 10, 5), dueDateAnchorDay = 5, dateMode = DateMode.FIXED_DATE,
            lastSweptMonth = "2026-09", linkedAccountId = "L")
        val d = profileWith().copy(accounts = listOf(loan), recurringExpenses = listOf(pay))
        val clock = FakeClock(LocalDate(2026, 10, 1))
        val (once, _) = NewMonth.processMonth(d, LocalDate(2026, 10, 1), clock) { ids.next() }
        val (twice, _) = NewMonth.processMonth(once, LocalDate(2026, 10, 1), clock) { ids.next() }
        assertEquals(moneyOf(9900.0), once.accounts.single().balance)
        assertEquals(moneyOf(9900.0), twice.accounts.single().balance)
        assertEquals(once.transactions.size, twice.transactions.size)
    }

    @Test fun `a marker already past the month is not rewound`() {
        val e = RecurringExpense("rent", "p-1", "Rent", null, moneyOf(950.0), Cadence.MONTHLY,
            firstDueDate = LocalDate(2026, 1, 5), dueDate = LocalDate(2026, 11, 5), dueDateAnchorDay = 5, dateMode = DateMode.FIXED_DATE,
            lastSweptMonth = "2026-11")
        val d = profileWith(categories = listOf(category("food", spent = 50.0).copy(lastResetMonth = "2026-11"))).copy(recurringExpenses = listOf(e))
        val (after, r) = NewMonth.processMonth(d, LocalDate(2026, 10, 1), FakeClock(LocalDate(2026, 10, 1))) { "x" }
        assertEquals(moneyOf(50.0), after.categories.single().spent)
        assertEquals("2026-11", after.categories.single().lastResetMonth)
        assertEquals("2026-11", after.recurringExpenses.single().lastSweptMonth)
        assertEquals(0L, r.sweptTotal)
    }

    // ---- §9.1 / §9.2 ----------------------------------------------------------------------------

    @Test fun `save for it stores the by-date derivation, not the loan payment`() {
        val today = LocalDate(2026, 9, 14)
        val m = Accounts.loanMath(moneyOf(12000.0), 0.065, 48, RateType.FIXED)
        val g = Accounts.saveForItGoal(moneyOf(12000.0), m, "", today, "g", "p-1")
        assertEquals(Goals.derive(g, today).monthly, g.monthly)
        assertEquals(moneyOf(250.0), g.monthly)
        assertNotEquals(m.monthly, g.monthly)
    }

    @Test fun `a payoff walk that hits its cap reports never, not 100 years`() {
        assertNull(Accounts.payoffAtFixedPayment(moneyOf(10000.0), 0.001, moneyOf(10.01)).months)
        assertNull(Accounts.payoffAtMinimum(moneyOf(100000.0), 0.0199).months)
    }

    @Test fun `committing a fixed payment releases the account's earlier link`() {
        val cc = Account("cc", "p-1", "Visa", null, AccountType.CREDIT, balance = moneyOf(640.0), linkedRecurringExpenseId = "old")
        val old = RecurringExpense("old", "p-1", "Visa payment", null, moneyOf(40.0), Cadence.MONTHLY,
            firstDueDate = LocalDate(2026, 1, 5), dueDate = LocalDate(2026, 10, 5), dueDateAnchorDay = 5, dateMode = DateMode.FIXED_DATE,
            linkedAccountId = "cc")
        val d = profileWith().copy(accounts = listOf(cc), recurringExpenses = listOf(old))
        val after = Accounts.commitFixedPayment(d, "cc", moneyOf(50.0), "", FakeClock(LocalDate(2026, 9, 14)), "new")
        assertNull(after.recurringExpenses.single { it.id == "old" }.linkedAccountId)
        assertEquals("cc", after.recurringExpenses.single { it.id == "new" }.linkedAccountId)
        assertEquals("new", after.accounts.single().linkedRecurringExpenseId)
    }

    @Test fun `a zero OFX row is dropped, as CSV drops one, so review never holds an outflow it cannot confirm`() {
        val ofx = "<STMTTRN><DTPOSTED>20261001<TRNAMT>0.00<FITID>z<NAME>Card check</STMTTRN><STMTTRN><DTPOSTED>20261001<TRNAMT>-5.00<FITID>a<NAME>Cafe</STMTTRN>"
        assertEquals(listOf(moneyOf(-5.0)), Import.parseOFX(ofx).map { it.amount })
    }

    @Test fun `money in can go on into a Goal, through Savings, and Savings is the default`() {
        val d = profileWith(savings = 500.0).let { it.copy(goals = listOf(Goal("g", it.profileId, "Holidays", saved = 0L))) }
        assertEquals(listOf("" to "Savings", "goal:g" to "Add to Holidays"), Import.inflowTargets(d))
        val review = Import.annotate(d, listOf(ImportRow(LocalDate(2026, 9, 3), moneyOf(100.0), "Gift"), ImportRow(LocalDate(2026, 9, 4), moneyOf(40.0), "Refund")))
        assertTrue(review.all { it.target == null })
        val after = Import.confirm(d, review.map { if (it.row.description == "Gift") it.copy(target = "goal:g") else it }, FakeClock(LocalDate(2026, 10, 1)), Ids()::next)
        assertEquals(moneyOf(540.0), after.savingsAmount)            // the gift passed through; the refund stayed
        assertEquals(moneyOf(100.0), after.goals.single().saved)
        assertEquals(3, after.transactions.size)
    }
}
