package app.scifin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.scifin.app.AppStore
import app.scifin.app.UiState
import app.scifin.notify.NotificationPermission
import app.scifin.ui.LocalTokens
import app.scifin.ui.components.Button
import app.scifin.ui.components.Page
import app.scifin.i18n.tr

/* §11.1's steps 2 and 4; step 3 is the tutorial (TutorialOverlay.kt). */

private val PERMISSION_TITLE: String get() = tr("One more thing before the tour")
private val PERMISSION_BODY: String get() = tr("Sci-Fin can send a single, quiet notification on the 1st of each month — nothing else, ever, unless you choose to turn more on later.")
private val CURRENCY_LEAD: String get() = tr("What currency should amounts show in? Change this — or anything else — anytime in Settings.")
private val CONTINUE_TO_APP: String get() = tr("Continue to Sci-Fin")

/**
 * §11.1 step 2: explain, then ask. Continue requests the permission where the OS has a prompt
 * (its answer moves on); otherwise Continue moves on.
 */
@Composable
fun PermissionStepScreen(state: UiState, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val canPrompt = state.notificationPermission == NotificationPermission.NOT_ASKED
    Column(Modifier.fillMaxSize().padding(t.spacing.pad), verticalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Column(verticalArrangement = Arrangement.spacedBy(t.spacing.u * 1.25f)) {
                Text(PERMISSION_TITLE, color = p.txt, fontSize = t.type.title, fontWeight = FontWeight.Bold, lineHeight = t.type.title * 1.15f)
                Text(PERMISSION_BODY, color = p.txt2, fontSize = t.type.body, lineHeight = t.type.body * 1.6f)
            }
        }
        Button(tr("Continue"), modifier = Modifier.fillMaxWidth()) { if (canPrompt) store.requestNotificationPermissionAndContinue() else store.advanceOnboarding() }
    }
}

/** Step 4: the real Appearance & accessibility screen, the currency sentence above it, "Continue to Sci-Fin" below. */
@Composable
fun CurrencyLandingScreen(state: UiState, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val d = state.data ?: return
    Page(null) {
        Text(CURRENCY_LEAD, color = p.txt2, fontSize = t.type.body, lineHeight = t.type.body * 1.6f, modifier = Modifier.padding(bottom = t.spacing.gap * 1.4f))
        AppearancePage(d, store, currencyFirst = true)
        Button(CONTINUE_TO_APP, modifier = Modifier.fillMaxWidth().padding(top = t.spacing.pad)) { store.completeOnboarding() }
    }
}
