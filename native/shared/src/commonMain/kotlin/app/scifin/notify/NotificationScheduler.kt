package app.scifin.notify

import app.scifin.i18n.tr
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/*
 * §1's platform seam: one declarative scheduling API, two native implementations. Nothing
 * here decides whether a reminder is owed (§3.11 lives in engine/Reminders.kt);
 * implementations make the OS match the list they are handed, and must fire with no process
 * of ours running.
 */

/**
 * One due-date reminder, fully resolved. Every field is already decided by shared code;
 * the platform layer only delivers it.
 *
 * @param expenseId  RecurringExpense.id — also the registration's stable key, so a re-sync
 *                   replaces rather than duplicates.
 * @param body       Composed in shared code, so no process has to be alive to build the
 *                   text at delivery time. This is what makes Windows' scheduled-toast
 *                   route viable at all.
 * @param dueDate    The occurrence this reminder is for.
 * @param fireAt     Local wall-clock instant to deliver at. MUST be in the future: the
 *                   caller drops any whose fire time has passed, and that instant is the
 *                   whole once-per-occurrence guard -- a reboot's re-registration cannot
 *                   re-fire what already fired (§3.11).
 */
data class ReminderRegistration(
    val expenseId: String,
    val title: String,
    val body: String,
    val dueDate: LocalDate,
    val fireAt: LocalDateTime,
)

/**
 * What the OS currently says, re-read rather than remembered.
 *
 * §12: `notificationPermissionGranted` "is re-read from the OS on app open rather than
 * trusted from storage, since the permission can be revoked at any time." §4.7 surfaces a
 * negative answer as a plain line beneath the Notifications toggle — a fact, not a warning,
 * with no prompt to go and change it — because "a toggle that displays as on while nothing
 * can ever fire is the app misreporting its own state."
 */
enum class NotificationPermission {
    /** Notifications will be delivered. */
    GRANTED,

    /** The OS will not deliver them. §4.7's line applies, however this was arrived at. */
    DENIED,

    /**
     * Android 13+ only: the runtime prompt has not been shown yet. §11.5 requests it at
     * onboarding step 2. On Windows this value never occurs — see [requestPermission].
     */
    NOT_ASKED,
}

/*
 * An interface plus one expect factory, not an expect class: the platforms need different
 * constructor inputs (Context vs executable path).
 */

/** What each platform needs to build a scheduler. Filled in per target. */
expect class PlatformContext

/** The one construction point. */
expect fun notificationScheduler(context: PlatformContext): NotificationScheduler

interface NotificationScheduler {

    /**
     * The OS's current answer. Cheap enough to call on every app open, which is what §12
     * asks for.
     */
    fun permissionState(): NotificationPermission

    /**
     * §11.5: "Step 2 fires only where the platform has a runtime permission prompt."
     * Android does. **Windows 10/11 does not** — notifications are enabled per-app by
     * default there and disabled from system Settings, with no runtime dialog to request —
     * so the Windows actual performs no prompt and simply returns [permissionState].
     * Onboarding skips the step entirely on that platform rather than rendering an in-app
     * stand-in for a dialog that does not exist.
     */
    suspend fun requestPermission(): NotificationPermission

    /**
     * Make the OS's registered due-date reminders exactly [registrations] — no more, no
     * fewer. Anything previously registered and absent here is cancelled.
     *
     * Called from every path that can change a due date, a reminder setting, or the expense
     * list itself, and again on boot.
     */
    fun syncReminders(registrations: List<ReminderRegistration>)

    /**
     * §3.9's New Month notification. Default on (§4.7) — "the one notification toggle
     * default-on rather than off, since it carries no real content and nothing to consent
     * away from beyond a single gentle ping."
     *
     * Fires on the 1st of every calendar month indefinitely. Android re-arms one month at a
     * time; Windows arms twelve and the daily task tops them up.
     */
    fun setNewMonthNotification(enabled: Boolean)

    /**
     * §13.3's boot recovery. Called at boot/logon and by each platform's daily top-up.
     */
    fun restoreAfterBoot(reminders: List<ReminderRegistration>, newMonthEnabled: Boolean)

    /**
     * One notice, shown now; a later one with the same [key] replaces it. §4.7's failed automatic copy is its
     * one user, once per run of failures. Hosts without notifications show nothing.
     */
    fun notifyNow(key: String, title: String, body: String) {}
}

/** Fixed by §3.9. Held here so neither platform can drift from the other's wording. */
object NewMonthCopy {
    val TITLE: String get() = tr("New Month")
    val BODY: String get() = tr("Fresh start — ready when you are.")
}
