package app.scifin.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/*
 * Colour roles derived from a palette rather than stored in it (§2.5), except the switch chip,
 * which is a per-palette table from tools/switch_colours.py.
 * tools/palette_derive.py stays the source of the eleven stored tokens; these follow from them.
 */

/** A row's + (Add to a goal) is this share of the way from the ground to the accent (Oklab). The floating + uses the Confirm button's accent. */
const val ROW_ADD_SHARE = 0.40f

/** The Savings figure, x the type ramp's figure size (§2.5). */
const val SAVINGS_FIGURE_SCALE = 1.4f

private fun contrast(a: Color, b: Color): Float {
    val x = a.luminance(); val y = b.luminance()
    return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
}

private val Palette.light: Boolean get() = bg.luminance() > 0.5f

/**
 * A + button's fill and glyph: the fill [share] of the way from the ground to this palette's own accent, so it keeps
 * its place below the Savings figure (drawn in the accent) in every theme; the glyph is whichever of the palette's
 * txt / onAcc / bg / surf / acc reads best on it. tools/plus_colours.py checks all eight palettes: 4.71:1 at worst.
 */
fun Palette.plusColours(share: Float): Pair<Color, Color> {
    val fill = lerp(bg, acc, share)
    return fill to listOf(txt, onAcc, bg, surf, acc).maxBy { contrast(it, fill) }
}

/** The Savings figure: accH in light mode, where it is the darker accent and keeps Savings above the floating +; acc in dark. */
val Palette.savingsFigure: Color get() = if (light) accH else acc

/**
 * The Envelopes | Categories switch has no track; the selected option sits on this chip
 * (tools/switch_colours.py: as quiet against the ground as the floors allow -- the chip no
 * closer to the track than surf was to surf2, labels 4.5:1, and lighter than the ground in dark mode).
 */
private val SWITCH_CHIPS = mapOf(
    Palettes.swissBlueLight to Color(0xFFE1E4E9),
    Palettes.swissBlueDark to Color(0xFF1E2328),
    Palettes.daylightLight to Color(0xFFEAF0ED),
    Palettes.daylightDark to Color(0xFF232323),
    Palettes.tallowTomeLight to Color(0xFFE7E1DE),
    Palettes.tallowTomeDark to Color(0xFF1C1916),
    Palettes.duskLight to Color(0xFFE3E3E3),
    Palettes.duskDark to Color(0xFF272727),
)

val Palette.switchChip: Color get() = SWITCH_CHIPS[this] ?: surf2
