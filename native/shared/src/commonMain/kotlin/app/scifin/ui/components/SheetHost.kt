package app.scifin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.scifin.i18n.tr
import app.scifin.ui.SCRIM
import app.scifin.ui.LocalTokens
import app.scifin.ui.LocalWidthClass
import app.scifin.ui.WidthClass

/**
 * §4.8 rule 3: the same form is a bottom sheet below 600dp and a centred dialog at 600dp and
 * above. The scrim is the prototype's rgba(10,10,14,.42); tapping it is [onDismiss] unless the
 * form says it cannot be dismissed (§11.1's first screen).
 *
 * Taps are taken with pointer input, not `clickable`, so the sheet's text is not merged into one
 * unlabelled button for a screen reader; the scrim offers its dismissal as a labelled action.
 */
@Composable
fun SheetScaffold(title: String?, onDismiss: (() -> Unit)?, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    val wide = LocalWidthClass.current != WidthClass.COMPACT
    val dismiss by rememberUpdatedState(onDismiss)
    val dismissable = onDismiss != null
    BoxWithConstraints(
        Modifier.fillMaxSize().background(SCRIM.copy(alpha = 0.42f))
            .pointerInput(dismissable) { if (dismissable) detectTapGestures { dismiss?.invoke() } }
            .semantics { if (dismissable) { role = Role.Button; onClick(label = tr("Close")) { dismiss?.invoke(); true } } },
        contentAlignment = if (wide) Alignment.Center else Alignment.BottomCenter,
    ) {
        val shape = if (wide) RoundedCornerShape(t.radii.xl) else RoundedCornerShape(topStart = t.radii.sheet, topEnd = t.radii.sheet)
        // `max-height` in the prototype: as tall as the content, capped at 86 % / 88 % of the screen.
        val cap = maxHeight * (if (wide) 0.86f else 0.88f)
        Column(
            Modifier
                .let { if (wide) it.widthIn(max = t.spacing.u * 56) else it.fillMaxWidth() }
                .heightIn(max = cap)
                .background(p.bg, shape)
                // a tap on the sheet itself never reaches the scrim
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics { if (title != null) paneTitle = title }
                .padding(start = t.spacing.pad, end = t.spacing.pad, top = t.spacing.u * 2, bottom = t.spacing.u * 2.5f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
        ) {
            // §3.6: a sheet's action sits at the title's height, at its end
            if (action != null) Row(Modifier.fillMaxWidth().padding(bottom = t.spacing.u * 0.5f), verticalAlignment = Alignment.CenterVertically) {
                Text(title ?: "", color = p.txt, fontSize = t.type.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).semantics { if (title != null) heading() })
                action()
            }
            else if (title != null) Text(title, color = p.txt, fontSize = t.type.title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = t.spacing.u * 1.5f).semantics { heading() })
            content()
        }
    }
}
