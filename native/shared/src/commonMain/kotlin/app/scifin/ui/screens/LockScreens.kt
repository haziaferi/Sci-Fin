package app.scifin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import app.scifin.app.AppStore
import app.scifin.app.UiState
import app.scifin.engine.LockAfter
import app.scifin.engine.Pin
import app.scifin.engine.hintProblem
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.LucideIcon
import app.scifin.ui.components.Button
import app.scifin.ui.components.ButtonKind
import app.scifin.ui.components.ButtonRow
import app.scifin.ui.components.Caption
import app.scifin.ui.components.ChipRow
import app.scifin.ui.components.FeasibilityNote
import app.scifin.ui.components.FieldGroup
import app.scifin.ui.components.FieldLabel
import app.scifin.ui.components.SettingRow
import app.scifin.ui.components.Switch
import app.scifin.ui.components.TextField
import app.scifin.ui.formatDateFull
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import app.scifin.i18n.typedWordMatches
import app.scifin.i18n.tr
import app.scifin.i18n.trf

/*
 * §4.7's Security & lock (owner, 2026-10-19): an optional PIN of the app's own, off by default, with an optional
 * hint; a wait after five wrong PINs; and, for a forgotten PIN, erasing the device and starting over. The gate
 * is drawn *instead of* the app (Frame.kt), so nothing beneath it can be read or reached by a screen reader.
 */

private val FORGOTTEN: String get() = tr("A forgotten PIN can’t be recovered. The way back in is to erase everything on this device and start over, then restore your last backup.")

// ---- the hint, where a PIN or password is chosen and where it is asked for ------------------------------

/** Where a secret is chosen: the optional hint field, shown once there is a secret, with the rule's message under it. */
@Composable
fun HintField(hint: String, onHint: (String) -> Unit, secret: String, noun: String, modifier: Modifier = Modifier) {
    if (secret.isEmpty()) return
    Column(modifier) {
        FieldGroup {
            FieldLabel(tr("Add a hint, optional"))
            TextField(hint, onHint, label = if (noun == "PIN") tr("Hint for the PIN, optional") else tr("Hint for the password, optional"))
        }
        Caption(
            hintProblem(secret, hint, noun)
                ?: if (noun == "PIN") tr("Shown only when you ask for it while entering the PIN. Anyone who can see the screen then can read it, so make it something only you would understand.")
                else tr("Shown only when you ask for it while entering the password. Anyone who can see the screen then can read it, so make it something only you would understand."),
        )
    }
}

/** Where a secret is asked for: "Show hint" when one was added, revealing it on request. */
@Composable
fun ShowHintLine(hint: String?, modifier: Modifier = Modifier, initiallyShown: Boolean = false) {
    if (hint.isNullOrBlank()) return
    var shown by rememberSaveable { mutableStateOf(initiallyShown) }
    Column(modifier) {
        Button(if (shown) tr("Hide hint") else tr("Show hint"), ButtonKind.GHOST, modifier = Modifier.fillMaxWidth()) { shown = !shown }
        if (shown) Caption(trf("Hint: {0}", hint.trim()))
    }
}

// ---- the gate ------------------------------------------------------------------------------------------

@Composable
fun LockGate(state: UiState, store: AppStore) {
    val lock = state.lock ?: return
    val t = LocalTokens.current; val p = t.palette
    var forgot by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(t.spacing.pad), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = t.measure).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(t.spacing.gap),
        ) {
            LucideIcon(Lucide.LOCK, tint = p.txt2, size = t.icons.md * 1.5f)
            Text(
                if (forgot) tr("Erase and start over") else tr("Sci-Fin is locked"),
                color = p.txt, fontSize = t.type.title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (forgot) ForgotPin(state, store) { forgot = false }
            else PinEntry(state, store, lock.pinLength, lock.hint) { forgot = true }
        }
    }
}

/** Seconds left on a running wait, ticking; null when there is none. */
@Composable
private fun waitLeft(state: UiState): Long? {
    val ends = state.pinWaitEnds ?: return null
    fun now() = ((-ends.elapsedNow()).inWholeMilliseconds + 999) / 1000
    var left by remember(ends) { mutableStateOf(now()) }
    LaunchedEffect(ends) {
        while (left > 0) { delay(1.seconds); left = now() }
    }
    return left.takeIf { it > 0 }
}

