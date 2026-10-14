@file:OptIn(ExperimentalTime::class)

package app.scifin

import app.scifin.engine.Clock
import app.scifin.model.Category
import app.scifin.model.Goal
import app.scifin.model.ProfileData
import app.scifin.model.Savings
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import app.scifin.notify.NotificationPermission
import app.scifin.notify.NotificationScheduler
import app.scifin.notify.ReminderRegistration

/** A clock the tests move by hand. `now` starts at the given day's 10:00 UTC. */
class FakeClock(day: LocalDate) : Clock {
    var today: LocalDate = day
    var now: Instant = Instant.parse("${day}T10:00:00Z")
    override fun today() = today
    override fun now() = now
    fun advance(by: Duration) { now += by }
    fun setDay(day: LocalDate) { today = day; now = Instant.parse("${day}T10:00:00Z") }
}

/** Monotonic ids, readable in a failure message. */
class Ids(private val prefix: String = "t") {
    private var n = 0
    fun next(): String = "$prefix-${++n}"
}

fun profileWith(
    savings: Double? = 1000.0,
    goals: List<Goal> = emptyList(),
    categories: List<Category> = emptyList(),
): ProfileData {
    val id = "p-1"
    return ProfileData.fresh(id, "Mine").copy(
        savings = Savings(id, savings?.let { moneyOf(it) }),
        goals = goals.map { it.copy(profileId = id) },
        categories = categories.map { it.copy(profileId = id) },
    )
}

fun goal(id: String, name: String = id, saved: Double = 0.0, target: Double? = null) =
    Goal(id, "p-1", name, saved = moneyOf(saved), target = target?.let { moneyOf(it) })

fun category(id: String, name: String = id, planned: Double = 100.0, spent: Double = 0.0) =
    Category(id, "p-1", name, planned = moneyOf(planned), spent = moneyOf(spent))

/** A scheduler that records what the app asked of it and delivers nothing. */
open class RecordingScheduler : NotificationScheduler {
    var reminders: List<ReminderRegistration> = emptyList()
    var newMonth: Boolean? = null
    var syncs = 0
    open override fun permissionState() = NotificationPermission.GRANTED
    open override suspend fun requestPermission() = NotificationPermission.GRANTED
    override fun syncReminders(registrations: List<ReminderRegistration>) { reminders = registrations; syncs++ }
    override fun setNewMonthNotification(enabled: Boolean) { newMonth = enabled }
    override fun restoreAfterBoot(reminders: List<ReminderRegistration>, newMonthEnabled: Boolean) { this.reminders = reminders; newMonth = newMonthEnabled }
}
