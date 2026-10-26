package app.scifin.ui.screens

import app.scifin.i18n.tr
import app.scifin.i18n.trf
import app.scifin.i18n.trp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import app.scifin.ui.components.FigureLabel
import app.scifin.ui.components.CheckboxMark
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.scifin.app.AppStore
import app.scifin.app.UiState
import app.scifin.ui.LocalTokens
import app.scifin.ui.components.Button
import app.scifin.ui.components.ButtonKind
import app.scifin.ui.components.ButtonRow
import app.scifin.ui.components.Caption
import app.scifin.ui.components.Dropdown
import app.scifin.ui.components.FeasibilityNote
import app.scifin.ui.components.FieldLabel
import app.scifin.ui.components.InfoNote
import app.scifin.ui.components.Page
import app.scifin.ui.components.Surface
import app.scifin.ui.components.TopRow
import app.scifin.ui.formatDateFull
import app.scifin.ui.formatMoney
import app.scifin.ui.components.AmountText
import app.scifin.model.Money
import app.scifin.ui.components.LearnLink
import app.scifin.app.Route
import app.scifin.engine.Import

/**
 * §4.3's statement import: pick a CSV/OFX, review every row -- each vetoable, and each expense
 * given a Category, Goal or Account -- and one Confirm names the total. Nothing is recorded until then.
 */
@Composable
fun ImportScreen(state: UiState, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val d = state.data ?: return
    val cur = d.settings.currency
    val review = state.importReview
    Page(null) {
        TopRow(if (review == null) tr("Import a statement") else tr("Review import"), onBack = { if (review != null) store.cancelImport() else store.back() })
        if (review == null) {
            InfoNote(tr("Export a statement from your bank as CSV or OFX and open it here. It is read on this device and nothing is sent anywhere."))
            Column(Modifier.padding(top = t.spacing.gap)) {
                FieldLabel(tr("Statement file"))
                Button(tr("Choose a statement file"), ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth()) { store.pickStatement() }
            }
            state.importError?.let { FeasibilityNote(it, Modifier.padding(top = t.spacing.gap)) }
            Caption(tr("If your bank offers it, choose OFX: it carries the bank’s own transaction id, so if you re-import an overlapping period, duplicates are identified exactly rather than by resemblance."), Modifier.padding(top = t.spacing.gap))
            state.importDone?.let { done ->
                InfoNote(importSummary(done.count, done.out, done.inn, cur), Modifier.padding(top = t.spacing.gap))
                LearnLink(tr("See them in the Activity log"), { store.push(Route.Ledger) })
            }
            d.importState.lastImportDate?.let { Caption(trf("Last import: {0}.", formatDateFull(it)), Modifier.padding(top = t.spacing.gap)) }
            return@Page
        }
        val included = review.rows.filter { it.include }
        val outTotal = included.filter { it.row.amount < 0 }.sumOf { -it.row.amount }
        val inTotal = included.filter { it.row.amount > 0 }.sumOf { it.row.amount }
        val skipped = review.rows.size - included.size
        Surface(Modifier.padding(bottom = t.spacing.gap)) {
            Column(verticalArrangement = Arrangement.spacedBy(t.spacing.u / 2)) {
                FigureLabel("${review.fileName} · ${review.format}")
                AmountText(formatMoney(outTotal, cur), color = p.acc, fontSize = t.type.figure, fontWeight = FontWeight.Bold)
                Caption(trf("{0} of {1} entries selected", included.size, review.rows.size) + (if (inTotal > 0) ", " + trf("plus {0} in", formatMoney(inTotal, cur)) else "") + (if (skipped > 0) " · " + trf("{0} skipped", skipped) else ""))
            }
        }
        InfoNote(tr("Nothing is recorded until you confirm. Anything already imported, or already swept as a recurring expense, is unticked — tick it if you disagree."), Modifier.padding(bottom = t.spacing.gap))
        Column(Modifier.padding(bottom = t.spacing.gap), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) { review.rows.forEachIndexed { i, r ->
            Column(
                Modifier.fillMaxWidth().background(p.surf2, RoundedCornerShape(t.radii.lg)).padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                    // the mark and the payee are one checkbox at target height: a screen reader hears the payee and its state
                    Row(
                        Modifier.weight(1f).heightIn(min = t.spacing.target).toggleable(value = r.include, role = Role.Checkbox) { store.toggleImportRow(i) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 0.75f),
                    ) {
                        CheckboxMark(r.include)
                        Text(tr(r.row.description), color = if (r.include) p.txt else p.txt2, fontSize = t.type.body, fontWeight = FontWeight.Bold)
                    }
                    Text(formatMoney(if (r.row.amount < 0) -r.row.amount else r.row.amount, cur), color = if (r.include) p.txt else p.txt2, fontSize = t.type.body, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = t.spacing.gap))
                }
                Text(formatDateFull(r.row.date) + " · " + (if (r.row.amount < 0) tr("out") else tr("in")), color = p.txt2, fontSize = t.type.caption)
                if (r.note.isNotEmpty()) Text(r.note, color = p.txt2, fontSize = t.type.caption)
                if (r.include && r.row.amount > 0) Box(Modifier.padding(top = t.spacing.u / 2)) {
                    Dropdown(Import.inflowTargets(d), r.target ?: "", { v -> store.setImportTarget(i, v.ifEmpty { null }) })
                }
                // §4.3: every outflow needs a target; nothing leaves Savings untracked
                if (r.include && r.row.amount < 0) Box(Modifier.padding(top = t.spacing.u / 2)) {
                    Dropdown(listOf("" to tr("Choose where it went")) + Import.outflowTargets(d, -r.row.amount), r.target ?: "", { v -> store.setImportTarget(i, v.ifEmpty { null }) })
                }
            }
        } }
        val open = Import.unassigned(d, review.rows).size
        if (open > 0) Caption(trp("{0} expense still needs a Category, Goal or Account.", "{0} expenses still need a Category, Goal or Account.", open), Modifier.padding(top = t.spacing.gap))
        Box(Modifier.padding(top = t.spacing.pad)) {
            ButtonRow {
                Button(tr("Cancel"), ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.cancelImport() }
                Button(trp("Add {0} entry", "Add {0} entries", included.size), enabled = included.isNotEmpty() && open == 0, modifier = Modifier.weight(1f)) { store.confirmImport() }
            }
        }
    }
}

/** §4.3: the line that confirms an import -- count, money out, money in. */
fun importSummary(count: Int, out: Money, inn: Money, currency: String): String {
    return if (inn > 0) trp("{0} entry added — {1} out, {2} in.", "{0} entries added — {1} out, {2} in.", count, formatMoney(out, currency), formatMoney(inn, currency))
    else trp("{0} entry added — {1} out.", "{0} entries added — {1} out.", count, formatMoney(out, currency))
}
