package app.scifin.ui.components

import app.scifin.ui.LocalProbe
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.scifin.ui.LocalTokens
import app.scifin.ui.LocalWidthClass
import app.scifin.ui.WidthClass

/**
 * How many one-measure columns fit in [avail] -- §4.8's column rule, stated as arithmetic:
 * the largest N satisfying N*measure + (N-1)*gap <= avail.
 *
 * Never returns less than 1. On a phone the measure alone already exceeds the available width,
 * which is the case the rule handles by being a *maximum* rather than a target.
 */
fun columnsIn(avail: Dp, measure: Dp, gap: Dp): Int =
    ((avail + gap).value / (measure + gap).value).toInt().coerceAtLeast(1)

/**
 * How many columns the enclosing [Page] is actually laying out, for content that needs to know.
 *
 * 1 everywhere except a desktop window wide enough for two *on a screen that asked for two*.
 * §4.8: a second column holds another view of the same destination and
 * never another destination, so opting in is per screen rather than global.
 */
val LocalPageColumns = compositionLocalOf { 1 }

/**
 * Where each [Page] on screen ends on the right, in window pixels, inside its padding: the desktop frame
 * puts its Add at the right-most of these (§3.6, owner 2026-10-22: "at the content edge"). Null outside a frame.
 */
val LocalContentEdges = staticCompositionLocalOf<SnapshotStateMap<Any, Float>?> { null }

/** The desktop Add's height while it is shown: a page's header row is at least this tall, so nothing starts beneath the button. */
val LocalHeaderMinHeight = compositionLocalOf { Dp.Unspecified }

/**
 * A tab's page: the title, then its sections in §4.2's order, scrolling as one column.
 *
 * §4.8's column rule: **a column is one measure wide and never wider**; surplus width is left
 * empty and the column centres. The cap is a *maximum*, so where the padded width is already
 * narrower than the measure (every phone up to about 496dp at the default sizes) it changes nothing.
 *
 * The cap is `measure + 2 × pad`, so the content inside the padding is exactly one measure.
 *
 * [header] (a tab's title row) scrolls with the page on a phone. Wider, it stays put above the scrolling column, so the
 * desktop's Add, drawn on the title's line, never has rows passing under it (owner, 2026-11-21).
 */
@Composable
fun Page(title: String?, maxColumns: Int = 1, header: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTokens.current
    val pinned = header != null && LocalWidthClass.current != WidthClass.COMPACT
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val n = columnsIn(maxWidth - t.spacing.pad * 2, t.measure, t.spacing.gap).coerceAtMost(maxColumns)
        val w = t.measure * n + t.spacing.gap * (n - 1) + t.spacing.pad * 2
        LocalProbe.current?.let { probe -> SideEffect { probe("page", mapOf("n" to n.toFloat(), "avail" to maxWidth.value, "maxColumns" to maxColumns.toFloat())) } }
        CompositionLocalProvider(LocalPageColumns provides n) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (pinned) Box(Modifier.widthIn(max = w).fillMaxWidth().padding(start = t.spacing.pad, end = t.spacing.pad, top = t.spacing.pad)) { header!!() }
                val probe = LocalProbe.current
                Box(
                    Modifier.fillMaxWidth().weight(1f)
                        // where the scrolling column starts: the tests check that nothing scrolls under the desktop's Add
                        .let { m -> if (probe == null) m else m.onGloballyPositioned { c -> probe("page:scroll", mapOf("y" to c.positionInWindow().y)) } }
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val edges = LocalContentEdges.current
                    val key = remember { Any() }
                    if (edges != null) DisposableEffect(key) { onDispose { edges.remove(key) } }
                    Column(
                        Modifier.widthIn(max = w).fillMaxWidth()
                            .let { m -> if (edges == null) m else m.onGloballyPositioned { c -> edges[key] = c.positionInWindow().x + c.size.width } }
                            .padding(start = t.spacing.pad, end = t.spacing.pad, bottom = t.spacing.pad)
                            .let { m -> if (pinned) m else m.padding(top = t.spacing.pad) },
                        verticalArrangement = Arrangement.spacedBy(t.spacing.gap),
                    ) {
                        if (title != null) Text(title, color = t.palette.txt, fontSize = t.type.title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = t.spacing.gap).semantics { heading() })
                        if (header != null && !pinned) header()
                        content()
                    }
                }
            }
        }
    }
}
