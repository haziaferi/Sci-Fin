package app.scifin.ui.screens

import app.scifin.i18n.typedWordMatches
import app.scifin.i18n.tr
import app.scifin.i18n.trf
import app.scifin.i18n.trp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Sheet
import app.scifin.app.UiState
import app.scifin.engine.Ledger
import app.scifin.engine.plusMonthsClamped
import app.scifin.model.ProfileData
import app.scifin.model.Transaction
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.components.Button
import app.scifin.ui.components.ButtonKind
import app.scifin.ui.components.ButtonRow
import app.scifin.ui.components.Caption
import app.scifin.ui.components.FeasibilityNote
import app.scifin.ui.components.FieldGroup
import app.scifin.ui.components.FieldLabel
import app.scifin.ui.components.InfoNote
import app.scifin.ui.components.Page
import app.scifin.ui.components.RoundIconButton
import app.scifin.ui.components.SectionLabel
import app.scifin.ui.components.SheetScaffold
import app.scifin.ui.components.TextField
import app.scifin.ui.components.TopRow
import app.scifin.ui.formatDate
import app.scifin.ui.formatMoney
import kotlinx.datetime.LocalDate
import app.scifin.ui.components.Dropdown

private val TARGETS get() = listOf("all" to tr("Everything"), "savings" to tr("Savings"), "goal" to tr("Goals"), "cat" to tr("Categories"), "rec" to tr("Recurring"), "acct" to tr("Accounts"))
private val RANGES get() = listOf("1m" to tr("This month"), "3m" to tr("3 months"), "12m" to tr("12 months"), "all" to tr("All"))

/** Where a row leads: the thing it touched, if it still exists (§7.3: corrections happen there, never in the log). */
private fun deepLink(t: Transaction, d: ProfileData): Route? {
    if (t.key == "savings") return null                 // the Savings editor is a sheet, opened below
    val kind = t.key.substringBefore(':'); val id = t.key.substringAfter(':')
    return when (kind) {
        "goal", "spend" -> if (d.goals.any { it.id == id }) Route.GoalEditor(id) else null
        "acct" -> if (d.accounts.any { it.id == id }) Route.AccountDetail(id) else null
        "cat" -> if (d.categories.any { it.id == id }) Route.CategoryEditor(id) else null
        "rec" -> if (d.recurringExpenses.any { it.id == id }) Route.RecurringEditor(id) else null
        else -> null
    }
}

/**
 * §7.3's ledger: browsable, filterable by target and range, searchable, in `date` order; the
 * figure shown is the Savings movement. A delete appears only on a pair -- a mistake undone
 * by its exact opposite within two days -- and eligibility is computed once, here, against
 * history as it stands before any deletion.
 */
