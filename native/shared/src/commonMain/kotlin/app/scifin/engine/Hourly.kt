package app.scifin.engine

import app.scifin.model.IncomeType
import app.scifin.model.Money
import app.scifin.model.ProfileData
import app.scifin.model.Settings
import app.scifin.model.Shift
import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong

/*
 * §3.10's hourly income (owner, 2026-10-26). Hours are logged whenever, as shifts that move nothing; income
 * lands only on payday, when the person confirms what actually arrived. A payday covers every shift dated on
 * or before it. The estimate is hours × rate "before deductions" until a payday has been confirmed, then
 * that figure scaled by what arrived against what was logged, over the last three paydays -- the person is
 * never asked for a tax rate, which for variable hours is not one stable figure anyway.
 */

/** The payday the prompt is about: its date and the shifts it covers. */
data class Payday(val date: LocalDate, val shifts: List<Shift>) {
    val hours: Double get() = shifts.sumOf { it.hours }
    val gross: Money get() = Hourly.gross(shifts)
}

object Hourly {
    /** Paydays averaged for the learned estimate (owner, 2026-10-26). */
    const val ESTIMATE_PAYDAYS = 3
    /** The payee of a confirmed hourly payday, so the log reads as what it was. */
    const val PAYDAY_PAYEE = "Payday"

    fun isHourly(s: Settings): Boolean = s.incomeEnabled && s.incomeType == IncomeType.HOURLY

    /** Income is not recorded by itself: hourly, or salaried without auto-deposit -- the paths payday notifies. */
    fun manualPayday(s: Settings): Boolean = s.incomeEnabled && (s.incomeType == IncomeType.HOURLY || !s.incomeAutoDeposit)

    fun gross(shifts: List<Shift>): Money = shifts.sumOf { (it.hours * it.rate).roundToLong() }

    /** Shifts not yet paid, oldest first. */
    fun pending(d: ProfileData): List<Shift> = d.shifts.sortedWith(compareBy({ it.date }, { it.id }))

    /**
     * The oldest payday that has passed and not been answered, or null. Paydays are §3.3's anchor dates; the first
     * one counted is the first on or after Income was turned on, so turning it on never conjures a payday behind it.
     */
    fun duePayday(d: ProfileData, today: LocalDate): Payday? {
        val s = d.settings
        if (!isHourly(s)) return null
        val from = s.lastPaydayHandled?.plusDays(1) ?: s.incomeEnabledDate ?: return null
        val day = Income.nextOccurrence(s, from) ?: return null
        if (day > today) return null
        return Payday(day, pending(d).filter { it.date <= day })
    }

    /** The next payday ahead of today, for the shift list's heading and the notification. */
    fun nextPayday(s: Settings, today: LocalDate): LocalDate? = Income.nextOccurrence(s, today)

    /**
     * What arrived over what was logged, averaged over the last [ESTIMATE_PAYDAYS] confirmed hourly paydays; null
     * before the first, when the estimate is the plain before-deductions figure.
     */
    fun learnedRatio(d: ProfileData): Double? {
        val paid = d.transactions
            .filter { it.isIncome && (it.hoursWorked ?: 0.0) > 0 && (it.rate ?: 0) > 0 && it.amount > 0 }
            .sortedWith(compareByDescending<app.scifin.model.Transaction> { it.date }.thenByDescending { it.loggedAt })
            .take(ESTIMATE_PAYDAYS)
        if (paid.isEmpty()) return null
        return paid.map { it.amount / (it.hoursWorked!! * it.rate!!) }.average()
    }

    /** The estimate for [shifts], and whether it is still the before-deductions figure. */
    fun estimate(d: ProfileData, shifts: List<Shift>): Pair<Money, Boolean> {
        val g = gross(shifts)
        val r = learnedRatio(d) ?: return g to true
        return (g * r).roundToLong() to false
    }

    fun logShift(d: ProfileData, shift: Shift): ProfileData = d.copy(shifts = d.shifts + shift.copy(profileId = d.profileId))

    fun editShift(d: ProfileData, shift: Shift): ProfileData = d.copy(shifts = d.shifts.map { if (it.id == shift.id) shift.copy(profileId = d.profileId) else it })