private fun waitText(seconds: Long) =
    if (seconds < 60) trf("{0} seconds", seconds) else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/** The PIN field: it tries itself on the last digit, so no second tap is needed; Unlock is there for a slip. */
@Composable
private fun PinField(
    state: UiState, label: String, action: String, pinLength: Int?, focus: Boolean, onTry: (String) -> Unit, clearStore: () -> Unit,
) {
    var entry by remember { mutableStateOf("") }
    val requester = remember { FocusRequester() }
    if (focus) LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    // a wrong PIN clears the field, ready for the next try
    LaunchedEffect(state.pinWrong) { if (state.pinWrong) entry = "" }
    val wait = waitLeft(state)
    FieldGroup {
        FieldLabel(label)
        TextField(entry, { v ->
            entry = Pin.clean(v); clearStore()
            if (wait == null && pinLength != null && entry.length == pinLength) onTry(entry)
        }, modifier = Modifier.focusRequester(requester), keyboard = KeyboardType.NumberPassword, label = label, masked = true)
    }
    when {
        wait != null -> Caption(trf("Too many wrong PINs. Try again in {0}.", waitText(wait)))
        state.pinWrong -> Caption(tr("That isn’t the PIN."))
    }
    Button(action, enabled = wait == null && Pin.valid(entry), modifier = Modifier.fillMaxWidth()) { onTry(entry) }
}

@Composable
private fun PinEntry(state: UiState, store: AppStore, pinLength: Int, hint: String?, onForgot: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        PinField(state, tr("PIN"), tr("Unlock"), pinLength, focus = true, onTry = store::unlock, clearStore = store::clearPinWrong)
        ShowHintLine(hint)
        Button(tr("Forgot PIN?"), ButtonKind.GHOST, modifier = Modifier.fillMaxWidth()) { onForgot() }
    }
}

/** §4.7's way back in: everything goes, after a warning naming the last export and the typed word ERASE. */
@Composable
private fun ForgotPin(state: UiState, store: AppStore, onCancel: () -> Unit) {
    val t = LocalTokens.current
    var typed by remember { mutableStateOf("") }
    val word = tr("ERASE")
    val ready = typedWordMatches(typed, "ERASE")
    val exported = state.appState?.lastBackupExportDate
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
        FeasibilityNote(
            tr("This deletes every profile, every transaction and every setting on this device, and the app’s own safety copies. It can’t be undone.") + " " +
                tr("Sci-Fin then opens as new, where you can restore a backup file."),
        )
        Caption(if (exported != null) trf("Your last export from this device was on {0}. Anything logged since then won’t be in it.", formatDateFull(exported)) else tr("No backup has been exported from this device, so nothing here can be brought back."))
        FieldGroup { FieldLabel(trf("Type {0} to confirm", word)); TextField(typed, { typed = it }, placeholder = word, label = trf("Type {0} to confirm", word)) }
        ButtonRow {
            Button(tr("Cancel"), ButtonKind.GHOST, modifier = Modifier.weight(1f)) { onCancel() }
            Button(tr("Erase everything"), enabled = ready, modifier = Modifier.weight(1f)) { store.eraseEverything() }
        }
    }
}

// ---- Settings › Security & lock ------------------------------------------------------------------------

