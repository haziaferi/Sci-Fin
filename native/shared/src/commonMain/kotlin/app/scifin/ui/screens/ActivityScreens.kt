package app.scifin.ui.screens

import app.scifin.i18n.tr
import app.scifin.i18n.trc
import app.scifin.i18n.trf
import app.scifin.i18n.trp
import app.scifin.ui.components.FigureLabel
import app.scifin.ui.components.EditorDeleteButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.scifin.ui.components.probeAs
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Sheet
import app.scifin.app.Tab
import app.scifin.app.UiState
import app.scifin.engine.AccountEditing
import app.scifin.engine.Accounts
import app.scifin.engine.Recurring
import app.scifin.ui.components.DeleteWithReturnSheet
import app.scifin.ui.components.deleteAddBackCopy
import app.scifin.ui.components.deleteOnlyCopy
import app.scifin.engine.monthsLabel
import app.scifin.engine.plusMonthsClamped
import app.scifin.model.Account
import app.scifin.model.AccountType
import app.scifin.model.Money
import app.scifin.model.RateType
import app.scifin.ui.LocalSecondColumn
import app.scifin.ui.LocalTokens
import app.scifin.ui.LocalWidthClass
import app.scifin.ui.Lucide
import app.scifin.ui.LucideIcon
import app.scifin.ui.WidthClass
import app.scifin.ui.opener
import app.scifin.ui.selectedWash
import app.scifin.ui.shownDetail
import app.scifin.ui.components.AddRow
import app.scifin.ui.components.Button
import app.scifin.ui.components.ButtonKind
import app.scifin.ui.components.ButtonRow
import app.scifin.ui.components.Caption
import app.scifin.ui.components.EntityIcon
import app.scifin.ui.components.ChipRow
import app.scifin.ui.components.FeasibilityNote
import app.scifin.ui.components.FieldGroup
import app.scifin.ui.components.FieldLabel
import app.scifin.ui.components.InfoNote
import app.scifin.ui.components.LearnLink
import app.scifin.ui.components.FloatingAddRoom
import app.scifin.ui.components.LedgerGroup
import app.scifin.ui.components.LedgerRow
import app.scifin.ui.components.NavRow
import app.scifin.ui.components.NoteLine
import app.scifin.ui.components.Page
import app.scifin.ui.components.RowAdd
import app.scifin.ui.components.SavingsRow
import app.scifin.ui.components.SectionLabel
import app.scifin.ui.components.Segmented
import app.scifin.ui.components.SettingRow
import app.scifin.ui.components.SheetScaffold
import app.scifin.ui.components.Surface
import app.scifin.ui.components.Switch
import app.scifin.ui.components.TabTitle
import app.scifin.ui.components.TextField
import app.scifin.ui.components.TopRow
import app.scifin.ui.currencySymbol
import app.scifin.ui.formatDate
import app.scifin.ui.formatMoney
import app.scifin.ui.moneyText
import app.scifin.ui.parseMoney
import app.scifin.ui.tutAnchor
import app.scifin.ui.components.AmountText
import kotlinx.serialization.json.Json

/** A percentage with [decimals] places (1 or 2) for a stored fraction: 0.219 -> "21.9". */
internal fun rateText(fraction: Double?, decimals: Int): String {
    if (fraction == null) return ""
    val f = if (decimals == 1) 10 else 100
    val scaled = kotlin.math.round(fraction * 100 * f).toLong()
    return "${scaled / f}." + (scaled % f).toString().padStart(decimals, '0')
}

