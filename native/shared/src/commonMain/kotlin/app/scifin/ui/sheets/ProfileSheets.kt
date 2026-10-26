package app.scifin.ui.sheets

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import app.scifin.app.AppStore
import app.scifin.app.Sheet
import app.scifin.app.UiState
import app.scifin.ui.LocalTokens
import app.scifin.ui.components.Button
import app.scifin.ui.components.ButtonKind
import app.scifin.ui.components.ButtonRow
import app.scifin.ui.components.FieldGroup
import app.scifin.ui.components.FieldLabel
import app.scifin.ui.components.SheetScaffold
import app.scifin.ui.components.TextField
import app.scifin.i18n.tr
import app.scifin.i18n.trf

/** §8.3: rename. */
@Composable
fun RenameProfileSheet(sheet: Sheet.RenameProfile, state: UiState, store: AppStore) {
    val current = state.profiles.firstOrNull { it.id == sheet.id }?.name ?: ""
    var name by remember { mutableStateOf(current) }
    SheetScaffold(title = tr("Rename profile"), onDismiss = store::closeSheet) {
        FieldGroup { FieldLabel(tr("Name")); TextField(name, { name = it }, placeholder = tr("Mum’s pension, Joint account…"), label = tr("Name")) }
        ButtonRow {
            Button(tr("Cancel"), ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.closeSheet() }
            Button(tr("Save"), enabled = name.isNotBlank(), modifier = Modifier.weight(1f)) { store.renameProfile(sheet.id, name); store.closeSheet() }
        }
    }
}

/** §8.3: delete -- one of §4.8 rule 3's delete confirmations; never the last Profile. */
@Composable
fun DeleteProfileSheet(sheet: Sheet.DeleteProfile, state: UiState, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val name = state.profiles.firstOrNull { it.id == sheet.id }?.name ?: ""
    SheetScaffold(title = tr("Delete profile"), onDismiss = store::closeSheet) {
        Text(
            trf("Delete {0}? Everything in it goes with it — its Savings, its goals and categories, its accounts and its whole ledger. Nothing moves into another profile. It waits in the Trash for 30 days, untouched, and Settings › Trash can bring it back.", name),
            color = p.txt, fontSize = t.type.body, lineHeight = t.type.body * 1.5, modifier = Modifier.padding(bottom = t.spacing.pad),
        )
        ButtonRow {
            Button(tr("Cancel"), ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.closeSheet() }
            Button(tr("Delete"), ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { store.deleteProfile(sheet.id) }
        }
    }
}
