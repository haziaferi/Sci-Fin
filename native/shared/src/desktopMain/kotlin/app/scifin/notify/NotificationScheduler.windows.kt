@file:OptIn(ExperimentalTime::class)

package app.scifin.notify

import app.scifin.engine.NOTIFY_HOUR
import app.scifin.engine.Reminders
import kotlinx.datetime.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * WINDOWS 10/11: a WinRT ScheduledToastNotification per registration, a Task Scheduler entry
 * that tops the queue back up, and a startup entry for boot recovery.
 *
 * §1 names exactly this combination as "work no stack choice removes", and the reason it is
 * three pieces rather than one is worth stating.
 *
 * THE TOAST IS THE MECHANISM. `ScheduledToastNotification` hands the OS a toast and a
 * delivery time; Windows stores it and shows it at that time with NO process of ours
 * running, and it survives a reboot on its own. That is what makes §3.11's requirement
 * satisfiable at all — "a reminder that only appears once you have already opened the app is
 * not a reminder." It works here specifically because §3.9's copy is fixed and each
 * reminder's body is composed in shared code before registration, so nothing has to be alive
 * to build the text.
 *
 * THE TASK IS THE HORIZON. Scheduled toasts have to be registered before they can fire, and
 * §3.9's New Month ping recurs indefinitely. So a daily Task Scheduler entry runs the app in
 * a headless top-up mode that re-registers the next slice of the schedule. Without it the app
 * would only ever be armed as far ahead as its last launch.
 *
 * THE STARTUP ENTRY IS THE RECOVERY. Same top-up, once per logon (§13.3's boot recovery).
 *
 * NO RESIDENT SERVICE. A local-only budgeting app has no business running all the time, and
 * the toast queue means it does not have to.
 *
 * ON CALLING WinRT FROM THE JVM. Compose Multiplatform's desktop target is a JVM, which
 * cannot call WinRT directly. Rather than take on a JNI shim for four API calls, this drives
 * the same WinRT types through PowerShell: the process registers the scheduled toast with the
 * OS and exits, and the registration outlives it.
 */

/**
 * Windows addresses toasts by AppUserModelID. A packaged (MSIX) build gets one from its
 * manifest; an unpackaged Win32 build must have a Start Menu shortcut carrying
 * System.AppUserModel.ID with this string, or the OS accepts every registration and shows
 * none of them — no error, no history entry (a per-user
 * `HKCU\Software\Classes\AppUserModelId` entry or a process-level AUMID is not enough; only
 * the shortcut works). jpackage's MSI writes a shortcut without the property, so the app
 * installs its own on first launch (`installAppRegistration`).
 */
private const val AUMID = "app.scifin.SciFin"

/** Under %APPDATA%. */
private const val SHORTCUT = """Microsoft\Windows\Start Menu\Programs\Sci-Fin.lnk"""

private const val TASK_FOLDER = "Sci-Fin"
private const val TASK_NAME = "TopUpNotifications"
private const val STARTUP_TASK_NAME = "TopUpNotificationsAtLogon"
private const val TOAST_GROUP = "scifin"

/** Half an hour before [NOTIFY_HOUR], so the day's top-up runs before that day's ping. */
private val TOP_UP_TIME = "%02d:30".format(NOTIFY_HOUR - 1)

private val PREAMBLE = """
    ${'$'}ErrorActionPreference = 'Stop'
    [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType=WindowsRuntime] | Out-Null
    [Windows.UI.Notifications.ScheduledToastNotification, Windows.UI.Notifications, ContentType=WindowsRuntime] | Out-Null
    ${'$'}notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('$AUMID')
""".trimIndent() + "\n"

/**
 * Content for a PowerShell single-quoted literal. PowerShell also ends such a literal on the
 * typographic single quotes (U+2018..U+201B), so those are doubled as well.
 */
internal fun psQuote(s: String): String = buildString {
    for (c in s) {
        if (c == '\'' || c in '\u2018'..'\u201B') append(c)
        append(c)
    }
}

internal fun xmlEscape(s: String) = s
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    .replace("'", "&apos;").replace("\"", "&quot;")

/**
 * One scheduled toast. Each is its own try/catch: under `Stop` a past delivery time or a
 * malformed title would otherwise abort the script and leave every later toast unregistered.
 */
internal fun toastRegistration(key: String, title: String, body: String, at: LocalDateTime): String {
    val whenText = "%04d-%02d-%02dT%02d:%02d:00".format(at.year, at.month.number, at.day, at.hour, at.minute)
    val tag = psQuote(key.take(64))
    return """
        try {
            ${'$'}xml = New-Object Windows.Data.Xml.Dom.XmlDocument
            ${'$'}xml.LoadXml('<toast><visual><binding template="ToastGeneric"><text>${psQuote(xmlEscape(title))}</text><text>${psQuote(xmlEscape(body))}</text></binding></visual></toast>')
            ${'$'}t = New-Object Windows.UI.Notifications.ScheduledToastNotification ${'$'}xml, ([datetime]::Parse('$whenText'))
            ${'$'}t.Tag = '$tag'
            ${'$'}t.Group = '$TOAST_GROUP'
            ${'$'}notifier.AddToSchedule(${'$'}t)
        } catch { Write-Output ('skip $tag: ' + ${'$'}_) }
    """.trimIndent()
}

/** Clears the group (New Month aside), then registers each reminder still a minute or more ahead. */
internal fun syncRemindersScript(registrations: List<ReminderRegistration>, now: Instant, tz: TimeZone): String {
    val script = StringBuilder(PREAMBLE)
    script.appendLine("foreach (${'$'}s in ${'$'}notifier.GetScheduledToastNotifications()) { if ((${'$'}s.Group -eq '$TOAST_GROUP') -and (${'$'}s.Tag -notlike 'newmonth-*')) { ${'$'}notifier.RemoveFromSchedule(${'$'}s) } }")
    for (r in registrations) {
        // Past registrations are dropped rather than delivered late: the fire time is the guard
        // that stops a reboot's re-sync re-firing what already fired (§3.11). The minute's margin
        // keeps one from going past before PowerShell registers it.
        if (r.fireAt.toInstant(tz) <= now + 1.minutes) continue
        script.appendLine(toastRegistration(r.expenseId, r.title, r.body, r.fireAt))
    }
    return script.toString()
}

/** Replaces the New Month toasts with twelve, from [first] on. */
internal fun newMonthScript(first: LocalDate): String {
    val script = StringBuilder(PREAMBLE)
    script.appendLine("foreach (${'$'}s in ${'$'}notifier.GetScheduledToastNotifications()) { if (${'$'}s.Tag -like 'newmonth-*') { ${'$'}notifier.RemoveFromSchedule(${'$'}s) } }")
    var month = first
    repeat(12) {
        script.appendLine(
            toastRegistration(
                key = "newmonth-${month.year}-${month.month.number}",
                title = NewMonthCopy.TITLE,
                body = NewMonthCopy.BODY,
                at = month.atTime(NOTIFY_HOUR, 0),
            )
        )
        month = month.plus(1, DateTimeUnit.MONTH)
    }
    return script.toString()
}

/**
 * Windows' half of the seam. It needs the executable's own path, because the Task Scheduler
 * entries have to invoke something -- and the app is the only thing that knows where it is.
 */
actual class PlatformContext(val executablePath: String)

actual fun notificationScheduler(context: PlatformContext): NotificationScheduler =
    WindowsNotificationScheduler(context.executablePath)

class WindowsNotificationScheduler(
    private val executablePath: String,
) : NotificationScheduler {

    override fun permissionState(): NotificationPermission {
        // §11.5: Windows has no runtime permission prompt — notifications are enabled per-app
        // by default and disabled from system Settings. So there is nothing to request and
        // NOT_ASKED never occurs here; the only question is what the system setting currently
        // says, which §12 requires be re-read rather than trusted from storage.
        //
        // Read from the registry, where the Settings toggles live: the WinRT notifier's
        // `Setting` property comes back empty through PowerShell's projection for every app,
        // registered or not, so it can never be the read. `NOC_GLOBAL_SETTING_TOASTS_ENABLED`
        // is the master switch and `<AUMID>\Enabled` the per-app one; each is absent until the
        // person touches it, and absent means on.
        val out = powershell(
            """
            ${'$'}base = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Notifications\Settings'
            ${'$'}g = (Get-ItemProperty -Path ${'$'}base -Name NOC_GLOBAL_SETTING_TOASTS_ENABLED -ErrorAction SilentlyContinue).NOC_GLOBAL_SETTING_TOASTS_ENABLED
            ${'$'}a = (Get-ItemProperty -Path "${'$'}base\$AUMID" -Name Enabled -ErrorAction SilentlyContinue).Enabled
            if ((${'$'}g -eq 0) -or (${'$'}a -eq 0)) { 'Disabled' } else { 'Enabled' }
            """.trimIndent()
        )
        // only an explicit off is off: a failed read keeps Windows' default, which is on
        return if (out.trim() == "Disabled") NotificationPermission.DENIED
        else NotificationPermission.GRANTED
    }

    /** No dialog exists on this platform (§11.5), so this is a read, and onboarding skips step 2. */
    override suspend fun requestPermission(): NotificationPermission = permissionState()

    override fun syncReminders(registrations: List<ReminderRegistration>) {
        // Declarative, the same contract as Android: clear the group, then register exactly
        // what was asked for. Clearing the group is what makes a finished or deleted expense's
        // registration disappear without the caller tracking what to cancel.
        logSkips(powershell(syncRemindersScript(registrations, Clock.System.now(), TimeZone.currentSystemDefault())))
    }

    override fun setNewMonthNotification(enabled: Boolean) {
        if (!enabled) {
            powershell(PREAMBLE + "foreach (${'$'}s in ${'$'}notifier.GetScheduledToastNotifications()) { if (${'$'}s.Tag -like 'newmonth-*') { ${'$'}notifier.RemoveFromSchedule(${'$'}s) } }")
            return
        }
        // Twelve months armed at a time, re-topped by the daily task. A fixed horizon is fine
        // precisely BECAUSE the task exists; without it this would silently stop after a year.
        val tz = TimeZone.currentSystemDefault()
        val first = Reminders.nextNewMonthFire(Clock.System.now().toLocalDateTime(tz)).date
        logSkips(powershell(newMonthScript(first)))
    }

    override fun restoreAfterBoot(
        reminders: List<ReminderRegistration>,
        newMonthEnabled: Boolean,
    ) {
        syncReminders(reminders)
        setNewMonthNotification(newMonthEnabled)
    }

    /**
     * The Start Menu shortcut that registers the AUMID (see the note above). The property is
     * set through the shell's property store from inline C#, because no cmdlet exposes it.
     * Idempotent: the shortcut is rewritten to point at this executable whenever
     * [installIfNeeded] finds the entries missing or pointing at another path.
     */
    fun installAppRegistration() {
        powershell(
            """
            ${'$'}ErrorActionPreference = 'Stop'
            ${'$'}lnk = Join-Path ${'$'}env:APPDATA '$SHORTCUT'
            ${'$'}ws = New-Object -ComObject WScript.Shell
            ${'$'}sc = ${'$'}ws.CreateShortcut(${'$'}lnk); ${'$'}sc.TargetPath = '${psQuote(executablePath)}'; ${'$'}sc.IconLocation = '${psQuote(executablePath)}'; ${'$'}sc.Save()
            Add-Type -TypeDefinition @'
            using System; using System.Runtime.InteropServices;
            namespace SciFin {
            [StructLayout(LayoutKind.Sequential, Pack = 4)] public struct PropertyKey { public Guid fmtid; public uint pid; public PropertyKey(Guid f, uint p) { fmtid = f; pid = p; } }
            [StructLayout(LayoutKind.Explicit)] public struct PropVariant { [FieldOffset(0)] public ushort vt; [FieldOffset(8)] public IntPtr p; }
            [ComImport, Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            public interface IPropertyStore {
              void GetCount(out uint c); void GetAt(uint i, out PropertyKey k); void GetValue(ref PropertyKey k, out PropVariant v);
              void SetValue(ref PropertyKey k, ref PropVariant v); void Commit(); }
            public static class Aumid {
              [DllImport("shell32.dll", CharSet = CharSet.Unicode)] static extern int SHGetPropertyStoreFromParsingName(string path, IntPtr bind, int flags, ref Guid iid, out IPropertyStore store);
              [DllImport("ole32.dll")] static extern int PropVariantClear(ref PropVariant v);
              public static void Set(string lnk, string id) {
                var iid = new Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"); IPropertyStore s;
                int hr = SHGetPropertyStoreFromParsingName(lnk, IntPtr.Zero, 2, ref iid, out s); if (hr != 0) throw new Exception("property store " + hr);
                var key = new PropertyKey(new Guid("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3"), 5);
                PropVariant v = new PropVariant(); v.vt = 31; v.p = Marshal.StringToCoTaskMemUni(id);
                s.SetValue(ref key, ref v); s.Commit(); PropVariantClear(ref v); }
            }
            }
            '@
            [SciFin.Aumid]::Set(${'$'}lnk, '$AUMID')
            """.trimIndent()
        )
    }

    /** Removes the shortcut. For an uninstaller, and for a clean development reset. */
    fun removeAppRegistration() {
        powershell("Remove-Item (Join-Path ${'$'}env:APPDATA '$SHORTCUT') -Force -ErrorAction SilentlyContinue")
    }

    /**
     * The first normal launch's one-time setup: the AUMID shortcut and the two Task Scheduler
     * entries, when either is absent or stale. A development run (a JVM launcher, not the
     * packaged exe) skips it: a task that re-launched `java.exe` daily would be junk.
     */
    fun installIfNeeded(log: (String) -> Unit = { System.err.println("[scifin] $it") }) {
        val name = executablePath.substringAfterLast('\\').substringAfterLast('/').lowercase()
        if (name == "java.exe" || name == "javaw.exe" || name == "java") { log("development launcher $executablePath: shortcut and scheduler entries not installed"); return }
        if (schedulerEntriesCurrent()) return
        installAppRegistration()
        installSchedulerEntries()
        log("installed the Start Menu shortcut and the two Task Scheduler entries for $executablePath")
    }

    /**
     * Installs the two Task Scheduler entries. Called by [installIfNeeded] when the entries are
     * absent or point at another executable; `-Force` overwrites.
     *
     * The daily entry is what keeps the queue armed beyond the last launch; the logon entry is
     * §13.3's boot recovery. Both invoke the app in a headless mode that re-runs the sync and
     * exits — the same code path a normal launch takes, not a second implementation.
     */
    fun installSchedulerEntries() {
        // Cmdlets, not `schtasks /TR`: /TR wants a quoted executable and its argument in one
        // value, and ProcessBuilder's Windows quoting wraps a value that starts with a quote in
        // another pair, so both creates failed silently.
        powershell(
            """
            ${'$'}ErrorActionPreference = 'Stop'
            ${'$'}action = New-ScheduledTaskAction -Execute '${psQuote(executablePath)}' -Argument '--sync-notifications'
            ${'$'}settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable
            Register-ScheduledTask -TaskPath '\$TASK_FOLDER\' -TaskName '$TASK_NAME' -Action ${'$'}action -Trigger (New-ScheduledTaskTrigger -Daily -At $TOP_UP_TIME) -Settings ${'$'}settings -Force | Out-Null
            Register-ScheduledTask -TaskPath '\$TASK_FOLDER\' -TaskName '$STARTUP_TASK_NAME' -Action ${'$'}action -Trigger (New-ScheduledTaskTrigger -AtLogOn -User "${'$'}env:USERDOMAIN\${'$'}env:USERNAME") -Settings ${'$'}settings -Force | Out-Null
            """.trimIndent()
        )
    }

    /** Removes both entries. For an uninstaller, and for a clean development reset. */
    fun removeSchedulerEntries() {
        powershell(
            """
            Unregister-ScheduledTask -TaskPath '\$TASK_FOLDER\' -TaskName '$TASK_NAME' -Confirm:${'$'}false -ErrorAction SilentlyContinue
            Unregister-ScheduledTask -TaskPath '\$TASK_FOLDER\' -TaskName '$STARTUP_TASK_NAME' -Confirm:${'$'}false -ErrorAction SilentlyContinue
            """.trimIndent()
        )
    }

    /**
     * Whether the daily entry exists AND runs this executable, and the AUMID shortcut exists --
     * a reinstall to another path leaves the old entries pointing at a file that is gone, and
     * without the shortcut no toast shows, so either counts as absent and the launch reinstalls.
     */
    fun schedulerEntriesCurrent(): Boolean =
        powershell("${'$'}t = Get-ScheduledTask -TaskPath '\\$TASK_FOLDER\\' -TaskName '$TASK_NAME' -ErrorAction SilentlyContinue; if (${'$'}t -and (${'$'}t.Actions[0].Execute -eq '${psQuote(executablePath)}') -and (Test-Path (Join-Path ${'$'}env:APPDATA '$SHORTCUT'))) { 'yes' } else { 'no' }").trim() == "yes"

    // ---------------------------------------------------------------- internals

    private fun logSkips(out: String) {
        out.lineSequence().filter { it.startsWith("skip ") }.forEach { System.err.println("[scifin] $it") }
    }

    private fun powershell(script: String): String {
        // Passed as a file rather than -Command, so quoting inside the script cannot be mangled
        // by the command line — reminder bodies carry apostrophes and currency symbols. The file
        // carries a UTF-8 BOM: without one Windows PowerShell reads it as ANSI, "€" arrives as
        // "â‚¬", and "‚" is a quote to its parser.
        val f: Path = Files.createTempFile("scifin-notify", ".ps1")
        return try {
            Files.write(f, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + script.toByteArray(Charsets.UTF_8))
            runProcess(
                listOf(
                    // Absolute, so a powershell.exe in the working directory is never the one run.
                    File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\WindowsPowerShell\\v1.0\\powershell.exe").path,
                    "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", f.toString(),
                )
            )
        } finally {
            runCatching { Files.deleteIfExists(f) }
        }
    }

    private fun runProcess(command: List<String>): String {
        return try {
            val p = ProcessBuilder(command).redirectErrorStream(true).start()
            // Read on a thread: a blocking read on this one would only return once the process
            // had exited, so the timeout below could never fire.
            var out = ""
            val reader = thread(isDaemon = true) { out = p.inputStream.bufferedReader().readText() }
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                System.err.println("[scifin] powershell timed out")
                return ""
            }
            reader.join(1000)
            if (p.exitValue() != 0) System.err.println("[scifin] powershell exit ${p.exitValue()}: $out")
            out
        } catch (e: Exception) {
            // A scheduling failure must never take the app down with it. §4.7 already carries
            // the honest line when nothing can fire, and that is the right place for the person
            // to learn it — not a crash at the moment they edited a due date.
            System.err.println("[scifin] scheduling call failed: ${e.message}")
            ""
        }
    }
}
