package app.scifin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import app.scifin.model.Theme
import app.scifin.ui.LocalProbe
import app.scifin.ui.LocalTokens
import app.scifin.ui.Palettes
import app.scifin.ui.probeSize

/**
 * §2.5's theme picker, shared by onboarding and Settings:
 * two cards per row, each exactly the touch-target height (48dp on a phone, 40 on Windows -- never
 * taller), holding the theme's accent as a swatch and its name. Every name takes one size, the
 * largest that fits the card, so a long name never wraps or breaks and the four stay alike.
 */
@Composable
fun ThemeGrid(selected: Theme, onSelect: (Theme) -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    val probe = LocalProbe.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val swatch = t.spacing.u * 2.5f
        val sidePad = t.spacing.u * 1.25f
        val cell = (maxWidth - t.spacing.gap) / 2
        val measurer = rememberTextMeasurer()
        val measure: (String, TextUnit) -> Float = { text, sz -> measurer.measure(text, TextStyle(fontSize = sz, fontWeight = FontWeight.Bold)).size.width.toFloat() }
        val labels = Theme.entries.map { it.label }
        val (roomWith, roomWithout) = with(LocalDensity.current) { (cell - sidePad * 2 - swatch - t.spacing.u).toPx() to (cell - sidePad * 2).toPx() }
        // a 320dp phone at 180-200% text cannot hold the longest name beside the swatch even at the
        // floor size; there the swatch steps aside so the name stays whole and the card stays 48dp
        val (showSwatch, size) = remember(labels, roomWith, roomWithout, t.type.body, measurer) {
            val withSwatch = fitLabelSize(labels, roomWith, t.type.body, measure)
            val fits = labels.maxOf { measure(it, withSwatch) } <= roomWith
            fits to (if (fits) withSwatch else fitLabelSize(labels, roomWithout, t.type.body, measure))
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
            Theme.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
                    for (th in pair) {
                        val on = th == selected
                        Row(
                            Modifier.weight(1f).height(t.spacing.target).probeSize(probe, "theme-card")
                                .background(if (on) p.acc.copy(alpha = 0.09f) else p.surf, RoundedCornerShape(t.radii.lg))
                                .border(if (on) t.strokes.emph else t.strokes.ctl, if (on) p.acc else p.bordStrong, RoundedCornerShape(t.radii.lg))
                                .selectable(selected = on, role = Role.RadioButton) { onSelect(th) }
                                .padding(horizontal = sidePad),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u),
                        ) {
                            if (showSwatch) Box(Modifier.size(swatch).background(Palettes.of(th, t.dark).acc, CircleShape))
                            Text(th.label, color = p.txt, fontSize = size, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                                modifier = Modifier.probeSize(probe, "theme-name"),
                                onTextLayout = { r -> probe?.invoke("theme-name-fit", mapOf("overflow" to if (r.hasVisualOverflow) 1f else 0f, "lines" to r.lineCount.toFloat())) })
                        }
                    }
                }
            }
        }
    }
}
