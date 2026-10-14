@file:OptIn(ExperimentalTime::class)

package app.scifin.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import app.scifin.engine.Reminders
import app.scifin.shared.R
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.*

/*
 * ANDROID: WorkManager one-shot delayed work.
 *
 * WHY NOT AN EXACT ALARM. `AlarmManager.setExactAndAllowWhileIdle` is the obvious reach and
 * it is the wrong one. Android 12 put it behind SCHEDULE_EXACT_ALARM, and Google Play
 * restricts USE_EXACT_ALARM to apps whose core function is alarms or timers — an app that
 * reminds you a bill is due in three days does not qualify, and shipping it would be the
 * same class of mistake §7.3 already refused once with READ_SMS: requesting a permission
 * whose policy gate the app cannot honestly pass.
 *
 * It is also unnecessary. §3.11's lead time is a whole number of DAYS counted back from a
 * due date; a reminder that arrives within WorkManager's window on the right morning is
 * exactly the artifact, and "exact to the second" was never the requirement.
 *
 * WHAT WE GET FOR FREE. WorkManager persists its queue in its own database and re-enqueues
 * across reboot, so §13.3's boot recovery costs nothing here and the app needs no
 * BOOT_COMPLETED receiver of its own — androidx.work already declares
 * RECEIVE_BOOT_COMPLETED in its manifest and we inherit it. Fewer permissions in our own
 * manifest is the right outcome, not a shortcut.
 */

private const val CHANNEL_REMINDERS = "scifin.reminders"
private const val CHANNEL_NEW_MONTH = "scifin.newmonth"

/** Carried by every due-date reminder, so a whole-schedule sync can clear them. */
private const val TAG_REMINDER = "scifin.reminder"
private const val WORK_NEW_MONTH = "scifin.newmonth.chain"

private const val KEY_ID = "id"
private const val KEY_TITLE = "title"
private const val KEY_BODY = "body"

/** The New Month ping's notification id; due-date reminders use their expense id's hash. */
private const val NEW_MONTH_ID = 1

/** A holder, not a typealias: an expect class is final and `Context` is abstract. */
actual class PlatformContext(val context: Context)

actual fun notificationScheduler(context: PlatformContext): NotificationScheduler =
    AndroidNotificationScheduler(context.context)

/**
 * Public rather than internal on purpose: [permissionRequester] has to be installed by the
 * host Activity, which owns the ActivityResult launcher, and shared code holding a
 * [NotificationScheduler] cannot reach it. Android-side host code constructs this type
 * directly; everything else goes through [notificationScheduler].
 */
