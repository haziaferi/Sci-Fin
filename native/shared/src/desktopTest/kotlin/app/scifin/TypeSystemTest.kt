package app.scifin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * §2.5's type (Decided 2026-09-26, with the owner): four sizes, one per job, and two weights.
 * The owner's complaint was "too many text sizes and weights in the mix" -- a census that day
 * found 23 size+weight styles over 7 sizes and 4 weights -- so the gate is on the source: a
 * size outside the four jobs, a third weight, or a literal `sp` fails here.
 */
class TypeSystemTest {
    private val ui = File("src/commonMain/kotlin/app/scifin/ui")
    private fun sources() = ui.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test fun `every text size is one of the four jobs`() {
        assertTrue(ui.isDirectory, "not run from the module directory: ${ui.absolutePath}")
        val used = sources().flatMap { f -> Regex("""\btype\.(\w+)""").findAll(f.readText()).map { it.groupValues[1] } }.toSet()
        assertEquals(setOf("caption", "body", "title", "figure"), used - setOf("token"))   // `draft.type.token` is an account type, not type
    }

    @Test fun `only Regular and Bold are drawn`() {
        val weights = sources().flatMap { f -> Regex("""FontWeight\.(\w+)""").findAll(f.readText()).map { it.groupValues[1] } }.toSet()
        assertEquals(setOf("Normal", "Bold"), weights)
    }

    @Test fun `no size is written as a literal`() {
        val literal = Regex("""\b\d+(\.\d+)?f?\.sp\b""")
        val hits = sources().flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (literal.containsMatchIn(l)) "${f.name}:${i + 1}" else null } }
        assertTrue(hits.isEmpty(), "literal sp sizes: $hits")
    }
}
