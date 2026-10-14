package app.scifin.ui.screens

import app.scifin.ui.components.EditorDeleteButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Sheet
import app.scifin.app.UiState
import app.scifin.engine.GoalDraft
import app.scifin.engine.GoalFrame
import app.scifin.engine.Goals
import app.scifin.engine.Income
import app.scifin.engine.Recurring
import app.scifin.model.GoalMode
import app.scifin.model.Money
import app.scifin.model.ceilDiv
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.LucideIcon
import app.scifin.ui.components.Button
import app.scifin.ui.components.Caption
import app.scifin.ui.components.DeleteWithReturnSheet
import app.scifin.ui.components.CheckboxRow
import app.scifin.ui.components.FeasibilityNote
import app.scifin.ui.components.FieldGroup
import app.scifin.ui.components.FieldLabel
import app.scifin.ui.components.InlineLearn
import app.scifin.ui.components.LearnLink
import app.scifin.ui.components.Page
import app.scifin.ui.components.Segmented
import app.scifin.ui.components.TextField
import app.scifin.ui.components.TopRow
import app.scifin.ui.currencySymbol
import app.scifin.ui.formatMoney
import app.scifin.ui.moneyText
import app.scifin.ui.parseMoney
import app.scifin.ui.components.deleteAddBackCopy
import app.scifin.ui.components.deleteOnlyCopy

/** §12's fixed, app-supplied icon set, in the prototype's order; "Default" (null) sits first as a real choice. */
val ICON_CHOICES = listOf(
    "wallet", "piggy-bank", "banknote", "shopping-cart", "utensils-crossed", "bus", "smartphone",
    "landmark", "credit-card", "tag", "repeat", "calendar-clock", "shield", "zap", "lightbulb",
    "telescope", "compass", "users", "layout-grid", "palette",
)

/** The icon picker every editor shares: chips of glyphs, which are the control's content and so never hidden by low-distraction mode. */
@Composable
fun IconPicker(current: String?, onPick: (String?) -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    FieldGroup {
        Row(Modifier.padding(bottom = t.spacing.u * 0.75f)) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = p.txt2, fontWeight = FontWeight.Bold)) { append("Icon ") }
                withStyle(SpanStyle(color = p.txt2)) { append("— optional") }
            }, fontSize = t.type.body)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 0.75f), verticalArrangement = Arrangement.spacedBy(t.spacing.u * 0.75f)) {
            Chip(on = current == null, onClick = { onPick(null) }) { Text("Default", color = if (current == null) p.acc else p.txt2, fontSize = t.type.body, fontWeight = FontWeight.Bold) }
            for (name in ICON_CHOICES) {
                val icon = Lucide.byKey(name) ?: continue
                val on = current == name
                Chip(on = on, onClick = { onPick(name) }, padding = t.spacing.u * 0.875f, label = name.replace('-', ' ')) { LucideIcon(icon, tint = if (on) p.acc else p.txt2, size = t.icons.md) }
            }
        }
    }
}

