@file:OptIn(ExperimentalTime::class)

package app.scifin

import app.scifin.notify.ReminderRegistration
import app.scifin.notify.newMonthScript
import app.scifin.notify.psQuote
import app.scifin.notify.syncRemindersScript
import app.scifin.ui.parseMoney
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/** The 2026-10-10 cleanup's fixes to ui/ and the Windows notification script. */
class CleanupUiRootTest {
    private val tz = TimeZone.UTC
    private val now = LocalDateTime(2026, 10, 10, 12, 0).toInstant(tz)
    private fun reg(id: String, title: String = "Rent", at: LocalDateTime = LocalDateTime(2026, 10, 20, 9, 0)) =
        ReminderRegistration(id, title, "€900 · due Oct 23", LocalDate(2026, 10, 23), at)

    @Test fun `an expense id from a backup cannot break out of the toast tag literal`() {
        val script = syncRemindersScript(listOf(reg("x'; Start-Process calc; '")), now, tz)
        assertTrue("\$t.Tag = 'x''; Start-Process calc; '''" in script, script)
        assertFalse("'x'; Start-Process" in script, script)
    }

    @Test fun `typographic single quotes are doubled too, since PowerShell ends a literal on them`() {
        assertEquals("a’’b''c", psQuote("a’b'c"))
        val script = syncRemindersScript(listOf(reg("id‘; calc; ‘", title = "Mum’s rent")), now, tz)
        assertTrue("\$t.Tag = 'id‘‘; calc; ‘‘'" in script, script)
        assertTrue("Mum’’s rent" in script, script)
    }

    @Test fun `each toast registers on its own, so one failure leaves the rest armed`() {
        val script = syncRemindersScript(listOf(reg("a"), reg("b"), reg("c")), now, tz)
        assertEquals(3, Regex("""(?m)^try \{""").findAll(script).count(), script)
        assertEquals(3, Regex("""(?m)^\} catch \{ Write-Output \('skip """).findAll(script).count(), script)
        assertEquals(12, Regex("""(?m)^try \{""").findAll(newMonthScript(LocalDate(2026, 11, 1))).count())
    }

    @Test fun `a reminder due within the next minute is not registered`() {
        val script = syncRemindersScript(
            listOf(reg("soon", at = LocalDateTime(2026, 10, 10, 12, 0, 30)), reg("later", at = LocalDateTime(2026, 10, 10, 12, 2))),
            now, tz,
        )
        assertFalse("'soon'" in script, script)
        assertTrue("\$t.Tag = 'later'" in script, script)
    }

    @Test fun `an amount too large for minor units is refused rather than wrapped negative`() {
        assertNull(parseMoney("99999999999999999"))
        assertNull(parseMoney("92233720368547758"))
        assertEquals(9_223_372_036_854_775_700L, parseMoney("92233720368547757"))
    }

    @Test fun `non-breaking and narrow no-break spaces group like a plain space`() {
        assertEquals(123450L, parseMoney("1 234,50"))
        assertEquals(123450L, parseMoney("1 234,50"))
    }
}
