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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Tab
import app.scifin.app.UiState
import app.scifin.engine.Reports
import app.scifin.engine.currencyNoun
import app.scifin.model.ProfileData
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.LucideIcon
import app.scifin.ui.Sparkline
import app.scifin.ui.TwoBarColumns
import app.scifin.ui.components.FigureLabel
import app.scifin.ui.components.Caption
import app.scifin.ui.components.EntityIcon
import app.scifin.ui.components.InfoNote
import app.scifin.ui.components.InlineLearn
import app.scifin.ui.components.LearnLink
import app.scifin.ui.components.LocalPageColumns
import app.scifin.ui.components.Page
import app.scifin.ui.components.ProgressBar
import app.scifin.ui.components.SectionLabel
import app.scifin.ui.components.Segmented
import app.scifin.ui.components.Surface
import app.scifin.ui.components.TabTitle
import app.scifin.ui.formatMoney
import app.scifin.ui.shortName
import app.scifin.ui.tutAnchor
import app.scifin.ui.components.AmountText
import app.scifin.model.Money
import app.scifin.model.Account
import app.scifin.engine.IncomeExpensePoint

/**
 * §4.6: Buffer (default, always) · Spending (always) · Income vs Expenses (Income on) · Savings vs.
 * Owed (enabled). The control's labels are the short ones: "Income" and "Owed"
 * are the only names of those two views that fit a quarter of a 320-360dp row at full size, and
 * each panel carries the view's full name as its heading.
 */
@Composable
fun ReportsScreen(state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val d = state.dataOrEmpty()
    val views = buildList {
        add("buffer" to "Buffer"); add("spending" to "Spending")
        if (d.settings.incomeEnabled) add("income" to "Income")
        if (d.settings.savingsVsOwedEnabled) add("savingsVsOwed" to "Owed")
    }
    val sub = state.sub?.takeIf { s -> views.any { it.first == s } } ?: "buffer"
    // §4.8: the second column holds a trend's supporting material; Spending has none, so it stays single.
    val twoCol = sub != "spending"
    Page(null, maxColumns = if (twoCol) 2 else 1) {
        TabTitle(Lucide.BAR_CHART_3, Tab.REPORTS.label) { store.selectTab(Tab.SETTINGS) }
        Segmented(views, sub, { store.selectSub(it) }, Modifier.tutAnchor("reports-seg").padding(bottom = t.spacing.gap * 1.3f))
        when (sub) {
            "spending" -> SpendingReport(d, store)
            "income" -> IncomeExpenseReport(d, store)
            "savingsVsOwed" -> SavingsVsOwedReport(d, store)
            else -> BufferReport(d, store)
        }
    }
}


/** §4.8: figure and chart in the first column, reading material in the second; one column emits both in order. The inner columns repeat Page's spacedBy(gap). */
@Composable
private fun ReportLayout(
    d: ProfileData,
    store: AppStore,
    primary: @Composable () -> Unit,
    supporting: @Composable () -> Unit,
) {
    val t = LocalTokens.current
    // §4.6's window control leads the supporting column on every view that has a trend, which is
    // exactly the set that uses this layout -- Spending is a single-month composition and does not.
    val sup: @Composable () -> Unit = { TrendWindow(d, store); supporting() }
    if (LocalPageColumns.current >= 2) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) { primary() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) { sup() }
        }
    } else {
        primary(); sup()
    }
}

/** §4.6: the trend window. A preference, so it lives in Settings.trendMonths (default 6), not in view state. */
@Composable
private fun TrendWindow(d: ProfileData, store: AppStore) {
    val t = LocalTokens.current
    SectionLabel("Months shown")
    Segmented(
        listOf("3" to "3", "6" to "6", "12" to "12", "24" to "24"),
        d.settings.trendMonths.toString(),
        { v -> store.updateSettings { s -> s.copy(trendMonths = v.toIntOrNull() ?: 6) } },
        Modifier.padding(bottom = t.spacing.gap * 0.5f),
    )
}

