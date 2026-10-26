package app.scifin.ui.sheets

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.scifin.app.AppStore
import app.scifin.engine.Writes
import app.scifin.engine.savingsAmount
import app.scifin.i18n.typedWordMatches
import app.scifin.i18n.tr
import app.scifin.i18n.trf
import app.scifin.model.ProfileData
import app.scifin.ui.LocalTokens
import app.scifin.ui.components.Button
import app.scifin.ui.components.ButtonKind
import app.scifin.ui.components.ButtonRow
import app.scifin.ui.components.Caption
import app.scifin.ui.components.FieldGroup
import app.scifin.ui.components.FieldLabel
import app.scifin.ui.components.InfoNote
import app.scifin.ui.components.SheetScaffold
import app.scifin.ui.components.TextField
import app.scifin.ui.currencySymbol
import app.scifin.ui.formatMoney
import app.scifin.ui.moneyText
import app.scifin.ui.parseMoney

/**
 * §3.6's Savings full editor: sets `Savings.amount` directly, behind the typed word UPDATE --
 * a heavier confirmation than Confirm/Cancel, chosen because a word is harder to type by
 * accident than a button is to miss-tap. It logs the signed delta, never the new total.
 */
@Composable
fun SavingsEditorSheet(d: ProfileData, store: AppStore) {
    val t = LocalTokens.current
    val cur = d.settings.currency
    var value by remember { mutableStateOf(d.savings.amount?.let { moneyText(it) } ?: "") }
    var typed by remember { mutableStateOf("") }
    val parsed = parseMoney(value)
    val delta = parsed?.let { it - d.savingsAmount }
    val word = tr("UPDATE")   // the word to type, in the reader's language; the prompt names the same word
    val ready = typedWordMatches(typed, "UPDATE") && parsed != null

    SheetScaffold(title = tr("Savings"), onDismiss = store::closeSheet) {
        InfoNote(tr("Here you can set or correct the Savings figure directly. To log money in or out, use the + button."), Modifier.padding(bottom = t.spacing.gap * 1.2f))
        FieldGroup { FieldLabel(tr("Savings amount")); TextField(value, { value = it }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = tr("Savings amount")) }
        Caption(
            when {
                delta == null -> tr("Enter the amount.")
                delta == 0L -> tr("No change yet.")
                else -> trf("Logs a {0}{1} correction: the difference between the new figure and the current one.", if (delta > 0) "+" else "−", formatMoney(if (delta < 0) -delta else delta, cur))
            },
            Modifier.padding(start = t.spacing.u * 0.25f, bottom = t.spacing.u * 1.25f),
        )
        FieldGroup { FieldLabel(trf("Type {0} to confirm", word)); TextField(typed, { typed = it }, placeholder = word, label = trf("Type {0} to confirm", word)) }
        ButtonRow {
            Button(tr("Cancel"), ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.closeSheet() }
            Button(tr("Save"), if (ready) ButtonKind.PRIMARY else ButtonKind.SECONDARY, enabled = ready, modifier = Modifier.weight(1f)) {
                val v = parsed ?: return@Button
                store.dispatch { p -> Writes.savingsDirectEdit(p, v, null, store.clock, store.newId()) }
                store.closeSheet()
            }
        }
    }
}

