package app.scifin.engine

import app.scifin.model.Money
import app.scifin.model.ProfileData
import kotlinx.datetime.LocalDate

/**
 * §3.5's recurring-expense threshold, as a fact to show rather than a verdict: the two
 * figures always on screen ([scheduled] and the [base]), and which rule flagged them. The
 * sentences are the display layer's; the engine only says when, and against what.
 */
data class ThresholdNote(val scheduled: Money, val base: Money, val incomeOn: Boolean) {
    /** §3.5: at a zero or absent base the note names the figure and takes no ratio. */
    val baseIsEmpty: Boolean get() = base <= 0
}

object Threshold {
    /**
     * Null when nothing is scheduled, when the total is within the threshold, or -- Income off
     * -- when Savings is still `null` (§3.1: there is no figure to take a twelfth of). Income
     * on: more than 1/3 of income received so far this month. Income off: more than 1/12 of
     * Savings, which at Savings ≤ 0 fires on any positive total, as §3.5 says it should.
     */
    fun recurringNote(d: ProfileData, today: LocalDate): ThresholdNote? {
        val scheduled = Recurring.scheduledTotal(d, today)
        if (scheduled <= 0) return null
        if (d.settings.incomeEnabled) {
            val received = Income.receivedThisMonth(d, today)
            if (scheduled <= received / 3) return null
            return ThresholdNote(scheduled, received, incomeOn = true)
        }
        if (d.savings.amount == null) return null
        val savings = d.savingsAmount
        if (scheduled <= savings / 12) return null
        return ThresholdNote(scheduled, savings, incomeOn = false)
    }
}
