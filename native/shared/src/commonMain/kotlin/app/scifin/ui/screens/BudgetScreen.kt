package app.scifin.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Sheet
import app.scifin.app.Tab
import app.scifin.app.UiState
import app.scifin.engine.Income
import app.scifin.engine.Recurring
import app.scifin.engine.Threshold
import app.scifin.model.ProfileData
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.LucideIcon
import app.scifin.ui.components.AddRow
import app.scifin.ui.components.Button
import app.scifin.ui.components.ButtonKind
import app.scifin.ui.components.ButtonRow
import app.scifin.ui.components.Caption
import app.scifin.ui.components.DisclosureNote
import app.scifin.ui.components.EntityIcon
import app.scifin.ui.components.FloatingAddRoom
import app.scifin.ui.components.LearnLink
import app.scifin.ui.components.LedgerGroup
import app.scifin.ui.components.LedgerLabel
import app.scifin.ui.components.LedgerRow
import app.scifin.ui.components.LocalPageColumns
import app.scifin.ui.components.Page
import app.scifin.ui.components.PlaceholderCard
import app.scifin.ui.components.ProgressBar
import app.scifin.ui.components.RoundIconButton
import app.scifin.ui.components.RowAdd
import app.scifin.ui.components.RuledNote
import app.scifin.ui.components.SavingsRow
import app.scifin.ui.components.SectionLabel
import app.scifin.ui.components.SheetScaffold
import app.scifin.ui.components.TabTitle
import app.scifin.ui.components.ViewSwitch
import app.scifin.ui.formatMoney
import app.scifin.ui.tutAnchor
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/** §12's default glyphs: `icon` is nullable and a null renders the entity's neutral default. */
fun entityIcon(iconName: String?, default: Lucide): Lucide = Lucide.byKey(iconName) ?: default

/** Before the first Profile exists (§11.1 screen 1's ground) the page shows what it will look like after Continue: empty. */
internal fun UiState.dataOrEmpty(): ProfileData = data ?: ProfileData.fresh("", "")

/** §4.3: the brand row, the Envelopes | Categories switch, and the chosen view. */
@Composable
fun BudgetScreen(state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val d = state.dataOrEmpty()
    val sub = state.sub ?: "envelopes"
    Page(null, maxColumns = 2) {
        TabTitle(Lucide.WALLET, Tab.BUDGET.label) { store.selectTab(Tab.SETTINGS) }
        // §4.3: at two columns both views render side by side and the switch is not drawn; `sub` governs one column.
        if (LocalPageColumns.current >= 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) { EnvelopesView(d, state, store) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) { CategoriesView(d, store) }
            }
        } else {
            ViewSwitch(listOf("envelopes" to "Envelopes", "categories" to "Categories"), sub, { store.selectSub(it) }, Modifier.padding(bottom = t.spacing.gap * 1.3f))
            if (sub == "categories") CategoriesView(d, store) else EnvelopesView(d, state, store)
            FloatingAddRoom()
        }
    }
    GoalPrompt(d, state, store)
}

/** §4.2 row 1: Savings → Goals → Income → Recurring Expenses with its threshold note → Learn links. */
@Composable
private fun EnvelopesView(d: ProfileData, state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val today = store.clock.today()
    CatchUpCard(state, d, store)
    SavingsRow(d, store)
    GoalsSection(d, store)
    IncomeSection(d, store, today)
    RecurringSection(d, store, today)
    Column(Modifier.padding(top = t.spacing.gap)) {
        LearnLink("Why split money into categories?", { store.push(Route.LearnTopic("why-categories")) })
        LearnLink("Why naming a goal helps", { store.push(Route.LearnTopic("name-your-goal")) })
    }
}

