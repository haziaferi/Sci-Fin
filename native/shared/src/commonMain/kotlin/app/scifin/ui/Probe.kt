package app.scifin.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged

/**
 * A measurement hook for the scoring renders (§13.1): what a
 * component actually laid out at, reported by name. Null in the app, so it costs nothing there;
 * the desktop render harness provides one and writes what it hears to a file.
 */
val LocalProbe = compositionLocalOf<((name: String, values: Map<String, Float>) -> Unit)?> { null }

/** Reports this element's laid-out size in px under [name], when a probe is listening. */
fun Modifier.probeSize(probe: ((String, Map<String, Float>) -> Unit)?, name: String): Modifier =
    if (probe == null) this else this.onSizeChanged { probe(name, mapOf("w" to it.width.toFloat(), "h" to it.height.toFloat())) }

/**
 * The census hook (§4.8's co-visibility count): every amount
 * the screens format passes through [formatMoney], so a render can count them. Null in the app.
 */
object FigureProbe {
    var sink: ((String) -> Unit)? = null
}