@Composable
internal fun SecurityPage(state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val lock = state.lock
    var settingUp by rememberSaveable { mutableStateOf(false) }
    var turningOff by rememberSaveable { mutableStateOf(false) }
    var changing by rememberSaveable { mutableStateOf(false) }
    SettingRow(Lucide.LOCK, tr("Lock with a PIN"), tr("Off by default — 4 to 6 digits, asked for each time Sci-Fin opens on this device")) {
        Switch(lock != null || settingUp) {
            when {
                lock != null -> { turningOff = !turningOff; changing = false; store.clearPinWrong() }
                else -> settingUp = !settingUp
            }
        }
    }
    when {
        lock == null && settingUp -> NewPin(state, store) { settingUp = false }
        lock != null && changing -> NewPin(state, store, change = true) { changing = false }
        lock != null && turningOff -> Column(Modifier.padding(top = t.spacing.gap)) {
            Caption(tr("Enter the PIN to turn the lock off."), Modifier.padding(bottom = t.spacing.gap))
            PinField(state, tr("Current PIN"), tr("Turn off"), null, focus = true, onTry = { store.turnOffLock(it) }, clearStore = store::clearPinWrong)
            Button(tr("Keep the lock"), ButtonKind.GHOST, modifier = Modifier.fillMaxWidth()) { turningOff = false; store.clearPinWrong() }
        }
        lock != null -> Column(Modifier.padding(top = t.spacing.gap)) {
            FieldGroup {
                FieldLabel(tr("Lock after leaving the app"))
                ChipRow(LockAfter.entries.map { it.token to it.label }, lock.after.token) { store.setLockAfter(LockAfter.of(it)) }
            }
            Caption(tr("Leaving is switching to another app or minimising Sci-Fin."), Modifier.padding(bottom = t.spacing.gap))
            ButtonRow {
                Button(tr("Change PIN"), ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { changing = true; store.clearPinWrong() }
                Button(tr("Lock now"), ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { store.lockNow() }
            }
        }
    }
    LaunchedEffect(lock == null) { if (lock == null) { turningOff = false; changing = false } }
    Caption(FORGOTTEN, Modifier.padding(top = t.spacing.gap))
}

/**
 * Choosing a PIN: setting the first one, or -- with [change] -- replacing it behind the current one, the hint
 * starting as the one kept. Typing the same PIN as the new one changes only the hint.
 */
@Composable
private fun NewPin(state: UiState, store: AppStore, change: Boolean = false, onDone: () -> Unit) {
    val t = LocalTokens.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf(if (change) state.lock?.hint.orEmpty() else "") }
    val wait = if (change) waitLeft(state) else null
    val ready = Pin.valid(first) && first == second && hintProblem(first, hint, "PIN") == null && (!change || (Pin.valid(current) && wait == null))
    // a wrong current PIN clears that field, ready for the next try
    LaunchedEffect(state.pinWrong) { if (change && state.pinWrong) current = "" }
    Column(Modifier.padding(top = t.spacing.gap)) {
        if (change) {
            FieldGroup {
                FieldLabel(tr("Current PIN"))
                TextField(current, { current = Pin.clean(it); store.clearPinWrong() }, keyboard = KeyboardType.NumberPassword, label = tr("Current PIN"), masked = true)
            }
            when {
                wait != null -> Caption(trf("Too many wrong PINs. Try again in {0}.", waitText(wait)), Modifier.padding(bottom = t.spacing.gap))
                state.pinWrong -> Caption(tr("That isn’t the PIN."), Modifier.padding(bottom = t.spacing.gap))
            }
        }
        FieldGroup {
            FieldLabel(tr("New PIN, 4 to 6 digits"))
            TextField(first, { first = Pin.clean(it) }, keyboard = KeyboardType.NumberPassword, label = tr("New PIN, 4 to 6 digits"), masked = true)
        }
        FieldGroup {
            FieldLabel(tr("The same PIN again"))
            TextField(second, { second = Pin.clean(it) }, keyboard = KeyboardType.NumberPassword, label = tr("The same PIN again"), masked = true)
        }
        if (second.isNotEmpty() && second.length >= first.length && second != first) Caption(tr("The two don’t match yet."))
        HintField(hint, { hint = it }, first, "PIN", Modifier.padding(top = t.spacing.gap))
        ButtonRow {
            Button(tr("Cancel"), ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.clearPinWrong(); onDone() }
            if (change) Button(tr("Change PIN"), enabled = ready, modifier = Modifier.weight(1f)) {
                scope.launch { store.changePin(current, first, hint).join(); if (!store.state.value.pinWrong && store.state.value.pinWaitEnds == null) onDone() }
            }
            else Button(tr("Set PIN"), enabled = ready, modifier = Modifier.weight(1f)) { store.setPin(first, hint); onDone() }
        }
    }
}
