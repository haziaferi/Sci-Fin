package app.scifin

import app.scifin.engine.AccountEditing
import app.scifin.engine.Accounts
import app.scifin.engine.NewMonth
import app.scifin.engine.Recurring
import app.scifin.model.Account
import app.scifin.model.AccountType
import app.scifin.model.Cadence
import app.scifin.model.DateMode
import app.scifin.model.RateType
import app.scifin.model.RecurringExpense
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The recurring-engine defects the 2026-09-25 audit found, one test per rule. */
class RecurringAuditTest {

    private fun monthly(
        id: String, amount: Double, day: Int, due: LocalDate, first: LocalDate = LocalDate(2026, 1, day),
        linked: String? = null, endDate: LocalDate? = null, swept: String? = null,
    ) = RecurringExpense(id, "p-1", id, null, moneyOf(amount), Cadence.MONTHLY, firstDueDate = first, dueDate = due,
        dueDateAnchorDay = day, dateMode = DateMode.FIXED_DATE, lastSweptMonth = swept, linkedAccountId = linked, endDate = endDate)

    private fun world(vararg e: RecurringExpense, accounts: List<Account> = emptyList(), reset: String) =
        profileWith(savings = 5000.0, categories = listOf(category("c1").copy(lastResetMonth = reset))).copy(recurringExpenses = e.toList(), accounts = accounts)

    private fun loan(id: String, balance: Double, linked: String, rateType: RateType) =
        Account(id, "p-1", "Car Loan", null, AccountType.LOAN, balance = moneyOf(balance), linkedRecurringExpenseId = linked, rateType = rateType, interestRate = 0.05)

    // ---- §3.11: an edit is a new plan ----------------------------------------------------------

    private fun semi(due: LocalDate) = RecurringExpense("phone", "p-1", "phone", null, moneyOf(20.0), Cadence.SEMIMONTHLY,
        firstDueDate = LocalDate(2026, 1, 15), dueDate = due, dueDate2 = LocalDate(2026, 1, 1))

    @Test fun `the semimonthly fields show the two days in calendar order, whichever is next`() {
        // the live dueDate is the 15th here; the 1st has passed, so its next date is in October
        assertEquals(LocalDate(2026, 10, 1) to LocalDate(2026, 9, 15), Recurring.semimonthlyFields(semi(LocalDate(2026, 9, 15))))
        // once the 15th passes, the first field still shows the 1st -- it had shown the live dueDate
        assertEquals(LocalDate(2026, 10, 1) to LocalDate(2026, 10, 15), Recurring.semimonthlyFields(semi(LocalDate(2026, 10, 1))))
    }

    @Test fun `the fields write back earliest first, keeping both days`() {
        val e = semi(LocalDate(2026, 10, 1))
        val (a, b) = Recurring.semimonthlyFields(e)
        val back = Recurring.withSemimonthlyFields(e, b, a)                 // order on screen does not matter
        assertEquals(LocalDate(2026, 10, 1), back.dueDate)
        assertEquals(LocalDate(2026, 10, 15), back.dueDate2)
        assertEquals(setOf(1, 15), setOf(back.firstDueDate.day, back.dueDate2!!.day))
    }

    @Test fun `moving only one of the two days keeps the other`() {
        // the phone bill is due on the 1st and the 15th; the person moves the 15th to the 20th
        val today = LocalDate(2026, 9, 20)
        val e = semi(LocalDate(2026, 10, 1)).copy(lastSweptMonth = "2026-09")
        val (first, _) = Recurring.semimonthlyFields(e)
        val (d, _) = Recurring.edit(world(e, reset = "2026-09"), Recurring.withSemimonthlyFields(e, first, LocalDate(2026, 10, 20)), today)
        val out = d.recurringExpenses.single()
        assertEquals(listOf(LocalDate(2026, 11, 1), LocalDate(2026, 11, 20)), Recurring.occurrenceDatesInMonth(out, 2026, 11))
    }

    @Test fun `editing a fixed-date expense from the 1st to the 15th moves its anchor and its sweeps`() {
        val today = LocalDate(2026, 9, 10)
        val e = monthly("rent", 900.0, 1, due = LocalDate(2026, 10, 1), first = LocalDate(2026, 1, 1), swept = "2026-09")
        val (d, _) = Recurring.edit(world(e, reset = "2026-09"), e.copy(dueDate = LocalDate(2026, 10, 15)), today)
        val out = d.recurringExpenses.single()
        assertEquals(15, out.dueDateAnchorDay)
        assertEquals(listOf(LocalDate(2026, 11, 15)), Recurring.occurrenceDatesInMonth(out, 2026, 11))
        assertEquals(LocalDate(2026, 11, 15), Recurring.nextOccurrenceAfter(out, out.dueDate))
    }

