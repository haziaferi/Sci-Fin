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
                "Backup from ${pend.exportedAt?.let { formatDateFull(it) } ?: "an unknown date"}",
                color = p.txt2, fontSize = t.type.body, fontWeight = FontWeight.Bold,
            )
            Text(
                "${pend.profiles.size} ${if (pend.profiles.size == 1) "profile" else "profiles"} · " +
                    "$txCount ${if (txCount == 1) "transaction" else "transactions"}",
                color = p.acc, fontSize = t.type.figure, fontWeight = FontWeight.Bold,
            )
            Caption(pend.profiles.joinToString(" · ") { it.profile.name })
        }
    }
    FeasibilityNote(BACKUP_REPLACE_NOTE, Modifier.padding(vertical = t.spacing.gap))
    ButtonRow {
        Button("Cancel", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.cancelBackup() }
        Button("Replace with this backup", modifier = Modifier.weight(1f)) { store.confirmBackup() }
    }
}

const val BACKUP_REPLACE_NOTE =
    "Restoring replaces everything on this device with what is in the file — every profile, every figure, every setting. " +
        "What is here now will be gone. It is not merged."