/** A pill: one choice of a set when [choice], otherwise a plain button; [label] names a chip whose content is a glyph. */
@Composable
private fun Chip(on: Boolean, onClick: () -> Unit, padding: Dp = LocalTokens.current.spacing.u * 1.5f, label: String? = null, choice: Boolean = true, content: @Composable () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    Box(
        Modifier.heightIn(min = t.spacing.target - t.spacing.u).background(if (on) p.acc.copy(alpha = 0.12f) else Color.Transparent, CircleShape)
            .border(t.strokes.ctl, if (on) p.acc else p.bordStrong, CircleShape)
            .then(if (choice) Modifier.selectable(selected = on, role = Role.RadioButton, onClick = onClick) else Modifier.clickable(role = Role.Button, onClick = onClick))
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
            .padding(horizontal = padding, vertical = t.spacing.u * 0.625f),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * The editor's typed figures and the draft computed from them. Every edit re-reads the field
 * that drives the calculation, so a field and the draft cannot disagree (§6.1's pair in step).
 */
internal data class GoalFields(val draft: GoalDraft, val saved: String, val target: String, val months: String, val monthly: String) {
    /** Re-derive the figure [changed] governs from what its driving field holds. */
    fun synced(changed: GoalMode): GoalFields {
        val typed = if (changed == GoalMode.AMOUNT) draft.copy(monthly = parseMoney(monthly)) else draft.copy(months = months.trim().toIntOrNull())
        val d = typed.synced(changed)
        return if (changed == GoalMode.DATE) copy(draft = d, monthly = d.monthly?.let { moneyText(it) } ?: "")
        else copy(draft = d, months = d.months?.toString() ?: "")
    }

    fun withSaved(v: String) = copy(saved = v, draft = draft.copy(saved = parseMoney(v) ?: 0)).synced(draft.mode)
    fun withTarget(v: String) = copy(target = v, draft = draft.copy(target = parseMoney(v))).synced(draft.mode)
    fun withMonths(v: String) = copy(months = v).synced(GoalMode.DATE)
    fun withMonthly(v: String) = copy(monthly = v).synced(GoalMode.AMOUNT)
    fun withMode(m: GoalMode) = copy(draft = draft.copy(mode = m)).synced(m)
    fun withNoTarget(on: Boolean) = copy(draft = draft.copy(noTarget = on)).synced(draft.mode)
    fun withSuggestion(amt: Money) = copy(draft = draft.copy(mode = GoalMode.AMOUNT), monthly = moneyText(amt)).synced(GoalMode.AMOUNT)

    /** A typed figure that does not read as one: Save waits rather than storing 0, or 1 month, in its place. */
    val unreadable: Boolean get() {
        fun bad(s: String) = s.isNotBlank() && parseMoney(s) == null
        if (bad(saved)) return true
        if (draft.noTarget) return false
        if (bad(target)) return true
        return if (draft.mode == GoalMode.DATE) (months.trim().toIntOrNull() ?: 0) < 1 && (months.isNotBlank() || (draft.target ?: 0) > 0)
        else bad(monthly)
    }

    companion object {
        fun of(d: GoalDraft) = GoalFields(d, if (d.saved > 0) moneyText(d.saved) else "", d.target?.let { moneyText(it) } ?: "", d.months?.toString() ?: "", d.monthly?.let { moneyText(it) } ?: "")
    }
}

/**
 * §6.4: one screen for create and edit -- §6.3's no-target mode, the by-date / by-amount
 * pair kept in step (§6.1), the frame view and the sustainability suggestions, the
 * direct-edit-to-Savings linkage for "Already saved" (§3.6). Delete is §4.8 rule 3's sheet.
 */
@Composable
fun GoalEditorScreen(route: Route.GoalEditor, state: UiState, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val d = state.data ?: return
    val today = store.clock.today()
    val cur = d.settings.currency
    val original = route.id?.let { id -> d.goals.firstOrNull { it.id == id } }
    var f by remember(route.id) { mutableStateOf(GoalFields.of(original?.let { GoalDraft.of(it, today) } ?: GoalDraft())) }
    val draft = f.draft
    var frameOpen by remember(route.id) { mutableStateOf(false) }
    val isNew = original == null

    Page(null) {
        TopRow(if (isNew) "New goal" else draft.name.ifBlank { "Goal" }, onBack = { store.back() })
        FieldGroup { FieldLabel("Name"); TextField(draft.name, { f = f.copy(draft = draft.copy(name = it)) }, placeholder = "Emergency fund, a trip, new laptop…", label = "Name") }
        IconPicker(draft.icon) { f = f.copy(draft = draft.copy(icon = it)) }
        CheckboxRow("Track without a target amount or date", draft.noTarget) { f = f.withNoTarget(!draft.noTarget) }
        Box(Modifier.padding(top = t.spacing.u * 1.5f)) {
            FieldGroup { FieldLabel("Already saved"); TextField(f.saved, { f = f.withSaved(it) }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = "Already saved") }
        }
        if (!draft.noTarget) {
            FieldGroup { FieldLabel("Target amount"); TextField(f.target, { f = f.withTarget(it) }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = "Target amount") }
            Segmented(listOf("date" to "By date", "amount" to "By amount"), draft.mode.token, { v -> f = f.withMode(GoalMode.of(v)) }, Modifier.padding(bottom = t.spacing.gap))
            if (draft.mode == GoalMode.DATE) {
                FieldGroup { FieldLabel("Months from now"); TextField(f.months, { f = f.withMonths(it) }, keyboard = KeyboardType.Number, label = "Months from now") }
                draft.monthly?.takeIf { it > 0 }?.let { Caption("Suggested: ${formatMoney(it, cur)}/month", Modifier.padding(start = t.spacing.u * 0.25f, bottom = t.spacing.u * 1.25f)) }
            } else {
                FieldGroup { FieldLabel("Monthly contribution"); TextField(f.monthly, { f = f.withMonthly(it) }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = "Monthly contribution") }
                draft.months?.takeIf { it > 0 }?.let { Caption("Suggested timeline: $it month${if (it == 1) "" else "s"}", Modifier.padding(start = t.spacing.u * 0.25f, bottom = t.spacing.u * 1.25f)) }
            }
            val monthly = draft.monthly ?: 0
            val target = draft.target ?: 0
            if (target > 0 && monthly > 0) {
                // §6.4's frame view: where this fits, as a plain comparison, never a block
                LearnLink(if (frameOpen) "Hide where this fits in your current frame" else "Show where this fits in your current frame", { frameOpen = !frameOpen })
                if (frameOpen) {
                    val frame = Goals.frame(d, monthly, draft.id, Recurring.scheduledTotal(d, today), today)
                    val line = when (frame) {
                        is GoalFrame.IncomeOn -> "With ${formatMoney(Income.receivedThisMonth(d, today), cur)} received so far this month, minus recurring expenses and other goals’ commitments, you have roughly ${formatMoney(frame.available, cur)} available. This goal asks for ${formatMoney(monthly, cur)}/mo."
                        is GoalFrame.IncomeOff -> "Recurring expenses plus other goals’ commitments plus this one come to ${formatMoney(frame.committed, cur)}/mo, weighed against your Savings of ${formatMoney(frame.savings, cur)}. Income is off, so this is a plain comparison, not a division."
                    }
                    InlineLearn(listOf(line, "You can still save it as-is — the app doesn’t block this either way."), null)
                }
                Goals.sustainability(d, monthly, today)?.let { s ->
                    Column(Modifier.fillMaxWidth().padding(top = t.spacing.gap)) {
                        FeasibilityNote(buildString {
                            append("If you’d like to make this more sustainable, here are a few options:")
                            if (s.suggestions == null) append("\n\n").append(if (s.incomeOn) "No income has landed yet this month to suggest alternative amounts from." else "There’s no Savings cushion right now to suggest alternative amounts from.")
                        })
                        s.suggestions?.let { list ->
                            FlowRow(Modifier.padding(top = t.spacing.gap), horizontalArrangement = Arrangement.spacedBy(t.spacing.gap), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
                                for (amt in list) {
                                    val months = if (amt > 0) draft.remaining.ceilDiv(amt) else 0
                                    Chip(on = true, onClick = { f = f.withSuggestion(amt) }, padding = t.spacing.u * 1.375f, choice = false) {
                                        Text("${formatMoney(amt, cur)}/mo · ${months}mo", color = p.acc, fontSize = t.type.caption, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Caption("You can still save ${formatMoney(monthly, cur)}/mo as-is.", Modifier.padding(top = t.spacing.u * 0.75f))
                    }
                }
            }
        }
        LearnLink(if (isNew) "Why naming it matters" else "Why naming this helps", { store.push(Route.LearnTopic("name-your-goal")) })
        Row(Modifier.fillMaxWidth().padding(top = t.spacing.pad), horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
            if (!isNew) EditorDeleteButton(original.name) { store.openSheet(Sheet.DeleteGoal(original.id)) }
            Button(if (isNew) "Add goal" else "Save", enabled = draft.name.isNotBlank() && !f.unreadable, modifier = Modifier.weight(1f)) {
                store.saveGoal(draft); store.back()
            }
        }
    }
}

/** §3.6's delete prompt: `saved` is earmarked, not spent, so it is offered back -- on accept only. */
@Composable
fun DeleteGoalSheet(sheet: Sheet.DeleteGoal, state: UiState, store: AppStore) {
    val d = state.data ?: return
    val g = d.goals.firstOrNull { it.id == sheet.id } ?: return
    val cur = d.settings.currency
    val saved = formatMoney(g.saved, cur)
    DeleteWithReturnSheet(
        title = "Delete goal",
        question = "Delete ${g.name}?" + (if (g.saved > 0) " Add $saved back to Savings? That money was never spent, only labeled." else " The goal itself is removed.") + " Its past history stays in the log either way.",
        hasReturn = g.saved > 0,
        confirmAddBack = deleteAddBackCopy(saved, g.name),
        confirmDeleteOnly = deleteOnlyCopy(g.name),
        onDismiss = store::closeSheet,
        onDelete = { returnMoney -> store.deleteGoal(g.id, returnSaved = returnMoney) },
    )
}
