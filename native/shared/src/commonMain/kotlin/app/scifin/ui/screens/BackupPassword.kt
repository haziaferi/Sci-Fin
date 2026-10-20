package app.scifin.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.scifin.app.AppStore
import app.scifin.app.UiState
import app.scifin.engine.BackupCrypto
import app.scifin.engine.LockedBackup
import app.scifin.engine.hintProblem
import app.scifin.ui.LocalTokens
import app.scifin.ui.components.BackupReview
import app.scifin.ui.components.Button
import app.scifin.ui.components.ButtonKind
import app.scifin.ui.components.ButtonRow
import app.scifin.ui.components.Caption
import app.scifin.ui.components.ChipRow
import app.scifin.ui.components.FieldGroup
import app.scifin.ui.components.FieldLabel
import app.scifin.ui.components.InfoNote
import app.scifin.ui.components.TextField
import app.scifin.ui.formatDateFull
import kotlinx.datetime.LocalDate

/*
 * §4.7: a backup file with a password (owner, 2026-10-19/20). Optional, and required while the app PIN is on; the
 * hint is required with it and travels in the file unencrypted, so whoever opens the file can be reminded.
 */

/** Restore's first screen, wherever the file was picked: its password when it has one, else the review card. */
@Composable
fun BackupOpen(state: UiState, store: AppStore): Boolean {
    state.lockedBackup?.let { BackupPasswordPrompt(it, state, store); return true }
    state.pendingBackup?.let { BackupReview(it, store); return true }
    return false
}

@Composable
private fun BackupPasswordPrompt(locked: LockedBackup, state: UiState, store: AppStore) {
    val t = LocalTokens.current
    var password by remember(locked) { mutableStateOf("") }
    val date = runCatching { LocalDate.parse(locked.exportedAt) }.getOrNull()
    InfoNote("This backup${date?.let { " from ${formatDateFull(it)}" } ?: ""} has a password. Enter it to see what the file holds; nothing on this device changes until you confirm on the next step.")
    FieldGroup {
        FieldLabel("The file’s password")
        TextField(password, { password = it; store.clearBackupPasswordWrong() }, modifier = Modifier.padding(top = t.spacing.gap), keyboard = KeyboardType.Password, label = "The file’s password", masked = true)
    }
    if (state.backupPasswordWrong) Caption("That password doesn’t open this file.")
    ShowHintLine(locked.hint)
    Caption("A lost password can’t be recovered: without it, nobody can open this file — not you, and not the app.", Modifier.padding(top = t.spacing.gap))
    Column(Modifier.padding(top = t.spacing.pad)) {
        ButtonRow {
            Button("Cancel", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.cancelBackup() }
            Button("Open", enabled = password.isNotEmpty(), modifier = Modifier.weight(1f)) { store.openLockedBackup(password) }
        }
    }
}

/**
 * Export's step: with a password or without. While the app PIN is on there is no "without". Returns to the
 * page through [onDone] once the file has been handed to the save dialog.
 */
@Composable
fun ExportStep(state: UiState, store: AppStore, onDone: () -> Unit) {
    val t = LocalTokens.current
    val required = state.lock != null
    var withPassword by rememberSaveable { mutableStateOf(required) }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    val on = required || withPassword
    val problem = if (on) BackupCrypto.passwordProblem(password, again, hint) else null
    if (required) InfoNote("While the app PIN is on, a backup file is always written with a password, so the file is no easier to read than the app.")
    else FieldGroup {
        FieldLabel("Protect the file")
        ChipRow(listOf("no" to "No password", "yes" to "With a password"), if (withPassword) "yes" else "no") { withPassword = it == "yes" }
    }
    if (on) {
        FieldGroup {
            FieldLabel("Password, at least ${BackupCrypto.MIN_PASSWORD} characters")
            TextField(password, { password = it }, keyboard = KeyboardType.Password, label = "Password", masked = true)
        }
        FieldGroup {
            FieldLabel("The same password again")
            TextField(again, { again = it }, keyboard = KeyboardType.Password, label = "The same password again", masked = true)
        }
        FieldGroup {
            FieldLabel("Hint")
            TextField(hint, { hint = it }, label = "Hint for the password")
        }
        // the first unmet rule, once there is something to judge; the hint's own rule as soon as it bites
        val shown = when {
            password.isEmpty() -> null
            hintProblem(password, hint, "password") != null -> hintProblem(password, hint, "password")
            again.isEmpty() && password.length >= BackupCrypto.MIN_PASSWORD -> null
            else -> problem
        }
        Caption(shown ?: "The hint is stored in the file without encryption, so anyone holding the file can read it — make it something only you would understand.")
        Caption("With a password, the file opens only with it. A lost password can’t be recovered, by you or by anyone.", Modifier.padding(top = t.spacing.gap))
    } else {
        Caption("Without a password, anyone who holds the file can read everything in it.")
    }
    Column(Modifier.padding(top = t.spacing.pad)) {
        ButtonRow {
            Button("Cancel", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { onDone() }
            Button("Export", enabled = !on || problem == null, modifier = Modifier.weight(1f)) {
                store.exportBackup(if (on) password else null, if (on) hint else ""); onDone()
            }
        }
    }
}
