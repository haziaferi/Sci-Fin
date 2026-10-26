package app.scifin.ui.screens

import app.scifin.i18n.tr
import app.scifin.i18n.trf
import app.scifin.i18n.trp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Sheet
import app.scifin.app.UiState
import app.scifin.engine.Accounts
import app.scifin.engine.LoanDraft
import app.scifin.engine.TimeWorth
import app.scifin.engine.Hourly
import app.scifin.engine.monthsLabel
import app.scifin.engine.plusMonthsClamped
import app.scifin.model.AccountType
import app.scifin.model.Money
import app.scifin.model.RateType
import app.scifin.model.ceilDiv
import app.scifin.model.moneyOf
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.components.Button
import app.scifin.ui.components.ButtonKind
import app.scifin.ui.components.ButtonRow
import app.scifin.ui.components.Caption
import app.scifin.ui.components.ChipRow
import app.scifin.ui.components.FeasibilityNote
import app.scifin.ui.components.FieldGroup
import app.scifin.ui.components.FieldLabel
import app.scifin.ui.components.InfoNote
import app.scifin.ui.components.Page
import app.scifin.ui.components.SectionLabel
import app.scifin.ui.components.SettingRow
import app.scifin.ui.components.SheetScaffold
import app.scifin.ui.components.Switch
import app.scifin.ui.components.TextField
import app.scifin.ui.components.TopRow
import app.scifin.ui.currencySymbol
import app.scifin.ui.formatDate
import app.scifin.ui.formatDateFull
import app.scifin.ui.formatMoney
import app.scifin.ui.moneyText
import app.scifin.ui.parseMoney

/** The prototype's `figure()`: a label, a large value, a caption. */
@Composable
private fun Figure(label: String, value: String, sub: String?) {
    val t = LocalTokens.current; val p = t.palette
    Column(Modifier.fillMaxWidth().padding(bottom = t.spacing.gap).background(p.surf2, RoundedCornerShape(t.radii.lg)).padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f)) {
        Text(label, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
        Text(value, color = p.txt, fontSize = t.type.title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = t.spacing.u * 0.375f))
        if (sub != null) Caption(sub, Modifier.padding(top = t.spacing.u * 0.875f))
    }
}

@Composable
private fun CardBox(accent: Boolean = false, content: @Composable () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    Column(Modifier.fillMaxWidth().background(p.surf, RoundedCornerShape(t.radii.lg)).let { if (accent) it.border(t.strokes.ctl, p.acc, RoundedCornerShape(t.radii.lg)) else it }.padding(t.spacing.pad)) { content() }
}

private val LoanDraftSaver = listSaver<LoanDraft, Any>(
    save = { listOf(it.principal, it.rate, it.termMonths, it.rateType.token) },
    restore = { LoanDraft(it[0] as Money, it[1] as Double, it[2] as Int, RateType.of(it[3] as String)) },
)

