package app.scifin

import app.scifin.engine.monthsLabel
import app.scifin.model.Account
import app.scifin.model.AccountType
import app.scifin.model.moneyOf
import app.scifin.ui.components.deleteAddBackCopy
import app.scifin.ui.components.deleteOnlyCopy
import app.scifin.ui.screens.owedNoteShown
import app.scifin.ui.screens.spendingRowCaption
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The 2026-09-15 device critique's copy findings, each pinned where the words are made. */
class CopyTest {
    @Test fun `the second delete step speaks in the future -- nothing has happened until Delete`() {
        assertEquals("Rent will be deleted. Savings stays as it is.", deleteOnlyCopy("Rent"))
        assertEquals("€950 will go back to Savings and Rent will be deleted. Its past history stays in the log.", deleteAddBackCopy("€950", "Rent"))
    }

    @Test fun `paid in full reads as an outcome, not a state`() {
        assertEquals("cleared at once", monthsLabel(0))
        assertEquals("never at this rate", monthsLabel(null))
    }

    @Test fun `a spending row states its share once -- the header already says whose outflow`() {
        assertEquals("40% — €1,120", spendingRowCaption(112000, 280000, "EUR"))
    }

    @Test fun `the Owed report's off-by-default note appears only while there is nothing to weigh`() {
        assertTrue(owedNoteShown(emptyList()))
        assertFalse(owedNoteShown(listOf(Account("cc", "p-1", "Visa", null, AccountType.CREDIT, balance = moneyOf(1200.0)))))
    }

    /**
     * A spec section number is for the spec; no user-facing string carries one -- except §4.5's
     * Learn source lines, which cite the research audit as "(audit §2.1, §3.3)" on purpose.
     * Widened 2026-09-25: it had caught only "(§", so "see §3.6" passed; and it now proves it
     * scanned something, since a wrong working directory used to pass with nothing checked.
     */
    @Test fun `no string literal under ui shows a section sign to the person`() {
        val ui = File("src/commonMain/kotlin/app/scifin/ui")
        assertTrue(ui.isDirectory, "not run from the module directory: ${ui.absolutePath}")
        val literal = Regex(""""[^"]*"""")
        val auditCite = Regex("""\(audit [^)]*\)""")
        val hits = ui.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val code = line.substringBefore("//")
                if (literal.findAll(code).any { '§' in auditCite.replace(it.value, "") }) "${f.relativeTo(ui).path.replace('\\', '/')}:${i + 1}" else null
            }
        }.toList()
        assertEquals(emptyList<String>(), hits)
    }
}
