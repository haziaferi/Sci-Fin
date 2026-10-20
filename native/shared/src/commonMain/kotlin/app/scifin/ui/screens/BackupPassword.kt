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
import app.scifin.app.COPIES_KEPT
import app.scifin.app.CopyEvery
import app.scifin.ui.Lucide
import app.scifin.ui.components.FeasibilityNote
import app.scifin.ui.components.NavRow
import app.scifin.ui.components.SectionLabel
import app.scifin.ui.components.SheetScaffold
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

/** A file password being chosen: the password twice and its required hint (§4.7). */
private class PasswordDraft {
    var password by mutableStateOf("")
    var again by mutableStateOf("")
    var hint by mutableStateOf("")
    val problem: String? get() = BackupCrypto.passwordProblem(password, again, hint)
}

/** The three fields and the first unmet rule, once there is something to judge. */
@Composable
private fun PasswordFields(d: PasswordDraft, hintNote: String) {
    val t = LocalTokens.current
    FieldGroup {
        FieldLabel("Password, at least ${BackupCrypto.MIN_PASSWORD} characters")
        TextField(d.password, { d.password = it }, keyboard = KeyboardType.Password, label = "Password", masked = true)
    }
    FieldGroup {
        FieldLabel("The same password again")
        TextField(d.again, { d.again = it }, keyboard = KeyboardType.Password, label = "The same password again", masked = true)
    }
    FieldGroup {
        FieldLabel("Hint")
        TextField(d.hint, { d.hint = it }, label = "Hint for the password")
    }
    val hintRule = hintProblem(d.password, d.hint, "password")
    val shown = when {
        d.password.isEmpty() -> null
        hintRule != null -> hintRule
        d.again.isEmpty() && d.password.length >= BackupCrypto.MIN_PASSWORD -> null
        else -> d.problem
    }
    Caption(shown ?: hintNote)
    Caption("A lost password can’t be recovered: without it, nobody can open the file — not you, and not the app.", Modifier.padding(top = t.spacing.gap))
}

private const val HINT_IN_FILE = "The hint is stored in the file without encryption, so anyone holding the file can read it — make it something only you would understand."
private const val HINT_IN_COPIES = "The hint travels in every copy without encryption, and shows when a copy is opened."

/**
 * Export's step. With the copies' password set, it is offered first (owner, 2026-10-20) and needs no typing;
 * "A different password" asks for one for this file; "No password" exists only while the app PIN is off.
 */