/** A typed percentage as a fraction; null for anything but a non-negative number. */
internal fun parseRate(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let { it / 100.0 }

private val AccountSaver = Saver<Account, String>(save = { Json.encodeToString(Account.serializer(), it) }, restore = { Json.decodeFromString(Account.serializer(), it) })

// ---- §4.4 Activity ---------------------------------------------------------------------------

@Composable
fun ActivityScreen(state: UiState, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val d = state.dataOrEmpty()
    val cur = d.settings.currency
    val open = opener(store)
    // §4.8: what the second column shows, so its row can say so; null on a single column
    val shown = if (LocalSecondColumn.current) shownDetail(state) else null
    Page(null) {
        TabTitle(Lucide.CREDIT_CARD, Tab.ACTIVITY.label) { store.selectTab(Tab.SETTINGS) }
        SavingsRow(d, store)
        // §4.4: Accounts, then the log, then the note, each row in the shared figure column
        Column(Modifier.tutAnchor("accounts-section"), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
            SectionLabel(tr("Accounts"))
            if (d.accounts.isNotEmpty()) LedgerGroup {
                for (a in d.accounts) {
                    val finished = Accounts.finished(d, a)
                    val wash = selectedWash(shown == Route.AccountDetail(a.id))
                    Box(Modifier.fillMaxWidth().then(if (wash != null) Modifier.background(wash) else Modifier)) {
                        LedgerRow(
                            onClick = { open(Route.AccountDetail(a.id)) },
                            name = {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 1.125f)) {
                                        EntityIcon(entityIcon(a.icon, if (a.type == AccountType.LOAN) Lucide.LANDMARK else Lucide.CREDIT_CARD))
                                        Text(a.name, color = if (finished) p.txt2 else p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
                                    }
                                    Caption(if (a.type == AccountType.LOAN && a.autoUpdateFromPayments) tr("Loan · auto-updates") else if (a.type == AccountType.LOAN) tr("Loan") else tr("Credit"))
                                }
                            },
                            figure = formatMoney(a.balance, cur), probeName = "account",
                            // §3.6: on a desktop every row carries its own +; a phone has the floating +
                            slot = if (LocalWidthClass.current != WidthClass.COMPACT)
                                ({ RowAdd(trf("Log on {0}", a.name)) { store.openSheet(Sheet.Entry("acct:${a.id}")) } })
                            else ({ LucideIcon(Lucide.CHEVRON_RIGHT, tint = p.txt2, size = t.icons.sm) }),
                        )
                    }
                }
            }
            AddRow(tr("New account")) { store.push(Route.AccountEditor(null)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
            SectionLabel(trc("noun", "Log"))
            LedgerGroup {
                NavRow(Lucide.ROTATE_CCW, tr("Activity log"), trp("{0} entry", "{0} entries", d.transactions.size), decorative = true, selected = shown == Route.Ledger) { open(Route.Ledger) }
                NavRow(Lucide.DATABASE, tr("Import a statement"), d.importState.lastImportDate?.let { trf("Last {0}", formatDate(it)) } ?: tr("CSV or OFX"), decorative = true, selected = shown == Route.Import) { open(Route.Import) }
            }
        }
        AccountsNote(store)
        FloatingAddRoom()
    }
}

private val ACCOUNTS_NOTE: String get() = tr("Accounts are informational — they create no money in or out in the Savings, Goal or Category math (a Loan’s auto-update only keeps its own balance correct). Select one to see its detail and, for a card, its payoff outlook.")

/** §4.4: the note's first clause on screen, the rest in a sheet. */
@Composable
private fun AccountsNote(store: AppStore) {
    NoteLine(tr("Accounts are informational."), Modifier.padding(top = LocalTokens.current.spacing.gap).probeAs("note")) { store.openSheet(Sheet.AccountsNote) }
}

@Composable
fun AccountsNoteSheet(store: AppStore) {
    val t = LocalTokens.current
    SheetScaffold(title = tr("Accounts"), onDismiss = store::closeSheet) {
        Text(ACCOUNTS_NOTE, color = t.palette.txt, fontSize = t.type.body, lineHeight = t.type.body * 1.5)
    }
}

// ---- §4.2 Account detail, hosting §9.2's payoff outlook ------------------------------------------

@Composable
fun AccountDetailScreen(route: Route.AccountDetail, state: UiState, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val d = state.data ?: return
    val cur = d.settings.currency
    val a = d.accounts.firstOrNull { it.id == route.id }
    Page(null) {
        TopRow(a?.name ?: tr("Account"), onBack = { store.back() })
        if (a == null) { InfoNote(tr("That account is gone.")); return@Page }
        val linked = a.linkedRecurringExpenseId?.let { id -> d.recurringExpenses.firstOrNull { it.id == id } }
        val finished = Accounts.finished(d, a)
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(t.spacing.u / 2)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u / 2)) {
                    LucideIcon(entityIcon(a.icon, if (a.type == AccountType.LOAN) Lucide.LANDMARK else Lucide.CREDIT_CARD), tint = p.txt2, size = t.icons.sm)
                    FigureLabel(if (a.type == AccountType.LOAN) tr("Loan balance") else tr("Credit balance"))
                }
                AmountText(formatMoney(a.balance, cur), color = p.acc, fontSize = t.type.figure, fontWeight = FontWeight.Bold)
                if (finished) Caption(tr("This loan’s payment schedule has ended. The account stays until you remove it."))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = t.spacing.gap), horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
            Button(tr("Log"), ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { store.openSheet(Sheet.Entry("acct:${a.id}")) }
            Button(tr("Edit"), ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { store.push(Route.AccountEditor(a.id)) }
        }
        if (a.type == AccountType.CREDIT) PayoffPanel(a, cur, store) else {
            SectionLabel(tr("Loan"))
            Surface {
                Column(verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
                    a.interestRate?.let { DetailRow(tr("Rate"), trf("{0}%", rateText(it, 2)), a.rateType?.let { r -> rateTypeLabel(r) }) }
                    a.principal?.let { DetailRow(tr("Original principal"), formatMoney(it, cur), null) }
                    a.termMonths?.let { DetailRow(tr("Term"), trp("{0} month", "{0} months", it), null) }
                    if (linked != null) DetailRow(tr("Payment"), formatMoney(linked.amount, cur), cadenceLabel(linked.cadence))
                    if (a.autoUpdateFromPayments) Caption(tr("Auto-updates as its linked expense sweeps — interest and principal are split from the live balance each cycle, so it survives extra payments and corrections."))
                }
            }
            LearnLink(BORROWING_LINK, { store.push(Route.LearnTopic("borrowing")) })
        }
        InfoNote(tr("Accounts are informational — they never feed Savings, Goal or Category math directly."), Modifier.padding(top = t.spacing.gap))
    }
}