class AndroidNotificationScheduler(
    private val context: Context,
) : NotificationScheduler {

    private val work get() = WorkManager.getInstance(context)

    init {
        ensureChannels()
    }

    override fun permissionState(): NotificationPermission {
        // On 13+ check the runtime permission first: until granted, areNotificationsEnabled()
        // is false too, and a fresh install must read NOT_ASKED.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) return NotificationPermission.NOT_ASKED
        }
        // areNotificationsEnabled() is the honest reading rather than a stored flag: it also
        // catches the user switching notifications off in system Settings later, which is
        // precisely the revocation §12 says must not be trusted from storage. Below Android 13
        // there is no runtime permission at all and this check is the whole answer.
        return if (NotificationManagerCompat.from(context).areNotificationsEnabled()) NotificationPermission.GRANTED else NotificationPermission.DENIED
    }

    /**
     * §11.5 places the request at onboarding step 2, before the tutorial. The dialog itself
     * belongs to the Activity, so this suspends on a launcher the host installs; the seam
     * only reports what the OS decided.
     */
    override suspend fun requestPermission(): NotificationPermission {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return permissionState()
        permissionRequester?.invoke(Manifest.permission.POST_NOTIFICATIONS)
        return permissionState()
    }

    /**
     * Installed by the host Activity, which owns the ActivityResult launcher. Left null in
     * tests and on any surface with no Activity — [requestPermission] then degrades to a
     * plain read, which is the correct behaviour rather than a crash.
     */
    var permissionRequester: (suspend (String) -> Unit)? = null

    override fun syncReminders(registrations: List<ReminderRegistration>) {
        // Declarative: clear the set, then lay down exactly what was asked for. Cancelling by
        // tag is what makes a removed or finished expense's registration go away without the
        // caller having to tell us it went — §3.11's finished expense "schedules nothing", and
        // it achieves that simply by being absent from the list.
        work.cancelAllWorkByTag(TAG_REMINDER)

        val now = Clock.System.now()
        for (r in registrations) {
            val delayMs = r.fireAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() -
                now.toEpochMilliseconds()
            // Registrations are required to be in the future (see ReminderRegistration.fireAt).
            // A past one is dropped rather than fired immediately: the fire time is the guard
            // that stops a reboot's re-sync re-firing what already fired (§3.11).
            if (delayMs <= 0) continue

            val request = OneTimeWorkRequestBuilder<NotifyWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(TAG_REMINDER)
                .setInputData(
                    workDataOf(
                        KEY_ID to r.expenseId,
                        KEY_TITLE to r.title,
                        KEY_BODY to r.body,
                    )
                )
                .build()

            // Keyed on the expense id, so a second sync replaces that expense's registration
            // rather than stacking a duplicate beside it.
            work.enqueueUniqueWork(
                "$TAG_REMINDER.${r.expenseId}",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    override fun setNewMonthNotification(enabled: Boolean) {
        if (!enabled) {
            work.cancelUniqueWork(WORK_NEW_MONTH)
            return
        }
        enqueueNextNewMonth(context)
    }

    override fun restoreAfterBoot(
        reminders: List<ReminderRegistration>,
        newMonthEnabled: Boolean,
    ) {
        // The daily top-up: arms occurrences that became due since the last sync.
        syncReminders(reminders)
        setNewMonthNotification(newMonthEnabled)
    }

    private fun ensureChannels() {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        // Two channels, so a person can silence due-date reminders without losing the New Month
        // ping or the reverse. IMPORTANCE_DEFAULT, never HIGH: §2.3's register rules out
        // anything that reads as an alert, and a full-screen or heads-up treatment for "rent is
        // due in three days" is exactly that.
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS, "Due-date reminders", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "A quiet note before a recurring expense is due." }
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NEW_MONTH, "New month", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "One gentle ping on the 1st of each month." }
        )
    }
}

/**
 * §3.9's notification recurs on the 1st of every calendar month — which a PeriodicWorkRequest
 * cannot express, since periodic work is a fixed interval and months are not one. So each
 * occurrence enqueues the next: a self-renewing chain that survives reboot with the rest of
 * WorkManager's queue and needs no fixed horizon.
 */
internal fun enqueueNextNewMonth(context: Context) {
    val tz = TimeZone.currentSystemDefault()
    val next = Reminders.nextNewMonthFire(Clock.System.now().toLocalDateTime(tz))
    val delayMs = next.toInstant(tz).toEpochMilliseconds() -
        Clock.System.now().toEpochMilliseconds()

    val request = OneTimeWorkRequestBuilder<NewMonthWorker>()
        .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
        .build()

    WorkManager.getInstance(context)
        .enqueueUniqueWork(WORK_NEW_MONTH, ExistingWorkPolicy.REPLACE, request)
}

/** Delivers one due-date reminder. Composes nothing — the text arrived with the request. */
class NotifyWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        post(
            applicationContext,
            CHANNEL_REMINDERS,
            inputData.getString(KEY_ID).orEmpty().hashCode(),
            inputData.getString(KEY_TITLE).orEmpty(),
            inputData.getString(KEY_BODY).orEmpty(),
        )
        return Result.success()
    }
}

/** Delivers the New Month ping, then arms the next one. */
class NewMonthWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        post(applicationContext, CHANNEL_NEW_MONTH, NEW_MONTH_ID, NewMonthCopy.TITLE, NewMonthCopy.BODY)
        enqueueNextNewMonth(applicationContext)
        return Result.success()
    }
}

private fun post(context: Context, channel: String, id: Int, title: String, body: String) {
    // If the permission is gone by delivery time, drop it silently. §4.7 already tells the
    // person in Settings that nothing can fire; re-raising it here would be a second, worse
    // channel for the same fact.
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    val n: Notification = NotificationCompat.Builder(context, channel)
        .setContentTitle(title)
        .setContentText(body)
        .setSmallIcon(R.drawable.ic_stat_scifin)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(openApp(context))
        .setAutoCancel(true)
        .build()
    try {
        NotificationManagerCompat.from(context).notify(id, n)
    } catch (_: SecurityException) {
        // Revoked between the check above and the post. Nothing to do and nothing to say.
    }
}

/** A tap opens the app at its launcher entry. One request code: the intent carries no extras. */
private fun openApp(context: Context): PendingIntent? =
    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
        PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
