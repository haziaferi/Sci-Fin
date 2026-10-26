package app.scifin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected as selectedState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.scifin.i18n.tr
import app.scifin.ui.LocalProbe
import app.scifin.ui.LocalResting
import app.scifin.ui.LocalTokens
import app.scifin.ui.LocalWidthClass
import app.scifin.ui.Lucide
import app.scifin.ui.LucideIcon
import app.scifin.ui.WidthClass
import app.scifin.ui.selectedWash

/*
 * The prototype's rows: `.rrow.nav-row` (a push), `.row-toggle` (a setting with its control on
 * the right), `.switch`, `.pill-toggle`, `.chip-row`, `.feasibility-note`, `.top-row` with its
 * back chevron, `.avatar-circle`. Geometry is the prototype's, on `u` (8dp at 360dp).
 */

/** `.rrow.nav-row`: icon, label, a right-hand caption with a chevron; the whole row pushes. */
@Composable
fun NavRow(icon: Lucide?, label: String, caption: String, leading: (@Composable () -> Unit)? = null, decorative: Boolean = false, selected: Boolean = false, onClick: () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    val wash = selectedWash(selected)
    val showsIcon = leading != null || (icon != null && !(decorative && t.lowDistraction))
    val lead = if (leading != null) avatarSize else t.icons.md
    val floor = with(LocalDensity.current) {
        (if (showsIcon) (lead + t.spacing.u * 1.125f).roundToPx() else 0) + longestWordPx(label, TextStyle(fontSize = t.type.body, fontWeight = FontWeight.Bold))
    }
    val gapPx = with(LocalDensity.current) { t.spacing.gap.roundToPx() }
    val shape = RoundedCornerShape(t.radii.lg)
    // §2.5: the caption goes under the label rather than split one of its words
    BesideOrUnder(
        floorPx = floor, gapPx = gapPx,
        modifier = Modifier.fillMaxWidth().heightIn(min = t.spacing.target).background(p.surf2, shape)
            .then(if (wash != null) Modifier.background(wash, shape) else Modifier).clickable(onClick = onClick).semantics { role = Role.Button; selectedState = selected }
            .padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f),
        label = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 1.125f)) {
                if (leading != null) leading() else if (showsIcon && icon != null) LucideIcon(icon, tint = p.txt2, size = t.icons.md)
                Text(label, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
            }
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u / 2)) {
                // the caption yields to the label: at most two lines, at most 18.75u wide
                Text(caption, color = p.txt2, fontSize = t.type.caption, maxLines = 2, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = t.spacing.u * 18.75f))
                LucideIcon(Lucide.CHEVRON_RIGHT, tint = p.txt2, size = t.icons.sm)
            }
        },
    )
}

/** The enclosing [SettingRow]'s title, which a [Switch] in its control slot takes as its accessible name. */
private val LocalControlLabel = compositionLocalOf<String?> { null }

/** `.row-toggle`: icon, a title with a one-line description, and the control on the right. */
@Composable
fun SettingRow(icon: Lucide, title: String, description: String, control: @Composable () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    val density = LocalDensity.current
    // §2.5 at large text sizes: the label keeps a floor of 18u; a control that would take it away goes under the label instead
    val gapPx = with(density) { (t.spacing.u * 1.25f).roundToPx() }
    BesideOrUnder(
        floorPx = with(density) { (t.spacing.u * 18).roundToPx() }, gapPx = gapPx, stackGapPx = gapPx,
        modifier = Modifier.fillMaxWidth().heightIn(min = t.spacing.target).background(p.surf, RoundedCornerShape(t.radii.lg))
            .padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.375f),
        label = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 1.125f)) {
                LucideIcon(icon, tint = p.txt2, size = t.icons.md)
                Column {
                    Text(title, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
                    Text(description, color = p.txt2, fontSize = t.type.caption)
                }
            }
        },
        trailing = { CompositionLocalProvider(LocalControlLabel provides title) { control() } },
    )
}

/** `.switch`: 4.75u × 2.75u, the knob 2.25u, accent when on. Wrapped in a target-sized hit area; [label] is its accessible name. */
@Composable
fun Switch(on: Boolean, label: String? = LocalControlLabel.current, onToggle: () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    val ru = t.spacing.u
    Box(
        Modifier.heightIn(min = t.spacing.target).toggleable(value = on, role = Role.Switch) { onToggle() }
            .let { m -> if (label == null) m else m.semantics { contentDescription = label } },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(ru * 4.75f).heightIn(min = ru * 2.75f).background(if (on) p.acc else p.surf2, CircleShape)) {
            Box(
                Modifier.padding(ru * 0.25f).offset(x = if (on) ru * 2f else 0.dp).size(ru * 2.25f)
                    .background(if (on) p.onAcc else p.surf, CircleShape),
            )
        }
    }
}

/** `.pill-toggle`: a small segmented pill for two to four short choices. */
@Composable
fun PillToggle(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    Row(Modifier.background(p.surf2, CircleShape).padding(horizontal = t.spacing.u / 2, vertical = t.spacing.u * 0.625f).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(t.spacing.u / 4)) {
        for ((id, label) in options) {
            val on = id == selected
            Box(
                Modifier.selectableAtTarget(on, Role.RadioButton, t.spacing.target - t.spacing.u * 1.25f, Modifier.background(if (on) p.acc else p.surf2, CircleShape)) { onSelect(id) }
                    .padding(horizontal = t.spacing.u * 1.25f, vertical = t.spacing.u * 0.625f),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) p.onAcc else p.txt2, fontSize = t.type.caption, fontWeight = FontWeight.Bold) }
        }
    }
}

