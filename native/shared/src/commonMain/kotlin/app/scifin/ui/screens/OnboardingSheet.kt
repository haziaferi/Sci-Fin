package app.scifin.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.UiState
import app.scifin.model.Theme
import app.scifin.ui.LocalTokens
import app.scifin.ui.components.Button
import app.scifin.ui.components.ButtonKind
import app.scifin.ui.components.FeasibilityNote
import app.scifin.ui.components.SheetScaffold
import app.scifin.ui.components.TextField
import app.scifin.ui.components.ThemeGrid
import app.scifin.i18n.tr

/*
 * §11.1 screen 1: a sheet over the app with no dismiss (the screen has no skip); Continue needs
 * a name. The theme cards wrap to two rows rather than shrink below the person's text size.
 */

private val ONBOARDING_TITLE: String get() = tr("Sci-Fin stands for Scientific-Finance")
private val ONBOARDING_DESCRIPTION: String get() = tr("This app grows out of scientific research on how we perceive money. Its structure centres on saving.")
private val ONBOARDING_NAME_LABEL: String get() = tr("Name this profile")
private val ONBOARDING_NAME_PLACEHOLDER: String get() = tr("Your name or a label")
private val ONBOARDING_THEME_LABEL: String get() = tr("Choose a theme:")
private val ONBOARDING_THEME_CAPTION: String get() = tr("You can always change it in Settings.")
private val ONBOARDING_RESTORE: String get() = tr("Restore a backup")
private val DAMAGED_TITLE: String get() = tr("Sci-Fin can’t read its data")
private val DAMAGED_BODY: String get() = tr("The file has been kept as a copy.")
private val DAMAGED_RESTORE: String get() = tr("Restore a backup")
private val DAMAGED_FRESH: String get() = tr("Start fresh")

@Composable
fun OnboardingSheet(route: Route.Onboarding, state: UiState, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    var name by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf(Theme.SWISS_BLUE) }
    val ready = name.isNotBlank()

    // §4.7's review takes the sheet over while a file is pending: the same card, the same note
    // and the same Confirm/Cancel pair the Settings route shows, so nothing is decided twice.
    // A file with a password asks for it first (§4.7), in the same sheet.
    if (state.pendingBackup != null || state.lockedBackup != null) {
        SheetScaffold(title = tr("Restore a backup"), onDismiss = null) { BackupOpen(state, store) }
        return
    }

    // §12: the store could not be read and was moved aside before anything opened it; shown
    // before screen 1, never over a Profile.
    if (state.damagedCopy != null && !route.forNewProfile) {
        SheetScaffold(title = DAMAGED_TITLE, onDismiss = null) {
            Column(verticalArrangement = Arrangement.spacedBy(t.spacing.gap * 1.5f)) {
                Text(DAMAGED_BODY, color = p.txt2, fontSize = t.type.body, lineHeight = t.type.body * 1.5f)
                Button(DAMAGED_RESTORE, modifier = Modifier.fillMaxWidth()) { store.pickBackup() }
                Button(DAMAGED_FRESH, ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth()) { store.startFresh() }
                state.backupError?.let { FeasibilityNote(it) }
            }
        }
        return
    }

    SheetScaffold(title = null, onDismiss = null) {
        // §2.5's screen factor, from the window (the tokens' own); `t.type` does not carry it, spacing does
        val k = t.screenScale
        val gap = t.spacing.gap
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap * 1.5f)) {
            Text(ONBOARDING_TITLE, color = p.txt, fontSize = t.type.title * k, fontWeight = FontWeight.Bold, lineHeight = t.type.title * k * 1.15f)
            Text(ONBOARDING_DESCRIPTION, color = p.txt2, fontSize = t.type.body * k, lineHeight = t.type.body * k * 1.5f)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Text(ONBOARDING_NAME_LABEL, color = p.txt, fontSize = t.type.body * k, fontWeight = FontWeight.Bold)
                TextField(name, { name = it }, placeholder = ONBOARDING_NAME_PLACEHOLDER)
            }
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                // heading and caption as one run of text, so the caption follows the heading and wraps as prose does
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = p.txt, fontSize = t.type.body * k, fontWeight = FontWeight.Bold)) { append(ONBOARDING_THEME_LABEL) }
                        append(" ")
                        withStyle(SpanStyle(color = p.txt2, fontSize = t.type.caption * k)) { append(ONBOARDING_THEME_CAPTION) }
                    },
                    lineHeight = t.type.body * k * 1.3f,
                )
                ThemeGrid(selected = theme) { theme = it; store.previewTheme(it) }
            }
            Button(tr("Continue"), enabled = ready, modifier = Modifier.fillMaxWidth()) { store.createProfile(name, theme, route.forNewProfile) }
            // §11.1: restore is offered here, not when adding a second Profile, where it would replace the pool in use
            if (!route.forNewProfile) {
                Text(
                    ONBOARDING_RESTORE,
                    color = p.txt2, fontSize = t.type.body * k, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = t.spacing.target)
                        .clickable(role = Role.Button) { store.pickBackup() }
                        .wrapContentHeight(Alignment.CenterVertically),
                    textAlign = TextAlign.Center,
                )
                // a refused file has to say so here: there is no Settings screen to say it on yet
                state.backupError?.let { FeasibilityNote(it) }
            }
        }
    }
}

