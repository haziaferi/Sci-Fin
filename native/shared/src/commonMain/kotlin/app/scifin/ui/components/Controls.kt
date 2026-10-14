package app.scifin.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.offset
import app.scifin.ui.LocalProbe
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.LucideIcon
import app.scifin.ui.SciFinTokens

/*
 * The prototype's controls, one to one: `.btn-primary` / `.btn-secondary` / `.btn-ghost`,
 * `.qa-btn`, `.seg`, `.field` / `.field-cur`, `.info-note`, `.section-label`. Every colour is
 * a token (§2.5) except the sheet scrim's. Hit areas reach the platform target (§2.5's targets
 * never shrink with density); the one exception, [LearnLink], says so.
 */

enum class ButtonKind { PRIMARY, SECONDARY, GHOST }

@Composable
fun Button(label: String, kind: ButtonKind = ButtonKind.PRIMARY, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    val bg = if (kind == ButtonKind.PRIMARY) p.acc else Color.Transparent
    val fg = when (kind) { ButtonKind.PRIMARY -> p.onAcc; ButtonKind.SECONDARY -> p.acc; ButtonKind.GHOST -> p.txt2 }
    var m = modifier.heightIn(min = t.spacing.target).background(bg, RoundedCornerShape(t.radii.md))
    if (kind == ButtonKind.SECONDARY) m = m.border(t.strokes.ctl, p.acc, RoundedCornerShape(t.radii.md))
    Box(
        m.clickable(enabled = enabled, onClick = onClick).semantics { role = Role.Button }.padding(horizontal = t.spacing.u * 2.25f, vertical = t.spacing.u * 1.5f),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg.copy(alpha = if (enabled) fg.alpha else fg.alpha * 0.45f), fontSize = t.type.body, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** `.btn-row`: the Cancel / Confirm pair §3.6 requires, each half the width. */
@Composable
fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    val t = LocalTokens.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(t.spacing.gap), content = content)
}

/** `.qa-btn`: the round icon button on a row (quick-add, Income, edit). [description] is what a screen reader says -- the prototype's `title`. */
@Composable
fun RoundIconButton(icon: Lucide, description: String, modifier: Modifier = Modifier, colours: Pair<Color, Color>? = null, onClick: () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    // §2.5: a 3.75u circle around a 2.5u glyph, laid out at that size, so it sets no row's height; the hit area is the
    // platform minimum, grown leftward and centred vertically, as the prototype's `.qa-btn::after` does. Beside another
    // round button, the left one is on top (zIndex at the call).
    val drawn = t.spacing.u * 3.75f
    val reach = maxOf(t.spacing.target, drawn)
    val press = remember { MutableInteractionSource() }
    Box(
        modifier.layout { m, _ ->
            val d = drawn.roundToPx(); val r = reach.roundToPx()
            val box = m.measure(Constraints.fixed(r, r))
            layout(d, d) { box.place(d - r, (d - r) / 2) }
        }.clickable(interactionSource = press, indication = null, onClick = onClick).semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.CenterEnd,
    ) {
        val probe = LocalProbe.current
        Box(Modifier.size(drawn).let { m -> if (probe == null) m else m.onGloballyPositioned { c -> val o = c.positionInWindow(); probe("round:$description", mapOf("x" to o.x, "y" to o.y, "w" to c.size.width.toFloat(), "h" to c.size.height.toFloat())) } }.clip(CircleShape).background(colours?.first ?: p.surf2).indication(press, LocalIndication.current), contentAlignment = Alignment.Center) {
            LucideIcon(icon, tint = colours?.second ?: p.txt2, size = t.spacing.u * 2.5f)
        }
    }
}

/** Lets what follows answer [extra] beyond its box above and below, without taking layout room: this reports the inner height less 2 × [extra]. */
fun Modifier.reachVertically(extra: Dp): Modifier = layout { m, c ->
    val e = extra.roundToPx()
    val p = m.measure(c.offset(vertical = 2 * e))
    layout(p.width, (p.height - 2 * e).coerceAtLeast(0)) { p.place(0, -e) }
}

/**
 * An option drawn [drawn] tall that answers taps at the platform target: the hit area grows above and below, centred,
 * and takes no layout room. [visual] (fill, border) and the press/hover indication stay on the drawn box.
 */
@Composable
fun Modifier.selectableAtTarget(selected: Boolean, role: Role, drawn: Dp, visual: Modifier, onClick: () -> Unit): Modifier {
    val t = LocalTokens.current
    val extra = ((t.spacing.target - drawn) / 2).coerceAtLeast(0.dp)
    val press = remember { MutableInteractionSource() }
    return reachVertically(extra).selectable(selected = selected, interactionSource = press, indication = null, role = role, onClick = onClick)
        .padding(vertical = extra).heightIn(min = drawn).then(visual).indication(press, LocalIndication.current)
}

/** `.seg`: the two- or three-way control (§4.3's tabs, §3.6's direction). */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current; val p = t.palette
    // §2.5: one size for every option, the largest that fits its cell, so no word breaks
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val cellPx = with(LocalDensity.current) { ((maxWidth - t.spacing.u * 0.75f) / options.size - t.spacing.u).toPx() }
        val size = remember(options, cellPx, t.type.body, measurer) {
            fitLabelSize(options.map { it.second }, cellPx, t.type.body) { text, sz -> measurer.measure(text, TextStyle(fontSize = sz, fontWeight = FontWeight.Bold)).size.width.toFloat() }
        }
        Row(Modifier.fillMaxWidth().background(p.surf2, RoundedCornerShape(t.radii.md)).padding(t.spacing.u * 0.375f).selectableGroup()) {
            for ((id, label) in options) {
                val on = id == selected
                Box(
                    Modifier.weight(1f)
                        .selectableAtTarget(on, Role.Tab, t.spacing.target - t.spacing.u * 0.75f, Modifier.background(if (on) p.surf else p.surf2, RoundedCornerShape(t.radii.sm))) { onSelect(id) }
                        .padding(vertical = t.spacing.u * 0.875f),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = if (on) p.txt else p.txt2, fontSize = size, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = t.spacing.u * 0.25f)) }
            }
        }
    }
}

