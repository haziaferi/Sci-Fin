package app.scifin.ui.screens

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
            SectionLabel("Accounts")
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
                                    Caption((if (a.type == AccountType.LOAN) "Loan" else "Credit") + (if (a.type == AccountType.LOAN && a.autoUpdateFromPayments) " · auto-updates" else ""))
                                }
                            },
                            figure = formatMoney(a.balance, cur), probeName = "account",
                            // §3.6: on a desktop every row carries its own +; a phone has the floating +
                            slot = if (LocalWidthClass.current != WidthClass.COMPACT)
                                ({ RowAdd("Log on ${a.name}") { store.openSheet(Sheet.Entry("acct:${a.id}")) } })
                            else ({ LucideIcon(Lucide.CHEVRON_RIGHT, tint = p.txt2, size = t.icons.sm) }),
                        )
                    }
                }
            }
            AddRow("New account") { store.push(Route.AccountEditor(null)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
            SectionLabel("Log")
            LedgerGroup {
                NavRow(Lucide.ROTATE_CCW, "Activity log", "${d.transactions.size} ${if (d.transactions.size == 1) "entry" else "entries"}", decorative = true, selected = shown == Route.Ledger) { open(Route.Ledger) }
                NavRow(Lucide.DATABASE, "Import a statement", d.importState.lastImportDate?.let { "Last ${formatDate(it)}" } ?: "CSV or OFX", decorative = true, selected = shown == Route.Import) { open(Route.Import) }
            }
        }
        AccountsNote(store)
        FloatingAddRoom()
    }
}

private const val ACCOUNTS_NOTE = "Accounts are informational — they never feed Savings, Goal or Category math directly (a Loan’s auto-update only keeps its own tracked balance honest). Choose one for its detail and, on a card, its payoff outlook."

/** §4.4: the note's first clause on screen, the rest in a sheet. */
@Composable
private fun AccountsNote(store: AppStore) {
    NoteLine("Accounts are informational.", Modifier.padding(top = LocalTokens.current.spacing.gap).probeAs("note")) { store.openSheet(Sheet.AccountsNote) }
}

