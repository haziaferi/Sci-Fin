package app.scifin.engine

import app.scifin.model.IncomeCadence
import app.scifin.model.Money
import app.scifin.model.Settings
import kotlin.math.roundToLong

/**
 * §10.2's calculator: what extra hours come to, hours × rate and nothing more. It reads Settings and
 * writes nothing -- no Transaction, no Goal (§2.3). The math stays the same whoever enters it (§10.2).
 */
object TimeWorth {
    /** §10.2's prototype default: a typical month's expected hours, used only for the Salaried pre-fill. */
    const val DEFAULT_EXPECTED_HOURS = 160.0

    /** One paycheck as a month's worth (§3.3's cadences); null with Income off or no amount, so there is no pre-fill. */
    fun monthlyEquivalent(s: Settings): Money? {
        if (!s.incomeEnabled || s.income <= 0) return null
        return when (s.incomeCadence) {
            IncomeCadence.WEEKLY -> (s.income * 52.0 / 12).roundToLong()
            IncomeCadence.BIWEEKLY -> (s.income * 26.0 / 12).roundToLong()
            IncomeCadence.SEMIMONTHLY -> s.income * 2
            IncomeCadence.MONTHLY -> s.income
        }
    }

    /** The Salaried pre-fill: a month's pay over the hours the person expects to work in it. A starting point, never a gate. */
    fun prefillRate(s: Settings, expectedHours: Double): Money? {
        val monthly = monthlyEquivalent(s) ?: return null
        if (expectedHours <= 0) return null
        return (monthly / expectedHours).roundToLong()
    }

    /** What [hours] extra hours a month at [rate] come to, in a month and over a year at the same rate and hours. */
    fun worth(rate: Money, hours: Double): Pair<Money, Money> {
        val monthly = (rate * hours.coerceAtLeast(0.0)).roundToLong()
        return monthly to monthly * 12
    }
}
