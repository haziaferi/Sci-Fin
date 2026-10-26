package app.scifin.app

import app.scifin.i18n.I18n
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
import app.scifin.engine.Backup
import app.scifin.engine.BackupCrypto
import app.scifin.engine.Trash
import app.scifin.model.TrashItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import app.scifin.i18n.tr
import app.scifin.i18n.trf

/** What one run of §4.7's automatic copy did. */
sealed class CopyOutcome {
    /** Off, no folder chosen, or nothing to copy yet. */
    data object NotSetUp : CopyOutcome()
    data object NotDue : CopyOutcome()
    /** The app PIN is on and the copies have no password: nothing is written until one is set. */
    data object Paused : CopyOutcome()
    data class Written(val name: String) : CopyOutcome()
    data class Failed(val reason: String) : CopyOutcome()
}

const val COPY_PREFIX = "sci-fin-copy-"
const val COPIES_KEPT = 14
private const val COPY_NOTICE = "scifin.copy.failed"
private const val TRASH_NOTICE = "scifin.trash.leaving"

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
    /** §4.7's automatic copy's hands on the platform; none in tests that do not copy. */
    val place: CopyPlace = CopyPlace.None,
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
            // the bodies are composed now, in the Profile's language -- also in a background run with no screen (§2.7)
            I18n.current = I18n.resolve(d.settings.language)
            val regs = Reminders.registrations(d, Reminders.localNow(clock))
            if (regs != lastReminders) { scheduler.syncReminders(regs); lastReminders = regs }
            val nm = d.settings.newMonthNotificationEnabled
            if (nm != lastNewMonth) { scheduler.setNewMonthNotification(nm); lastNewMonth = nm }
        }
    }

    // ---- §4.7's automatic copy ----------------------------------------------------------------

    /** One copy at a time: the daily task and the app's own run (or Copy now) can meet. */
    private val copying = Mutex()

    /**
     * Writes a copy when one is due -- or, with [force] (Copy now), whenever the copy is set up. Runs from the
     * daily background task on both platforms and on every open of the app, so a missed day is caught up the
     * next time either runs. While the app PIN is on and the copies have no password, nothing is written
     * ([CopyOutcome.Paused]): a plain file must never be easier to read than the app.
     */
    suspend fun runCopy(force: Boolean = false): CopyOutcome = copying.withLock {
        val dao = db.dao()
        val c = dao.copySchedule() ?: return CopyOutcome.NotSetUp
        val every = CopyEvery.of(c.every)
        val folder = c.folder
        if (folder == null || (every == CopyEvery.OFF && !force)) return CopyOutcome.NotSetUp
        if (dao.appLock() != null && c.sealedPassword == null) return CopyOutcome.Paused
        val today = clock.today()
        val last = c.lastCopy?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (!force && last != null && last.daysUntil(today) < every.days) return CopyOutcome.NotDue
        val app = dao.appState() ?: return CopyOutcome.NotSetUp

        suspend fun fail(reason: String): CopyOutcome {
            val now = dao.copySchedule() ?: c
            if (!now.failureNotified) scheduler.notifyNow(COPY_NOTICE, tr("Automatic copy not written"), reason)
            dao.upsertCopySchedule(now.copy(lastError = reason, failureNotified = true))
            return CopyOutcome.Failed(reason)
        }

        val plain = Backup.encode(Backup.build(dao.profiles().mapNotNull { dao.load(it.id) }, app.activeProfileId, today))
        val text = c.sealedPassword?.let { sealed ->
            val password = place.unseal(sealed) ?: return fail(tr("The copies’ password can’t be read on this device any more. Set it again in Data & backup."))
            BackupCrypto.encrypt(plain, password, c.hint.orEmpty(), today.toString())
        } ?: plain
        val name = "$COPY_PREFIX$today.json"
        val existing = place.list(folder) ?: return fail(tr("The copies’ folder can’t be reached. It may have been moved or removed, or Sci-Fin’s access to it withdrawn."))
        if (name in existing) place.delete(folder, name)          // Copy now twice in a day: the later one stands
        if (!place.write(folder, name, text)) return fail(tr("The copy could not be written to its folder. The folder may be full or read-only."))
        // the oldest beyond the fourteen kept go (owner, 2026-10-20); only this app's own copies are ever touched
        val ours = (place.list(folder) ?: existing + name).filter { it.startsWith(COPY_PREFIX) && it.endsWith(".json") }.sorted()
        ours.dropLast(COPIES_KEPT).forEach { place.delete(folder, it) }
        dao.upsertCopySchedule((dao.copySchedule() ?: c).copy(lastCopy = today.toString(), lastError = null, failureNotified = false))
        CopyOutcome.Written(name)
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
        // the same daily run writes §4.7's automatic copy when one is due; a failure is recorded, never thrown
        runCatching { runCopy() }
        runCatching { tidyTrash() }
    }

    /**
     * The Trash's two dates (owner, 2026-10-20): at 20 days one notice says what will go for good in ten; at 30
     * the item goes. Runs daily with the background task and on every open. Returns how many went.
     */
    suspend fun tidyTrash(): Int {
        val dao = db.dao()
        val today = clock.today()
        var gone = 0
        val toWarn = ArrayList<TrashItem>()
        for (t in dao.trash()) {
            val on = runCatching { LocalDate.parse(t.deletedOn) }.getOrNull() ?: continue
            when {
                Trash.expired(on, today) -> { dao.deleteTrash(t.id); gone++ }
                !t.warned && Trash.warnDue(on, today) -> toWarn += t
            }
        }
        if (toWarn.isNotEmpty()) {
            val names = toWarn.joinToString(", ") { "“${tr(it.name)}”" }
            val body = if (toWarn.size == 1) trf("{0} leaves the Trash for good in 10 days. Restore it from Settings › Trash if you still want it.", names)
                else trf("{0} items leave the Trash for good in 10 days: {1}. Restore any you still want from Settings › Trash.", toWarn.size, names)
            scheduler.notifyNow(TRASH_NOTICE, tr("Leaving the Trash soon"), body)
            toWarn.forEach { dao.upsertTrash(it.copy(warned = true)) }
        }
        return gone
    }
}
