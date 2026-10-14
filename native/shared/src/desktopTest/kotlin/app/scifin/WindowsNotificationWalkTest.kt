@file:OptIn(ExperimentalTime::class)

package app.scifin

import app.scifin.notify.ReminderRegistration
import app.scifin.notify.WindowsNotificationScheduler
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * §11.5 / §14's Windows walk, against the real OS -- so it runs only when asked
 * (`SCIFIN_WALK=1`), never in the acceptance build. It registers one reminder two minutes out
 * through the seam, reads the OS's schedule back, installs the two Task Scheduler entries and the
 * AUMID shortcut against a fake executable, lists them and removes them. Whether the toast then
 * *shows* is checked by eye: the OS holds it after this JVM has exited, which is the point of the
 * design -- and it shows only while a Start Menu shortcut carries the AUMID (2026-09-15).
 */
class WindowsNotificationWalkTest {
    private val walk = System.getenv("SCIFIN_WALK") == "1"
    private val fakeExe = """C:\fake\Sci-Fin.exe"""

    private fun ps(script: String): String {
        val f = java.nio.file.Files.createTempFile("scifin-walk", ".ps1")
        java.nio.file.Files.write(f, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + script.toByteArray(Charsets.UTF_8))
        val p = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", f.toString())
            .redirectErrorStream(true).start()
        return p.inputStream.bufferedReader().readText().also { p.waitFor(); java.nio.file.Files.deleteIfExists(f) }
    }

    private fun scheduled(): String = ps(
        """
        [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType=WindowsRuntime] | Out-Null
        ${'$'}n = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('SciFin')
        foreach (${'$'}s in ${'$'}n.GetScheduledToastNotifications()) { "{0} {1} {2}" -f ${'$'}s.Group, ${'$'}s.Tag, ${'$'}s.DeliveryTime.ToString('s') }
        """.trimIndent()
    )

    @Test fun `a reminder registered through the seam is on the OS's schedule, and the New Month queue holds twelve`() {
        if (!walk) return
        val s = WindowsNotificationScheduler(fakeExe)
        println("permission: " + s.permissionState())
        val fireAt = (Clock.System.now() + 2.minutes).toLocalDateTime(TimeZone.currentSystemDefault())
        s.syncReminders(listOf(ReminderRegistration("walk-rent", "Rent is due in 3 days", "€900 on Oct 1 — it comes out of Savings up front.", LocalDate(2026, 10, 1), fireAt)))
        s.setNewMonthNotification(true)
        val listed = scheduled()
        println("scheduled:\n$listed")
        assertTrue("scifin walk-rent" in listed, "the reminder is not on the schedule:\n$listed")
        assertTrue(listed.lines().count { it.contains("newmonth-") } == 12, "twelve New Month toasts expected:\n$listed")
        // and off again: the New Month queue is cleared, the reminder stays for the eye check
        s.setNewMonthNotification(false)
        val after = scheduled()
        assertTrue(after.lines().none { it.contains("newmonth-") }, "New Month toasts should be gone:\n$after")
        assertTrue("scifin walk-rent" in after)
    }

    @Test fun `the two Task Scheduler entries install, list and remove`() {
        if (!walk) return
        val s = WindowsNotificationScheduler(fakeExe)
        s.installSchedulerEntries()
        val q = ps("schtasks /Query /TN 'Sci-Fin\\TopUpNotifications' /V /FO LIST; schtasks /Query /TN 'Sci-Fin\\TopUpNotificationsAtLogon' /V /FO LIST")
        println("schtasks:\n$q")
        assertTrue(q.lines().count { it.contains("--sync-notifications") } == 2, "both tasks should carry the command:\n$q")
        assertTrue(fakeExe in q, "the task should invoke the executable it was given:\n$q")
        s.removeSchedulerEntries()
        val gone = ps("schtasks /Query /TN 'Sci-Fin\\TopUpNotifications' 2>&1; schtasks /Query /TN 'Sci-Fin\\TopUpNotificationsAtLogon' 2>&1")
        assertFalse("--sync-notifications" in gone, "tasks should be removed:\n$gone")
    }

    @Test fun `the Start Menu shortcut carries the AUMID, and is removed again`() {
        if (!walk) return
        val s = WindowsNotificationScheduler(fakeExe)
        s.installAppRegistration()
        val props = ps(
            """
            ${'$'}lnk = Join-Path ${'$'}env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Sci-Fin.lnk'
            ${'$'}sh = New-Object -ComObject Shell.Application
            ${'$'}item = ${'$'}sh.NameSpace((Split-Path ${'$'}lnk)).ParseName((Split-Path ${'$'}lnk -Leaf))
            "aumid=" + ${'$'}item.ExtendedProperty('System.AppUserModel.ID')
            "target=" + (New-Object -ComObject WScript.Shell).CreateShortcut(${'$'}lnk).TargetPath
            """.trimIndent()
        )
        println(props)
        assertTrue("aumid=SciFin" in props, props)
        assertTrue("target=$fakeExe" in props, props)
        s.removeAppRegistration()
        assertFalse(java.io.File(System.getenv("APPDATA"), """Microsoft\Windows\Start Menu\Programs\Sci-Fin.lnk""").exists())
    }
}
