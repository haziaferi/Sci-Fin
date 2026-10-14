package app.scifin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.SettingsPage
import app.scifin.app.Tab
import app.scifin.app.UiState
import app.scifin.ui.components.columnsIn
import app.scifin.ui.screens.LEARN_TOPICS

/*
 * §4.8's second column for Activity, Learn and Settings: when two measure-wide columns fit, the
 * root stays left and the screens it would push open right: Activity rests on the activity log,
 * Learn on the first topic's text, Settings on Appearance. Each is another view of the same
 * destination.
 */

/** True inside a destination root that has its second column beside it: rows open there, not over the page. */
val LocalSecondColumn = compositionLocalOf { false }

/** True for the second column's resting view, which has nothing to go back to. */
val LocalResting = compositionLocalOf { false }

/** The three destinations that gained a second column, and what each shows until something is chosen. */
fun restingDetail(tab: Tab): Route? = when (tab) {
    Tab.ACTIVITY -> Route.Ledger
    Tab.LEARN -> Route.LearnTopic(LEARN_TOPICS.first().id)
    Tab.SETTINGS -> Route.Settings(SettingsPage.APPEARANCE)
    else -> null
}

/** What the second column shows now: the chosen screen, or the resting one. */
fun shownDetail(state: UiState): Route? {
    val tab = (state.routes.first() as? Route.TabRoute)?.tab ?: return null
    val resting = restingDetail(tab) ?: return null
    return state.routes.drop(1).lastOrNull { it !is Route.Onboarding } ?: resting
}

/** Opens [route] where this destination shows its screens: beside the root at two columns, over it otherwise. */
@Composable
fun opener(store: AppStore): (Route) -> Unit {
    val beside = LocalSecondColumn.current
    return { r -> if (beside) store.openDetail(r) else store.push(r) }
}

/** The chosen row, marked as the rail marks the current tab: a 12 % accent wash. */
@Composable
fun selectedWash(on: Boolean): Color? = if (on) LocalTokens.current.palette.acc.copy(alpha = 0.12f) else null

/** Lays out [root] and [detail] as two measure-wide columns, centred, when the width allows; [single] otherwise. */
@Composable
fun TwoColumns(root: @Composable () -> Unit, detail: @Composable () -> Unit, single: @Composable () -> Unit) {
    val t = LocalTokens.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (columnsIn(maxWidth - t.spacing.pad * 2, t.measure, t.spacing.gap) < 2) { single(); return@BoxWithConstraints }
        val col = t.measure + t.spacing.pad * 2
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            // each column is a Page with its own padding, so the two paddings are folded into §2.5's gap
            Row(horizontalArrangement = Arrangement.spacedBy(t.spacing.gap - t.spacing.pad * 2)) {
                Box(Modifier.width(col).fillMaxHeight()) { CompositionLocalProvider(LocalSecondColumn provides true) { root() } }
                Box(Modifier.width(col).fillMaxHeight()) { detail() }
            }
        }
    }
}