@Composable
fun LedgerScreen(state: UiState, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val d = state.data ?: return
    val cur = d.settings.currency
    val today = store.clock.today()
    var q by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("all") }
    var range by remember { mutableStateOf("1m") }
    val pairs = remember(d.transactions) { Ledger.pairEligibility(d.transactions) }
    val from: LocalDate? = when (range) { "all" -> null; "1m" -> LocalDate(today.year, today.month, 1); "3m" -> today.plusMonthsClamped(-3); else -> today.plusMonthsClamped(-12) }
    val query = q.trim().lowercase()
    val rows = d.transactions.filter { tx ->
        (from == null || tx.date >= from) &&
            (target == "all" || tx.key.substringBefore(':').let { k -> k == target || (target == "goal" && k == "spend") }) &&
            (query.isEmpty() || (tx.payee ?: "").lowercase().contains(query) || tx.targetLabel.lowercase().contains(query))
    }.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.loggedAt })

    Page(null) {
        TopRow(tr("Activity log"), onBack = { store.back() })
        FieldGroup { TextField(q, { q = it }, placeholder = tr("Search payee or label…")) }
        // §7.3: the two filters as two dropdowns side by side
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
            Column(Modifier.weight(1f)) { FieldLabel(tr("Show")); Dropdown(TARGETS, target, { target = it }) }
            Column(Modifier.weight(1f)) { FieldLabel(tr("Range")); Dropdown(RANGES, range, { range = it }) }
        }
        SectionLabel(trp("{0} entry", "{0} entries", rows.size))
        if (rows.isEmpty()) InfoNote(tr("Nothing matches those filters."))
        for (tx in rows) {
            val eff = Ledger.savingsEffect(tx)
            val link = deepLink(tx, d)
            val onClick: (() -> Unit)? = when {
                tx.key == "savings" -> { { store.openSheet(Sheet.SavingsEditor) } }
                link != null -> { { store.push(link) } }
                else -> null
            }
            val payeeNote = tx.payee?.takeIf { it != tx.targetLabel }
            val note = ledgerRowNote(tx.date, today)
            Row(
                Modifier.fillMaxWidth().heightIn(min = t.spacing.target).background(p.surf2, RoundedCornerShape(t.radii.lg))
                    .let { m -> if (onClick != null) m.clickable(role = Role.Button, onClick = onClick) else m }
                    .padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr(tx.targetLabel.ifEmpty { "Savings" }), color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
                    Text(formatDate(tx.date) + (payeeNote?.let { " · " + tr(it) } ?: "") + (if (tx.isIncome) " · " + tr("income") else "") + (note?.let { " · $it" } ?: ""), color = p.txt2, fontSize = t.type.caption)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 1.25f)) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (eff == 0L) {
                            Text(formatMoney(tx.amount, cur), color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
                            Text(if (tx.key.startsWith("spend:")) trf("from {0}", tr(tx.targetLabel)) else tr("tracked only"), color = p.txt2, fontSize = t.type.caption)
                        } else Text((if (eff > 0) "+" else "") + formatMoney(eff, cur), color = if (note != null) p.txt2 else p.txt.copy(alpha = if (eff < 0) 0.92f else 1f), fontSize = t.type.body, fontWeight = FontWeight.Bold)
                    }
                    pairs[tx.id]?.let { other -> RoundIconButton(Lucide.TRASH_2, tr("Delete this pair")) { store.openSheet(Sheet.DeletePair(tx.id, other)) } }
                }
            }
        }
        InfoNote(tr("This log is written automatically and can’t be edited directly. Instead, select an entry to correct it on its own page. “Delete” appears only when a mistake was cancelled by an equal and opposite entry within two days."), Modifier.padding(top = t.spacing.gap))
    }
}

/** §7.3's delete: the heavier register, typed DELETE, because the blast radius is bounded and the action is rare. */
@Composable
fun DeletePairSheet(sheet: Sheet.DeletePair, state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val d = state.data ?: return
    val cur = d.settings.currency
    val a = d.transactions.firstOrNull { it.id == sheet.id } ?: return
    val b = d.transactions.firstOrNull { it.id == sheet.otherId } ?: return
    var typed by remember { mutableStateOf("") }
    val word = tr("DELETE")
    val ready = typedWordMatches(typed, "DELETE")
    SheetScaffold(title = tr("Remove both entries"), onDismiss = store::closeSheet) {
        FeasibilityNote(trf("{0} — {1} and {2}, logged within two days of each other. One corrects the other, so both are removed and every figure they changed returns to its previous value.", tr(a.targetLabel.ifEmpty { "Savings" }), formatMoney(a.amount, cur), formatMoney(b.amount, cur)))
        Caption(tr("None of the current balances change: the two already summed to zero. The trend points you have seen so far will move. Both stay in the Trash for 30 days."), Modifier.padding(top = t.spacing.gap, bottom = t.spacing.u * 1.25f))
        FieldGroup { FieldLabel(trf("Type {0} to confirm", word)); TextField(typed, { typed = it }, placeholder = word, label = trf("Type {0} to confirm", word)) }
        ButtonRow {
            Button(tr("Cancel"), ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.closeSheet() }
            Button(tr("Remove both"), ButtonKind.SECONDARY, enabled = ready, modifier = Modifier.weight(1f)) { store.deletePair(a.id, b.id) }
        }
    }
}

/** §7.3: a row dated after today is a booked sweep, not a spend. */
fun ledgerRowNote(date: LocalDate, today: LocalDate): String? = if (date > today) tr("scheduled") else null
