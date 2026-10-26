package app.scifin.ui.screens

import app.scifin.i18n.tr
import app.scifin.i18n.trf
import app.scifin.i18n.trp
import app.scifin.ui.components.EditorDeleteButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Sheet
import app.scifin.app.UiState
import app.scifin.engine.Recurring
import app.scifin.engine.Recurring.apportioned
import app.scifin.engine.minusDays
import app.scifin.model.AccountType
import app.scifin.model.Cadence
import app.scifin.model.DateMode
import app.scifin.model.REMINDER_LEAD_DAYS
import app.scifin.model.RecurringExpense
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.components.Button
import app.scifin.ui.components.ButtonKind
import app.scifin.ui.components.ButtonRow
import app.scifin.ui.components.Caption
import app.scifin.ui.components.DeleteWithReturnSheet
import app.scifin.ui.components.ChipRow
import app.scifin.ui.components.DateField
import app.scifin.ui.components.FeasibilityNote
import app.scifin.ui.components.FieldGroup
import app.scifin.ui.components.FieldLabel
import app.scifin.ui.components.InfoNote
import app.scifin.ui.components.Page
import app.scifin.ui.components.Segmented
import app.scifin.ui.components.SettingRow
import app.scifin.ui.components.SheetScaffold
import app.scifin.ui.components.Switch
import app.scifin.ui.components.TextField
import app.scifin.ui.components.TopRow
import app.scifin.ui.currencySymbol
import app.scifin.ui.formatDate
import app.scifin.ui.formatMoney
import app.scifin.ui.moneyText
import app.scifin.ui.parseMoney
import kotlinx.datetime.LocalDate
import kotlin.math.round
import app.scifin.ui.components.deleteAddBackCopy
import app.scifin.ui.components.deleteOnlyCopy

/** A fresh draft: monthly, fixed-date, due today, no reminder, no end. */
fun freshExpense(id: String, profileId: String, today: LocalDate) =
    RecurringExpense(id, profileId, "", null, 0, Cadence.MONTHLY, firstDueDate = today, dueDate = today, dateMode = DateMode.FIXED_DATE)

private val CADENCES get() = Cadence.entries.map { it.token to cadenceLabel(it) }

/**
 * §3.11's editor: automatic sweep configuration, not a manual log. `dateMode` is offered for
 * the three month-anchored cadences only; apportioned cadences show what each month sets
 * aside; the reminder is a real OS notification off by default; `endDate` is optional.
 */
