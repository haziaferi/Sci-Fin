package app.scifin.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import app.scifin.app.AppStore
import app.scifin.app.Sheet
import app.scifin.engine.Entry
import app.scifin.engine.Hourly
import app.scifin.i18n.tr
import app.scifin.i18n.trf
import app.scifin.ui.screens.hoursShort
import app.scifin.ui.screens.parseHoursText
import app.scifin.model.Money
import app.scifin.model.ProfileData
import app.scifin.ui.LocalTokens
import app.scifin.ui.components.Caption
import app.scifin.ui.components.DateField
import app.scifin.ui.components.SheetScaffold
import app.scifin.ui.components.probeAs
import app.scifin.ui.currencySymbol
import app.scifin.ui.formatDate
import app.scifin.ui.formatMoney
import app.scifin.ui.moneyText
import app.scifin.ui.parseMoney
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * §3.6's entry sheet: one sentence whose underlined words are its
 * controls. A tapped word opens its options above the sentence, so the sentence stays at the sheet's foot. "Done"
 * sits at the title's height and is the only way to write; dismissing the sheet writes nothing. The cursor starts
 * in the amount, and Enter writes once the readout is complete. Wording and rules live in [Entry].
 */
@Composable
fun EntrySheet(sheet: Sheet.Entry, d: ProfileData, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val today = store.clock.today()
    val yesterday = today.minus(DatePeriod(days = 1))
    fun dayWord(x: LocalDate) = when (x) { today -> "today"; yesterday -> "yesterday"; else -> null }
    fun dayLabel(x: LocalDate) = when (x) { today -> tr("today"); yesterday -> tr("yesterday"); else -> null }
    val cur = d.settings.currency
    val start = remember {
        // §3.10: payday's entry (salaried, manual) and "Log hours" open on Savings' Income
        if (sheet.income && d.settings.incomeEnabled) return@remember "savings" to "income"
        val last = store.lastEntry?.takeIf { Entry.exists(d, it.first) }
        val target = sheet.target?.takeIf { Entry.exists(d, it) } ?: last?.first ?: Entry.defaultTarget(d)
        val dir = last?.takeIf { it.first == target && Entry.directions(d, target)?.any { o -> o.first == it.second } == true }?.second
        target to (dir ?: Entry.firstDirection(d, target))
    }
    var target by remember { mutableStateOf(start.first) }
    var direction by remember { mutableStateOf(start.second) }
    var amountText by remember { mutableStateOf(if (sheet.income && !Hourly.isHourly(d.settings) && d.settings.income > 0) moneyText(d.settings.income) else "") }
    // §3.10: on hourly income the Income verb logs a shift -- hours at a rate -- and moves nothing until payday
    var rateText by remember { mutableStateOf(if (d.settings.hourlyRate > 0) moneyText(d.settings.hourlyRate) else "") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today) }
    var pair by remember { mutableStateOf(Entry.pairDefault(d, start.first)) }
    var open by remember { mutableStateOf<String?>(null) }
    val amountFocus = remember { FocusRequester() }

    val shift = Hourly.isHourly(d.settings) && Entry.areaOf(target) == Entry.Area.SAVINGS && direction == "income"
    val hours = if (shift) parseHoursText(amountText) else null
    val rate = if (shift) parseMoney(rateText)?.takeIf { it > 0 } else null
    val amount: Money? = if (shift) null else parseMoney(amountText)
    val moves = Entry.moves(d, target, direction, amount, pair)
    val ready = if (shift) hours != null && rate != null else moves != null
    val done = {
        if (shift) {
            if (hours != null && rate != null) { store.logShift(date, hours, rate); store.lastEntry = target to direction; store.closeSheet() }
        } else if (moves != null) {
            val a = amount!!; val tg = target; val dir = direction; val n = note; val dt = date; val pr = pair
            store.dispatch { pd -> Entry.write(pd, tg, dir, a, n, dt, pr, store.clock, store.newId) }
            store.lastEntry = tg to dir
            store.closeSheet()
        }
    }
    fun choose(next: String) {
        if (Entry.areaOf(next) != Entry.areaOf(target) || Entry.directions(d, next)?.any { it.first == direction } != true) direction = Entry.firstDirection(d, next)
        target = next; pair = Entry.pairDefault(d, next); open = null
    }
    fun pickDirection(next: String) {
        direction = next; open = null
        // §3.3's manual path: Income starts on the paycheck amount
        if (next == "income" && amountText.isEmpty() && !d.settings.incomeAutoDeposit && !Hourly.isHourly(d.settings) && d.settings.income > 0) amountText = moneyText(d.settings.income)
    }
    // the cursor returns to the amount whenever no options are open
    LaunchedEffect(open) { if (open == null) runCatching { amountFocus.requestFocus() } }
    // Enter (main or keypad) writes once complete; Back closes without writing. A focused field receives Back's KeyDown
    // but not its KeyUp, so the activity's handler never fires; handle it here.
    val enter = Modifier.onPreviewKeyEvent { e ->
        when {
            (e.key == Key.Enter || e.key == Key.NumPadEnter) && e.type == KeyEventType.KeyDown -> { done(); true }
            e.key == Key.Back && e.type == KeyEventType.KeyDown -> { store.closeSheet(); true }
            else -> false
        }
    }

    SheetScaffold(title = tr("Log"), onDismiss = store::closeSheet, action = {
        Box(Modifier.heightIn(min = t.spacing.target).widthIn(min = t.spacing.target).clickable(enabled = ready, role = Role.Button, onClickLabel = tr("Log it")) { done() }.probeAs("entry-done"), contentAlignment = Alignment.CenterEnd) {
            Text(tr("Done"), color = if (ready) p.acc else p.txt3, fontSize = t.type.body)
        }
    }) {
        // the options of the word that was tapped, above the sentence
        val area = Entry.areaOf(target)
        when (open) {
            "area" -> Options(Entry.Area.entries.filter { Entry.targetsIn(d, it).isNotEmpty() }.map { it.name to it.word }, area.name) { a ->
                val next = Entry.Area.valueOf(a)
                if (next == area) { open = null; return@Options }
                val ts = Entry.targetsIn(d, next)
                val remembered = store.lastEntry?.first?.takeIf { Entry.areaOf(it) == next && Entry.exists(d, it) }
                choose(remembered ?: ts.first().first)
                if (ts.size > 1) open = "target"            // a new area shows its names at once; Savings has one
            }
            "dir" -> Options(Entry.directions(d, target).orEmpty(), direction) { pickDirection(it) }
            "target" -> Options(Entry.targetsIn(d, area), target) { choose(it) }
            "date" -> Column {
                Options(listOf("today" to tr("Today"), "yesterday" to tr("Yesterday")), dayWord(date) ?: "") {
                    date = if (it == "today") today else yesterday; open = null
                }
                DateField(date) { date = it; open = null }
            }
        }
        // the sentence
        val field = TextStyle(color = p.txt, fontSize = t.type.body)
        // the gaps are the row's spacing, so a wrapped line never starts with a space; a comma travels with its word
        FlowRow(Modifier.fillMaxWidth().padding(top = t.spacing.u).probeAs("entry-sentence"), horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 0.6f), verticalArrangement = Arrangement.Center) {
            Word(area.word, open == "area", "area") { open = if (open == "area") null else "area" }
            Inline("–")
            val dirs = Entry.directions(d, target)
            if (dirs != null) Word(dirs.first { it.first == direction }.second, open == "dir", "dir") { open = if (open == "dir") null else "dir" }
            else Inline(Entry.fixedVerb(target))
            if (!shift) Inline(currencySymbol(cur))
            BasicTextField(
                amountText, { amountText = it }, singleLine = true, textStyle = field.copy(fontWeight = FontWeight.Bold), cursorBrush = SolidColor(p.acc),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done, autoCorrectEnabled = false), keyboardActions = KeyboardActions(onDone = { done() }),
                modifier = Modifier.width(t.spacing.u * 11).heightIn(min = t.spacing.target).focusRequester(amountFocus).then(enter).probeAs("word-amount"),
                decorationBox = { inner ->
                    // the one always-typed field: underlined in the accent
                    val line = t.strokes.emph; val gap = t.spacing.u
                    Box(Modifier.heightIn(min = t.spacing.target).drawBehind {
                        val y = size.height - gap.toPx(); drawLine(p.acc, Offset(0f, y), Offset(size.width, y), line.toPx())
                    }, contentAlignment = Alignment.CenterStart) {
                        if (amountText.isEmpty()) Text(if (shift) "0" else "0.00", color = p.txt3, fontSize = t.type.body, fontWeight = FontWeight.Bold)
                        inner()
                    }
                },
            )
            if (shift) {
                Inline(tr("h at"))
                BasicTextField(
                    rateText, { rateText = it }, singleLine = true, textStyle = field.copy(fontWeight = FontWeight.Bold), cursorBrush = SolidColor(p.acc),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done, autoCorrectEnabled = false), keyboardActions = KeyboardActions(onDone = { done() }),
                    modifier = Modifier.width(t.spacing.u * 10).heightIn(min = t.spacing.target).then(enter).probeAs("word-rate"),
                    decorationBox = { inner ->
                        // typed like the hours, so underlined like them; the symbol travels with the figure when the line wraps
                        val line = t.strokes.emph; val gap = t.spacing.u
                        Row(Modifier.heightIn(min = t.spacing.target).drawBehind {
                            val y = size.height - gap.toPx(); drawLine(p.acc, Offset(0f, y), Offset(size.width, y), line.toPx())
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Text(currencySymbol(cur), color = p.txt2, fontSize = t.type.body)
                            Box(Modifier.padding(start = t.spacing.u * 0.4f), contentAlignment = Alignment.CenterStart) {
                                if (rateText.isEmpty()) Text("0.00", color = p.txt3, fontSize = t.type.body, fontWeight = FontWeight.Bold)
                                inner()
                            }
                        }
                    },
                )
            }
            Entry.preposition(target, direction)?.let { prep ->
                Inline(prep)
                Word(Entry.nameOf(d, target), open == "target", "target") { open = if (open == "target") null else "target" }
            }
            val word = if (shift) null else Entry.noteWord(target, direction)
            val pays = Entry.areaOf(target) == Entry.Area.ACCOUNT && direction == "payment"
            Word(dayLabel(date) ?: formatDate(date), open == "date", "date",
                trailing = if (word != null || pays) "," else "") { open = if (open == "date") null else "date" }
            if (word != null) {
                Inline(word)
                BasicTextField(
                    note, { note = it }, singleLine = true, textStyle = field, cursorBrush = SolidColor(p.acc),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { done() }),
                    modifier = Modifier.widthIn(min = t.spacing.u * 16).heightIn(min = t.spacing.target).then(enter).probeAs("word-note"),
                    decorationBox = { inner ->
                        Box(Modifier.heightIn(min = t.spacing.target), contentAlignment = Alignment.CenterStart) {
                            if (note.isEmpty()) Text(trf("{0} (optional)", Entry.noteHint(word)), color = p.txt3, fontSize = t.type.body)
                            inner()
                        }
                    },
                )
            } else if (pays) {
                Word(if (pair) tr("also from Savings") else tr("not from Savings"), false, "pair") { pair = !pair }
            }
        }
        // the readout: what will move, and the captions
        val readout = if (shift) (if (hours != null && rate != null) trf("{0} · {1} before deductions", hoursShort(hours), formatMoney(Hourly.gross(listOf(app.scifin.model.Shift("", "", date, hours, rate))), cur)) else null)
            else moves?.joinToString(" · ") { (n, a) -> "$n ${if (a > 0) "+" else "−"}${formatMoney(if (a < 0) -a else a, cur)}" }
        Text(readout ?: "", color = p.txt2, fontSize = t.type.caption, modifier = Modifier.padding(top = t.spacing.u * 0.5f).probeAs("entry-readout"))
        val captions = if (shift) listOf(tr("Nothing reaches Savings until payday, when you confirm what arrived."))
            else Entry.captions(d, target, direction, amount, note, pair) { formatMoney(it, cur) }
        for (c in captions) Caption(c, Modifier.padding(top = t.spacing.u * 0.5f))
    }
}

