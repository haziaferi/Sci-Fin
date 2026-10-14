package app.scifin

import app.scifin.engine.Accounts
import app.scifin.engine.monthsLabel
import app.scifin.model.Account
import app.scifin.model.AccountType
import app.scifin.model.RateType
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §7.2 accounts, §9.1 loan maths, §9.2 payoff projections. */
class AccountsTest {
    private val today = LocalDate(2026, 9, 14)

    // ---- §9.1 ------------------------------------------------------------------------

    @Test fun `fixed-rate amortisation - €12,000 at 6_5 percent over 48 months is €284_58 a month`() {
        val m = Accounts.loanMath(moneyOf(12000.0), 0.065, 48, RateType.FIXED)
        assertEquals(moneyOf(284.58), m.monthly)
        assertEquals(moneyOf(284.58) * 48, m.totalRepaid)
        assertEquals(m.totalRepaid - moneyOf(12000.0), m.totalInterest)
        assertEquals(0L, m.principalRemaining)
    }

    @Test fun `interest-only pays interest alone and leaves the principal outstanding`() {
        val m = Accounts.loanMath(moneyOf(12000.0), 0.065, 48, RateType.INTEREST_ONLY)
        assertEquals(moneyOf(65.0), m.monthly)
        assertEquals(moneyOf(65.0) * 48, m.totalRepaid)            // total repaid is the payments, not payments + principal
        assertEquals(moneyOf(12000.0), m.principalRemaining)
    }

    @Test fun `a zero rate divides evenly`() {
        assertEquals(moneyOf(250.0), Accounts.loanMath(moneyOf(12000.0), 0.0, 48, RateType.FIXED).monthly)
    }

    @Test fun `get loan creates the account and its payment atomically, linked both ways, with an opening row`() {
        val clock = FakeClock(today)
        val d = Accounts.getLoan(profileWith(), moneyOf(9800.0), 0.059, 48, RateType.FIXED, "Car Loan", "Car loan payment", autoUpdate = true, clock) { Ids().next() }
        val a = d.accounts.single(); val e = d.recurringExpenses.single()
        assertEquals(e.id, a.linkedRecurringExpenseId); assertEquals(a.id, e.linkedAccountId)
        assertEquals(LocalDate(2026, 10, 14), e.firstDueDate)                    // one month after origination
        assertEquals(LocalDate(2030, 9, 14), e.endDate)                          // the term's end
        assertEquals("2026-09", e.lastSweptMonth)                                // creation branch 3: nothing this month
        assertEquals(moneyOf(9800.0), a.balance); assertEquals(moneyOf(9800.0), a.principal)
        assertEquals(Accounts.OPENING_BALANCE, d.transactions.single().payee)
        assertEquals(moneyOf(1000.0), d.savings.amount)                          // §7.1: Savings untouched
    }

    @Test fun `save for it creates a by-date goal for the principal over the term`() {
        val m = Accounts.loanMath(moneyOf(12000.0), 0.065, 48, RateType.FIXED)
        val g = Accounts.saveForItGoal(moneyOf(12000.0), m, "", today, "g", "p-1")
        assertEquals("Savings goal", g.name); assertEquals(moneyOf(12000.0), g.target); assertEquals(LocalDate(2030, 9, 14), g.targetDate)
    }

    // ---- §9.2 ------------------------------------------------------------------------

    @Test fun `payoff at a fixed payment is walked, and a payment at or below the interest never clears`() {
        val p = Accounts.payoffAtFixedPayment(moneyOf(640.0), 0.219 / 12, moneyOf(50.0))
        assertEquals(15, p.months)
        assertTrue(p.interest!! in moneyOf(80.0)..moneyOf(100.0))
        assertNull(Accounts.payoffAtFixedPayment(moneyOf(640.0), 0.219 / 12, moneyOf(11.0)).months)
        assertEquals(0, Accounts.payoffAtFixedPayment(0, 0.02, moneyOf(50.0)).months)
        assertEquals(13, Accounts.payoffAtFixedPayment(moneyOf(640.0), 0.0, moneyOf(50.0)).months)
    }

    @Test fun `minimum payment is 2 percent floored at €25 on a moving balance`() {
        val m = Accounts.payoffAtMinimum(moneyOf(640.0), 0.219 / 12)
        assertEquals(moneyOf(25.0), m.first)                                    // 2 % of 640 = 12.80 -> floor 25
        assertTrue(m.months!! > 30)
        assertEquals(moneyOf(60.0), Accounts.payoffAtMinimum(moneyOf(3000.0), 0.0).first)
        assertNull(Accounts.payoffAtMinimum(moneyOf(1000.0), 0.05).months)        // 2 % floor 25 < 5 % interest: never
    }