    @Test fun `switching to interval mode sets the interval and drops the anchor`() {
        val today = LocalDate(2026, 9, 10)
        val e = monthly("sub", 10.0, 1, due = LocalDate(2026, 10, 1))
        val (d, _) = Recurring.edit(world(e, reset = "2026-09"), e.copy(dateMode = DateMode.INTERVAL), today)
        val out = d.recurringExpenses.single()
        assertEquals(30, out.intervalDays); assertNull(out.dueDateAnchorDay)
        assertEquals(LocalDate(2026, 10, 31), Recurring.nextOccurrenceAfter(out, LocalDate(2026, 10, 1)))
    }

    @Test fun `a semimonthly edit of only the second date keeps the first`() {
        val today = LocalDate(2026, 9, 10)
        val e = RecurringExpense("s", "p-1", "s", null, moneyOf(300.0), Cadence.SEMIMONTHLY, firstDueDate = LocalDate(2026, 1, 15),
            dueDate = LocalDate(2026, 9, 15), dueDate2 = LocalDate(2026, 9, 30))
        val (d, _) = Recurring.edit(world(e, reset = "2026-09"), e.copy(dueDate2 = LocalDate(2026, 9, 28)), today)
        assertEquals(LocalDate(2026, 1, 15), d.recurringExpenses.single().firstDueDate)
        assertEquals(listOf(LocalDate(2026, 10, 15), LocalDate(2026, 10, 28)), Recurring.occurrenceDatesInMonth(d.recurringExpenses.single(), 2026, 10))
    }

    // ---- §7.2: linked both ways, one way at a time ------------------------------------------

    @Test fun `re-linking an account releases the old expense, so deleting it leaves the account alone`() {
        val clock = FakeClock(LocalDate(2026, 9, 10)); val ids = Ids()
        val x = monthly("x", 200.0, 5, due = LocalDate(2026, 10, 5), linked = "loan")
        val y = monthly("y", 250.0, 20, due = LocalDate(2026, 9, 20))
        val a = loan("loan", 4000.0, "x", RateType.FIXED)
        var d = AccountEditing.save(world(x, y, accounts = listOf(a), reset = "2026-09"), a.copy(linkedRecurringExpenseId = "y"), clock) { ids.next() }
        assertNull(d.recurringExpenses.first { it.id == "x" }.linkedAccountId)
        assertEquals("loan", d.recurringExpenses.first { it.id == "y" }.linkedAccountId)
        d = Recurring.delete(d, "x", reverse = false, clock) { ids.next() }
        assertEquals(1, d.accounts.size)
    }

    // ---- §3.11 + §9.1: the end of a term -----------------------------------------------------

    @Test fun `an open between the last occurrence and the next 1st still ends it on that 1st, with its notice and closing row`() {
        val ids = Ids()
        val e = monthly("pay", 260.0, 15, due = LocalDate(2026, 10, 15), linked = "loan", endDate = LocalDate(2026, 10, 15), swept = "2026-10")
        val w = world(e, accounts = listOf(loan("loan", 120.0, "pay", RateType.FIXED)), reset = "2026-10")
        val (mid, none) = NewMonth.runCatchUp(w, FakeClock(LocalDate(2026, 10, 20))) { ids.next() }
        assertNull(none); assertNull(mid.recurringExpenses.single().finishedAt)
        val (d, s) = NewMonth.runCatchUp(mid, FakeClock(LocalDate(2026, 11, 2))) { ids.next() }
        assertEquals(1, s!!.endedNow.size)
        assertEquals(LocalDate(2026, 11, 1), d.recurringExpenses.single().finishedAt)
        assertEquals(1, d.transactions.count { it.payee == "Loan finished" })
    }