/** §5.1: Age of Money, its trend with leading empty points omitted, and the coverage figure behind a disclosure. */
@Composable
private fun BufferReport(d: ProfileData, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val today = store.clock.today()
    if (d.savings.amount == null) {
        // §3.1: while Savings is null, one plain line -- never a zero
        InfoNote("The figure appears once Savings has a value.")
        return
    }
    // §5.1: months before the opening lot are already left out by the engine; a zero after it is drawn
    val shown = Reports.ageOfMoneyTrend(d, today, d.settings.trendMonths)
    val current = shown.last().value; val first = shown.first().value
    // N points span N-1 months
    val spanLabel = if (shown.size > 1) "${shown.size - 1} month${if (shown.size == 2) "" else "s"} ago" else null
    val coverage = Reports.coverageDaysOrNull(d, today)
    var open by remember { mutableStateOf(false) }
    ReportLayout(d, store, primary = {
    Surface {
        Column(verticalArrangement = Arrangement.spacedBy(t.spacing.u * 0.75f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u / 2)) {
                LucideIcon(Lucide.TRENDING_UP, tint = p.txt2, size = t.icons.sm); FigureLabel("Age of Money")
            }
            Text("$current days", color = p.acc, fontSize = t.type.figure, fontWeight = FontWeight.Bold)
            if (spanLabel != null) Caption("${if (current - first >= 0) "Up" else "Down"} from $first days $spanLabel")
            if (shown.size > 1) Sparkline(shown.map { it.value }, shown.map { it.month.month.shortName() })
        }
    }
    }, supporting = {
    if (coverage != null) {
        LearnLink(
            if (open) "Hide how long this would cover you" else "Show how long this would cover you", { open = !open },
            Modifier.padding(top = t.spacing.u * 1.25f, start = t.spacing.u * 0.25f).semantics { role = Role.Button; toggleableState = ToggleableState(open) },
        )
        if (open) InlineLearn(listOf("At your typical spend over the last three complete months, today’s Savings would last about $coverage day${if (coverage == 1) "" else "s"}. Goal contributions are counted as money that has left the pool, so this is the conservative reading."), null)
    }
    InfoNote("This is a live figure: it counts from today back to the oldest ${currencyNoun(d.settings.currency)} still sitting in Savings, so it rises while money sits untouched. That’s the intended reading — money that sits is getting older, and a figure that only moved when you did would be measuring activity, not cushion.", Modifier.padding(top = t.spacing.u * 1.5f))
    // §13.1 item 4 (owner, 2026-10-25): each view's reading is a Learn topic, linked last in its side column
    LearnLink("Why money on hand matters", { store.push(Route.LearnTopic("money-on-hand")) }, Modifier.padding(top = t.spacing.gap))
    })
}

/** §5.2: the month's outflow as shares of the whole -- or as plain figures where a share would describe nothing. */
@Composable
private fun SpendingReport(d: ProfileData, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val cur = d.settings.currency
    val s = Reports.spendingComposition(d, store.clock.today())
    Surface(Modifier.padding(bottom = t.spacing.gap * 1.3f)) {
        Column {
            FigureLabel("This month’s net outflow")
            AmountText(formatMoney(s.net, cur), color = p.acc, fontSize = t.type.figure, fontWeight = FontWeight.Bold)
        }
    }
    if (s.slices.isEmpty()) { InfoNote("Nothing has left Savings yet this month, so there’s nothing to break down."); return }
    if (s.plainList) {
        for (r in s.slices) Row(
            Modifier.fillMaxWidth().background(p.surf2, RoundedCornerShape(t.radii.lg)).padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 1.125f)) { EntityIcon(entityIcon(r.icon, Lucide.TAG)); Text(r.label, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold) }
            Text(formatMoney(r.amount, cur), color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
        }
        InfoNote(
            if (s.net <= 0) "Nothing net left Savings this month — money came back in at least as fast as it went out, so there are no shares to draw."
            else "One of these nets out negative this month — a correction or a reversal returned more than went out — so the figures are shown plainly rather than as shares of a whole.",
            Modifier.padding(top = t.spacing.u * 1.25f),
        )
        return
    }
    for (r in s.slices) Column(Modifier.fillMaxWidth().background(p.surf, RoundedCornerShape(t.radii.lg)).padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.gap), modifier = Modifier.padding(bottom = t.spacing.gap)) {
            EntityIcon(entityIcon(r.icon, Lucide.TAG)); Text(r.label, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
        }
        ProgressBar(r.amount, s.net)
        Caption(spendingRowCaption(r.amount, s.net, cur), Modifier.padding(top = t.spacing.u * 0.875f))
    }
}