@Composable
private fun DetailRow(label: String, value: String, caption: String?) {
    val t = LocalTokens.current; val p = t.palette
    Row(Modifier.fillMaxWidth().background(p.surf2, RoundedCornerShape(t.radii.lg)).padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
        Column(horizontalAlignment = Alignment.End) {
            Text(value, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
            if (caption != null) Text(caption, color = p.txt2, fontSize = t.type.caption)
        }
    }
}

/** The borrowing topic's link, on a loan's and a card's own screen (§4.5, topic 7). */
private val BORROWING_LINK: String get() = tr("What borrowing really costs")

fun rateTypeLabel(r: RateType) = when (r) { RateType.FIXED -> tr("Fixed"); RateType.VARIABLE -> tr("Variable"); RateType.INTEREST_ONLY -> tr("Interest-only") }

/** §9.2 surface 1: three ways of paying a card off and what each actually costs; the fixed payment can become a linked Recurring Expense. */
@Composable
private fun PayoffPanel(a: Account, cur: String, store: AppStore) {
    val t = LocalTokens.current
    // committed on every valid change and NOT keyed on a.apr: re-keying resets the field mid-typing
    var aprText by rememberSaveable(a.id) { mutableStateOf(rateText(a.apr, 1)) }
    var fixedText by rememberSaveable(a.id) { mutableStateOf("") }
    SectionLabel(tr("Payoff outlook"))
    Surface {
        Column {
            FieldGroup {
                FieldLabel(tr("Interest rate (APR)"))
                TextField(aprText, { v -> aprText = v; val r = parseRate(v); if (r != null || v.isBlank()) store.setAccountApr(a.id, r) }, prefix = "%", placeholder = tr("e.g. 21.9"), keyboard = KeyboardType.Decimal, label = tr("Interest rate (APR)"))
                Caption(tr("Saved on this account for next time."), Modifier.padding(top = t.spacing.u * 0.75f))
            }
            val apr = a.apr
            if (apr == null || a.balance <= 0) {
                Caption(if (a.balance <= 0) tr("Nothing outstanding on this card right now, so there is no payoff to project.") else tr("Add the card’s rate and this will show what each way of paying it off actually costs."))
            } else {
                val i = apr / 12
                // §9.2: no amount typed, no projection; a typed amount is floored to the 100-minor-unit minimum
                val fixedAmt = parseMoney(fixedText)?.takeIf { it > 0 }?.let { maxOf(100L, it) }
                val min = Accounts.payoffAtMinimum(a.balance, i)
                Outcome(tr("Paid in full this cycle"), tr("the whole balance, once"), 0, 0, cur, primary = true)
                FieldGroup {
                    Box(Modifier.padding(top = t.spacing.gap)) { FieldLabel(tr("A fixed payment each month")) }
                    TextField(fixedText, { fixedText = it }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = tr("A fixed payment each month"))
                }
                if (fixedAmt != null) {
                    val fixed = Accounts.payoffAtFixedPayment(a.balance, i, fixedAmt)
                    Outcome(trf("{0} every month", formatMoney(fixedAmt, cur)), tr("a payment you choose and keep"), fixed.months, fixed.interest, cur, primary = false)
                    if (fixed.months != null) {
                        Button(trf("Commit to {0} a month", formatMoney(fixedAmt, cur)), modifier = Modifier.fillMaxWidth().padding(top = t.spacing.u * 0.75f)) { store.openSheet(Sheet.PayoffCommit(a.id, fixedAmt)) }
                        Caption(tr("Creates a recurring expense for the payment, linked to this account."), Modifier.padding(top = t.spacing.u * 0.75f))
                    }
                }
                Outcome(tr("Minimum only"), trf("about {0} to start, falling as the balance does", formatMoney(min.first, cur)), min.months, min.interest, cur, primary = false)
                Caption(trf("Minimum modelled as {0}% of the balance or {1}, whichever is larger — an assumption, not your card’s actual terms. It can’t become a recurring expense, because it isn’t a fixed amount.", (Accounts.MIN_PAYMENT_PCT * 100).toInt(), formatMoney(Accounts.MIN_PAYMENT_FLOOR, cur)))
            }
        }
    }
    // under the outlook it explains, above the credit note and its topic (§4.5's order, owner 2026-10-23)
    LearnLink(BORROWING_LINK, { store.push(Route.LearnTopic("borrowing")) })
    InfoNote(tr("In countries that have credit scores, paying it off in full each cycle also adds to your payment history."), Modifier.padding(top = t.spacing.gap))
    LearnLink(tr("How credit scoring works, and who it leaves out"), { store.push(Route.LearnTopic("credit-score")) })
}

@Composable
private fun Outcome(title: String, sub: String, months: Int?, interest: Money?, cur: String, primary: Boolean) {
    val t = LocalTokens.current; val p = t.palette
    Column(
        Modifier.fillMaxWidth().padding(top = t.spacing.gap).background(p.surf2, RoundedCornerShape(t.radii.lg))
            .let { if (primary) it.border(t.strokes.ctl, p.acc, RoundedCornerShape(t.radii.lg)) else it }
            .padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f),
    ) {
        Text(title, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
        Text(monthsLabel(months), color = p.txt, fontSize = t.type.title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = t.spacing.u / 2))
        Caption(if (interest == null) trf("The balance never falls at that payment. — {0}", sub) else trf("{0} of interest — {1}", formatMoney(interest, cur), sub), Modifier.padding(top = t.spacing.u * 0.875f))
    }
}

