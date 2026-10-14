package app.scifin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.scifin.ui.LocalTokens

/*
 * §2.5 under the text-size setting: three things that must not wrap. An amount is one
 * token -- "€1," on one line and "095" on the next is a different number. A tab label shrinks before it
 * breaks. A SettingRow gives its label a floor and stacks the control when the row cannot
 * hold both.
 */

/** A money figure: never wraps, never breaks inside; if the column is narrower than the text, the text overflows rather than lies. */
@Composable
fun AmountText(
    text: String, color: Color = LocalTokens.current.palette.txt, fontSize: TextUnit = LocalTokens.current.type.body,
    fontWeight: FontWeight? = null, modifier: Modifier = Modifier, onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    Text(text, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible, modifier = modifier, onTextLayout = onTextLayout ?: {})
}

/**
 * A label set's size (the tab bar, [Segmented], [ThemeGrid]): the largest half-point step from [max] down to 9sp at which every label,
 * measured by [measure] (text, size -> width in px), fits [limitPx]. One size for every label in the set,
 * so a row never mixes sizes.
 */
fun fitLabelSize(labels: List<String>, limitPx: Float, max: TextUnit, measure: (String, TextUnit) -> Float): TextUnit {
    var size = max.value
    while (size > 9f && labels.maxOf { measure(it, size.sp) } > limitPx) size -= 0.5f
    return size.sp
}

/** The SettingRow rule: the label keeps at least [labelFloorDp]; if the control would take that away, the control goes under the label. All three in one unit. */
fun settingRowStacks(rowWidthDp: Float, controlWidthDp: Float, labelFloorDp: Float): Boolean = rowWidthDp - controlWidthDp < labelFloorDp

/**
 * §2.5's "never breaks a word", for rows with something on their right: the SettingRow rule.
 * [trailing] sits beside [label] while the label keeps [floorPx] (a NavRow: its longest word)
 * and goes under it, end-aligned and [stackGapPx] below, when it would not.
 */
@Composable
fun BesideOrUnder(floorPx: Int, gapPx: Int, modifier: Modifier = Modifier, stackGapPx: Int = gapPx / 2, label: @Composable () -> Unit, trailing: @Composable () -> Unit) {
    Layout(content = { Box(Modifier.layoutId("label")) { label() }; Box(Modifier.layoutId("trailing")) { trailing() } }, modifier = modifier) { ms, c ->
        val loose = c.copy(minWidth = 0, minHeight = 0)
        val tr = ms.first { it.layoutId == "trailing" }.measure(loose)
        val lm = ms.first { it.layoutId == "label" }
        val w = c.maxWidth
        if (!settingRowStacks(w.toFloat(), (tr.width + gapPx).toFloat(), floorPx.toFloat())) {
            val l = lm.measure(loose.copy(maxWidth = w - tr.width - gapPx))
            val h = maxOf(l.height, tr.height)
            layout(w, h) { l.placeRelative(0, (h - l.height) / 2); tr.placeRelative(w - tr.width, (h - tr.height) / 2) }
        } else {
            val l = lm.measure(loose.copy(maxWidth = w))
            layout(w, l.height + stackGapPx + tr.height) { l.placeRelative(0, 0); tr.placeRelative(w - tr.width, l.height + stackGapPx) }
        }
    }
}

/** The widest single word of [text] in [style], in px: the least a label needs so no word is split. */
@Composable
fun longestWordPx(text: String, style: TextStyle): Int {
    val m = rememberTextMeasurer()
    return text.split(' ').filter { it.isNotEmpty() }.maxOfOrNull { m.measure(it, style).size.width } ?: 0
}