/** A word of the sentence that is its own control: underlined, and its options open above the sentence. */
@Composable
private fun Word(text: String, isOpen: Boolean, id: String, trailing: String = "", onClick: () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.heightIn(min = t.spacing.target).clickable(role = Role.Button, onClickLabel = tr("Change"), onClick = onClick).probeAs("word-$id"), contentAlignment = Alignment.CenterStart) {
            Text(text, color = if (isOpen) p.acc else p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
        }
        if (trailing.isNotEmpty()) Text(trailing, color = p.txt2, fontSize = t.type.body)
    }
}

@Composable
private fun Inline(text: String) {
    val t = LocalTokens.current
    Box(Modifier.heightIn(min = t.spacing.target), contentAlignment = Alignment.CenterStart) { Text(text, color = t.palette.txt2, fontSize = t.type.body) }
}

/** The options of the tapped word, as chips; the current one is filled. */
@Composable
private fun Options(options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    val pill = RoundedCornerShape(percent = 50)
    FlowRow(Modifier.fillMaxWidth().probeAs("entry-options"), horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 0.75f), verticalArrangement = Arrangement.Center) {
        for ((key, label) in options) {
            val on = key == current
            Box(Modifier.heightIn(min = t.spacing.target).selectable(selected = on, role = Role.RadioButton) { onPick(key) }.probeAs("option-$label"), contentAlignment = Alignment.Center) {
                Text(label, color = p.txt, fontSize = t.type.body, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.let { m -> if (on) m.background(p.surf2, pill) else m.border(t.strokes.ctl, p.bord, pill) }.padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 0.75f))
            }
        }
    }
}
