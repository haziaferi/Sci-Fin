package app.scifin.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/*
 * §5's charts, drawn on Canvas: no chart library is in the offline cache, and the prototype's
 * own drawings are a stroke sparkline and two-bar columns in one accent at two opacities.
 */

/** The prototype's `sparkline()`: one `strokes.line` stroke through the points, min-to-max on the height, no axes. */
@Composable
fun Sparkline(points: List<Long>, labels: List<String> = emptyList(), modifier: Modifier = Modifier) {
    val t = LocalTokens.current; val p = t.palette
    Column(modifier.fillMaxWidth().padding(top = t.spacing.gap)) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(ChartShapes.SPARKLINE).padding(horizontal = t.spacing.u / 2).probeSize(LocalProbe.current, "sparkline")) {
            if (points.size < 2) return@Canvas
            val g = chartGeometry(points, size.width, size.height)
            g.zeroY?.let { drawLine(p.bordStrong, Offset(0f, it), Offset(size.width, it), strokeWidth = t.strokes.hair.toPx().coerceAtLeast(1f)) }
            val path = Path()
            g.points.forEachIndexed { i, pt -> if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y) }
            drawPath(path, p.acc, style = Stroke(width = t.strokes.line.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(p.acc, radius = (t.spacing.u / 2).toPx(), center = g.endpoint)
        }
        if (labels.size == points.size) Row(Modifier.fillMaxWidth().padding(top = t.spacing.u * 0.75f), horizontalArrangement = Arrangement.SpaceBetween) {
            for (l in labels) Text(l, color = p.txt2, fontSize = t.type.caption)
        }
    }
}

/** Where a sparkline's points land: x spread across the width, y by value, plus the endpoint and the zero line if the values cross it. */
data class ChartGeometry(val points: List<Offset>, val endpoint: Offset, val zeroY: Float?)

fun chartGeometry(values: List<Long>, width: Float, height: Float): ChartGeometry {
    val max = maxOf(values.max(), 1L); val min = minOf(values.min(), 0L)
    val range = (max - min).takeIf { it != 0L } ?: 1L
    val stepX = if (values.size > 1) width / (values.size - 1) else 0f
    fun y(v: Long) = height - ((v - min).toFloat() / range) * height
    val pts = values.mapIndexed { i, v -> Offset(i * stepX, y(v)) }
    val crosses = values.any { it < 0 } && values.any { it > 0 }
    return ChartGeometry(pts, pts.last(), if (crosses) y(0) else null)
}

/** §5.4's columns: six months, two bars each -- one accent, two opacities, because income and expenses are not a good/bad pair. */
@Composable
fun TwoBarColumns(months: List<Triple<String, Long, Long>>) {
    val t = LocalTokens.current; val p = t.palette
    val maxV = maxOf(months.maxOfOrNull { maxOf(it.second, it.third) } ?: 0L, 1L)
    Row(
        Modifier.fillMaxWidth().aspectRatio(ChartShapes.MONTH_COLUMNS).probeSize(LocalProbe.current, "month-columns").background(p.surf, RoundedCornerShape(t.radii.lg)).padding(start = t.spacing.u * 1.5f, end = t.spacing.u * 1.5f, top = t.spacing.pad, bottom = t.spacing.u * 1.25f),
        horizontalArrangement = Arrangement.spacedBy(t.spacing.gap), verticalAlignment = Alignment.Bottom,
    ) {
        for ((label, inc, exp) in months) Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 0.375f), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.width(t.spacing.u * 1.125f).fillMaxHeight(inc.toFloat() / maxV).background(p.acc, RoundedCornerShape(topStart = t.radii.micro, topEnd = t.radii.micro)))
                Box(Modifier.width(t.spacing.u * 1.125f).fillMaxHeight(exp.toFloat() / maxV).background(p.acc.copy(alpha = 0.4f), RoundedCornerShape(topStart = t.radii.micro, topEnd = t.radii.micro)))
            }
            Text(label, color = p.txt2, fontSize = t.type.caption, modifier = Modifier.padding(top = t.spacing.u / 2))
        }
    }
    Row(Modifier.padding(top = t.spacing.u * 1.25f, start = t.spacing.u * 0.25f), horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 2)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 0.75f)) { Box(Modifier.size(t.spacing.u * 1.25f).background(p.acc, RoundedCornerShape(t.radii.micro))); Text("Income", color = p.txt, fontSize = t.type.body) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 0.75f)) { Box(Modifier.size(t.spacing.u * 1.25f).background(p.acc.copy(alpha = 0.4f), RoundedCornerShape(t.radii.micro))); Text("Expenses", color = p.txt, fontSize = t.type.body) }
    }
}