@Composable
fun RecurringEditorScreen(route: Route.RecurringEditor, state: UiState, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val d = state.data ?: return
    val cur = d.settings.currency
    val today = store.clock.today()
    val original = route.id?.let { id -> d.recurringExpenses.firstOrNull { it.id == id } }
    val isNew = original == null
    var draft by remember(route.id) { mutableStateOf(original ?: freshExpense(store.newId(), d.profileId, today)) }
    var amountText by remember(route.id) { mutableStateOf(if (draft.amount != 0L) moneyText(draft.amount) else "") }
    val amountUnreadable = amountText.isNotBlank() && parseMoney(amountText) == null
    val semi = draft.cadence == Cadence.SEMIMONTHLY
    // §3.11: the two days in calendar order, each as its next date.
    // Written back only if one was touched, so opening and saving never restarts the chain.
    val semiShown = remember(route.id, draft.cadence) { Recurring.semimonthlyFields(draft) }
    var semiA by remember(route.id, draft.cadence) { mutableStateOf(semiShown.first) }
    var semiB by remember(route.id, draft.cadence) { mutableStateOf(semiShown.second) }
    val showDateMode = draft.cadence.monthAnchored

    Page(null) {
        TopRow(if (isNew) tr("New recurring expense") else draft.name.ifBlank { tr("Recurring expense") }, onBack = { store.back() })
        FieldGroup { FieldLabel(tr("Name")); TextField(draft.name, { draft = draft.copy(name = it) }, placeholder = tr("Rent, phone bill…"), label = tr("Name")) }
        FieldGroup { FieldLabel(tr("Amount")); TextField(amountText, { v -> amountText = v; draft = draft.copy(amount = parseMoney(v) ?: 0) }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = tr("Amount")) }
        IconPicker(draft.icon) { draft = draft.copy(icon = it) }
        FieldGroup {
            FieldLabel(tr("Cadence"))
            ChipRow(CADENCES, draft.cadence.token) { v ->
                val c = Cadence.of(v)
                // §3.11: dateMode applies to monthly/bimonthly/yearly only; clearing it keeps the illegal combination out of the draft
                draft = draft.copy(cadence = c, dateMode = if (c.monthAnchored) draft.dateMode ?: DateMode.FIXED_DATE else null)
            }
        }
        if (semi) {
            FieldGroup { FieldLabel(tr("First due date")); DateField(semiA) { semiA = it } }
            FieldGroup { FieldLabel(tr("Second due date")); DateField(semiB) { semiB = it } }
        } else FieldGroup { FieldLabel(tr("Due date")); DateField(draft.dueDate) { draft = draft.copy(dueDate = it) } }
        if (showDateMode) FieldGroup {
            FieldLabel(tr("Date mode"))
            Segmented(listOf("interval" to tr("Interval"), "fixedDate" to tr("Fixed date")), draft.dateMode?.token ?: "fixedDate", { v -> draft = draft.copy(dateMode = DateMode.of(v)) })
            Caption(tr("Interval drifts across months, like most subscriptions. Fixed date never drifts, snapping to month-end where needed."), Modifier.padding(top = t.spacing.u * 0.75f))
        }
        apportionedCaption(draft, original, today, cur)?.let { InfoNote(it, Modifier.padding(bottom = t.spacing.u * 1.25f)) }
        val nextDue = Recurring.normalisedNextDue(if (semi) Recurring.withSemimonthlyFields(draft, semiA, semiB) else draft, today)
        SettingRow(Lucide.BELL, tr("Reminder"), if (!draft.reminderEnabled) tr("Off by default") else trp("Next on {1}, {0} day before {2}", "Next on {1}, {0} days before {2}", draft.reminderLeadDays, formatDate(nextDue.minusDays(draft.reminderLeadDays)), formatDate(nextDue))) {
            Switch(draft.reminderEnabled) { draft = draft.copy(reminderEnabled = !draft.reminderEnabled) }
        }
        if (draft.reminderEnabled) Box(Modifier.padding(bottom = t.spacing.u * 1.25f)) {
            ChipRow(REMINDER_LEAD_DAYS.map { it.toString() to trp("{0} day before", "{0} days before", it) }, draft.reminderLeadDays.toString()) { v -> draft = draft.copy(reminderLeadDays = v.toInt()) }
        }
        FieldGroup {
            FieldLabel(tr("Ends on (optional)"))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
                Box(Modifier.weight(1f)) { DateField(draft.endDate, placeholder = tr("Ongoing")) { draft = draft.copy(endDate = it) } }
                if (draft.endDate != null) Button(tr("Clear"), ButtonKind.GHOST) { draft = draft.copy(endDate = null) }
            }
            Caption(tr("Leave blank for ongoing expenses like rent or a subscription. Set for a finite one, like a loan payoff date."), Modifier.padding(top = t.spacing.u * 0.75f))
        }
        Row(Modifier.fillMaxWidth().padding(top = t.spacing.pad), horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
            if (!isNew) EditorDeleteButton(original.name) { store.openSheet(Sheet.DeleteExpense(original.id)) }
            Button(if (isNew) tr("Add expense") else tr("Save"), enabled = draft.name.isNotBlank() && !amountUnreadable, modifier = Modifier.weight(1f)) {
                val touched = isNew || draft.dueDate2 == null || semiA to semiB != semiShown
                store.saveExpense(if (semi && touched) Recurring.withSemimonthlyFields(draft, semiA, semiB) else draft, isNew); store.back()
            }
        }
    }
}

/** §3.11's apportioned share, reported as the figure this save would actually store. */
private fun apportionedCaption(d: RecurringExpense, original: RecurringExpense?, today: LocalDate, cur: String): String? {
    if (!d.cadence.apportioned || d.amount <= 0) return null
    val due = Recurring.normalisedNextDue(d, today)
    val held = if (original != null && !Recurring.planChanged(original, d)) original.apportionedShare else null
    val first = held ?: Recurring.apportionedShareFor(d.copy(dueDate = due), today) ?: return null
    val settled = d.amount / Recurring.cycleLength(d.cadence)
    return if (first == settled) trf("Sets aside {0} a month, so the full {1} is there when it lands.", formatMoney(settled, cur), formatMoney(d.amount, cur))
    else trf("Sets aside {0} a month until {1}, then {2} a month — fewer months to spread the first one over, so the full {3} is still there when it lands.", formatMoney(first, cur), formatDate(due), formatMoney(settled, cur), formatMoney(d.amount, cur))
}