@Composable
fun ExportStep(state: UiState, store: AppStore, onDone: () -> Unit) {
    val t = LocalTokens.current
    val pinOn = state.lock != null
    val copies = state.copy?.hasPassword == true
    val options = buildList {
        if (copies) add("copies" to "The copies’ password")
        add("new" to if (copies) "A different password" else "With a password")
        if (!pinOn) add("none" to "No password")
    }
    var choice by rememberSaveable { mutableStateOf(if (copies) "copies" else if (pinOn) "new" else "none") }
    val draft = remember { PasswordDraft() }
    if (pinOn) InfoNote("While the app PIN is on, a backup file is always written with a password, so the file is no easier to read than the app.")
    if (options.size > 1) FieldGroup {
        FieldLabel("Protect the file")
        ChipRow(options, choice) { choice = it }
    }
    when (choice) {
        "copies" -> Caption("The file opens with the same password as the automatic copies, and carries the same hint: “${state.copy?.hint.orEmpty()}”.")
        "new" -> PasswordFields(draft, HINT_IN_FILE)
        else -> Caption("Without a password, anyone who holds the file can read everything in it.")
    }
    Column(Modifier.padding(top = t.spacing.pad)) {
        ButtonRow {
            Button("Cancel", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { onDone() }
            Button("Export", enabled = choice != "new" || draft.problem == null, modifier = Modifier.weight(1f)) {
                when (choice) {
                    "copies" -> store.exportWithCopiesPassword()
                    "new" -> store.exportBackup(draft.password, draft.hint)
                    else -> store.exportBackup()
                }
                onDone()
            }
        }
    }
}

// ---- §4.7's automatic copy ----------------------------------------------------------------------------

/** Data & backup's "Automatic copy" section. */
@Composable
fun AutomaticCopy(state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val c = state.copy
    val every = c?.every ?: CopyEvery.OFF
    var settingPassword by rememberSaveable { mutableStateOf(false) }
    SectionLabel("Automatic copy")
    FieldGroup {
        FieldLabel("How often")
        ChipRow(CopyEvery.entries.map { it.token to it.label }, every.token) { store.setCopyEvery(CopyEvery.of(it)) }
    }
    if (c == null || every == CopyEvery.OFF) {
        // ambient (§2.3): a fact, no instruction
        val last = c?.lastCopy
        Caption(if (last != null) "Automatic copies are off. The last one was written on ${formatDateFull(last)}." else "This data is only on this device until you export it or turn automatic copies on.")
        return
    }
    NavRow(Lucide.DATABASE, "Folder", c.folderLabel ?: "Choose a folder") { store.chooseCopyFolder() }
    Caption(
        "Each copy is the whole backup, written ${if (every == CopyEvery.DAILY) "once a day" else "once a week"} into that folder, " +
            "where a sync app can carry it off the device. The last $COPIES_KEPT are kept; the oldest is replaced by the newest.",
        Modifier.padding(start = t.spacing.u * 0.25f, top = t.spacing.u / 2, bottom = t.spacing.gap),
    )
    when {
        c.paused -> FeasibilityNote("Paused: the app PIN is on, and the copies have no password yet. Nothing is written until one is set, so a copy is never easier to read than the app.")
        c.lastError != null -> FeasibilityNote("The last copy could not be written. ${c.lastError}")
    }
    Caption(c.lastCopy?.let { "Last copy: ${formatDateFull(it)}." } ?: "No copy written yet.", Modifier.padding(bottom = t.spacing.gap))

    if (settingPassword || (c.paused && !c.hasPassword)) {
        val draft = remember { PasswordDraft() }
        PasswordFields(draft, HINT_IN_COPIES)
        ButtonRow {
            if (!c.paused) Button("Cancel", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { settingPassword = false }
            Button("Set the copies’ password", enabled = draft.problem == null, modifier = Modifier.weight(1f)) {
                store.setCopyPassword(draft.password, draft.hint); settingPassword = false
            }
        }
    } else if (c.hasPassword) {
        Caption("Copies are written with a password. Its hint, kept in each copy: “${c.hint.orEmpty()}”.")
        ButtonRow {
            Button("Change password", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { settingPassword = true }
            if (state.lock == null) Button("Remove it", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.removeCopyPassword() }
        }
    } else {
        Caption("Copies are written without a password: anyone who can open the folder can read them.")
        Button("Add a password", ButtonKind.GHOST, modifier = Modifier.fillMaxWidth()) { settingPassword = true }
    }
    Button("Copy now", ButtonKind.SECONDARY, enabled = !c.paused && c.folderLabel != null, modifier = Modifier.fillMaxWidth().padding(top = t.spacing.gap)) { store.copyNow() }
}

/**
 * The prompt owed right after the app PIN is turned on while the copies have no password (owner, 2026-10-20):
 * the copies are paused until a password is set. "Later" leaves them paused; Data & backup says so.
 */
@Composable
fun CopyPasswordPrompt(store: AppStore) {
    val draft = remember { PasswordDraft() }
    SheetScaffold(title = "A password for the copies", onDismiss = { store.dismissCopyPasswordPrompt() }) {
        InfoNote("The app PIN is now on, so automatic copies are paused until they have a password of their own — a copy must never be easier to read than the app. Choose one, with a hint to show whenever a copy is opened.")
        PasswordFields(draft, HINT_IN_COPIES)
        ButtonRow {
            Button("Later", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.dismissCopyPasswordPrompt() }
            Button("Set password", enabled = draft.problem == null, modifier = Modifier.weight(1f)) { store.setCopyPassword(draft.password, draft.hint) }
        }
    }
}
