package app.scifin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.scifin.app.AppStore
import app.scifin.app.Sheet
import app.scifin.i18n.tr
import app.scifin.model.ProfileData
import app.scifin.ui.LocalTokens
import app.scifin.ui.LocalWidthClass
import app.scifin.ui.Lucide
import app.scifin.ui.LucideIcon
import app.scifin.ui.WidthClass
import app.scifin.ui.formatMoney
import app.scifin.ui.tutAnchor

/**
 * §3.6 / §3.7: one component on Budget → Envelopes and on Activity, because "the same number read in two places"
 * is only true if the affordances match. A tap on the row opens the full editor (§3.6); logging money in is the floating + on a phone and the row's own + on a desktop. While `Savings.amount` is null the
 * figure is an em dash (§3.1) and §11.7's note sits beneath.
 */
@Composable
fun SavingsRow(d: ProfileData, store: AppStore) {
    val t = LocalTokens.current
    val unset = d.savings.amount == null
    Column(Modifier.tutAnchor("savings-row"), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
        SavingsLedgerRow(
            if (unset) "—" else formatMoney(d.savings.amount, d.settings.currency),
            onClick = { store.openSheet(Sheet.SavingsEditor) }, clickLabel = tr("Edit Savings directly"),
            // §3.6: a desktop's Savings row has its own + (Add / Income); a phone has the floating +
            slot = if (LocalWidthClass.current != WidthClass.COMPACT)
                ({ RowAdd(tr("Add to Savings")) { store.openSheet(Sheet.Entry("savings")) } }) else null,
        )
        if (unset) PlainNote(tr("The app’s core functions are based on this figure. Enter your current savings to set it."))
    }
}

/** A quiet note that cannot be tapped: the info glyph and a sentence on the page ground, no fill. */
@Composable
fun PlainNote(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val t = LocalTokens.current; val p = t.palette
    Row(modifier.fillMaxWidth().padding(horizontal = t.spacing.u * 0.25f), verticalAlignment = if (trailing != null) Alignment.CenterVertically else Alignment.Top, horizontalArrangement = Arrangement.spacedBy(t.spacing.u)) {
        LucideIcon(Lucide.INFO, tint = p.txt2, size = t.icons.sm, modifier = if (trailing != null) Modifier else Modifier.padding(top = t.spacing.u * 0.25f))
        Text(text, color = p.txt2, fontSize = t.type.caption, lineHeight = t.type.caption * 1.45, modifier = Modifier.weight(1f, fill = trailing == null).measureCap(t))
        trailing?.invoke()
    }
}