/** §3.11's deletion reversal: only what has genuinely been swept for the current period is offered back; a finished entry reverses nothing. */
@Composable
fun DeleteExpenseSheet(sheet: Sheet.DeleteExpense, state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val d = state.data ?: return
    val e = d.recurringExpenses.firstOrNull { it.id == sheet.id } ?: return
    val cur = d.settings.currency
    val back = Recurring.reversalAmount(d, e, store.clock.today())
    val paired = e.linkedAccountId?.let { id -> d.accounts.firstOrNull { it.id == id } }
    val amount = formatMoney(back, cur)
    DeleteWithReturnSheet(
        title = tr("Delete recurring expense"),
        question = if (back > 0) trf("Delete {0}?", e.name) + " " + trf("Add {0} back to Savings? That’s what’s actually been swept for the current period.", amount)
            else trf("Delete {0}? Its due date has already passed this period, so nothing is reversed.", e.name),
        hasReturn = back > 0,
        confirmAddBack = deleteAddBackCopy(amount, e.name),
        confirmDeleteOnly = deleteOnlyCopy(e.name),
        onDismiss = store::closeSheet,
        onDelete = { returnMoney -> store.deleteExpense(e.id, reverse = returnMoney) },
        note = { if (paired != null) FeasibilityNote(trf("This also removes the linked account “{0}” — they were created as a pair and they go as one.", paired.name), Modifier.padding(bottom = t.spacing.gap)) },
    )
}

/** §3.11's edit-and-reconcile: this month was swept at the old settings; asked once, never silently moved. */
@Composable
fun ReconcileSheet(sheet: Sheet.Reconcile, state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val d = state.data ?: return
    val r = sheet.reconcile
    val e = d.recurringExpenses.firstOrNull { it.id == r.expenseId } ?: return
    val cur = d.settings.currency
    val up = r.delta > 0
    val acct = e.linkedAccountId?.let { id -> d.accounts.firstOrNull { it.id == id } }?.takeIf { it.type == AccountType.LOAN && it.autoUpdateFromPayments }
    var rateText by remember { mutableStateOf("") }
    SheetScaffold(title = e.name, onDismiss = null) {
        FeasibilityNote(if (up) trf("This month was already swept at the old settings. Under the new ones it comes to {0} more.", formatMoney(r.delta, cur)) else trf("This month was already swept at the old settings. Under the new ones it comes to {0} less.", formatMoney(-r.delta, cur)))
        Caption(tr("Either way the new settings govern from the next sweep onward."), Modifier.padding(top = t.spacing.gap, bottom = t.spacing.u * 1.25f))
        if (acct != null) FieldGroup {
            FieldLabel(trf("{0}’s interest rate — optional", acct.name))
            TextField(rateText, { rateText = it }, prefix = "%", placeholder = acct.interestRate?.let { (round(it * 10000) / 100).toString() } ?: "", keyboard = KeyboardType.Decimal, label = trf("{0}’s interest rate — optional", acct.name))
            Caption(tr("If this change came from a rate reset, update it here — the balance decrements against this figure every cycle. Leave it blank to keep the current rate."), Modifier.padding(top = t.spacing.u * 0.75f))
        }
        // a percentage from 0 to 100; anything else holds both answers rather than being dropped as "keep the rate"
        val rate = parseRate(rateText)?.takeIf { it <= 1.0 }
        val rateUnreadable = rateText.isNotBlank() && rate == null
        ButtonRow {
            Button(tr("Leave it as swept"), ButtonKind.GHOST, enabled = !rateUnreadable, modifier = Modifier.weight(1f)) { store.resolveReconcile(null, acct?.id, rate) }
            Button(if (up) trf("Take a further {0}", formatMoney(r.delta, cur)) else trf("Return {0}", formatMoney(-r.delta, cur)), ButtonKind.SECONDARY, enabled = !rateUnreadable, modifier = Modifier.weight(1f)) { store.resolveReconcile(r, acct?.id, rate) }
        }
    }
}