    @Test fun `months copy`() {
        assertEquals("never at this rate", monthsLabel(null)); assertEquals("cleared at once", monthsLabel(0))
        assertEquals("1 month", monthsLabel(1)); assertEquals("11 months", monthsLabel(11))
        assertEquals("1 year", monthsLabel(12)); assertEquals("2 years 3 months", monthsLabel(27)); assertEquals("1 year 1 month", monthsLabel(13))
    }

    @Test fun `committing a fixed payment links both ways and never auto-updates a credit account`() {
        val clock = FakeClock(today)
        val a = Account("cc", "p-1", "Credit Card", null, AccountType.CREDIT, balance = moneyOf(640.0), apr = 0.219, autoUpdateFromPayments = true)
        val d = Accounts.commitFixedPayment(profileWith().copy(accounts = listOf(a)), "cc", moneyOf(50.0), "", clock, "e1")
        val e = d.recurringExpenses.single()
        assertEquals("Credit Card payment", e.name); assertEquals("cc", e.linkedAccountId); assertNull(e.endDate)
        assertEquals("e1", d.accounts[0].linkedRecurringExpenseId); assertEquals(false, d.accounts[0].autoUpdateFromPayments)
    }

    // ---- §7.2 ------------------------------------------------------------------------

    @Test fun `a charge raises the balance, a payment lowers it, and only a paired payment moves Savings`() {
        val clock = FakeClock(today)
        val ids = Ids()
        val a = Account("cc", "p-1", "Card", null, AccountType.CREDIT, balance = moneyOf(100.0))
        var d = profileWith(savings = 1000.0).copy(accounts = listOf(a))
        d = Accounts.quickAdd(d, "cc", moneyOf(40.0), Accounts.Direction.CHARGE, "Shoes", pairSavings = false, clock) { ids.next() }
        assertEquals(moneyOf(140.0), d.accounts[0].balance); assertEquals(moneyOf(1000.0), d.savings.amount)
        d = Accounts.quickAdd(d, "cc", moneyOf(60.0), Accounts.Direction.PAYMENT, null, pairSavings = true, clock) { ids.next() }
        assertEquals(moneyOf(80.0), d.accounts[0].balance); assertEquals(moneyOf(940.0), d.savings.amount)
        assertEquals("Payment to Card", d.transactions.last().payee)
        assertTrue(Accounts.pairedOutflowDefault(a))
        assertEquals(false, Accounts.pairedOutflowDefault(a.copy(linkedRecurringExpenseId = "e")))
    }

    @Test fun `retype clears the state of the type it leaves`() {
        val loan = Account("l", "p-1", "L", "landmark", AccountType.LOAN, autoUpdateFromPayments = true, principal = moneyOf(1.0), interestRate = 0.05, termMonths = 12, rateType = RateType.FIXED, originationDate = today)
        val asCredit = Accounts.retype(loan, AccountType.CREDIT)
        assertEquals(false, asCredit.autoUpdateFromPayments); assertNull(asCredit.principal); assertNull(asCredit.interestRate); assertNull(asCredit.rateType); assertEquals("credit-card", asCredit.icon)
        val credit = Account("c", "p-1", "C", "credit-card", AccountType.CREDIT, apr = 0.2)
        assertNull(Accounts.retype(credit, AccountType.LOAN).apr)
        assertEquals(false, Accounts.loanInputsComplete(Accounts.retype(credit, AccountType.LOAN)))
    }

    @Test fun `deleting an account cascades to its linked expense with a closing row`() {
        val clock = FakeClock(today)
        val d0 = Accounts.getLoan(profileWith(), moneyOf(9800.0), 0.059, 48, RateType.FIXED, "L", "P", false, clock) { Ids("a").next() }
        val d1 = Accounts.deleteCascade(d0, d0.accounts[0].id, clock) { Ids("b").next() }
        assertTrue(d1.accounts.isEmpty()); assertTrue(d1.recurringExpenses.isEmpty())
        assertEquals(moneyOf(-9800.0), d1.transactions.last().amount); assertEquals(Accounts.ACCOUNT_CLOSED, d1.transactions.last().payee)
    }
}
