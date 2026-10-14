package app.scifin.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.scifin.ui.screens.LearnTopicScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import app.scifin.app.TUTORIAL_STOPS
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Tab
import app.scifin.app.UiState
import app.scifin.ui.screens.AccountDetailScreen
import app.scifin.ui.screens.AccountEditorScreen
import app.scifin.ui.screens.ActivityScreen
import app.scifin.ui.screens.ImportScreen
import app.scifin.ui.screens.LedgerScreen
import app.scifin.ui.screens.BudgetScreen
import app.scifin.ui.screens.CategoryEditorScreen
import app.scifin.ui.screens.CurrencyLandingScreen
import app.scifin.ui.screens.PermissionStepScreen
import app.scifin.ui.screens.GoalEditorScreen
import app.scifin.ui.screens.RecurringEditorScreen
import app.scifin.ui.screens.RecurringListScreen
import app.scifin.ui.screens.LearnScreen
import app.scifin.ui.screens.LoanCalcScreen
import app.scifin.ui.screens.PurchaseCalcScreen
import app.scifin.ui.screens.OnboardingSheet
import app.scifin.ui.screens.ReportsScreen
import app.scifin.ui.screens.SettingsPageScreen
import app.scifin.ui.screens.SettingsScreen
import app.scifin.ui.screens.goalPromptShows
import app.scifin.ui.sheets.Sheets
import app.scifin.ui.components.fitLabelSize
import app.scifin.ui.components.FloatingAdd
import app.scifin.app.Sheet
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/** §4.8's width classes: the container swaps at 600dp, list-detail begins at 840dp. */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

fun widthClassOf(widthDp: Float): WidthClass = when {
    widthDp < 600f -> WidthClass.COMPACT
    widthDp < 840f -> WidthClass.MEDIUM
    else -> WidthClass.EXPANDED
}

val LocalWidthClass = staticCompositionLocalOf { WidthClass.COMPACT }

/**
 * The app: one frame both hosts show. Tokens come from the active Profile's Settings (§2.5,
 * §8.4 -- appearance is inside the wrap), the navigation container from the width (§4.8
 * rule 1), and the screen from the top of the route stack. [targetDp] is the platform's
 * minimum control size: 48 on Android, 40 on Windows.
 */
@Composable
fun SciFinApp(store: AppStore, targetDp: Int, systemDark: Boolean = isSystemInDarkTheme()) {
    val state by store.state.collectAsState()
    // The palette and the type ramp do not depend on the width, so the ground can be painted
    // before anything is measured; the spacing and icon tokens do (§2.5's screen factor),
    // so the real token set is built inside the box, from the width of the content area itself.
    val ground = tokensFor(state.settings, systemDark, targetDp)
    val anchors = remember { mutableStateMapOf<String, Rect>() }
    val activeAnchor = state.tutorialStop?.let { TUTORIAL_STOPS[it].anchor }
    // the ground runs under the system bars; content keeps clear of them (zero insets on desktop)
    BoxWithConstraints(Modifier.fillMaxSize().background(ground.palette.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val tokens = tokensFor(state.settings, systemDark, targetDp, screenScale(maxWidth.value), LocalDensity.current.fontScale)
        SciFinTheme(tokens) {
            val wc = widthClassOf(maxWidth.value)
            CompositionLocalProvider(LocalWidthClass provides wc, LocalTutorialAnchors provides anchors, LocalTutorialActive provides activeAnchor) {
                if (!state.ready) return@CompositionLocalProvider
                Box(Modifier.fillMaxSize()) {
                    when (wc) {
                        WidthClass.COMPACT -> Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxWidth()) { Screen(state, store); FloatingAddOver(state, store, wc) }
                            if (!state.inOnboardingPage) NavBar(state.tab, tokens, onSelect = store::selectTab)
                        }
                        else -> Row(Modifier.fillMaxSize()) {
                            if (!state.inOnboardingPage) NavRail(state.tab, tokens, onSelect = store::selectTab)
                            Box(Modifier.weight(1f).fillMaxHeight()) { Screen(state, store) }
                        }
                    }
                    state.sheet?.let { Sheets(it, state, store) }
                    (state.route as? Route.Onboarding)?.let { if (it.step == 1) OnboardingSheet(it, state, store) }
                    state.tutorialStop?.let { TutorialOverlay(it, anchors, state.data, store) }
                }
            }
        }
    }
}

