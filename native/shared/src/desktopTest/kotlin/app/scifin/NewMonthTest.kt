package app.scifin

import app.scifin.engine.Ledger
import app.scifin.engine.NewMonth
import app.scifin.engine.Recurring
import app.scifin.model.Account
import app.scifin.model.AccountType
import app.scifin.model.Cadence
import app.scifin.model.DateMode
import app.scifin.model.IncomeCadence
import app.scifin.model.RateType
import app.scifin.model.RecurringExpense
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §3.9's engine: idempotent per month, replays every elapsed month, two order-independent triggers. */
class NewMonthTest {

    private fun monthly(id: String, amount: Double, day: Int, linked: String? = null, endDate: LocalDate? = null, swept: String? = "2026-08") =
        RecurringExpense(id, "p-1", id, null, moneyOf(amount), Cadence.MONTHLY, firstDueDate = LocalDate(2026, 1, day), dueDate = LocalDate(2026, 9, day),
            dueDateAnchorDay = day, dateMode = DateMode.FIXED_DATE, lastSweptMonth = swept, linkedAccountId = linked, endDate = endDate)

    private fun world(vararg exps: RecurringExpense, savings: Double = 5000.0, resetMonth: String = "2026-08") = profileWith(
        savings = savings,
        categories = listOf(category("c1", "Groceries", spent = 120.0).copy(lastResetMonth = resetMonth)),
    ).copy(recurringExpenses = exps.toList())

    @Test fun `crossing one boundary resets categories without a transaction and sweeps each expense once`() {
        val clock = FakeClock(LocalDate(2026, 9, 1))
        val ids = Ids()
        val (d, s) = NewMonth.runCatchUp(world(monthly("rent", 950.0, 1), monthly("phone", 45.0, 10)), clock) { ids.next() }
        assertNotNull(s)
        assertEquals(1, s.monthsReplayed)
        assertEquals(moneyOf(995.0), s.sweptTotal)
        assertEquals(0L, d.categories[0].spent); assertEquals("2026-09", d.categories[0].lastResetMonth)
        assertEquals(moneyOf(4005.0), d.savings.amount)
        assertEquals(setOf("rec:rent", "rec:phone"), d.transactions.map { it.key }.toSet())     // no row for the reset
        assertEquals(LocalDate(2026, 9, 10), d.transactions.first { it.key == "rec:phone" }.date) // dated to the real due date
        assertEquals(false, d.settings.catchUpSummaryPending)                                     // one month: the ordinary boundary
        // Idempotent: a second run does nothing.
        val (d2, s2) = NewMonth.runCatchUp(d, clock) { ids.next() }
        assertNull(s2); assertEquals(d.transactions.size, d2.transactions.size)
    }

    @Test fun `a three-month absence replays every month in order with real dates, and the summary fires`() {
        val clock = FakeClock(LocalDate(2026, 11, 14))
        val ids = Ids()
        val (d, s) = NewMonth.runCatchUp(world(monthly("rent", 950.0, 1)), clock) { ids.next() }
        assertEquals(3, s!!.monthsReplayed)
        assertEquals(listOf(LocalDate(2026, 9, 1), LocalDate(2026, 10, 1), LocalDate(2026, 11, 1)), d.transactions.map { it.date })
        assertTrue(d.settings.catchUpSummaryPending)
        assertEquals("2026-11", d.recurringExpenses[0].lastSweptMonth)
        assertEquals(LocalDate(2026, 12, 1), d.recurringExpenses[0].dueDate)                    // the anchor chain ran too
    }

    @Test fun `the anchor chain runs on every open, not only across a boundary`() {
        val clock = FakeClock(LocalDate(2026, 9, 20))
        val e = monthly("phone", 45.0, 10, swept = "2026-09")
        val (d, s) = NewMonth.runCatchUp(world(e, resetMonth = "2026-09"), clock) { Ids().next() }
        assertNull(s)
        assertEquals(LocalDate(2026, 10, 10), d.recurringExpenses[0].dueDate)
    }

    @Test fun `endDate finishes the expense and an amortising loan, and the notice says it is paid off`() {
        val clock = FakeClock(LocalDate(2026, 9, 1))
        val ids = Ids()
        val loan = Account("loan", "p-1", "Car Loan", null, AccountType.LOAN, balance = moneyOf(120.0), linkedRecurringExpenseId = "pay",
            rateType = RateType.FIXED, autoUpdateFromPayments = false)
        val w = world(monthly("pay", 260.0, 15, linked = "loan", endDate = LocalDate(2026, 8, 15))).copy(accounts = listOf(loan))
        val (d, s) = NewMonth.runCatchUp(w, clock) { ids.next() }
        assertEquals(1, s!!.endedNow.size)
        assertEquals("Loan paid off — this recurring expense has ended.", s.endedNow[0].note)
        assertNotNull(d.recurringExpenses[0].finishedAt)
        assertEquals(0L, d.accounts[0].balance)
        assertEquals(1, d.transactions.count { it.key == "acct:loan" && it.payee == "Loan finished" })
    }