/** §9.2's commit: the fixed payment becomes a monthly Recurring Expense, first due a month from today, linked to the card. */
@Composable
fun PayoffCommitSheet(sheet: Sheet.PayoffCommit, state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val d = state.data ?: return
    val a = d.accounts.firstOrNull { it.id == sheet.accountId } ?: return
    val cur = d.settings.currency
    val today = store.clock.today()
    var name by rememberSaveable(sheet) { mutableStateOf(trf("{0} payment", a.name)) }
    SheetScaffold(title = trf("Commit to {0} a month", formatMoney(sheet.amount, cur)), onDismiss = store::closeSheet) {
        FieldGroup { FieldLabel(tr("Name this recurring expense")); TextField(name, { name = it }, label = tr("Name this recurring expense")) }
        FeasibilityNote(trf("Monthly, on the {0}, linked to {1}. The first payment is {2} — a month from today, not today — so nothing leaves Savings until then.", ordinal(today.day), a.name, formatDate(today.plusMonthsClamped(1))))
        Caption(tr("No end date: a card’s payoff moves with every new purchase. Edit or delete it any time."), Modifier.padding(top = t.spacing.gap, bottom = t.spacing.u * 1.25f))
        ButtonRow {
            Button(tr("Cancel"), ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.closeSheet() }
            Button(tr("Create it"), modifier = Modifier.weight(1f)) { store.commitPayoff(a.id, sheet.amount, name); store.closeSheet() }
        }
    }
}

// ---- §7.2 Account editor ------------------------------------------------------------------------