@Composable
private fun Screen(state: UiState, store: AppStore) {
    // §11.1's screen 1 is a sheet over the screen beneath it; its later steps are pages of their own
    val top = state.route
    if (top is Route.Onboarding && top.step > 1) {
        when (top.step) {
            2 -> PermissionStepScreen(state, store)
            else -> CurrencyLandingScreen(state, store)
        }
        return
    }
    val first = state.routes.first() as Route.TabRoute
    // §4.8: Activity, Learn and Settings open their screens beside the root when two columns fit
    val detail = if (LocalWidthClass.current != WidthClass.COMPACT) shownDetail(state) else null
    if (detail != null) {
        TwoColumns(
            root = { TabRootScreen(first.tab, state, store) },
            detail = { CompositionLocalProvider(LocalResting provides (state.routes.size == 1)) { RouteScreen(detail, state, store) } },
            single = { RouteScreen(state.routes.lastOrNull { it !is Route.Onboarding } ?: first, state, store) },
        )
        return
    }
    RouteScreen(state.routes.lastOrNull { it !is Route.Onboarding } ?: first, state, store)
}

@Composable
private fun TabRootScreen(tab: Tab, state: UiState, store: AppStore) = when (tab) {
    Tab.BUDGET -> BudgetScreen(state, store)
    Tab.ACTIVITY -> ActivityScreen(state, store)
    Tab.LEARN -> LearnScreen(state, store)
    Tab.REPORTS -> ReportsScreen(state, store)
    Tab.SETTINGS -> SettingsScreen(state, store)
}

@Composable
private fun RouteScreen(route: Route, state: UiState, store: AppStore) {
    when (val r = route) {
        is Route.Onboarding -> Unit
        is Route.LearnTopic -> LearnTopicScreen(r, store)
        is Route.Settings -> SettingsPageScreen(r, state, store)
        is Route.GoalEditor -> GoalEditorScreen(r, state, store)
        Route.RecurringList -> RecurringListScreen(state, store)
        is Route.AccountDetail -> AccountDetailScreen(r, state, store)
        is Route.AccountEditor -> AccountEditorScreen(r, state, store)
        Route.Ledger -> LedgerScreen(state, store)
        Route.Import -> ImportScreen(state, store)
        is Route.CategoryEditor -> CategoryEditorScreen(r, state, store)
        is Route.RecurringEditor -> RecurringEditorScreen(r, state, store)
        Route.LoanCalc -> LoanCalcScreen(state, store)
        Route.PurchaseCalc -> PurchaseCalcScreen(state, store)
        is Route.TabRoute -> TabRootScreen(r.tab, state, store)
    }
}

private val Tab.icon: Lucide
    get() = when (this) {
        Tab.BUDGET -> Lucide.WALLET; Tab.ACTIVITY -> Lucide.CREDIT_CARD; Tab.LEARN -> Lucide.LIGHTBULB
        Tab.REPORTS -> Lucide.BAR_CHART_3; Tab.SETTINGS -> Lucide.SETTINGS
    }

/** The phone bar's tabs; Settings is reached from the title row's glyph instead (§4.1). */
internal val BAR_TABS = Tab.entries - Tab.SETTINGS

