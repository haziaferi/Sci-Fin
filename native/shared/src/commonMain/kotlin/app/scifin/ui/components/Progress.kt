package app.scifin.ui.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import app.scifin.i18n.tr
import app.scifin.i18n.trf
import app.scifin.ui.probeSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.CornerRadius
import app.scifin.model.Money
import app.scifin.ui.ChartShapes
import app.scifin.ui.LocalProbe
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.LucideIcon
import app.scifin.ui.formatMoney

/*
 * §2.1's bar: accent fill to the reference, a hatch past it, and a tick at the reference --
 * the app's only non-colour encoding of state, drawn in `txt` at the emphasis weight. The
 * caption states the difference and nothing else (§2.2): "€100 past plan", "€40 left of plan".
 */

@Composable
fun ProgressBar(amount: Money, reference: Money) {
    val t = LocalTokens.current; val p = t.palette
    val ref = if (reference <= 0) 1L else reference
    val over = amount > ref
    val frac = if (over) ref.toFloat() / amount.toFloat() else (amount.toFloat() / ref.toFloat()).coerceIn(0f, 1f)
    val micro = t.radii.micro
    Box(Modifier.fillMaxWidth().aspectRatio(ChartShapes.BAR)) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight().probeSize(LocalProbe.current, "bar")) {
            val w = size.width; val h = size.height; val r = micro.toPx()
            drawRoundRect(p.surf2, size = Size(w, h), cornerRadius = CornerRadius(r))
            val fillW = w * frac
            drawRoundRect(p.acc, size = Size(fillW, h), cornerRadius = CornerRadius(r))
            if (over) {
                // the hatch over the past-reference stretch: diagonal stripes `r` wide, `2r` apart along the bar
                clipRect(left = fillW, top = 0f, right = w, bottom = h) {
                    var x = fillW - h
                    while (x < w) {
                        drawLine(p.acc.copy(alpha = 0.85f), Offset(x, h), Offset(x + h, 0f), strokeWidth = r)
                        x += r * 2
                    }
                }
            }
            if (over || amount == ref) {
                val tx = if (over) fillW else w
                drawRect(p.txt, topLeft = Offset(tx - t.strokes.emph.toPx() / 2, 0f), size = Size(t.strokes.emph.toPx(), h))   // the tick spans the bar, no overhang
            }
        }
    }
}

fun progressCaption(amount: Money, reference: Money, refWord: String, currency: String): String {
    if (amount == reference && reference > 0) return tr("As planned")
    val diff = if (amount > reference) amount - reference else reference - amount
    val m = formatMoney(diff, currency)
    // [refWord] is a token; "plan" (the only one in use) reads as whole sentences so a language can order them
    return when {
        refWord == "plan" -> if (amount > reference) trf("{0} past plan", m) else trf("{0} left of plan", m)
        else -> if (amount > reference) trf("{0} past {1}", m, refWord) else trf("{0} left of {1}", m, refWord)
    }
}

/** `.prow`: icon + name, the + (§3.6's entry on this row), the bar, the caption, an optional learn link. */
@Composable
fun ProgressRow(icon: Lucide?, label: String, amount: Money, reference: Money, refWord: String, currency: String, onQuickAdd: (() -> Unit)?, onClick: () -> Unit, learnLink: Pair<String, () -> Unit>? = null) {
    val t = LocalTokens.current; val p = t.palette
    Column(Modifier.fillMaxWidth().background(p.surf, RoundedCornerShape(t.radii.lg)).clickable(onClick = onClick).semantics { role = Role.Button }.padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f)) {
        Row(Modifier.fillMaxWidth().padding(bottom = t.spacing.gap), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
                if (icon != null) EntityIcon(icon)
                Text(label, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
            }
            if (onQuickAdd != null) RoundIconButton(Lucide.PLUS, trf("Spent on {0}", label), onClick = onQuickAdd)
        }
        ProgressBar(amount, reference)
        Caption(progressCaption(amount, reference, refWord, currency), Modifier.padding(top = t.spacing.u * 0.875f))
        if (learnLink != null) LearnLink(learnLink.first, learnLink.second)
    }
}

/**
 * §2.5's low-distraction mode: an entity's icon beside its name is decoration -- the name carries
 * the meaning -- so it is the one thing the mode removes. The icon picker's glyphs are content
 * and stay. Screen readers never hear these icons either way.
 */