/** §9.1: what a loan actually costs, beside the same amount saved instead -- and the two hand-offs, each behind a confirm. */
@Composable
fun LoanCalcScreen(state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val d = state.data ?: return
    val cur = d.settings.currency
    var draft by rememberSaveable(stateSaver = LoanDraftSaver) { mutableStateOf(LoanDraft()) }
    var principalText by rememberSaveable { mutableStateOf(moneyText(draft.principal)) }
    var loanRateText by rememberSaveable { mutableStateOf(rateText(draft.rate, 1)) }
    var termText by rememberSaveable { mutableStateOf(draft.termMonths.toString()) }
    val m = Accounts.loanMath(draft.principal, draft.rate, draft.termMonths, draft.rateType)
    val saved = m.monthly * m.months
    Page(null) {
        TopRow(tr("Loan"), onBack = { store.back() })
        FieldGroup { FieldLabel(tr("Amount borrowed")); TextField(principalText, { v -> principalText = v; parseMoney(v)?.let { draft = draft.copy(principal = it) } }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = tr("Amount borrowed")) }
        FieldGroup { FieldLabel(tr("Interest rate")); TextField(loanRateText, { v -> loanRateText = v; parseRate(v)?.let { draft = draft.copy(rate = it) } }, prefix = "%", keyboard = KeyboardType.Decimal, label = tr("Interest rate")) }
        FieldGroup { FieldLabel(tr("Term (months)")); TextField(termText, { v -> termText = v; v.trim().toIntOrNull()?.takeIf { it >= 1 }?.let { draft = draft.copy(termMonths = it) } }, prefix = "#", keyboard = KeyboardType.Number, label = tr("Term (months)")) }
        FieldGroup { FieldLabel(tr("Rate type")); ChipRow(RateType.entries.map { it.token to rateTypeLabel(it) }, draft.rateType.token) { v -> draft = draft.copy(rateType = RateType.of(v)) } }
        when (draft.rateType) {
            RateType.VARIABLE -> FeasibilityNote(tr("The app can’t know future rate resets, so the payment it carries is the one as of today. Update the recurring expense when a reset happens — it will settle the difference for you."))
            RateType.INTEREST_ONLY -> FeasibilityNote(tr("Interest-only: the payment covers interest and nothing else, so the whole principal is still owed at the end of the period."))
            RateType.FIXED -> Unit
        }
        SectionLabel(tr("If you finance it"))
        CardBox {
            Figure(tr("Every month"), formatMoney(m.monthly, cur), trp("for {0} month", "for {0} months", m.months))
            Figure(tr("Interest paid"), formatMoney(m.totalInterest, cur), tr("on top of what you borrowed"))
            Figure(tr("Total repaid"), formatMoney(m.totalRepaid, cur), if (m.principalRemaining > 0) trf("plus {0} of principal still owed at the end", formatMoney(m.principalRemaining, cur)) else tr("over the whole term"))
        }
        SectionLabel(tr("If you save the same amount instead"))
        CardBox {
            Figure(tr("Put aside each month"), formatMoney(m.monthly, cur), trp("for the same {0} month", "for the same {0} months", m.months))
            Figure(tr("You would have"), formatMoney(saved, cur), tr("no interest assumed — this app doesn’t model a return on Savings"))
        }
        Button(tr("Save for it"), modifier = Modifier.fillMaxWidth().padding(top = t.spacing.gap)) { store.openSheet(Sheet.LoanSave(draft)) }
        Row(Modifier.fillMaxWidth().padding(top = t.spacing.u * 0.75f), horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
            Button(tr("Get loan"), ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { store.openSheet(Sheet.LoanBorrow(draft)) }
            Button(tr("Reset"), ButtonKind.GHOST) { draft = LoanDraft(); principalText = moneyText(draft.principal); loanRateText = rateText(draft.rate, 1); termText = draft.termMonths.toString() }
        }
        InfoNote(tr("Exploring costs nothing and creates nothing. “Get loan” creates a real loan account and its payment together, in one step; “Save for it” creates a goal. Either way you confirm first."), Modifier.padding(top = t.spacing.gap))
    }
}

@Composable
fun LoanSaveSheet(sheet: Sheet.LoanSave, state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val d = state.data ?: return
    val cur = d.settings.currency
    val m = Accounts.loanMath(sheet.draft.principal, sheet.draft.rate, sheet.draft.termMonths, sheet.draft.rateType)
    var name by rememberSaveable(sheet) { mutableStateOf(tr("Savings goal")) }
    SheetScaffold(title = tr("Save for it instead"), onDismiss = store::closeSheet) {
        FieldGroup { FieldLabel(tr("Name this goal")); TextField(name, { name = it }, label = tr("Name this goal")) }
        FeasibilityNote(trp("{1} a month toward {2}, over {0} month.", "{1} a month toward {2}, over {0} months.", m.months, formatMoney(m.monthly, cur), formatMoney(sheet.draft.principal, cur)), Modifier.padding(bottom = t.spacing.gap))
        ButtonRow {
            Button(tr("Cancel"), ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.closeSheet() }
            Button(tr("Create the goal"), modifier = Modifier.weight(1f)) { store.loanSaveForIt(sheet.draft, name) }
        }
    }
}

@Composable
fun LoanBorrowSheet(sheet: Sheet.LoanBorrow, state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val d = state.data ?: return
    val cur = d.settings.currency
    val today = store.clock.today()
    val m = Accounts.loanMath(sheet.draft.principal, sheet.draft.rate, sheet.draft.termMonths, sheet.draft.rateType)
    var acctName by rememberSaveable(sheet) { mutableStateOf(tr("Loan")) }
    var expName by rememberSaveable(sheet) { mutableStateOf(tr("Loan payment")) }
    var autoUpdate by rememberSaveable(sheet) { mutableStateOf(true) }
    val firstDue = today.plusMonthsClamped(1); val endDue = today.plusMonthsClamped(m.months)
    SheetScaffold(title = tr("Get this loan"), onDismiss = store::closeSheet) {
        FieldGroup { FieldLabel(tr("Name the account")); TextField(acctName, { acctName = it }, label = tr("Name the account")) }
        FieldGroup { FieldLabel(tr("Name the payment")); TextField(expName, { expName = it }, label = tr("Name the payment")) }
        SettingRow(Lucide.REPEAT, tr("Auto-update the balance"), tr("Splits interest from principal each cycle")) { Switch(autoUpdate) { autoUpdate = !autoUpdate } }
        FeasibilityNote(trf("Creates both together: a {0} loan account and a {1} monthly payment. Real loans pay in arrears, so the first payment is {2} — a month from today — and the last is {3}. Nothing leaves Savings now.", formatMoney(sheet.draft.principal, cur), formatMoney(m.monthly, cur), formatDate(firstDue), formatDateFull(endDue)), Modifier.padding(vertical = t.spacing.gap))
        ButtonRow {
            Button(tr("Cancel"), ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.closeSheet() }
            Button(tr("Create both"), modifier = Modifier.weight(1f)) { store.loanGetLoan(sheet.draft, acctName, expName, autoUpdate) }
        }
    }
}

/** §9.2 surface 2: the pre-decision purchase calculator -- waiting first, as arithmetic, not advice. */
@Composable
fun PurchaseCalcScreen(state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val d = state.data ?: return
    val cur = d.settings.currency
    var amountText by rememberSaveable { mutableStateOf("") }
    var aprText by rememberSaveable { mutableStateOf("") }
    var monthlyText by rememberSaveable { mutableStateOf("") }
    var acctId by rememberSaveable { mutableStateOf<String?>(null) }
    val amount = parseMoney(amountText) ?: 0L
    val i = (parseRate(aprText) ?: 0.0) / 12
    // §9.2: no amount typed, no projection; a typed amount is floored to the 100-minor-unit minimum
    val pay = parseMoney(monthlyText)?.takeIf { it > 0 }?.let { maxOf(100L, it) }
    val monthsToSave = if (amount > 0 && pay != null) amount.ceilDiv(pay).toInt() else null
    val res = if (amount > 0 && pay != null) Accounts.payoffAtFixedPayment(amount, i, pay) else null
    val cards = d.accounts.filter { it.type == AccountType.CREDIT }
    Page(null) {
        TopRow(tr("Putting it on a card"), onBack = { store.back() })
        FieldGroup { FieldLabel(tr("What it costs")); TextField(amountText, { amountText = it }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = tr("What it costs")) }
        FieldGroup { FieldLabel(tr("Card’s interest rate (APR)")); TextField(aprText, { aprText = it }, prefix = "%", keyboard = KeyboardType.Decimal, label = tr("Card’s interest rate (APR)")) }
        FieldGroup { FieldLabel(tr("What you’d pay each month")); TextField(monthlyText, { monthlyText = it }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = tr("What you’d pay each month")) }
        SectionLabel(tr("If you wait and pay cash"))
        CardBox(accent = true) {
            Figure(tr("Ready in"), monthsToSave?.let { monthsLabel(it) } ?: "—", pay?.let { trf("putting {0} aside each month", formatMoney(it, cur)) })
            Figure(tr("Interest paid"), formatMoney(0, cur), tr("nothing is financed, so nothing accrues"))
        }
        SectionLabel(tr("If you put it on the card"))
        CardBox {
            Figure(tr("Cleared in"), res?.let { monthsLabel(it.months) } ?: "—", pay?.let { trf("paying {0} each month", formatMoney(it, cur)) })
            Figure(tr("Interest paid"), res?.interest?.let { formatMoney(it, cur) } ?: "—", when {
                res == null -> null
                res.months == null -> tr("At that payment the balance never falls — interest outruns it.")
                else -> trf("{0} in total", formatMoney(amount + (res.interest ?: 0), cur))
            })
        }
        if (cards.isNotEmpty()) {
            SectionLabel(tr("Act on it"))
            ChipRow(cards.map { it.id to it.name }, acctId ?: "") { acctId = it }
            Button(tr("Open this card’s payoff outlook"), ButtonKind.SECONDARY, enabled = acctId != null, modifier = Modifier.fillMaxWidth().padding(top = t.spacing.u * 0.75f)) { acctId?.let { store.push(Route.AccountDetail(it)) } }
        }
        InfoNote(tr("This screen changes nothing on its own — no balance moves and nothing is created. To commit to a payment, go to the card itself, where the balance and its payoff outlook live."), Modifier.padding(top = t.spacing.gap))
    }
}

/**
 * §10.2: what extra hours come to, as a reflection rather than a claim. The rate is always enterable; with Income
 * on, a month's pay over the expected hours pre-fills it, and nothing here is saved or created.
 */
@Composable
fun TimeWorthCalcScreen(state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val d = state.data ?: return
    val cur = d.settings.currency
    var hoursText by rememberSaveable { mutableStateOf("10") }
    var expectedText by rememberSaveable { mutableStateOf(TimeWorth.DEFAULT_EXPECTED_HOURS.toInt().toString()) }
    var rateText by rememberSaveable { mutableStateOf("") }
    val hours = parseHours(hoursText) ?: 0.0
    val prefill = TimeWorth.prefillRate(d.settings, parseHours(expectedText) ?: TimeWorth.DEFAULT_EXPECTED_HOURS)
    // a typed rate wins; an empty field falls back to the pre-fill, shown as the field's placeholder
    val rate = parseMoney(rateText) ?: prefill ?: 0L
    val (month, year) = TimeWorth.worth(rate, hours)
    Page(null) {
        TopRow(tr("What your time is worth"), onBack = { store.back() })
        FieldGroup { FieldLabel(tr("Extra hours in a typical month")); TextField(hoursText, { hoursText = it }, prefix = "#", keyboard = KeyboardType.Decimal, label = tr("Extra hours in a typical month")) }
        if (TimeWorth.monthlyEquivalent(d.settings) != null) FieldGroup {
            FieldLabel(tr("Hours you’d normally expect in a month"))
            TextField(expectedText, { expectedText = it }, prefix = "#", keyboard = KeyboardType.Decimal, label = tr("Hours you’d normally expect in a month"))
            Caption(tr("Only used to work out a starting rate from what you told the app you earn. Change the rate directly below if you’d rather."), Modifier.padding(top = t.spacing.u * 0.75f))
        }
        FieldGroup {
            FieldLabel(tr("What an hour of your time is worth"))
            TextField(rateText, { rateText = it }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = tr("What an hour of your time is worth"), placeholder = prefill?.let { moneyText(it) } ?: tr("e.g. 22.00"))
            Caption(when {
                prefill != null && Hourly.isHourly(d.settings) -> tr("Your hourly rate from Settings — a starting point, fully editable.")
                prefill != null -> tr("Worked out from your income and the hours above — a starting point, fully editable.")
                else -> tr("Your figure, entered directly. The app doesn’t need Income switched on for this.")
            }, Modifier.padding(top = t.spacing.u * 0.75f))
        }
        SectionLabel(tr("What those hours come to"))
        CardBox {
            Figure(tr("In a month"), formatMoney(month, cur), trf("{0} at {1}", hoursLabel(hours), formatMoney(rate, cur)))
            Figure(tr("Over a year"), formatMoney(year, cur), tr("at the same rate and the same hours"))
        }
        InfoNote(tr("This is a reflection, not a claim. It changes nothing in the app, creates nothing, and isn’t a statement about what you’re owed — what asking costs isn’t the same for everyone."), Modifier.padding(top = t.spacing.gap))
    }
}

/** Hours typed with a comma or a point; negative or unreadable is no figure. */
private fun parseHours(v: String): Double? = v.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

private fun hoursLabel(h: Double): String {
    val n = if (h % 1.0 == 0.0) h.toLong().toString() else h.toString()
    return if (h == 1.0) trf("{0} hour", n) else trf("{0} hours", n)
}
