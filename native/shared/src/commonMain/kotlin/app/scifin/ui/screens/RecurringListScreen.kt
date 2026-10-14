package app.scifin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.UiState
import app.scifin.engine.Recurring
import app.scifin.model.Cadence
import app.scifin.model.RecurringExpense
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.components.FigureLabel
import app.scifin.ui.components.AddRow
import app.scifin.ui.components.EntityIcon
import app.scifin.ui.components.Page
import app.scifin.ui.components.Surface
import app.scifin.ui.components.TopRow
import app.scifin.ui.formatDateFull
import app.scifin.ui.formatMoney

fun cadenceLabel(c: Cadence) = when (c) {
    Cadence.WEEKLY -> "Weekly"; Cadence.BIWEEKLY -> "Every 2 weeks"; Cadence.SEMIMONTHLY -> "Twice a month"
    Cadence.MONTHLY -> "Monthly"; Cadence.BIMONTHLY -> "Every 2 months"; Cadence.YEARLY -> "Yearly"
}

/** §4.3's drill-in: the month's scheduled total, one row per expense, the dashed "New" row. */
@Composable
fun RecurringListScreen(state: UiState, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val d = state.data ?: return
    val cur = d.settings.currency
    val total = Recurring.scheduledTotal(d, store.clock.today())
    Page(null) {
        TopRow("Recurring expenses", onBack = { store.back() })
        Surface(Modifier.padding(bottom = t.spacing.gap * 1.3f)) {
            Column {
                FigureLabel("Total")
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = p.acc, fontSize = t.type.figure, fontWeight = FontWeight.Bold)) { append(formatMoney(total, cur)) }
                    withStyle(SpanStyle(color = p.txt, fontSize = t.type.body)) { append("/mo") }
                })
            }
        }
        for (e in d.recurringExpenses) RecurringExpenseRow(e, cur) { store.push(Route.RecurringEditor(e.id)) }
        AddRow("New recurring expense") { store.push(Route.RecurringEditor(null)) }
    }
}

@Composable
internal fun RecurringExpenseRow(e: RecurringExpense, cur: String, onClick: () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    Row(
        Modifier.fillMaxWidth().heightIn(min = t.spacing.target).background(p.surf2, RoundedCornerShape(t.radii.lg)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 1.125f)) {
            EntityIcon(entityIcon(e.icon, Lucide.REPEAT))
            // §3.11: once finished, the title carries a strikethrough -- no longer active, distinct from deleted
            Text(e.name, color = if (e.finishedAt != null) p.txt2 else p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold, textDecoration = if (e.finishedAt != null) TextDecoration.LineThrough else null)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMoney(e.amount, cur), color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
            Text(cadenceLabel(e.cadence), color = p.txt2, fontSize = t.type.caption)
            e.finishedAt?.let { Text("Ended ${formatDateFull(it)}", color = p.txt2, fontSize = t.type.caption) }
                ?: e.endDate?.let { Text("Ends ${formatDateFull(it)}", color = p.txt2, fontSize = t.type.caption) }
        }
    }
}
