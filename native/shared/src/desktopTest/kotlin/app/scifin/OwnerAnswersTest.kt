package app.scifin

import app.scifin.engine.GoalDraft
import app.scifin.engine.GoalEditing
import app.scifin.model.moneyOf
import app.scifin.ui.components.progressCaption
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The owner's answers to the cleanup pass's open questions (2026-10-10). */
class OwnerAnswersTest {

    @Test fun `a Goal saved with an empty target is a running tally, not reached from the start`() {
        val after = GoalEditing.save(profileWith(), GoalDraft(name = "Trip", target = null), FakeClock(LocalDate(2026, 10, 10)), Ids()::next)
        val g = after.goals.single()
        assertNull(g.target); assertNull(g.mode); assertNull(g.targetDate); assertNull(g.monthly)
    }

    @Test fun `spending exactly the plan reads As planned, and an unplanned empty category keeps its figure`() {
        assertEquals("As planned", progressCaption(moneyOf(200.0), moneyOf(200.0), "plan", "EUR"))
        assertEquals("€0 left of plan", progressCaption(0, 0, "plan", "EUR"))
    }
}
