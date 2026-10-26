package app.scifin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.scifin.app.AppStore
import app.scifin.engine.ValidatedBackup
import app.scifin.ui.LocalTokens
import app.scifin.ui.formatDateFull
import app.scifin.i18n.tr
import app.scifin.i18n.trf
import app.scifin.i18n.trp

/**
 * §4.7's restore review: what the file holds, what restoring costs, and the Confirm/Cancel
 * pair -- shown before anything changes, and identical wherever the file was chosen. It is
 * reached from Settings and from §11.1's screen 1, where a fresh install
 * has no Settings to go to yet.
 */
@Composable
fun BackupReview(pend: ValidatedBackup, store: AppStore) {
    val t = LocalTokens.current
    val p = t.palette
    val txCount = pend.profiles.sumOf { it.transactions.size }
    Surface {
        Column(verticalArrangement = Arrangement.spacedBy(t.spacing.u / 2)) {
            Text(
                pend.exportedAt?.let { trf("Backup from {0}", formatDateFull(it)) } ?: tr("Backup from an unknown date"),
                color = p.txt2, fontSize = t.type.body, fontWeight = FontWeight.Bold,
            )
            Text(
                trp("{0} profile", "{0} profiles", pend.profiles.size) + " · " +
                    trp("{0} transaction", "{0} transactions", txCount),
                color = p.acc, fontSize = t.type.figure, fontWeight = FontWeight.Bold,
            )
            Caption(pend.profiles.joinToString(" · ") { it.profile.name })
        }
    }
    FeasibilityNote(BACKUP_REPLACE_NOTE, Modifier.padding(vertical = t.spacing.gap))
    ButtonRow {
        Button(tr("Cancel"), ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.cancelBackup() }
        Button(tr("Replace with this backup"), modifier = Modifier.weight(1f)) { store.confirmBackup() }
    }
}

val BACKUP_REPLACE_NOTE: String get() =
    tr("Restoring replaces everything on this device with what is in the file — every profile, every figure, every setting.") + " " +
        tr("What is here now will be gone. It is not merged.")
