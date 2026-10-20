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

/*
 * The Trash (owner, 2026-10-19/20): what was deleted waits here 30 days, inert, then goes for good; a notice comes
 * at 20 days. The money question was asked at the delete, so Restore simply undoes what the delete did -- money
 * included, as a new logged row -- and Delete forever has nothing left to ask but itself.
 */

private fun kindLabel(kind: String) = when (kind) {
    "goal" -> "Goal"; "category" -> "Category"; "expense" -> "Recurring expense"; "account" -> "Account"
    "pair" -> "Ledger entries"; "profile" -> "Profile"; else -> "Item"
}

@Composable
internal fun TrashPage(state: UiState, store: AppStore) {
    val t = LocalTokens.current
    InfoNote("Deleted things wait here for ${Trash.KEPT_DAYS} days, then go for good — a notification says so ten days before. While here they do nothing: a payment doesn’t sweep or remind, and nothing counts towards any figure. Restore puts one back as it was.")
    if (state.trash.isEmpty()) {
        Caption("Nothing is in the Trash.", Modifier.padding(top = t.spacing.gap))
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
        Text(e.name.ifEmpty { kindLabel(e.kind) }, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
        Caption("${kindLabel(e.kind)} · deleted ${formatDateFull(e.deletedOn)} · goes for good on ${formatDateFull(e.goesOn)}")
        if (e.money > 0) Caption("Its delete returned ${formatMoney(e.money, cur)} to Savings; restoring takes that back out, logged.")
        if (e.kind == "profile") Caption("Restoring brings it back as one more profile; it is not opened.")
        if (confirming) {
            Caption("Delete for good? It can’t be brought back after this.", Modifier.padding(top = t.spacing.u / 2))
            ButtonRow {
                Button("Keep it", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { confirming = false }
                Button("Delete forever", ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { store.deleteForever(e.id) }
            }
        } else ButtonRow {
            Button("Delete forever", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { confirming = true }
            Button("Restore", ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { store.restoreFromTrash(e.id) }
        }
    }
}
