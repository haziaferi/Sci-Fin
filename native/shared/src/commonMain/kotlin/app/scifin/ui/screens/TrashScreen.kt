package app.scifin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.scifin.app.AppStore
import app.scifin.app.TrashEntry
import app.scifin.app.UiState
import app.scifin.engine.Trash
import app.scifin.ui.LocalTokens
import app.scifin.ui.components.Button
import app.scifin.ui.components.ButtonKind
import app.scifin.ui.components.ButtonRow
import app.scifin.ui.components.Caption
import app.scifin.ui.components.InfoNote
import app.scifin.ui.formatDateFull
import app.scifin.ui.formatMoney
import app.scifin.i18n.tr
import app.scifin.i18n.trf

/*
 * The Trash (owner, 2026-10-19/20): what was deleted waits here 30 days, inert, then goes for good; a notice comes
 * at 20 days. The money question was asked at the delete, so Restore simply undoes what the delete did -- money
 * included, as a new logged row -- and Delete forever has nothing left to ask but itself.
 */

private fun kindLabel(kind: String) = when (kind) {
    "goal" -> tr("Goal"); "category" -> tr("Category"); "expense" -> tr("Recurring expense"); "account" -> tr("Account")
    "pair" -> tr("Ledger entries"); "profile" -> tr("Profile"); else -> tr("Item")
}

@Composable
internal fun TrashPage(state: UiState, store: AppStore) {
    val t = LocalTokens.current
    InfoNote(trf("Deleted items stay here for {0} days, then are erased for good — a notification tells you ten days before. Items in the Trash are inactive and change nothing in the app. You can restore them until they are erased.", Trash.KEPT_DAYS))
    if (state.trash.isEmpty()) {
        Caption(tr("Nothing is in the Trash."), Modifier.padding(top = t.spacing.gap))
        return
    }
    Column(Modifier.padding(top = t.spacing.gap), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
        for (e in state.trash) TrashRow(e, state, store)
    }
}

@Composable
private fun TrashRow(e: TrashEntry, state: UiState, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    var confirming by rememberSaveable(e.id) { mutableStateOf(false) }
    val cur = state.data?.settings?.currency ?: "USD"
    Column(Modifier.fillMaxWidth().background(p.surf2, RoundedCornerShape(t.radii.lg)).padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f)) {
        Text(e.name.ifEmpty { kindLabel(e.kind) }.let { tr(it) }, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
        Caption(trf("{0} · deleted {1} · goes for good on {2}", kindLabel(e.kind), formatDateFull(e.deletedOn), formatDateFull(e.goesOn)))
        if (e.money > 0) Caption(trf("Its delete returned {0} to Savings; restoring takes that back out, logged.", formatMoney(e.money, cur)))
        if (e.kind == "profile") Caption(tr("Restoring brings it back as one more profile; it is not opened."))
        if (confirming) {
            Caption(tr("Delete for good? It can’t be brought back after this."), Modifier.padding(top = t.spacing.u / 2))
            ButtonRow {
                Button(tr("Keep it"), ButtonKind.GHOST, modifier = Modifier.weight(1f)) { confirming = false }
                Button(tr("Delete forever"), ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { store.deleteForever(e.id) }
            }
        } else ButtonRow {
            Button(tr("Delete forever"), ButtonKind.GHOST, modifier = Modifier.weight(1f)) { confirming = true }
            Button(tr("Restore"), ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { store.restoreFromTrash(e.id) }
        }
    }
}
