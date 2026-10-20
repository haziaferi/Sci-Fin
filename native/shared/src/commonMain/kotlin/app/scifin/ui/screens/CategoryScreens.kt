package app.scifin.ui.screens

import app.scifin.ui.components.FigureLabel
import app.scifin.ui.components.EditorDeleteButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Sheet
import app.scifin.app.UiState
import app.scifin.engine.CategoryEditing
import app.scifin.model.ProfileData
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.components.AddRow
import app.scifin.ui.components.Button
import app.scifin.ui.components.ButtonKind
import app.scifin.ui.components.ButtonRow
import app.scifin.ui.components.Caption
import app.scifin.ui.components.TRASH_LINE
import app.scifin.ui.components.FieldGroup
import app.scifin.ui.components.FieldLabel
import app.scifin.ui.components.InfoNote
import app.scifin.ui.components.Page
import app.scifin.ui.components.ProgressRow
import app.scifin.ui.components.SheetScaffold
import app.scifin.ui.components.Surface
import app.scifin.ui.components.TextField
import app.scifin.ui.components.TopRow
import app.scifin.ui.currencySymbol
import app.scifin.ui.formatMoney
import app.scifin.ui.moneyText
import app.scifin.ui.parseMoney
import app.scifin.ui.tutAnchor
import app.scifin.ui.components.AmountText

/** §4.3 Categories: total spent, category rows on §2.1's tick+hatch bar, + New. */
@Composable
fun CategoriesView(d: ProfileData, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val cur = d.settings.currency
    Surface(Modifier.padding(bottom = t.spacing.gap * 1.3f)) {
        Column {
            FigureLabel("Spent this month")
            AmountText(formatMoney(d.categories.sumOf { it.spent }, cur), color = p.acc, fontSize = t.type.figure, fontWeight = FontWeight.Bold)
        }
    }
    Column(Modifier.tutAnchor("categories-list"), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
    for (c in d.categories) ProgressRow(
        entityIcon(c.icon, Lucide.TAG), c.name, c.spent, c.planned, "plan", cur,
        onQuickAdd = { store.openSheet(Sheet.Entry("cat:${c.id}")) }, onClick = { store.push(Route.CategoryEditor(c.id)) },
        learnLink = if (c.spent > c.planned) "Why nothing here turns red" to { store.push(Route.LearnTopic("why-no-red")) } else null,
    )
    AddRow("New category") { store.push(Route.CategoryEditor(null)) }
    }
    InfoNote("“Spent” is directly editable and goes up with each purchase you log.", Modifier.padding(top = t.spacing.gap * 1.3f))
}

/** §3.6 / §4.3's editor: `spent` is a plain, directly-editable number; changing it moves the difference to or from Savings. */
@Composable
fun CategoryEditorScreen(route: Route.CategoryEditor, state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val d = state.data ?: return
    val cur = d.settings.currency
    val original = route.id?.let { id -> d.categories.firstOrNull { it.id == id } }
    val isNew = original == null
    var draft by remember(route.id) { mutableStateOf(original?.let { CategoryEditing.Draft.of(it) } ?: CategoryEditing.Draft()) }
    var plannedText by remember(route.id) { mutableStateOf(if (draft.planned != 0L) moneyText(draft.planned) else "") }
    var spentText by remember(route.id) { mutableStateOf(if (draft.spent != 0L) moneyText(draft.spent) else "") }
    // a typed figure that does not read as one holds Save rather than saving 0 (§3.6: Spent moves Savings)
    val unreadable = listOf(plannedText, spentText).any { it.isNotBlank() && parseMoney(it) == null }
    Page(null) {
        TopRow(if (isNew) "New category" else draft.name.ifBlank { "Category" }, onBack = { store.back() })
        FieldGroup { FieldLabel("Name"); TextField(draft.name, { draft = draft.copy(name = it) }, placeholder = "Groceries, hobby supplies…", label = "Name") }
        IconPicker(draft.icon) { draft = draft.copy(icon = it) }
        FieldGroup { FieldLabel("Planned this month"); TextField(plannedText, { v -> plannedText = v; draft = draft.copy(planned = parseMoney(v) ?: 0) }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = "Planned this month") }
        FieldGroup {
            FieldLabel("Spent so far this month"); TextField(spentText, { v -> spentText = v; draft = draft.copy(spent = parseMoney(v) ?: 0) }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = "Spent so far this month")
            Caption("Changing this moves the difference to or from Savings — same mechanism as logging a purchase, just from this screen instead.", Modifier.padding(top = t.spacing.u * 0.75f))
        }
        if (!isNew) Button("Log a purchase", ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth().padding(bottom = t.spacing.u * 1.25f)) { store.openSheet(Sheet.Entry("cat:${original.id}")) }
        Row(Modifier.fillMaxWidth().padding(top = t.spacing.pad), horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
            if (!isNew) EditorDeleteButton(original.name) { store.openSheet(Sheet.DeleteCategory(original.id)) }
            Button(if (isNew) "Add category" else "Save", enabled = draft.name.isNotBlank() && !unreadable, modifier = Modifier.weight(1f)) { store.saveCategory(draft); store.back() }
        }
    }
}

/** §3.6: deleting a Category un-spends nothing; only the label goes. */
@Composable
fun DeleteCategorySheet(sheet: Sheet.DeleteCategory, state: UiState, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val d = state.data ?: return
    val c = d.categories.firstOrNull { it.id == sheet.id } ?: return
    SheetScaffold(title = "Delete category", onDismiss = store::closeSheet) {
        Text("Delete ${c.name}? This month’s spend is not un-spent — only the category itself goes away.", color = p.txt, fontSize = t.type.body, lineHeight = t.type.body * 1.5, modifier = Modifier.padding(bottom = t.spacing.gap))
        Caption(TRASH_LINE, Modifier.padding(bottom = t.spacing.pad))
        ButtonRow {
            Button("Cancel", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.closeSheet() }
            Button("Delete", ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { store.deleteCategory(c.id) }
        }
    }
}