    @Test fun `an absence that spans endDate sweeps every real occurrence before finishing`() {
        val ids = Ids()
        val e = monthly("gym", 40.0, 15, due = LocalDate(2026, 9, 15), endDate = LocalDate(2026, 10, 15), swept = "2026-08")
        val (d, s) = NewMonth.runCatchUp(world(e, reset = "2026-08"), FakeClock(LocalDate(2026, 11, 20))) { ids.next() }
        assertEquals(listOf(LocalDate(2026, 9, 15), LocalDate(2026, 10, 15)), d.transactions.filter { it.key == "rec:gym" }.map { it.date })
        assertEquals(moneyOf(5000.0 - 80.0), d.savings.amount)
        assertEquals(1, s!!.endedNow.size)
        assertNotNull(d.recurringExpenses.single().finishedAt)
    }

    @Test fun `an interest-only loan keeps its balance at the end of its term, as its notice says`() {
        val ids = Ids()
        val e = monthly("pay", 260.0, 15, due = LocalDate(2026, 8, 15), linked = "loan", endDate = LocalDate(2026, 8, 15), swept = "2026-08")
        val w = world(e, accounts = listOf(loan("loan", 5000.0, "pay", RateType.INTEREST_ONLY)), reset = "2026-08")
        val (d, s) = NewMonth.runCatchUp(w, FakeClock(LocalDate(2026, 9, 1))) { ids.next() }
        assertTrue(s!!.endedNow.single().note.contains("principal is still outstanding"))
        assertEquals(moneyOf(5000.0), d.accounts.single().balance)
        assertEquals(0, d.transactions.count { it.payee == "Loan finished" })
    }

    // ---- §3.11: a confirmed reconcile on a Loan moves the tracked debt too ----------------------

    @Test fun `accepting a reconcile on a loan payment moves the loan balance by the same amount, dated to the occurrence`() {
        val clock = FakeClock(LocalDate(2026, 9, 10)); val ids = Ids()
        val e = monthly("pay", 260.0, 20, due = LocalDate(2026, 9, 20), linked = "loan", swept = "2026-09")
        val w = world(e, accounts = listOf(loan("loan", 4000.0, "pay", RateType.VARIABLE)), reset = "2026-09")
        val (edited, r) = Recurring.edit(w, e.copy(amount = moneyOf(300.0)), clock.today())
        val d = Recurring.applyReconcile(edited, r!!, clock) { ids.next() }
        assertEquals(moneyOf(3960.0), d.accounts.single().balance)
        val row = d.transactions.single { it.key == "acct:loan" }
        assertEquals(moneyOf(-40.0), row.amount); assertEquals(LocalDate(2026, 9, 20), row.date)
    }

    // ---- §7.2: the reversal runs in the same flow from the Account's side ------------------------

    @Test fun `deleting an account offers back its payment's un-elapsed sweep`() {
        val clock = FakeClock(LocalDate(2026, 9, 10)); val ids = Ids()
        var w = world(accounts = listOf(loan("loan", 4000.0, "pay", RateType.FIXED)), reset = "2026-09")
        w = Recurring.create(w, monthly("pay", 260.0, 20, due = LocalDate(2026, 9, 20), first = LocalDate(2026, 9, 20), linked = "loan"), clock) { ids.next() }
        assertEquals(moneyOf(5000.0 - 260.0), w.savings.amount)
        val d = Accounts.deleteCascade(w, "loan", clock, reverse = true) { ids.next() }
        assertTrue(d.accounts.isEmpty()); assertTrue(d.recurringExpenses.isEmpty())
        assertEquals(moneyOf(5000.0), d.savings.amount)
    }

    @Test fun `re-typing a loan to credit takes its payment with it, the account stays`() {
        val clock = FakeClock(LocalDate(2026, 9, 10)); val ids = Ids()
        val a = loan("loan", 4000.0, "pay", RateType.FIXED)
        var w = world(accounts = listOf(a), reset = "2026-09")
        w = Recurring.create(w, monthly("pay", 260.0, 20, due = LocalDate(2026, 9, 20), first = LocalDate(2026, 9, 20), linked = "loan"), clock) { ids.next() }
        val draft = Accounts.retype(a, AccountType.CREDIT)
        assertTrue(Accounts.retypeNeedsCascade(w, draft))
        val d = Accounts.retypeToCreditCascade(w, draft, reverse = true, clock) { ids.next() }
        assertTrue(d.recurringExpenses.isEmpty())
        val out = d.accounts.single()
        assertEquals(AccountType.CREDIT, out.type); assertNull(out.linkedRecurringExpenseId)
        assertEquals(moneyOf(5000.0), d.savings.amount)
    }
}