@Composable
fun EntityIcon(icon: Lucide, size: Dp = LocalTokens.current.icons.md) {
    val t = LocalTokens.current
    if (!t.lowDistraction) LucideIcon(icon, tint = t.palette.txt2, size = size)
}

/** `.learn-link`: the accent-coloured "Why … →" line. Its hit area is 3u tall, under the platform target. */
@Composable
fun LearnLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Text(label + "\u00A0→", color = t.palette.acc, fontSize = t.type.caption, fontWeight = FontWeight.Bold, textDecoration = LEARN_LINK_DECORATION, modifier = modifier.heightIn(min = t.spacing.u * 3).clickable(onClick = onClick).padding(top = t.spacing.u * 0.875f))
}

/** §2.5: a link needs a cue that is not colour, since an accent can sit one step from `txt2`. */
val LEARN_LINK_DECORATION: TextDecoration = TextDecoration.Underline

/** `.placeholder-card`: a dashed, centred, quiet card for a section that is off. */
@Composable
fun PlaceholderCard(text: String, modifier: Modifier = Modifier) {
    val t = LocalTokens.current; val p = t.palette
    Box(modifier.fillMaxWidth().background(p.surf2, RoundedCornerShape(t.radii.lg)).border(t.strokes.hair, p.bord, RoundedCornerShape(t.radii.lg)).padding(t.spacing.u * 2), contentAlignment = Alignment.Center) {
        Text(text, color = p.txt2, fontSize = t.type.body, lineHeight = t.type.body * 1.6, textAlign = TextAlign.Center)
    }
}

/** `.checkbox-row`: a labelled checkbox at target height. */
@Composable
fun CheckboxRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    Row(Modifier.fillMaxWidth().heightIn(min = t.spacing.target).toggleable(value = checked, role = Role.Checkbox) { onToggle() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
        CheckboxMark(checked)
        Text(label, color = p.txt2, fontSize = t.type.body, fontWeight = FontWeight.Bold)
    }
}

/** The box and tick alone; whatever holds it owns the toggle, its role and its label. */
@Composable
fun CheckboxMark(checked: Boolean) {
    val t = LocalTokens.current; val p = t.palette
    Box(
        Modifier.size(t.spacing.u * 2.375f).background(if (checked) p.acc else p.surf, RoundedCornerShape(t.radii.tiny)).border(t.strokes.ctl, if (checked) p.acc else p.bordStrong, RoundedCornerShape(t.radii.tiny)),
        contentAlignment = Alignment.Center,
    ) { if (checked) LucideIcon(Lucide.CHECK, tint = p.onAcc, size = t.icons.xs) }
}

/** A feasibility note with a disclosure: the summary is always on screen, the detail behind the chevron (§3.5's note). */
@Composable
fun DisclosureNote(summary: String, detail: String, open: Boolean, onToggle: () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    Column(
        Modifier.fillMaxWidth().background(p.surf, RoundedCornerShape(t.radii.md)).background(p.acc.copy(alpha = 0.14f), RoundedCornerShape(t.radii.md))
            .clickable(role = Role.Button) { onToggle() }
            .semantics { if (open) collapse { onToggle(); true } else expand { onToggle(); true } }
            .padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = t.spacing.target - t.spacing.u * 2.5f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(summary, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            LucideIcon(Lucide.CHEVRON_RIGHT, tint = p.txt, size = t.icons.md, modifier = Modifier.padding(start = t.spacing.gap).rotate(if (open) 90f else 0f))
        }
        if (open) Text(detail, color = p.txt, fontSize = t.type.body, lineHeight = t.type.body * 1.6, modifier = Modifier.padding(top = t.spacing.u))
    }
}

/** `.inline-learn`: the expanded body under a learn link. */
@Composable
fun InlineLearn(paragraphs: List<String>, source: String?) {
    val t = LocalTokens.current; val p = t.palette
    Column(Modifier.fillMaxWidth().padding(top = t.spacing.gap).background(p.surf2, RoundedCornerShape(t.radii.md)).padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
        for (para in paragraphs) Text(para, color = p.txt2, fontSize = t.type.body, lineHeight = t.type.body * 1.6, modifier = Modifier.measureCap(t))
        if (source != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u / 2)) {
            LucideIcon(Lucide.INFO, tint = p.txt2, size = t.icons.xs); Text(source, color = p.txt2, fontSize = t.type.caption)
        }
    }
}
