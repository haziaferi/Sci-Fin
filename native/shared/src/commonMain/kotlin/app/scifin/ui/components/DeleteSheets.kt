package app.scifin.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.scifin.ui.Lucide
import app.scifin.ui.LucideIcon
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.scifin.ui.LocalTokens

/**
 * §3.6's and §3.11's delete prompt when there is money to add back: the
 * accepting action on its own line, then Cancel / Delete only -- and either delete leads to a
 * second step that states exactly what is about to happen, with Go back / Delete. A confirmation
 * rather than an undo: the person reads the consequence before it happens, not after.
 *
 * With nothing to add back there is one question and one Cancel / Delete row, which is already
 * the confirmation.
 */
@Composable
fun DeleteWithReturnSheet(
    title: String,
    question: String,
    hasReturn: Boolean,
    confirmAddBack: String,
    confirmDeleteOnly: String,
    onDismiss: () -> Unit,
    onDelete: (returnMoney: Boolean) -> Unit,
    note: @Composable () -> Unit = {},
    /** Renders only: start on a confirmation step instead of the question. */
    initialPending: Boolean? = null,
) {
    val t = LocalTokens.current; val p = t.palette
    // null: the question; true / false: confirming that path
    var pending by remember { mutableStateOf(initialPending) }
    SheetScaffold(title = title, onDismiss = onDismiss) {
        note()
        val body = when (pending) { null -> question; true -> confirmAddBack; false -> confirmDeleteOnly }
        Text(body, color = p.txt, fontSize = t.type.body, lineHeight = t.type.body * 1.5, modifier = Modifier.padding(bottom = t.spacing.pad))
        if (pending == null) {
            // three actions do not share one row at phone width, so the accepting one takes its own line
            if (hasReturn) Button("Delete and add back", modifier = Modifier.fillMaxWidth().padding(bottom = t.spacing.gap)) { pending = true }
            ButtonRow {
                Button("Cancel", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { onDismiss() }
                if (hasReturn) Button("Delete only", ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { pending = false }
                else Button("Delete", ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { onDelete(false) }
            }
        } else ButtonRow {
            Button("Go back", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { pending = null }
            Button("Delete", ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { onDelete(pending == true) }
        }
    }
}

/**
 * The editors' trash button: target height, and a label that names what it deletes.
 */
@Composable
fun EditorDeleteButton(name: String, onClick: () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    Box(
        Modifier.heightIn(min = t.spacing.target)
            .border(t.strokes.ctl, p.txt2, RoundedCornerShape(t.radii.md))
            .clickable { onClick() }
            .semantics { contentDescription = "Delete $name"; role = Role.Button }
            .padding(horizontal = t.spacing.u * 2, vertical = t.spacing.u * 1.5f),
        contentAlignment = Alignment.Center,
    ) { LucideIcon(Lucide.TRASH_2, tint = p.txt2, size = t.icons.md) }
}

/*
 * The second step's copy, in one place. Future tense: the confirming Delete is still ahead when
 * these are read.
 */
fun deleteOnlyCopy(name: String): String = "$name will be deleted. Savings stays as it is."
fun deleteAddBackCopy(amount: String, name: String): String = "$amount will go back to Savings and $name will be deleted. Its past history stays in the log."