@Composable
fun AccountEditorScreen(route: Route.AccountEditor, state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val d = state.data ?: return
    val cur = d.settings.currency
    val original = route.id?.let { id -> d.accounts.firstOrNull { it.id == id } }
    val isNew = original == null
    // keyed on the route: a second editor can open at the same call site (§4.8's second column)
    var draft by rememberSaveable(route, stateSaver = AccountSaver) { mutableStateOf(original ?: AccountEditing.fresh(store.newId(), d.profileId)) }
    var balanceText by rememberSaveable(route) { mutableStateOf(if (draft.balance != 0L) moneyText(draft.balance) else "") }
    var loanRateText by rememberSaveable(route) { mutableStateOf(rateText(draft.interestRate, 2)) }
    var aprText by rememberSaveable(route) { mutableStateOf(rateText(draft.apr, 1)) }
    var principalText by rememberSaveable(route) { mutableStateOf(draft.principal?.let { moneyText(it) } ?: "") }
    var termText by rememberSaveable(route) { mutableStateOf(draft.termMonths?.toString() ?: "") }
    Page(null) {
        TopRow(if (isNew) tr("New account") else draft.name.ifBlank { tr("Account") }, onBack = { store.back() })
        FieldGroup { FieldLabel(tr("Name")); TextField(draft.name, { draft = draft.copy(name = it) }, placeholder = tr("Credit card, car loan…"), label = tr("Name")) }
        FieldGroup { FieldLabel(tr("Type")); Segmented(listOf("credit" to tr("Credit"), "loan" to tr("Loan")), draft.type.token, { v -> draft = Accounts.retype(draft, AccountType.of(v)); loanRateText = ""; aprText = ""; principalText = ""; termText = "" }) }
        FieldGroup {
            FieldLabel(tr("Balance owed")); TextField(balanceText, { v -> balanceText = v; draft = draft.copy(balance = parseMoney(v) ?: 0) }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = tr("Balance owed"))
            Caption(tr("Editable at any time. You can also log a payment."), Modifier.padding(top = t.spacing.u * 0.75f))
        }
        if (draft.type == AccountType.LOAN) {
            SectionLabel(tr("Loan terms"))
            FieldGroup {
                FieldLabel(tr("Interest rate")); TextField(loanRateText, { v -> loanRateText = v; draft = draft.copy(interestRate = parseRate(v)) }, prefix = "%", placeholder = tr("e.g. 5.90"), keyboard = KeyboardType.Decimal, label = tr("Interest rate"))
                Caption(tr("Auto-update needs this: each cycle it splits interest from principal off the live balance."), Modifier.padding(top = t.spacing.u * 0.75f))
            }
            FieldGroup { FieldLabel(tr("Original principal")); TextField(principalText, { v -> principalText = v; draft = draft.copy(principal = parseMoney(v)) }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = tr("Original principal")) }
            FieldGroup { FieldLabel(tr("Term (months)")); TextField(termText, { v -> termText = v; draft = draft.copy(termMonths = v.trim().toIntOrNull()?.takeIf { it >= 1 }) }, prefix = "#", keyboard = KeyboardType.Number, label = tr("Term (months)")) }
            FieldGroup { FieldLabel(tr("Rate type")); ChipRow(RateType.entries.map { it.token to rateTypeLabel(it) }, draft.rateType?.token ?: "") { v -> draft = draft.copy(rateType = RateType.of(v)) } }
            // §7.2: a Loan with no rate has nothing for the decrement to compute from -- the toggle is absent, not greyed
            if (Accounts.loanInputsComplete(draft)) SettingRow(Lucide.REPEAT, tr("Auto-update from payments"), tr("Decrements as its linked expense sweeps")) {
                Switch(draft.autoUpdateFromPayments) { draft = draft.copy(autoUpdateFromPayments = !draft.autoUpdateFromPayments) }
            }
            if (Accounts.loanInputsComplete(draft) && draft.autoUpdateFromPayments) {
                val eligible = d.recurringExpenses.filter { it.linkedAccountId == null || it.linkedAccountId == draft.id }
                FieldGroup { FieldLabel(tr("Linked recurring expense")); ChipRow(eligible.map { it.id to it.name }, draft.linkedRecurringExpenseId ?: "") { v -> draft = draft.copy(linkedRecurringExpenseId = v) } }
            }
        } else FieldGroup {
            FieldLabel(tr("Interest rate (APR)")); TextField(aprText, { v -> aprText = v; draft = draft.copy(apr = parseRate(v)) }, prefix = "%", placeholder = tr("e.g. 21.9"), keyboard = KeyboardType.Decimal, label = tr("Interest rate (APR)"))
            Caption(tr("Feeds the payoff outlook on this card’s detail screen."), Modifier.padding(top = t.spacing.u * 0.75f))
        }
        Row(Modifier.fillMaxWidth().padding(top = t.spacing.pad), horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
            if (!isNew) EditorDeleteButton(original.name) { store.openSheet(Sheet.DeleteAccount(original.id)) }
            Button(if (isNew) tr("Add account") else tr("Save"), enabled = draft.name.isNotBlank(), modifier = Modifier.weight(1f)) {
                // §7.2: a Loan leaving for Credit with a payment linked asks first; the sheet saves
                if (Accounts.retypeNeedsCascade(d, draft)) store.openSheet(Sheet.RetypeCascade(draft)) else { store.saveAccount(draft); store.back() }
            }
        }
    }
}