    @Test fun `a variable loan that its final payment clears is paid off, not left to check with the lender`() {
        val loan = Account("loan", "p-1", "L", null, AccountType.LOAN, balance = moneyOf(100.0), linkedRecurringExpenseId = "pay",
            rateType = RateType.VARIABLE, autoUpdateFromPayments = true)
        val w = world(monthly("pay", 260.0, 1, linked = "loan", endDate = LocalDate(2026, 9, 1))).copy(accounts = listOf(loan))
        val (d, s) = NewMonth.runCatchUp(w, FakeClock(LocalDate(2026, 9, 2))) { Ids().next() }
        assertEquals(0L, d.accounts[0].balance)
        assertEquals("Loan paid off — this recurring expense has ended.", s!!.endedNow.single().note)
    }

    @Test fun `variable and interest-only loans carry hedged copy`() {
        val base = world(monthly("pay", 260.0, 15, linked = "loan"))
        val variable = base.copy(accounts = listOf(Account("loan", "p-1", "L", null, AccountType.LOAN, balance = moneyOf(100.0), rateType = RateType.VARIABLE)))
        assertTrue(NewMonth.completionNoticeFor(variable, base.recurringExpenses[0]).contains("Check the balance"))
        val io = base.copy(accounts = listOf(Account("loan", "p-1", "L", null, AccountType.LOAN, balance = moneyOf(100.0), rateType = RateType.INTEREST_ONLY)))
        assertTrue(NewMonth.completionNoticeFor(io, base.recurringExpenses[0]).contains("principal is still outstanding"))
        // Auto-update took the balance to zero: clear whatever the rate type.
        val paid = base.copy(accounts = listOf(Account("loan", "p-1", "L", null, AccountType.LOAN, balance = 0, rateType = RateType.VARIABLE, autoUpdateFromPayments = true)))
        assertEquals("Loan paid off — this recurring expense has ended.", NewMonth.completionNoticeFor(paid, base.recurringExpenses[0]))
    }

    @Test fun `loan auto-update pays principal down by payment minus this cycle's interest`() {
        val clock = FakeClock(LocalDate(2026, 9, 1))
        val ids = Ids()
        val loan = Account("loan", "p-1", "Car Loan", null, AccountType.LOAN, balance = moneyOf(6000.0), linkedRecurringExpenseId = "pay",
            autoUpdateFromPayments = true, interestRate = 0.06, rateType = RateType.FIXED)
        val w = world(monthly("pay", 260.0, 15, linked = "loan")).copy(accounts = listOf(loan))
        val (d, _) = NewMonth.runCatchUp(w, clock) { ids.next() }
        assertEquals(moneyOf(6000.0 - 230.0), d.accounts[0].balance)                                   // interest 30, principal 230
        // §7.2: dated to the payment's real occurrence, payee the payment's own name (audit 2026-09-25)
        val row = d.transactions.single { it.key == "acct:loan" }
        assertEquals(moneyOf(-230.0), row.amount); assertEquals("pay", row.payee); assertEquals(LocalDate(2026, 9, 15), row.date)
        assertEquals(moneyOf(5000.0 - 260.0), d.savings.amount)                                         // acct rows never touch Savings
    }

    @Test fun `an auto-updating loan's expense finishes the moment the balance reaches zero, whatever endDate says`() {
        val clock = FakeClock(LocalDate(2026, 9, 1))
        val loan = Account("loan", "p-1", "L", null, AccountType.LOAN, balance = moneyOf(200.0), linkedRecurringExpenseId = "pay", autoUpdateFromPayments = true, interestRate = 0.0)
        val w = world(monthly("pay", 260.0, 15, linked = "loan", endDate = LocalDate(2030, 1, 1))).copy(accounts = listOf(loan))
        val (d, _) = NewMonth.runCatchUp(w, clock) { Ids().next() }
        assertEquals(0L, d.accounts[0].balance)
        assertNotNull(d.recurringExpenses[0].finishedAt)
    }

    @Test fun `the two triggers are order-independent and both run from one open`() {
        val clock = FakeClock(LocalDate(2026, 10, 2))
        val ids = Ids()
        val w = world(monthly("rent", 950.0, 1, swept = "2026-09"), resetMonth = "2026-09").let {
            it.copy(settings = it.settings.copy(incomeEnabled = true, income = moneyOf(1450.0), incomeCadence = IncomeCadence.BIWEEKLY,
                incomePayDate = LocalDate(2026, 9, 11), incomeAutoDeposit = true, incomeEnabledDate = LocalDate(2026, 9, 11)))
        }
        val (d, s) = NewMonth.runCatchUp(w, clock) { ids.next() }
        assertEquals(1, s!!.monthsReplayed)
        assertEquals(2, s.deposits.count)                                                              // 11 Sep, 25 Sep
        assertEquals(moneyOf(5000.0 - 950.0 + 2 * 1450.0), d.savings.amount)
        assertEquals(2, d.transactions.count { it.payee == Ledger.AUTO_DEPOSIT_PAYEE })
    }

    @Test fun `scheduled total is what is committed this month, swept-to-date is what moved`() {
        val clock = FakeClock(LocalDate(2026, 9, 18))
        val w = world(monthly("rent", 950.0, 1, swept = "2026-09"), monthly("late", 830.0, 5, swept = "2026-09"))
        assertEquals(moneyOf(1780.0), Recurring.scheduledTotal(w, clock.today()))
    }
}