/** §5.4: income against expenses over the chosen window (goal contributions excluded -- money kept, not spent). */
@Composable
private fun IncomeExpenseReport(d: ProfileData, store: AppStore) {
    val t = LocalTokens.current
    val pts = Reports.incomeVsExpenses(d, store.clock.today(), d.settings.trendMonths) ?: return
    val p = t.palette
    ReportLayout(d, store, primary = {
    Surface {
        Column {
            FigureLabel("Income vs Expenses · this month")
            AmountText(incomeHeadline(pts, d.settings.currency), color = p.acc, fontSize = t.type.figure, fontWeight = FontWeight.Bold)
        }
    }
    TwoBarColumns(pts.map { Triple(it.month.month.shortName(), it.income, it.expenses) })
    }, supporting = {
    InfoNote("One accent, two opacities — income and expenses aren’t a good/bad pair, just two different things.", Modifier.padding(top = t.spacing.gap))
    LearnLink("Why uneven months are normal", { store.push(Route.LearnTopic("uneven-months")) }, Modifier.padding(top = t.spacing.gap))
    })
}

/** §5.3: unallocated Savings minus every Account balance, and its replay over the chosen window -- a dip drawn no differently than a rise. */
@Composable
private fun SavingsVsOwedReport(d: ProfileData, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val cur = d.settings.currency
    val trend = Reports.savingsVsOwedTrend(d, store.clock.today(), d.settings.trendMonths)
    ReportLayout(d, store, primary = {
    Surface {
        Column(verticalArrangement = Arrangement.spacedBy(t.spacing.u * 0.75f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u / 2)) {
                LucideIcon(Lucide.LANDMARK, tint = p.txt2, size = t.icons.sm); FigureLabel("Savings vs. Owed")
            }
            AmountText(formatMoney(Reports.savingsVsOwedNow(d), cur), color = p.acc, fontSize = t.type.figure, fontWeight = FontWeight.Bold)
            Caption("Unallocated Savings minus Credit and Loan balances — a dip is drawn no differently than a rise. Money held in Goals is not counted.")
            Sparkline(trend.map { it.value }, trend.map { it.month.month.shortName() })
        }
    }
    }, supporting = {
    if (owedNoteShown(d.accounts)) InfoNote("Off by default — with no Credit or Loan accounts, this would just be Savings again under a different name.", Modifier.padding(top = t.spacing.u * 1.5f))
    })
}

/** §5.2: the header names the outflow; each row states its share once. */
fun spendingRowCaption(amount: Money, net: Money, currency: String): String = "${kotlin.math.round(amount * 100.0 / net).toInt()}% — ${formatMoney(amount, currency)}"

/** §5.3: the note that explains the toggle is for the reader with nothing to weigh yet. */
fun owedNoteShown(accounts: List<Account>): Boolean = accounts.isEmpty()

/** §5.4's headline: the current month's two figures side by side, no verdict. */
fun incomeHeadline(points: List<IncomeExpensePoint>, currency: String): String {
    val now = points.last()
    return "${formatMoney(now.income, currency)} in · ${formatMoney(now.expenses, currency)} out"
}