/** §6: each Goal a row -- name, the amount left (or saved, §6.3) in the figure column, its + in the slot, the bar under. */
@Composable
private fun GoalsSection(d: ProfileData, store: AppStore) {
    val t = LocalTokens.current
    val cur = d.settings.currency
    Column(Modifier.tutAnchor("goals-section"), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
        SectionLabel("Goals")
        if (d.goals.isNotEmpty()) LedgerGroup {
            for (g in d.goals) {
                val target = g.target
                // progressCaption's two parts, apart: the amount in the figure column, the words under the bar
                val figure = formatMoney(if (target != null) abs(g.saved - target) else g.saved, cur)
                val words = when {
                    target == null -> "saved so far — no target, just a running tally"
                    g.saved > target -> "past goal"
                    else -> "left of goal"
                }
                LedgerRow(
                    onClick = { store.push(Route.GoalEditor(g.id)) },
                    name = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
                            EntityIcon(entityIcon(g.icon, Lucide.PIGGY_BANK))
                            Text(g.name, color = t.palette.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
                        }
                    },
                    figure = figure, probeName = "goal",
                    slot = { RowAdd("Add to ${g.name}") { store.openSheet(Sheet.Entry("goal:${g.id}")) } },
                    under = {
                        if (target != null) Box(Modifier.padding(top = t.spacing.u * 0.75f)) { ProgressBar(g.saved, target) }
                        Caption(words, Modifier.padding(top = t.spacing.u * 0.5f))
                    },
                )
            }
        }
        AddRow("New goal") { store.push(Route.GoalEditor(null)) }
    }
}

/** §3.3: present only while Income is on -- otherwise the section's placeholder (hidden entirely, not greyed). */
@Composable
private fun IncomeSection(d: ProfileData, store: AppStore, today: LocalDate) {
    val t = LocalTokens.current; val p = t.palette
    val cur = d.settings.currency
    Column(Modifier.tutAnchor("income-section"), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
        SectionLabel("Income")
        if (!d.settings.incomeEnabled) {
            PlaceholderCard("Off by default. Turn it on in Settings to track paychecks and let this section come back.")
            return@Column
        }
        LedgerRow(
            onClick = null,
            name = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u / 2)) {
                    LucideIcon(Lucide.BANKNOTE, tint = p.txt2, size = t.icons.sm)
                    LedgerLabel("Per paycheck (${cadenceLabel(d.settings.incomeCadence).lowercase()})")
                }
            },
            figure = formatMoney(d.settings.income, cur), probeName = "income",
            under = { Caption("${formatMoney(Income.receivedThisMonth(d, today), cur)} received so far this month", Modifier.padding(top = t.spacing.u * 0.5f)) },
        )
        Income.noIncomeLoggedDays(d, today)?.let { days -> RuledNote("No income logged in the last $days ${if (days == 1) "day" else "days"}.") }
    }
}

/** §4.2's Recurring Expenses summary (this month's scheduled total, pushing to the list), §3.5's note directly under it. */
@Composable
private fun RecurringSection(d: ProfileData, store: AppStore, today: LocalDate) {
    val t = LocalTokens.current; val p = t.palette
    val cur = d.settings.currency
    Column(Modifier.tutAnchor("recurring-section"), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
        SectionLabel("Recurring expenses")
        LedgerGroup {
            LedgerRow(
                onClick = { store.push(Route.RecurringList) },
                name = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 1.125f)) {
                        EntityIcon(Lucide.REPEAT)
                        val n = d.recurringExpenses.size
                        Text(if (n == 0) "No recurring expenses yet" else "$n recurring expense${if (n == 1) "" else "s"}", color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
                    }
                },
                figure = if (d.recurringExpenses.isEmpty()) "" else "${formatMoney(Recurring.scheduledTotal(d, today), cur)}/mo", probeName = "recurring",
                slot = { LucideIcon(Lucide.CHEVRON_RIGHT, tint = p.txt2, size = t.icons.sm) },
            )
        }
        // §3.5: the two plain figures always shown; the interpretation behind the disclosure
        Threshold.recurringNote(d, today)?.let { n ->
            var open by remember { mutableStateOf(false) }
            val summary = "${formatMoney(n.scheduled, cur)} recurring · ${formatMoney(n.base, cur)} ${if (n.incomeOn) "income this month" else "Savings"}"
            val detail = when {
                n.incomeOn && n.baseIsEmpty -> "Usual at the start of the month, before the first paycheck arrives."
                n.incomeOn -> "More than a third of what has landed so far. Common early in the month, before more income lands."
                n.baseIsEmpty -> "There’s no cushion to draw them from."
                else -> "More than a twelfth of your Savings. At that pace they alone would use up the balance in about a year."
            }
            DisclosureNote(summary, detail, open) { open = !open }
        }
        // §4.3: at two columns the summary unwraps into its occurrences, below the note
        if (LocalPageColumns.current >= 2 && d.recurringExpenses.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
                for (e in d.recurringExpenses) RecurringExpenseRow(e, cur) { store.push(Route.RecurringEditor(e.id)) }
            }
        }
    }
}