    fun removeShift(d: ProfileData, id: String): ProfileData = d.copy(shifts = d.shifts.filterNot { it.id == id })

    /**
     * The payday prompt's Confirm: what arrived lands in Savings as income on the payday, carrying the hours and the
     * rate they were logged at (§3.10's metadata, which the learned estimate reads back), and the shifts it paid go.
     */
    fun confirmPayday(d: ProfileData, payday: Payday, arrived: Money, clock: Clock, txId: String): ProfileData {
        require(arrived > 0) { "a confirmed payday has an amount" }
        val hours = payday.hours
        val rate = if (hours > 0) (payday.gross / hours).roundToLong() else null
        val moved = Ledger.logAndMove(
            d, Movement(arrived, TransactionKey.SavingsKey, "Savings", PAYDAY_PAYEE, isIncome = true, date = payday.date,
                hoursWorked = hours.takeIf { it > 0 }, rate = rate), clock, txId,
        ).first
        return answered(moved, payday, paid = true)
    }

    /**
     * The prompt's other answer: nothing arrived this payday. With no hours logged that closes it; with hours logged
     * they wait for the next payday, which covers every shift dated on or before it.
     */
    fun nothingThisTime(d: ProfileData, payday: Payday): ProfileData = answered(d, payday, paid = false)

    private fun answered(d: ProfileData, payday: Payday, paid: Boolean): ProfileData {
        val ids = payday.shifts.map { it.id }.toSet()
        return d.copy(
            shifts = if (paid) d.shifts.filterNot { it.id in ids } else d.shifts,
            settings = d.settings.copy(lastPaydayHandled = payday.date),
        )
    }

    /**
     * Leaving hourly with shifts still logged (owner, 2026-10-26): either what arrived for them is confirmed now,
     * dated today, or they are discarded. Either way nothing is left pending under a type that no longer uses it.
     */
    fun settleOnSwitch(d: ProfileData, arrived: Money?, clock: Clock, txId: String): ProfileData {
        val shifts = pending(d)
        if (shifts.isEmpty()) return d
        if (arrived == null || arrived <= 0) return d.copy(shifts = emptyList())
        return confirmPayday(d, Payday(clock.today(), shifts), arrived, clock, txId)
    }

    /**
     * §3.3's type switch. Hourly has no auto-deposit -- what arrives varies -- and starts counting paydays from today,
     * so none behind it is asked about; back to salaried keeps §3.3's own rules. Shifts must be settled first.
     */
    fun setType(d: ProfileData, type: IncomeType, today: LocalDate): ProfileData {
        val s = d.settings
        if (s.incomeType == type) return d
        require(d.shifts.isEmpty()) { "shifts are settled before the type changes" }
        return d.copy(settings = s.copy(
            incomeType = type,
            incomeAutoDeposit = false,
            lastIncomeDepositDate = null,
            lastPaydayHandled = if (type == IncomeType.HOURLY) today.minusDays(1) else null,
        ))
    }

    /**
     * Salaried without auto-deposit (owner, 2026-10-26): the payday that has passed since the last one handled with
     * no income logged on or after it, so opening the app on it -- or the day after -- brings up the Income entry once. Null otherwise.
     */
    fun salariedPaydayToLog(d: ProfileData, today: LocalDate): LocalDate? {
        val s = d.settings
        if (!s.incomeEnabled || s.incomeType != IncomeType.SALARIED || s.incomeAutoDeposit) return null
        val from = s.lastPaydayHandled?.plusDays(1) ?: s.incomeEnabledDate ?: return null
        var day = Income.nextOccurrence(s, from) ?: return null
        if (day > today) return null
        // the most recent passed payday: one prompt, however many were missed
        var guard = 0
        while (guard++ < 400) {
            val next = Income.nextOccurrence(s, day.plusDays(1)) ?: break
            if (next > today) break
            day = next
        }
        // as tapping the payday notification would: on the payday or the day after; older misses are the note's (§3.3)
        if (day.daysTo(today) > 1) return null
        val logged = s.lastIncomeLoggedDate
        return if (logged != null && logged >= day) null else day
    }
}
