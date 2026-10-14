package app.scifin

import app.scifin.engine.Ledger
import app.scifin.model.moneyOf
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Replaying the prototype's 112 seeded rows through §3.6's sign table must reach the Savings
 * figure the prototype shows, and every derived balance the prototype's `seedFromLedger`
 * computes must fall out the same way. This is the ledger as oracle; the Reports engines get
 * their own parity class in step 6.
 */
class LedgerParityTest {

    @Test
    fun `replaying the seeded ledger reaches the prototype's Savings`() {
        val fromRows = Fixture.transactions.sumOf { Ledger.savingsEffect(it) }
        assertEquals(Fixture.profile.savings.amount, fromRows)
        assertEquals(moneyOf(3067.0), fromRows)
    }

    @Test
    fun `account balances fall out of their own acct rows`() {
        for (a in Fixture.accounts) {
            val sum = Fixture.transactions.filter { it.key == "acct:${a.id}" }.sumOf { it.amount }
            assertEquals(a.balance, sum, "balance of ${a.name}")
        }
    }

    @Test
    fun `goal saved totals fall out of their goal rows`() {
        for (g in Fixture.goals) {
            val sum = Fixture.transactions.filter { it.key == "goal:${g.id}" }.sumOf { it.amount }
            assertEquals(g.saved, sum, "saved of ${g.name}")
        }
    }

    @Test
    fun `category spent is this month's cat rows only`() {
        val ym = Fixture.simDate.toString().substring(0, 7)
        for (c in Fixture.categories) {
            val sum = Fixture.transactions.filter { it.key == "cat:${c.id}" && it.date.toString().startsWith(ym) }.sumOf { it.amount }
            assertEquals(c.spent, sum, "spent of ${c.name}")
        }
    }

    /**
     * Replaces a pair-eligibility "parity" check that compared two sizes over a fixture with no
     * pairs in it -- 0 == 0, unable to fail (audit 2026-09-25). Pair logic is pinned by LedgerTest's
     * own cases; this checks the fixture figure that nothing read.
     */
    @Test
    fun `the trailing spend window agrees with the prototype on the seeded day`() {
        val f = Fixture.figures["trailingSpend"]!!.jsonObject
        val w = app.scifin.engine.Reports.trailingSpendWindow(Fixture.simDate)
        assertEquals(f["start"]!!.jsonPrimitive.content, w.start.toString())
        assertEquals(f["end"]!!.jsonPrimitive.content, w.end.toString())
        assertEquals(f["days"]!!.jsonPrimitive.content.toInt(), w.days)
    }
}