/** Below 600dp: the bar along the bottom, a control stroke above it, one equal cell per [BAR_TABS] entry. */
@Composable
private fun NavBar(selected: Tab, t: SciFinTokens, onSelect: (Tab) -> Unit) {
    val p = t.palette
    BoxWithConstraints(
        Modifier.fillMaxWidth().background(p.bg)
            .drawBehind { drawLine(p.bordStrong, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = t.strokes.ctl.toPx().coerceAtLeast(1f)) }
            .padding(horizontal = t.spacing.u / 2, vertical = t.spacing.gap),
    ) {
        // one size for every label: the largest step at which the longest label fits its cell (§2.5 at xl);
        // maxWidth is already inside the padding above
        val cell = maxWidth / BAR_TABS.size
        val labelSize = navLabelSize(rememberTextMeasurer(), BAR_TABS.map { it.label }, cell, t.type.caption)
        Row(Modifier.fillMaxWidth()) {
            for (tab in BAR_TABS) {
                val on = tab == selected
                Column(
                    Modifier.weight(1f).heightIn(min = t.spacing.target).selectable(selected = on, role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    LucideIcon(tab.icon, tint = if (on) p.acc else p.txt2)
                    Text(tab.label, color = if (on) p.acc else p.txt2, fontSize = labelSize, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** The bar's label size: one step for every label, from [max] down in half-points until the widest fits its cell with a little air. */
@Composable
private fun navLabelSize(measurer: TextMeasurer, labels: List<String>, cell: Dp, max: TextUnit): TextUnit {
    val limit = with(LocalDensity.current) { (cell - LocalTokens.current.spacing.u * 0.75f).toPx() }
    return fitLabelSize(labels, limit, max) { text, size -> measurer.measure(text, TextStyle(fontSize = size, fontWeight = FontWeight.Bold)).size.width.toFloat() }
}

/** At 600dp and above: every tab, Settings included, as a rail on the left; the selected cell sits on a 12 % accent wash. */
@Composable
private fun NavRail(selected: Tab, t: SciFinTokens, onSelect: (Tab) -> Unit) {
    val p = t.palette
    // §2.5: one label size for every cell, the largest that fits; the rail scrolls when the cells outgrow the height.
    val labelSize = navLabelSize(rememberTextMeasurer(), Tab.entries.map { it.label }, t.spacing.u * 11 - t.spacing.u - t.spacing.u / 2, t.type.caption)
    Column(
        Modifier.fillMaxHeight().width(t.spacing.u * 11).background(p.bg)
            .drawBehind { drawLine(p.bordStrong, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = t.strokes.ctl.toPx().coerceAtLeast(1f)) }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = t.spacing.u / 2, vertical = t.spacing.gap),
        verticalArrangement = Arrangement.spacedBy(t.spacing.u / 2),
    ) {
        for (tab in Tab.entries) {
            val on = tab == selected
            Column(
                Modifier.fillMaxWidth().heightIn(min = t.spacing.target).probeSize(LocalProbe.current, "rail-cell")
                    .background(selectedWash(on) ?: p.bg, RoundedCornerShape(t.spacing.u))
                    .selectable(selected = on, role = Role.Tab) { onSelect(tab) }.padding(vertical = t.spacing.gap, horizontal = t.spacing.u / 4),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                LucideIcon(tab.icon, tint = if (on) p.acc else p.txt2)
                val probe = LocalProbe.current
                Text(
                    tab.label, color = if (on) p.acc else p.txt2, fontSize = labelSize, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1, softWrap = false,
                    onTextLayout = { r -> probe?.invoke("rail-label", mapOf("w" to r.size.width.toFloat(), "lines" to r.lineCount.toFloat(), "overflow" to if (r.hasVisualOverflow) 1f else 0f)) },
                )
            }
        }
    }
}

/**
 * The floating + (§3.6): on Budget and Activity while their root is on screen -- alone, or beside
 * an open detail at two columns -- and nowhere else, as on the phone.
 */
internal fun showsFloatingAdd(state: UiState, wc: WidthClass): Boolean {
    val root = state.routes.firstOrNull() as? Route.TabRoute ?: return false
    if (state.inOnboardingPage || state.data == null || root.tab !in listOf(Tab.BUDGET, Tab.ACTIVITY)) return false
    return state.routes.size == 1 || (wc != WidthClass.COMPACT && root.tab == Tab.ACTIVITY && shownDetail(state) != null)
}

/** The floating + (phone only, §3.6): the last area and target used. A desktop's rows carry their own +, and Ctrl+N. */
@Composable
private fun BoxScope.FloatingAddOver(state: UiState, store: AppStore, wc: WidthClass) {
    if (wc != WidthClass.COMPACT || !showsFloatingAdd(state, wc) || goalPromptShows(state)) return
    val t = LocalTokens.current
    FloatingAdd(Modifier.align(Alignment.BottomEnd).padding(end = t.spacing.pad, bottom = t.spacing.pad)) { store.openSheet(Sheet.Entry()) }
}