/**
 * §3.9's catch-up summary: a dismissible card, not a modal, reporting what the catch-up did in
 * both directions, no verdict. It persists until dismissed (`catchUpSummaryPending`), so a
 * person who backgrounds the app first still learns that months of sweeps left Savings.
 * §3.11's completion notices ride on the same card as plain lines.
 */
@Composable
private fun CatchUpCard(state: UiState, d: ProfileData, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val s = state.catchUp ?: return
    val cur = d.settings.currency
    val parts = buildList {
        if (s.deposits.count > 0) add("${s.deposits.count} deposit${if (s.deposits.count == 1) "" else "s"} added ${formatMoney(s.deposits.total, cur)}")
        if (s.sweptTotal > 0) add("${s.monthsReplayed} month${if (s.monthsReplayed == 1) "" else "s"} of recurring expenses took ${formatMoney(s.sweptTotal, cur)}")
    }
    if (parts.isEmpty() && s.endedNow.isEmpty()) return
    Column(Modifier.fillMaxWidth().background(p.surf, RoundedCornerShape(t.radii.lg)).padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Since you were last here", color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
            RoundIconButton(Lucide.X, "Dismiss") { store.dismissCatchUpCard() }
        }
        if (parts.isNotEmpty()) Caption(parts.joinToString(", and ") + ".", Modifier.padding(top = t.spacing.u * 0.75f))
        for (ended in s.endedNow) Caption("${ended.name} — ${ended.note}", Modifier.padding(top = t.spacing.u * 0.75f))
    }
}

/** §11.3's prompt is up; the floating + steps aside while it is. Budget home only: the tab bar stays reachable under it. */
internal fun goalPromptShows(state: UiState): Boolean {
    val d = state.data ?: return false
    val s = d.settings
    return s.onboardingComplete && !s.goalPromptDismissed && d.savings.amount != null && state.catchUp == null && !s.catchUpSummaryPending && state.sheet == null &&
        (state.route as? Route.TabRoute)?.tab == Tab.BUDGET
}

/**
 * §11.3: once, after Savings has a real value, on Budget home, never over §3.9's summary; both answers
 * set the one-way flag, and accepting opens §6's goal calculator.
 */
@Composable
private fun GoalPrompt(d: ProfileData, state: UiState, store: AppStore) {
    if (!goalPromptShows(state)) return
    val t = LocalTokens.current
    SheetScaffold(title = null, onDismiss = null) {
        Text("Is there something in particular you’d like to save up for?", color = t.palette.txt, fontSize = t.type.title, fontWeight = FontWeight.Bold, lineHeight = t.type.title * 1.25f, modifier = Modifier.padding(bottom = t.spacing.u * 1.25f))
        ButtonRow {
            Button("Not right now", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.updateSettings { it.copy(goalPromptDismissed = true) } }
            Button("Let’s set one up", modifier = Modifier.weight(1f)) { store.updateSettings { it.copy(goalPromptDismissed = true) }; store.push(Route.GoalEditor(null)) }
        }
    }
}