/** `.chip-row` / `.chip`: outlined pills that wrap, the chosen one on a 12 % accent wash. */
@Composable
fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 0.75f), verticalArrangement = Arrangement.spacedBy(t.spacing.u * 0.75f)) {
        for ((id, label) in options) {
            val on = id == selected
            val visual = Modifier.background(if (on) p.acc.copy(alpha = 0.12f) else Color.Transparent, CircleShape).border(t.strokes.ctl, if (on) p.acc else p.bordStrong, CircleShape)
            Box(
                Modifier.selectableAtTarget(on, Role.RadioButton, t.spacing.target - t.spacing.u, visual) { onSelect(id) }
                    .padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 0.75f),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) p.acc else p.txt2, fontSize = t.type.body, fontWeight = FontWeight.Bold) }
        }
    }
}

/** `.feasibility-note`: §2.3's treatment -- a 14 % accent wash, body text colour, never a warning colour. */
@Composable
fun FeasibilityNote(text: String, modifier: Modifier = Modifier) {
    val t = LocalTokens.current; val p = t.palette
    val pr = LocalProbe.current
    Box(
        modifier.fillMaxWidth().background(p.surf, RoundedCornerShape(t.radii.md)).background(p.acc.copy(alpha = 0.14f), RoundedCornerShape(t.radii.md))
            .let { m -> if (pr == null) m else m.onGloballyPositioned { c -> pr("fill:info", mapOf("y" to c.positionInWindow().y, "h" to c.size.height.toFloat())) } }
            .padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f),
    ) {
        Text(text, color = p.txt, fontSize = t.type.body, lineHeight = t.type.body * 1.6, modifier = Modifier.measureCap(t))
    }
}

/**
 * A tab page's title (Budget, Activity, Learn, Reports). On a phone it ends with the Settings glyph, level with the
 * title (§4.1: tab pages only, not pushed pages or Settings itself); a wide window has Settings in the rail and draws no glyph.
 */
@Composable
fun TabTitle(icon: Lucide?, title: String, onSettings: () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    Row(Modifier.fillMaxWidth().padding(bottom = t.spacing.gap * 1.4f).heightIn(min = LocalHeaderMinHeight.current), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 0.875f)) {
        if (icon != null) EntityIcon(icon, size = t.icons.lg)
        Text(title, color = p.txt, fontSize = t.type.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).semantics { heading() })
        if (LocalWidthClass.current == WidthClass.COMPACT) Box(
            Modifier.size(t.spacing.target).clickable(onClick = onSettings).semantics { contentDescription = tr("Settings"); role = Role.Button }.probeAs("settings-corner"),
            contentAlignment = Alignment.Center,
        ) { LucideIcon(Lucide.SETTINGS, tint = p.txt2, size = t.icons.md) }
    }
}

/** `.top-row` with the back chevron: every pushed screen's header, so nothing new is learned to get out of one. */
@Composable
fun TopRow(title: String, onBack: (() -> Unit)?, trailing: (@Composable () -> Unit)? = null) {
    val t = LocalTokens.current; val p = t.palette
    // the second column's resting view has nothing to go back to (§4.8)
    @Suppress("NAME_SHADOWING") val onBack = if (LocalResting.current) null else onBack
    Row(Modifier.fillMaxWidth().padding(bottom = t.spacing.gap * 1.4f).heightIn(min = LocalHeaderMinHeight.current), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        if (onBack != null) {
            Box(Modifier.size(t.spacing.target).background(p.surf2, CircleShape).clickable(onClick = onBack).semantics { contentDescription = tr("Back"); role = Role.Button }, contentAlignment = Alignment.Center) { LucideIcon(Lucide.CHEVRON_LEFT, tint = p.txt2, size = t.icons.md) }
        }
        Text(title, color = p.txt, fontSize = t.type.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).then(if (onBack != null) Modifier.padding(horizontal = t.spacing.gap) else Modifier).semantics { heading() })
        if (trailing != null) trailing() else if (onBack != null) Box(Modifier.size(t.spacing.target))
    }
}

/** The Profile avatar's diameter, 4.25u, read by [NavRow] too so a row can leave room for it. */
private val avatarSize: Dp @Composable get() = LocalTokens.current.spacing.u * 4.25f

/** `.avatar-circle`: the Profile's initial on the accent. */
@Composable
fun Avatar(initial: String) {
    val t = LocalTokens.current; val p = t.palette
    Box(Modifier.size(avatarSize).background(p.acc, CircleShape), contentAlignment = Alignment.Center) {
        Text(initial, color = p.onAcc, fontSize = t.type.body, fontWeight = FontWeight.Bold)
    }
}

/** A dashed "+ New …" row. */
@Composable
fun AddRow(label: String, onClick: () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    val pr = LocalProbe.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = t.spacing.target).border(t.strokes.ctl, p.bordStrong, RoundedCornerShape(t.radii.lg)).clickable(onClick = onClick).semantics { role = Role.Button }
            .let { m -> if (pr == null) m else m.onGloballyPositioned { c -> val o = c.positionInWindow(); pr("el:add:$label", mapOf("x" to o.x, "y" to o.y, "w" to c.size.width.toFloat(), "h" to c.size.height.toFloat())) } }
            .padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        LucideIcon(Lucide.PLUS, tint = p.txt2, size = t.icons.md)
        Text(label, color = p.txt2, fontSize = t.type.body, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = t.spacing.u * 0.75f))
    }
}
