package app.scifin.app

import app.scifin.data.SciFinDao
import app.scifin.data.SciFinDatabase
import app.scifin.engine.Clock
import app.scifin.engine.NewMonth
import app.scifin.engine.Recurring
import app.scifin.engine.Reminders
import app.scifin.notify.ReminderRegistration
import app.scifin.model.AppState
import app.scifin.model.ProfileData
import app.scifin.model.Theme
import app.scifin.notify.NotificationScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What "opening the app" means (§3.9, §3.11, §11.5), in one place both hosts call:
 * load the active Profile, run the New Month engine and the auto-deposit catch-up, persist,
 * and make the OS hold exactly the reminder schedule the state now implies. On a fresh
 * install there is no Profile to load and none is invented: §11.1's screen 1 creates the
 * first one from the name and theme the person chooses ([createProfile]).
 */
class AppSession(
    private val db: SciFinDatabase,
    private val scheduler: NotificationScheduler,
    val clock: Clock,
    val newId: () -> String,
) {
    /** The active Profile, caught up and saved; null on a fresh install: nothing exists until screen 1's Continue. */
    suspend fun open(): ProfileData? {
        val dao = db.dao()
        val loaded = activeOrFirst(dao) ?: return null
        val (after, _) = NewMonth.runCatchUp(loaded, clock, newId)
        if (after != loaded) dao.save(after)
        syncNotifications(after)
        return after
    }

    /** The stored active Profile; when that id names none, the first Profile, made active so `app_state` never dangles. */
    private suspend fun activeOrFirst(dao: SciFinDao): ProfileData? {
        val app = dao.appState() ?: return null
        return dao.load(app.activeProfileId) ?: dao.profiles().firstOrNull()?.let { first -> dao.load(first.id)?.also { dao.setActive(first.id) } }
    }

    private suspend fun SciFinDao.setActive(id: String) = upsertAppState((appState() ?: AppState(activeProfileId = id)).copy(activeProfileId = id))

    /**
     * Make a Profile the active one the way opening the app does (§3.9's trigger): its months
     * replayed, its anchors advanced, saved, and its schedule handed to the OS. A switch (§8.3),
     * the fallback after a delete, and a restore (§4.7) all come through here -- a Profile last
     * open in June must not show June's Categories in September.
     */
    suspend fun activate(id: String): ProfileData? {
        val dao = db.dao()
        val loaded = dao.load(id) ?: return null
        val (after, _) = NewMonth.runCatchUp(loaded, clock, newId)
        if (after != loaded) dao.save(after)
        dao.setActive(id)
        syncNotifications(after)
        return after
    }

    /**
     * §11.1 screen 1's Continue: a Profile born with the name typed and the theme chosen, made
     * active, in one save. The first Profile still has steps 2-5 ahead of it and records step 2 to
     * come back to; a later one (§8.3) has only the Currency landing, so its `onboardingComplete`
     * is set here (§12).
     */
    suspend fun createProfile(name: String, theme: Theme, first: Boolean): ProfileData {
        val dao = db.dao()
        val id = newId()
        val d = ProfileData.fresh(id, name.trim(), onboardingComplete = !first)
            .let { it.copy(settings = it.settings.copy(theme = theme, onboardingStep = if (first) 2 else null)) }
        dao.save(d)
        dao.setActive(id)
        syncNotifications(d)
        return d
    }

    /** §11.5 / §4.7: what the OS says right now; re-read on every open rather than remembered. */
    fun notificationPermission() = scheduler.permissionState()

    /** §11.1 step 2: the OS prompt, where the OS has one; the answer is recorded on the Profile (§12). */
    suspend fun requestNotificationPermission() = scheduler.requestPermission()

    /** Guards the two caches below: the store's writes and Android's top-up worker both reach them. */
    private val notify = Mutex()
    private var lastReminders: List<ReminderRegistration>? = null
    private var lastNewMonth: Boolean? = null

    /**
     * Hands the OS only what changed since the last call in this process: on Windows each call is
     * a PowerShell process, and every write calls this. The first call of a process always goes through.
     */
    suspend fun syncNotifications(d: ProfileData) {
        notify.withLock {
            val regs = Reminders.registrations(d, Reminders.localNow(clock))
            if (regs != lastReminders) { scheduler.syncReminders(regs); lastReminders = regs }
            val nm = d.settings.newMonthNotificationEnabled
            if (nm != lastNewMonth) { scheduler.setNewMonthNotification(nm); lastNewMonth = nm }
        }
    }

    /** §4.7's erase: the OS holds nothing once there is no Profile to remind about. */
    suspend fun clearSchedule() {
        notify.withLock {
            scheduler.syncReminders(emptyList()); scheduler.setNewMonthNotification(false)
            lastReminders = emptyList(); lastNewMonth = false
        }
    }

    /**
     * §11.5: after a reboot, and from the daily top-up, re-arm the whole schedule from state as of
     * today: the anchor chain is advanced in memory first (nothing is saved), so a passed occurrence
     * does not filter out every later one. Falls back to the first Profile as [open] does. Read and
     * handed over under [notify], so a write's sync cannot land between the two and be overwritten.
     */
    suspend fun restoreAfterBoot() {
        notify.withLock {
            val stored = activeOrFirst(db.dao()) ?: return
            val d = Recurring.advanceDueDates(stored, clock.today())
            val regs = Reminders.registrations(d, Reminders.localNow(clock))
            scheduler.restoreAfterBoot(regs, d.settings.newMonthNotificationEnabled)
            lastReminders = regs; lastNewMonth = d.settings.newMonthNotificationEnabled
        }
    }
}