/** `.field-label` over a control. */
@Composable
fun FieldLabel(text: String) {
    val t = LocalTokens.current
    Text(text, color = t.palette.txt2, fontSize = t.type.body, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = t.spacing.u * 0.75f))
}

/** `.field`: a bordered single-line text field; [prefix] gives `.field-cur`'s currency symbol; [label] is its name for a screen reader (the [FieldLabel] text). */
@Composable
fun TextField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "", prefix: String? = null, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, label: String? = null) {
    val t = LocalTokens.current; val p = t.palette
    Row(
        modifier.fillMaxWidth().heightIn(min = t.spacing.target).background(p.surf, RoundedCornerShape(t.radii.md))
            .border(t.strokes.ctl, p.bordStrong, RoundedCornerShape(t.radii.md)).padding(horizontal = t.spacing.u * 1.5f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.gap),
    ) {
        if (prefix != null) Text(prefix, color = p.txt2, fontSize = t.type.body, fontWeight = FontWeight.Bold)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = singleLine,
            textStyle = TextStyle(color = p.txt, fontSize = t.type.body), cursorBrush = SolidColor(p.acc),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.weight(1f).let { m -> if (label == null) m else m.semantics { contentDescription = label } },
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, color = p.txt3, fontSize = t.type.body); inner() } },
        )
    }
}

/** `.info-note`: the info glyph beside a quiet sentence on `surf2`. */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    val t = LocalTokens.current; val p = t.palette
    val probe = LocalProbe.current
    Row(
        modifier.fillMaxWidth().background(p.surf2, RoundedCornerShape(t.radii.md))
            .let { m -> if (probe == null) m else m.onGloballyPositioned { c -> probe("fill:info", mapOf("y" to c.positionInWindow().y, "h" to c.size.height.toFloat())) } }
            .padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f),
        horizontalArrangement = Arrangement.spacedBy(t.spacing.gap),
    ) {
        LucideIcon(Lucide.INFO, tint = p.txt2, size = t.spacing.u * 1.875f, modifier = Modifier.padding(top = t.spacing.u * 0.15f))
        Text(
            text, color = p.txt2, fontSize = t.type.body, lineHeight = t.type.body * 1.5, modifier = Modifier.measureCap(t),
            onTextLayout = { r -> probe?.invoke("note-text", mapOf("w" to r.size.width.toFloat(), "lines" to r.lineCount.toFloat(), "chars0" to r.getLineEnd(0, visibleEnd = true).toFloat(), "len" to text.length.toFloat())) },
        )
    }
}

/** §4.8's measure cap: prose never runs the full width of a window -- 52ch, as the prototype's `--measure`. */
fun Modifier.measureCap(t: SciFinTokens): Modifier = this.widthIn(max = t.measure)

/** `.prow-caption`: a caption under a field or row. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Text(text, color = t.palette.txt2, fontSize = t.type.caption, lineHeight = t.type.caption * 1.45, modifier = modifier.measureCap(t))
}

/**
 * The label naming a card's headline figure -- "Savings", "Per paycheck (monthly)", "Total": the
 * prototype's `.lbl`, one job with one style (§2.5).
 */
@Composable
fun FigureLabel(text: String) {
    val t = LocalTokens.current
    Text(text, color = t.palette.txt2, fontSize = t.type.body, fontWeight = FontWeight.Bold)
}

/** `.section-label`: uppercase small heading over a section, with an optional right-hand slot. */
@Composable
fun SectionLabel(text: String, trailing: (@Composable () -> Unit)? = null) {
    val t = LocalTokens.current
    val probe = LocalProbe.current
    Row(
        Modifier.fillMaxWidth()
            .padding(top = t.spacing.u * 1.5f, start = t.spacing.u * 0.25f, bottom = t.spacing.u * 0.25f)
            .let { m -> if (probe == null) m else m.onGloballyPositioned { c -> probe("section:$text", mapOf("y" to c.positionInWindow().y, "h" to c.size.height.toFloat())) } },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text.uppercase(), color = t.palette.acc, fontSize = t.type.caption, fontWeight = FontWeight.Bold, letterSpacing = t.type.caption * 0.04, modifier = Modifier.semantics { heading() })
        trailing?.invoke()
    }
}

/** `.card`-shaped surface: `surf`, the large radius, the standard padding. */
@Composable
fun Surface(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val t = LocalTokens.current
    val pr = LocalProbe.current
    var m = modifier.fillMaxWidth().background(t.palette.surf, RoundedCornerShape(t.radii.lg))
        .let { mm -> if (pr == null || onClick != null) mm else mm.onGloballyPositioned { c -> pr("fill:info", mapOf("y" to c.positionInWindow().y, "h" to c.size.height.toFloat())) } }
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m.padding(horizontal = t.spacing.u * 1.625f, vertical = t.spacing.u * 1.5f)) { content() }
}

/** A form field's block: its label and control, then 1.5u before the next. */
@Composable
fun FieldGroup(content: @Composable () -> Unit) {
    val t = LocalTokens.current
    Column(Modifier.fillMaxWidth().padding(bottom = t.spacing.u * 1.5f)) { content() }
}