/**
 * §7.2's delete: informational-only, so nothing else moves -- unless a payment was created as its
 * pair, in which case both go as one, and §3.11's add-back is offered in the same flow (§7.2).
 */
@Composable
fun DeleteAccountSheet(sheet: Sheet.DeleteAccount, state: UiState, store: AppStore) {
    val d = state.data ?: return
    val a = d.accounts.firstOrNull { it.id == sheet.id } ?: return
    val cur = d.settings.currency
    val linked = a.linkedRecurringExpenseId?.let { id -> d.recurringExpenses.firstOrNull { it.id == id } }
    val back = linked?.let { Recurring.reversalAmount(d, it, store.clock.today()) } ?: 0L
    val amount = formatMoney(back, cur)
    val pair = if (linked != null) trf("{0} and “{1}”", a.name, linked.name) else a.name
    DeleteWithReturnSheet(
        title = tr("Delete account"),
        question = when {
            linked == null -> trf("Delete {0}? This is informational-only, so nothing else in the app is affected.", a.name)
            back > 0 -> trf("Delete {0}? This also removes the {1}/month payment “{2}” — they were created as a pair and they go as one.", a.name, formatMoney(linked.amount, cur), linked.name) + " " + trf("Add {0} back to Savings? That’s what’s actually been swept for the current period.", amount)
            else -> trf("Delete {0}? This also removes the {1}/month payment “{2}” — they were created as a pair and they go as one.", a.name, formatMoney(linked.amount, cur), linked.name)
        },
        hasReturn = back > 0,
        confirmAddBack = deleteAddBackCopy(amount, pair),
        confirmDeleteOnly = deleteOnlyCopy(pair),
        onDismiss = store::closeSheet,
        onDelete = { returnMoney -> store.deleteAccount(a.id, reverse = returnMoney) },
    )
}

/** §7.2's retype cascade: "the cascade prompt above", asked before a Loan becomes Credit, with §3.11's add-back where it applies. */
@Composable
fun RetypeCascadeSheet(sheet: Sheet.RetypeCascade, state: UiState, store: AppStore) {
    val d = state.data ?: return
    val before = d.accounts.firstOrNull { it.id == sheet.draft.id } ?: return
    val linked = before.linkedRecurringExpenseId?.let { id -> d.recurringExpenses.firstOrNull { it.id == id } } ?: return
    val cur = d.settings.currency
    val back = Recurring.reversalAmount(d, linked, store.clock.today())
    val amount = formatMoney(back, cur)
    DeleteWithReturnSheet(
        title = tr("Change to a credit account"),
        question = trf("Change {0} to Credit? This also removes the {1}/month payment “{2}” — a credit account can’t track a loan’s balance.", sheet.draft.name, formatMoney(linked.amount, cur), linked.name) +
            (if (back > 0) " " + trf("Add {0} back to Savings? That’s what’s actually been swept for the current period.", amount) else ""),
        hasReturn = back > 0,
        confirmAddBack = deleteAddBackCopy(amount, trf("“{0}”", linked.name), trash = false),
        confirmDeleteOnly = deleteOnlyCopy(trf("“{0}”", linked.name), trash = false),
        onDismiss = store::closeSheet,
        onDelete = { returnMoney -> store.retypeAccountToCredit(sheet.draft, reverse = returnMoney) },
        trash = false,
    )
}
