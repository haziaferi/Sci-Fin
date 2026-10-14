package app.scifin

import androidx.compose.ui.graphics.Color
import app.scifin.model.Theme
import app.scifin.ui.Palette
import app.scifin.ui.Palettes
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import app.scifin.ui.components.LEARN_LINK_DECORATION

/**
 * §2.5's contrast table, re-measured from the tokens themselves rather than transcribed.
 * Alpha tokens are composited over the ground they are measured against first, which is
 * what the eye sees. The 2026-09-15 device critique found the one ground §2.5 never
 * measured: `txt2` on `surf2`, where InlineLearn, InfoNote and the disclosure body sit.
 */
class TokenContrastTest {
    private fun lum(c: Color): Double {
        fun ch(v: Float): Double { val d = v.toDouble(); return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }
    private fun over(fg: Color, bg: Color): Color = if (fg.alpha >= 1f) fg else Color(
        fg.red * fg.alpha + bg.red * (1 - fg.alpha), fg.green * fg.alpha + bg.green * (1 - fg.alpha), fg.blue * fg.alpha + bg.blue * (1 - fg.alpha), 1f,
    )
    private fun ratio(fg: Color, bg: Color): Double { val a = lum(over(fg, bg)); val b = lum(bg); return (max(a, b) + 0.05) / (min(a, b) + 0.05) }
    private val all: List<Pair<String, Palette>> = Theme.entries.flatMap { listOf("${it.token} light" to Palettes.of(it, false), "${it.token} dark" to Palettes.of(it, true)) }

    @Test fun `txt2 clears 4_5 to 1 on every ground it is drawn on, surf2 included`() {
        val failing = all.flatMap { (name, p) -> listOf("bg" to p.bg, "surf" to p.surf, "surf2" to p.surf2).filter { (_, g) -> ratio(p.txt2, g) < 4.5 }.map { (g, ground) -> "$name txt2/$g = ${"%.2f".format(ratio(p.txt2, ground))}" } }
        assertEquals(emptyList<String>(), failing)
    }

    @Test fun `txt clears 4_5 and the accent pairs clear their lines in all eight`() {
        for ((name, p) in all) {
            assertTrue(ratio(p.txt, p.surf2) >= 4.5, "$name txt/surf2")
            assertTrue(ratio(p.onAcc, p.acc) >= 4.5, "$name onAcc/acc")
            assertTrue(ratio(p.acc, p.bg) >= 3.0, "$name acc/bg as a fill")
            assertTrue(ratio(p.bordStrong, p.bg) >= 3.0 && ratio(p.bordStrong, p.surf) >= 3.0, "$name bordStrong")
        }
    }

    /** §2.5 (Decided 2026-10-07, with the owner): one pair of grounds for every theme. */
    @Test fun `every theme sits on the owner's grounds`() {
        for (th in Theme.entries) {
            assertEquals(Color(0xFFF4F4F2), Palettes.of(th, false).bg, "${th.token} light bg")
            assertEquals(Color(0xFF17181A), Palettes.of(th, true).bg, "${th.token} dark bg")
        }
    }

    /** §2.5 (Decided 2026-10-07): the accent draws section titles, figures and the selected tab, so it is held to 7:1 everywhere. */
    @Test fun `the accent clears 7 to 1 on every ground and under its own label`() {
        val failing = all.flatMap { (name, p) ->
            listOf("bg" to p.bg, "surf" to p.surf, "surf2" to p.surf2).filter { (_, g) -> ratio(p.acc, g) < 7.0 }.map { (g, ground) -> "$name acc/$g = ${"%.2f".format(ratio(p.acc, ground))}" } +
                (if (ratio(p.onAcc, p.acc) < 7.0) listOf("$name onAcc/acc = ${"%.2f".format(ratio(p.onAcc, p.acc))}") else emptyList())
        }
        assertEquals(emptyList<String>(), failing)
    }

    /** §2.5: `txt3` is decorative and inactive only -- a placeholder may wear it, a fact may not. */
    @Test fun `no Text draws in txt3 except the placeholder sites`() {
        val ui = File("src/commonMain/kotlin/app/scifin/ui")
        assertTrue(ui.isDirectory, "run from the shared module: ${File(".").absolutePath}")
        // the entry sheet's two field placeholders ("0.00", "where (optional)") joined these on 2026-10-09
        val allowed = setOf("components/Controls.kt", "components/DateField.kt", "sheets/EntrySheet.kt")
        val hits = ui.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (Regex("""Text\(.*color = (p|t\.palette)\.txt3""").containsMatchIn(line)) "${f.relativeTo(ui).path.replace('\\', '/')}:${i + 1}" else null }
        }.filterNot { hit -> allowed.any { hit.startsWith(it) } }.toList()
        assertEquals(emptyList<String>(), hits)
    }
}

/** §2.5 (Decided 2026-09-15): Slate stays quiet, so a learn link carries a cue that is not colour -- the underline. */
class LearnLinkCueTest {
    @Test fun `a learn link is underlined in every palette, not just coloured`() {
        assertEquals(androidx.compose.ui.text.style.TextDecoration.Underline, LEARN_LINK_DECORATION)
    }
}
