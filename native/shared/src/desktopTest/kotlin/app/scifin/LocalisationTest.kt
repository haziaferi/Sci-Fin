package app.scifin

import app.scifin.engine.moneyText
import app.scifin.i18n.CATALOGUES
import app.scifin.i18n.I18n
import app.scifin.i18n.Language
import app.scifin.i18n.SHIPPED
import app.scifin.i18n.tr
import app.scifin.i18n.trc
import app.scifin.i18n.trf
import app.scifin.i18n.trp
import app.scifin.model.moneyOf
import app.scifin.ui.formatDate
import app.scifin.ui.formatDateFull
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Localisation (owner, 2026-10-26): the English text is the key. This reads every key from the source, so a
 * translation can never fall behind the text it translates, and no screen text can skip [tr].
 */
class LocalisationTest {
    @AfterTest fun english() { I18n.current = Language.EN }

    private val roots = listOf("src/commonMain", "src/androidMain", "src/desktopMain").map(::File) + File("../desktopApp/src/main")
    private val sources: List<Pair<File, String>> by lazy {
        roots.filter { it.exists() }.flatMap { r -> r.walkTopDown().filter { it.extension == "kt" }.map { it to it.readText() } }
    }

    companion object {
        /** A Kotlin string literal's body, escapes and all. */
        private const val LIT = "\"((?:[^\"\\\\\\n]|\\\\.)*)\""
        private val CALL = Regex("""\b(tr|trf|stored)\(\s*$LIT""")
        private val PLURAL = Regex("""\btrp\(\s*$LIT\s*,\s*$LIT""")
        /** [trc]'s key: the sense, then the English. */
        private val SENSE = Regex("""\btrc\(\s*$LIT\s*,\s*$LIT""")

        fun unescape(s: String): String = s.replace(Regex("""\\u([0-9a-fA-F]{4})""")) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace("\\\"", "\"").replace("\\$", "$").replace("\\\\", "\\")
    }

    /** Every English key in the source. */
    private val keys: Set<String> by lazy {
        sources.flatMap { (_, text) -> CALL.findAll(text).map { it.groupValues[2] } + PLURAL.findAll(text).flatMap { sequenceOf(it.groupValues[1], it.groupValues[2]) } +
            SENSE.findAll(text).map { "${it.groupValues[1]}::${it.groupValues[2]}" } }
            .map(::unescape).toSet()
    }

    @Test fun `every key is plain text a catalogue can hold -- no template, no runaway placeholder`() {
        assertTrue(keys.size > 500, "only ${keys.size} keys found -- the text is not yet routed through tr")
        val templated = keys.filter { Regex("""(?<!\\)\$[{a-zA-Z]""").containsMatchIn(it) }
        assertTrue(templated.isEmpty(), "keys with a Kotlin template, which tr could never match: ${templated.take(10)}")
        val badHoles = keys.filter { k -> Regex("""\{(\d+)}""").findAll(k).any { it.groupValues[1].toInt() > 9 } }
        assertTrue(badHoles.isEmpty(), "placeholders beyond {9}: $badHoles")
    }

    /**
     * No screen text skips [tr]: the components that show text are never handed a raw literal. A literal that is
     * genuinely not text (a symbol, a figure) is allowed when it holds no letters.
     */
    @Test fun `no screen hands a component raw text`() {
        val shown = Regex("""\b(Text|Caption|FieldLabel|SectionLabel|InfoNote|FeasibilityNote|RuledNote|PlaceholderCard|LearnLink|Button|NavRow|SettingRow|TopRow|TabTitle|InlineLearn|Inline|Word|LedgerLabel|FigureLabel|DisclosureNote)\(\s*(?:[a-zA-Z.]+\s*,\s*)?$LIT""")
        val named = Regex("""\b(title|label|placeholder|contentDescription|onClickLabel|stateDescription|caption|hint)\s*=\s*$LIT""")
        val raw = sources.filter { (f, _) -> f.path.replace('\\', '/').contains("/ui/") }.flatMap { (f, text) ->
            (shown.findAll(text).map { it.groupValues[2] } + named.findAll(text).map { it.groupValues[2] })
                // text that is only values (a file name and its format) has no words to translate
                .filter { s -> s.replace(Regex("""\$\{[^}]*}|\$[a-zA-Z_.]+"""), "").any { it.isLetter() } }.map { "${f.name}: $it" }
        }
        assertTrue(raw.isEmpty(), "${raw.size} raw strings reach the screen:\n" + raw.take(25).joinToString("\n"))
    }

    /** The Learn topics' keys, which a catalogue's interface part does not hold (§2.7 step 3). */
    private val learnKeys: Set<String> by lazy {
        sources.filter { (f, _) -> f.name == "LearnScreen.kt" || f.name == "CreditTopic.kt" }.flatMap { (_, text) ->
            CALL.findAll(text).map { it.groupValues[2] } + PLURAL.findAll(text).flatMap { sequenceOf(it.groupValues[1], it.groupValues[2]) }
        }.map(::unescape).toSet()
    }

    @Test fun `every shipped catalogue holds every key, and every catalogue keeps its placeholders`() {
        for ((lang, cat) in CATALOGUES) {
            if (lang in SHIPPED) {
                val missing = keys - cat.keys
                assertTrue(missing.isEmpty(), "${lang.label} is missing ${missing.size}: ${missing.take(10)}")
            }
            val holes = cat.filter { (en, it) -> Regex("""\{\d+}""").findAll(en).map { m -> m.value }.toSet() != Regex("""\{\d+}""").findAll(it).map { m -> m.value }.toSet() }
            assertTrue(holes.isEmpty(), "${lang.label} drops or adds a placeholder: ${holes.keys.take(5)}")
            val stale = cat.keys - keys
            assertTrue(stale.isEmpty(), "${lang.label} translates text the app no longer has: ${stale.take(10)}")
        }
    }

    @Test fun `the Italian interface holds every key outside the Learn topics`() {
        val missing = keys - learnKeys - CATALOGUES.getValue(Language.IT).keys
        assertTrue(missing.isEmpty(), "Italian is missing ${missing.size}: ${missing.take(15)}")
    }

    @Test fun `English text comes back unchanged, placeholders and plurals included`() {
        assertEquals("Log hours", tr("Log hours"))
        assertEquals("6 h logged for the payday on Nov 13", trf("{0} logged for the payday on {1}", "6 h", "Nov 13"))
        assertEquals("1 day", trp("{0} day", "{0} days", 1)); assertEquals("3 days", trp("{0} day", "{0} days", 3))
        assertEquals("Log", trc("noun", "Log"))
    }

    @Test fun `one English word in two senses takes each sense's own word`() {
        I18n.current = Language.IT
        assertEquals("Registra", tr("Log")); assertEquals("Registro", trc("noun", "Log"))
    }

    @Test fun `amounts and dates take the language's own style`() {
        val d = LocalDate(2026, 11, 13)
        assertEquals("€1,450.50", moneyText(moneyOf(1450.50), "EUR")); assertEquals("−$12", moneyText(moneyOf(-12.0), "USD"))
        assertEquals("Nov 13", formatDate(d)); assertEquals("Nov 13, 2026", formatDateFull(d))
        I18n.current = Language.IT
        assertEquals("1.450,50 €", moneyText(moneyOf(1450.50), "EUR")); assertEquals("−12 $", moneyText(moneyOf(-12.0), "USD"))
        assertEquals("13 nov", formatDate(d)); assertEquals("13 nov 2026", formatDateFull(d))
    }
}
