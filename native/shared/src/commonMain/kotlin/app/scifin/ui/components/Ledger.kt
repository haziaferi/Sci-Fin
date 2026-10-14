package app.scifin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.scifin.ui.LocalProbe
import app.scifin.ui.LocalTokens
import app.scifin.ui.LocalWidthClass
import app.scifin.ui.WidthClass
import app.scifin.ui.Lucide
import app.scifin.ui.LucideIcon
import app.scifin.ui.ROW_ADD_SHARE
import app.scifin.ui.SAVINGS_FIGURE_SCALE
import app.scifin.ui.plusColours
import app.scifin.ui.savingsFigure
import app.scifin.ui.switchChip

/*
 * Budget and Activity's ledger layout (§4.2):
 * labels at the left, every figure ending at one right-hand edge, and after each figure one fixed slot that holds
 * the row's action or nothing. A fill means "you can tap this"; information is not boxed.
 */

/** Reports this element's box to the render probe (`el:<name>`), for tools/scratch_score.py and tools/function_score.py. */
@Composable
fun Modifier.probeAs(name: String): Modifier {
    val p = LocalProbe.current ?: return this
    return this.onGloballyPositioned { c -> val o = c.positionInWindow(); p("el:$name", mapOf("x" to o.x, "y" to o.y, "w" to c.size.width.toFloat(), "h" to c.size.height.toFloat())) }
}

/** The label naming a row's figure: caption size, regular weight, so the figure carries the row. */
@Composable
fun LedgerLabel(text: String) {
    val t = LocalTokens.current
    Text(text, color = t.palette.txt2, fontSize = t.type.caption)
}

/** A section's rows share one container, hairlines between. */
@Composable
fun LedgerGroup(content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTokens.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(t.radii.lg)).background(t.palette.bord), verticalArrangement = Arrangement.spacedBy(t.strokes.ctl), content = content)
}

/** The slot after a figure: a round button's drawn width, holding the row's action or nothing. */
@Composable
fun FigureSlot(content: (@Composable () -> Unit)? = null) {
    val t = LocalTokens.current
    Box(Modifier.padding(start = t.spacing.gap).size(t.spacing.u * 3.75f), contentAlignment = Alignment.Center) { content?.invoke() }
}

/**
 * One ledger row: [name] at the left, [figure] at the shared right edge, then the slot. Filled only when it can be
 * tapped. [big] is the Savings figure: the figure size x [SAVINGS_FIGURE_SCALE], in the palette's Savings colour.
 */
@Composable
fun LedgerRow(
    onClick: (() -> Unit)?, name: @Composable () -> Unit, figure: String?, big: Boolean = false, slot: (@Composable () -> Unit)? = null,
    under: (@Composable ColumnScope.() -> Unit)? = null, probeName: String? = null, clickLabel: String? = null,
) {
    val t = LocalTokens.current; val p = t.palette
    var m = Modifier.fillMaxWidth().heightIn(min = t.spacing.target)
    if (onClick != null) m = m.background(if (big) p.surf else p.surf2).clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
    Column(m.padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u)) {
        Row(Modifier.fillMaxWidth().heightIn(min = t.spacing.target - t.spacing.u * 2), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { name() }
            if (figure != null) AmountText(
                figure, color = if (big) p.savingsFigure else p.txt, fontSize = if (big) t.type.figure * SAVINGS_FIGURE_SCALE else t.type.body, fontWeight = FontWeight.Bold,
                modifier = if (probeName != null) Modifier.probeAs(probeName) else Modifier,
            )
            FigureSlot(slot)
        }
        under?.invoke(this)
    }
}

/** A row's + (Add to a goal): the round button in the row-add fill. */
@Composable
fun RowAdd(description: String, onClick: () -> Unit) {
    RoundIconButton(Lucide.PLUS, description, colours = LocalTokens.current.palette.plusColours(ROW_ADD_SHARE), onClick = onClick)
}

/** The floating +: logs an expense or income (M3, the top action), in the Confirm button's colours. Drawn and touched at the target size. */
@Composable
fun FloatingAdd(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = LocalTokens.current
    val fill = t.palette.acc; val glyph = t.palette.onAcc
    Box(
        modifier.probeAs("fab").size(t.spacing.target).clip(CircleShape).background(fill).clickable(onClick = onClick)
            .semantics { contentDescription = "Log"; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { LucideIcon(Lucide.PLUS, tint = glyph, size = t.icons.lg) }
}

/**
 * Budget's Envelopes | Categories switch: no track, the selected option on [switchChip], caption-size
 * labels -- the quietest form the floors allow, since the switch ranks last among Budget's elements.
 */
@Composable
fun ViewSwitch(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current; val p = t.palette
    Row(modifier.fillMaxWidth().padding(t.spacing.u * 0.375f).selectableGroup()) {
        for ((id, label) in options) {
            val on = id == selected
            Box(
                Modifier.weight(1f)
                    .selectableAtTarget(on, Role.Tab, t.spacing.target - t.spacing.u * 0.75f, if (on) Modifier.background(p.switchChip, RoundedCornerShape(t.radii.sm)) else Modifier) { onSelect(id) },
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) p.txt else p.txt2, fontSize = t.type.caption, fontWeight = FontWeight.Bold, maxLines = 1) }
        }
    }
}

/** Information with the accent kept as a rule at its start, unfilled (it cannot be tapped). */
@Composable
fun RuledNote(text: String, modifier: Modifier = Modifier) {
    val t = LocalTokens.current; val p = t.palette
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(t.strokes.emph).fillMaxHeight().background(p.acc))
        Text(text, color = p.txt, fontSize = t.type.body, lineHeight = t.type.body * 1.6, modifier = Modifier.padding(start = t.spacing.u * 1.25f).measureCap(t))
    }
}

/** A one-line note whose whole line is the target, the info glyph before it and a chevron after: it opens [onClick]'s sheet. */
@Composable
fun NoteLine(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    Row(
        modifier.fillMaxWidth().heightIn(min = t.spacing.target).clickable(role = Role.Button, onClick = onClick).padding(horizontal = t.spacing.u * 0.25f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u),
    ) {
        LucideIcon(Lucide.INFO, tint = p.txt2, size = t.icons.sm)
        Text(text, color = p.txt2, fontSize = t.type.caption, modifier = Modifier.weight(1f))
        LucideIcon(Lucide.CHEVRON_RIGHT, tint = p.txt2, size = t.icons.sm)
    }
}

/** The Savings row, the same on Budget and Activity (§3.6, §3.7): a tap opens what [onClick] opens. */
@Composable
fun SavingsLedgerRow(amount: String, onClick: () -> Unit, clickLabel: String, slot: (@Composable () -> Unit)? = null) {
    val t = LocalTokens.current; val p = t.palette
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(t.radii.lg))) {
        LedgerRow(onClick = onClick, clickLabel = clickLabel, name = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u / 2)) {
                LedgerLabel("Savings"); LucideIcon(Lucide.PENCIL, tint = p.txt3, size = t.spacing.u * 1.6f)
            }
        }, figure = amount, big = true, probeName = "savings", slot = slot)
    }
}

/**
 * Room at the end of the column the floating + sits over, so it never covers the last row once scrolled to the end.
 */
@Composable
fun FloatingAddRoom() {
    val t = LocalTokens.current
    val over = LocalWidthClass.current == WidthClass.COMPACT   // a desktop has no floating + (§3.6)
    if (over) Box(Modifier.height(t.spacing.target + t.spacing.gap))
}
