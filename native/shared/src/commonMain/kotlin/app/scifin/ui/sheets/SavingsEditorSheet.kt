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
    val ready = typed.trim().uppercase() == "UPDATE" && parsed != null

    SheetScaffold(title = "Savings", onDismiss = store::closeSheet) {
        InfoNote("This sets the figure directly. Recording something that happened is what the + buttons are for — this is for corrections.", Modifier.padding(bottom = t.spacing.gap * 1.2f))
        FieldGroup { FieldLabel("Savings amount"); TextField(value, { value = it }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = "Savings amount") }
        Caption(
            when {
                delta == null -> "Enter the amount."
                delta == 0L -> "No change yet."
                else -> "Logs a ${if (delta > 0) "+" else "−"}${formatMoney(if (delta < 0) -delta else delta, cur)} correction — the signed difference, never the new total."
            },
            Modifier.padding(start = t.spacing.u * 0.25f, bottom = t.spacing.u * 1.25f),
        )
        FieldGroup { FieldLabel("Type UPDATE to confirm"); TextField(typed, { typed = it }, placeholder = "UPDATE", label = "Type UPDATE to confirm") }
        ButtonRow {
            Button("Cancel", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.closeSheet() }
            Button("Save", if (ready) ButtonKind.PRIMARY else ButtonKind.SECONDARY, enabled = ready, modifier = Modifier.weight(1f)) {
                val v = parsed ?: return@Button
                store.dispatch { p -> Writes.savingsDirectEdit(p, v, null, store.clock, store.newId()) }
                store.closeSheet()
            }
        }
    }
}

