package app.scifin

import app.scifin.engine.IncomeExpensePoint
import app.scifin.model.moneyOf
import app.scifin.ui.ChartGeometry
import app.scifin.ui.chartGeometry
import app.scifin.ui.screens.importSummary
import app.scifin.ui.screens.incomeHeadline
import app.scifin.ui.screens.ledgerRowNote
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.scifin.ui.screenScale

/** The 2026-09-15 follow-up list, decided with the owner the same day: each drawing or wording rule pinned where it is computed. */
class FollowUpTest {
    @Test fun `a sparkline places six points across the width, marks the last one, and draws a zero line only where the values cross it`() {
        val g: ChartGeometry = chartGeometry(listOf(30L, 40L, 50L, 60L, 70L, 80L), width = 500f, height = 64f)
        assertEquals(6, g.points.size)
        assertEquals(0f, g.points.first().x); assertEquals(500f, g.points.last().x)
        assertEquals(g.points.last(), g.endpoint)
        assertNull(g.zeroY, "all positive: no zero line")
        val crossing = chartGeometry(listOf(20L, 10L, -10L), width = 200f, height = 60f)
        assertTrue(crossing.zeroY!! in 0f..60f)
        assertTrue(crossing.points[1].y < crossing.zeroY && crossing.points[2].y > crossing.zeroY, "positive above the line, negative below")
    }

    @Test fun `the Income report leads with this month's two figures`() {
        val pts = listOf(IncomeExpensePoint(LocalDate(2026, 8, 1), moneyOf(20.0), moneyOf(100.0)), IncomeExpensePoint(LocalDate(2026, 9, 1), moneyOf(2400.0), moneyOf(2774.0)))
        assertEquals("€2,400 in · €2,774 out", incomeHeadline(pts, "EUR"))
    }

    @Test fun `a confirmed import says what landed`() {
        assertEquals("12 entries added — €648 out, €240 in.", importSummary(12, moneyOf(648.0), moneyOf(240.0), "EUR"))
        // exact since 2026-09-25 (the owner's decision): cents shown, .00 dropped
        assertEquals("1 entry added — €58.20 out.", importSummary(1, moneyOf(58.2), 0L, "EUR"))
        assertEquals("1 entry added — €58 out.", importSummary(1, moneyOf(58.0), 0L, "EUR"))
    }

    @Test fun `a ledger row dated after today is scheduled, one dated today or before is not`() {
        val today = LocalDate(2026, 9, 15)
        assertEquals("scheduled", ledgerRowNote(LocalDate(2026, 9, 28), today))
        assertNull(ledgerRowNote(today, today)); assertNull(ledgerRowNote(LocalDate(2026, 9, 1), today))
    }

    @Test fun `the onboarding sheet scales with the width over 360dp, as its comment and the spec say`() {
        // the sheet follows §2.5's phone rule since 2026-09-25: grows past 360dp, floored at 0.85
        assertEquals(1f, screenScale(360f)); assertEquals(412f / 360f, screenScale(412f))
        assertEquals(320f / 360f, screenScale(320f))
    }
}
