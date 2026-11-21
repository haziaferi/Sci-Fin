package app.scifin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.scifin.ui.LocalTokens

/*
 * The notice (owner, 2026-11-21): one line at the foot of the screen for a few seconds, with one action. It is the
 * app's only one. Everywhere else a consequence is read before it happens (DeleteSheets); Income's switch is the
 * exception because turning it off clears a whole setup in one tap. What the notice offers stays in Settings ›
 * Trash, so a notice missed, or gone before it was read, loses nothing.
 */

/** How long a notice stays: about five seconds (owner, 2026-11-21). */
const val NOTICE_MILLIS = 5_000L

/** Drawn in the text colour with the ground's colour on it, so it reads as apart from the page in either mode. */
@Composable
fun NoticeBar(text: String, action: String, modifier: Modifier = Modifier, onAction: () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    Row(
        modifier.probeAs("notice").widthIn(max = t.spacing.u * 60).fillMaxWidth()
            .background(p.txt, RoundedCornerShape(t.radii.lg))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = t.spacing.pad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = p.bg, fontSize = t.type.body, modifier = Modifier.weight(1f).padding(vertical = t.spacing.u * 1.5f))
        Box(
            Modifier.probeAs("notice-action").heightIn(min = t.spacing.target).clickable(onClick = onAction)
                .semantics { role = Role.Button }.padding(horizontal = t.spacing.pad),
            contentAlignment = Alignment.Center,
        ) {
            Text(action, color = p.bg, fontSize = t.type.body, fontWeight = FontWeight.Bold)
        }
    }
}