@Composable
fun AccountsNoteSheet(store: AppStore) {
    val t = LocalTokens.current
    SheetScaffold(title = "Accounts", onDismiss = store::closeSheet) {
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
        TopRow(a?.name ?: "Account", onBack = { store.back() })
        if (a == null) { InfoNote("That account is gone."); return@Page }
        val linked = a.linkedRecurringExpenseId?.let { id -> d.recurringExpenses.firstOrNull { it.id == id } }
        val finished = Accounts.finished(d, a)
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(t.spacing.u / 2)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u / 2)) {
                    LucideIcon(entityIcon(a.icon, if (a.type == AccountType.LOAN) Lucide.LANDMARK else Lucide.CREDIT_CARD), tint = p.txt2, size = t.icons.sm)
                    FigureLabel((if (a.type == AccountType.LOAN) "Loan" else "Credit") + " balance")
                }
                AmountText(formatMoney(a.balance, cur), color = p.acc, fontSize = t.type.figure, fontWeight = FontWeight.Bold)
                if (finished) Caption("This loan’s payment schedule has ended. The account stays until you remove it.")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = t.spacing.gap), horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
            Button("Log", ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { store.openSheet(Sheet.Entry("acct:${a.id}")) }
            Button("Edit", ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { store.push(Route.AccountEditor(a.id)) }
        }
        if (a.type == AccountType.CREDIT) PayoffPanel(a, cur, store) else {
            SectionLabel("Loan")
            Surface {
                Column(verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
                    a.interestRate?.let { DetailRow("Rate", "${rateText(it, 2)}%", a.rateType?.let { r -> rateTypeLabel(r) }) }
                    a.principal?.let { DetailRow("Original principal", formatMoney(it, cur), null) }
                    a.termMonths?.let { DetailRow("Term", "$it month${if (it == 1) "" else "s"}", null) }
                    if (linked != null) DetailRow("Payment", formatMoney(linked.amount, cur), cadenceLabel(linked.cadence))
                    if (a.autoUpdateFromPayments) Caption("Auto-updates as its linked expense sweeps — interest and principal are split from the live balance each cycle, so it survives extra payments and corrections.")
                }
            }
            LearnLink(BORROWING_LINK, { store.push(Route.LearnTopic("borrowing")) })
        }
        InfoNote("Accounts are informational — they never feed Savings, Goal or Category math directly.", Modifier.padding(top = t.spacing.gap))
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
private const val BORROWING_LINK = "What borrowing really costs"

fun rateTypeLabel(r: RateType) = when (r) { RateType.FIXED -> "Fixed"; RateType.VARIABLE -> "Variable"; RateType.INTEREST_ONLY -> "Interest-only" }

/** §9.2 surface 1: three ways of paying a card off and what each actually costs; the fixed payment can become a linked Recurring Expense. */
@Composable
private fun PayoffPanel(a: Account, cur: String, store: AppStore) {
    val t = LocalTokens.current
    // committed on every valid change and NOT keyed on a.apr: re-keying resets the field mid-typing
    var aprText by rememberSaveable(a.id) { mutableStateOf(rateText(a.apr, 1)) }
    var fixedText by rememberSaveable(a.id) { mutableStateOf("") }
    SectionLabel("Payoff outlook")
    Surface {
        Column {
            FieldGroup {
                FieldLabel("Interest rate (APR)")
                TextField(aprText, { v -> aprText = v; val r = parseRate(v); if (r != null || v.isBlank()) store.setAccountApr(a.id, r) }, prefix = "%", placeholder = "e.g. 21.9", keyboard = KeyboardType.Decimal, label = "Interest rate (APR)")
                Caption("Kept on this account, so the outlook is here next time rather than blank.", Modifier.padding(top = t.spacing.u * 0.75f))
            }
            val apr = a.apr
            if (apr == null || a.balance <= 0) {
                Caption(if (a.balance <= 0) "Nothing outstanding on this card right now, so there is no payoff to project." else "Add the card’s rate and this will show what each way of paying it off actually costs.")
            } else {
                val i = apr / 12
                // §9.2: no amount typed, no projection; a typed amount is floored to the 100-minor-unit minimum
                val fixedAmt = parseMoney(fixedText)?.takeIf { it > 0 }?.let { maxOf(100L, it) }
                val min = Accounts.payoffAtMinimum(a.balance, i)
                Outcome("Paid in full this cycle", "the whole balance, once", 0, 0, cur, primary = true)
                FieldGroup {
                    Box(Modifier.padding(top = t.spacing.gap)) { FieldLabel("A fixed payment each month") }
                    TextField(fixedText, { fixedText = it }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = "A fixed payment each month")
                }
                if (fixedAmt != null) {
                    val fixed = Accounts.payoffAtFixedPayment(a.balance, i, fixedAmt)
                    Outcome("${formatMoney(fixedAmt, cur)} every month", "a payment you choose and keep", fixed.months, fixed.interest, cur, primary = false)
                    if (fixed.months != null) {
                        Button("Commit to ${formatMoney(fixedAmt, cur)} a month", modifier = Modifier.fillMaxWidth().padding(top = t.spacing.u * 0.75f)) { store.openSheet(Sheet.PayoffCommit(a.id, fixedAmt)) }
                        Caption("Creates a recurring expense for the payment, linked to this account.", Modifier.padding(top = t.spacing.u * 0.75f))
                    }
                }
                Outcome("Minimum only", "about ${formatMoney(min.first, cur)} to start, falling as the balance does", min.months, min.interest, cur, primary = false)
                Caption("Minimum modelled as ${(Accounts.MIN_PAYMENT_PCT * 100).toInt()}% of the balance or ${formatMoney(Accounts.MIN_PAYMENT_FLOOR, cur)}, whichever is larger — an assumption, not your card’s actual terms. It can’t become a recurring expense, because it isn’t a fixed amount.")
            }
        }
    }
    // under the outlook it explains, above the credit note and its topic (§4.5's order, owner 2026-10-23)
    LearnLink(BORROWING_LINK, { store.push(Route.LearnTopic("borrowing")) })
    InfoNote("In places that score credit, paying this off in full each cycle also reports payment history — not something this app is telling you to do, just part of the picture.", Modifier.padding(top = t.spacing.gap))
    LearnLink("How credit scoring works, and who it leaves out", { store.push(Route.LearnTopic("credit-score")) })
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
        Caption((if (interest == null) "The balance never falls at that payment." else "${formatMoney(interest, cur)} of interest") + " — $sub", Modifier.padding(top = t.spacing.u * 0.875f))
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
    var name by rememberSaveable(sheet) { mutableStateOf("${a.name} payment") }
    SheetScaffold(title = "Commit to ${formatMoney(sheet.amount, cur)} a month", onDismiss = store::closeSheet) {
        FieldGroup { FieldLabel("Name this recurring expense"); TextField(name, { name = it }, label = "Name this recurring expense") }
        FeasibilityNote("Monthly, on the ${ordinal(today.day)}, linked to ${a.name}. The first payment is ${formatDate(today.plusMonthsClamped(1))} — a month from today, not today — so nothing leaves Savings until then.")
        Caption("No end date: a card’s payoff moves with every new purchase. Edit or delete it any time.", Modifier.padding(top = t.spacing.gap, bottom = t.spacing.u * 1.25f))
        ButtonRow {
            Button("Cancel", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.closeSheet() }
            Button("Create it", modifier = Modifier.weight(1f)) { store.commitPayoff(a.id, sheet.amount, name); store.closeSheet() }
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
        TopRow(if (isNew) "New account" else draft.name.ifBlank { "Account" }, onBack = { store.back() })
        FieldGroup { FieldLabel("Name"); TextField(draft.name, { draft = draft.copy(name = it) }, placeholder = "Credit card, car loan…", label = "Name") }
        FieldGroup { FieldLabel("Type"); Segmented(listOf("credit" to "Credit", "loan" to "Loan"), draft.type.token, { v -> draft = Accounts.retype(draft, AccountType.of(v)); loanRateText = ""; aprText = ""; principalText = ""; termText = "" }) }
        FieldGroup {
            FieldLabel("Balance owed"); TextField(balanceText, { v -> balanceText = v; draft = draft.copy(balance = parseMoney(v) ?: 0) }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = "Balance owed")
            Caption("Directly editable at any time — logging a payment stays available as a lower-friction nudge too.", Modifier.padding(top = t.spacing.u * 0.75f))
        }
        if (draft.type == AccountType.LOAN) {
            SectionLabel("Loan terms")
            FieldGroup {
                FieldLabel("Interest rate"); TextField(loanRateText, { v -> loanRateText = v; draft = draft.copy(interestRate = parseRate(v)) }, prefix = "%", placeholder = "e.g. 5.90", keyboard = KeyboardType.Decimal, label = "Interest rate")
                Caption("Auto-update needs this: each cycle it splits interest from principal off the live balance.", Modifier.padding(top = t.spacing.u * 0.75f))
            }
            FieldGroup { FieldLabel("Original principal"); TextField(principalText, { v -> principalText = v; draft = draft.copy(principal = parseMoney(v)) }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = "Original principal") }
            FieldGroup { FieldLabel("Term (months)"); TextField(termText, { v -> termText = v; draft = draft.copy(termMonths = v.trim().toIntOrNull()?.takeIf { it >= 1 }) }, prefix = "#", keyboard = KeyboardType.Number, label = "Term (months)") }
            FieldGroup { FieldLabel("Rate type"); ChipRow(RateType.entries.map { it.token to rateTypeLabel(it) }, draft.rateType?.token ?: "") { v -> draft = draft.copy(rateType = RateType.of(v)) } }
            // §7.2: a Loan with no rate has nothing for the decrement to compute from -- the toggle is absent, not greyed
            if (Accounts.loanInputsComplete(draft)) SettingRow(Lucide.REPEAT, "Auto-update from payments", "Decrements as its linked expense sweeps") {
                Switch(draft.autoUpdateFromPayments) { draft = draft.copy(autoUpdateFromPayments = !draft.autoUpdateFromPayments) }
            }
            if (Accounts.loanInputsComplete(draft) && draft.autoUpdateFromPayments) {
                val eligible = d.recurringExpenses.filter { it.linkedAccountId == null || it.linkedAccountId == draft.id }
                FieldGroup { FieldLabel("Linked recurring expense"); ChipRow(eligible.map { it.id to it.name }, draft.linkedRecurringExpenseId ?: "") { v -> draft = draft.copy(linkedRecurringExpenseId = v) } }
            }
        } else FieldGroup {
            FieldLabel("Interest rate (APR)"); TextField(aprText, { v -> aprText = v; draft = draft.copy(apr = parseRate(v)) }, prefix = "%", placeholder = "e.g. 21.9", keyboard = KeyboardType.Decimal, label = "Interest rate (APR)")
            Caption("Feeds the payoff outlook on this card’s detail screen.", Modifier.padding(top = t.spacing.u * 0.75f))
        }
        Row(Modifier.fillMaxWidth().padding(top = t.spacing.pad), horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
            if (!isNew) EditorDeleteButton(original.name) { store.openSheet(Sheet.DeleteAccount(original.id)) }
            Button(if (isNew) "Add account" else "Save", enabled = draft.name.isNotBlank(), modifier = Modifier.weight(1f)) {
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
    val pair = if (linked != null) "${a.name} and “${linked.name}”" else a.name
    DeleteWithReturnSheet(
        title = "Delete account",
        question = "Delete ${a.name}?" + when {
            linked == null -> " This is informational-only, so nothing else in the app is affected."
            back > 0 -> " This also removes the ${formatMoney(linked.amount, cur)}/month payment “${linked.name}” — they were created as a pair and they go as one. Add $amount back to Savings? That’s what’s actually been swept for the current period."
            else -> " This also removes the ${formatMoney(linked.amount, cur)}/month payment “${linked.name}” — they were created as a pair and they go as one."
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
        title = "Change to a credit account",
        question = "Change ${sheet.draft.name} to Credit? This also removes the ${formatMoney(linked.amount, cur)}/month payment “${linked.name}” — a credit account can’t keep a loan’s tracked figure honest." +
            (if (back > 0) " Add $amount back to Savings? That’s what’s actually been swept for the current period." else ""),
        hasReturn = back > 0,
        confirmAddBack = deleteAddBackCopy(amount, "“${linked.name}”", trash = false),
        confirmDeleteOnly = deleteOnlyCopy("“${linked.name}”", trash = false),
        onDismiss = store::closeSheet,
        onDelete = { returnMoney -> store.retypeAccountToCredit(sheet.draft, reverse = returnMoney) },
        trash = false,
    )
}
