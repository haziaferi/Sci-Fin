package app.scifin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import app.scifin.app.AppStore
import app.scifin.app.Sheet
import app.scifin.engine.Hourly
import app.scifin.engine.Income
import app.scifin.model.IncomeType
import app.scifin.model.ProfileData
import app.scifin.model.Shift
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.LucideIcon
import app.scifin.ui.components.Button
import app.scifin.ui.components.ButtonKind
import app.scifin.ui.components.ButtonRow
import app.scifin.ui.components.Caption
import app.scifin.ui.components.DateField
import app.scifin.ui.components.FieldGroup
import app.scifin.ui.components.FieldLabel
import app.scifin.ui.components.LedgerGroup
import app.scifin.ui.components.LedgerLabel
import app.scifin.ui.components.LedgerRow
import app.scifin.ui.components.SheetScaffold
import app.scifin.ui.components.TextField
import app.scifin.ui.components.probeAs
import app.scifin.ui.currencySymbol
import app.scifin.ui.formatDate
import app.scifin.ui.formatMoney
import app.scifin.ui.moneyText
import app.scifin.ui.parseMoney
import kotlinx.datetime.LocalDate

/*
 * §3.10's hourly income on screen (owner, 2026-10-26): Budget's Income section with this period's shifts, the
 * payday prompt that waits on Budget until it is answered, and the two sheets -- one shift, and settling the
 * logged hours when leaving hourly.
 */

/** "14.5 h", "6 h": a whole number of hours drops its decimals. */
fun hoursShort(h: Double): String = (if (h % 1.0 == 0.0) h.toLong().toString() else h.toString()) + " h"

/** Hours typed with a comma or a point; zero, negative or unreadable is no figure. */
fun parseHoursText(v: String): Double? = v.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

private fun hoursText(h: Double): String = if (h % 1.0 == 0.0) h.toLong().toString() else h.toString()

/** What the estimate is, in words the person can check against the payslip. */
private fun estimateWord(beforeDeductions: Boolean) = if (beforeDeductions) "before deductions" else "from your recent paydays"

/** Budget's Income section while Income is hourly: the rate, what has arrived this month, and the shifts not yet paid. */
@Composable
fun HourlyIncomeSection(d: ProfileData, store: AppStore, today: LocalDate) {
    val t = LocalTokens.current; val p = t.palette
    val s = d.settings
    val cur = s.currency
    LedgerRow(
        onClick = null,
        name = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u / 2)) {
                LucideIcon(Lucide.BANKNOTE, tint = p.txt2, size = t.icons.sm)
                LedgerLabel("Per hour (paid ${cadenceLabel(s.incomeCadence).lowercase()})")
            }
        },
        figure = formatMoney(s.hourlyRate, cur), probeName = "income",
        under = { Caption("${formatMoney(Income.receivedThisMonth(d, today), cur)} received so far this month", Modifier.padding(top = t.spacing.u * 0.5f)) },
    )
    val shifts = Hourly.pending(d)
    val next = Hourly.nextPayday(s, today)
    if (shifts.isEmpty()) {
        Caption("No hours logged since the last payday." + (next?.let { " Next payday: ${formatDate(it)}." } ?: ""))
    } else {
        val (est, before) = Hourly.estimate(d, shifts)
        Caption("${hoursShort(shifts.sumOf { it.hours })} logged" + (next?.let { " for the payday on ${formatDate(it)}" } ?: "") + " · about ${formatMoney(est, cur)} ${estimateWord(before)}", Modifier.probeAs("shift-summary"))
        LedgerGroup {
            for (sh in shifts) LedgerRow(
                onClick = { store.openSheet(Sheet.EditShift(sh.id)) },
                name = { LedgerLabel(formatDate(sh.date)) },
                figure = hoursShort(sh.hours),
                under = { Caption("at ${formatMoney(sh.rate, cur)} an hour") },
                clickLabel = "Edit this shift",
            )
        }
    }
    Button("Log hours", ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth().probeAs("log-hours")) { store.openSheet(Sheet.Entry("savings", income = true)) }
}

/**
 * The payday prompt (owner, 2026-10-26): it waits on Budget until answered, pre-filled with the estimate, and nothing
 * lands until the person confirms what actually arrived.
 */
@Composable
fun PaydayCard(d: ProfileData, store: AppStore, today: LocalDate) {
    val t = LocalTokens.current; val p = t.palette
    val payday = Hourly.duePayday(d, today) ?: return
    val cur = d.settings.currency
    val (est, before) = Hourly.estimate(d, payday.shifts)
    var arrivedText by remember(payday.date) { mutableStateOf(if (payday.shifts.isNotEmpty()) moneyText(est) else "") }
    val arrived = parseMoney(arrivedText)?.takeIf { it > 0 }
    Column(
        Modifier.fillMaxWidth().background(p.surf, RoundedCornerShape(t.radii.lg)).padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f).probeAs("payday-card"),
        verticalArrangement = Arrangement.spacedBy(t.spacing.u * 0.75f),
    ) {
        Text(
            "Payday, ${formatDate(payday.date)} — " + if (payday.shifts.isEmpty()) "no hours logged. Did anything arrive?" else "${hoursShort(payday.hours)} logged. What arrived?",
            color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold,
        )
        TextField(arrivedText, { arrivedText = it }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = "What arrived")
        if (payday.shifts.isNotEmpty()) Caption("Estimated at ${formatMoney(est, cur)}, ${estimateWord(before)}. Change it to what actually arrived.")
        ButtonRow {
            Button("Nothing this time", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.nothingThisTime() }
            Button("Confirm", enabled = arrived != null, modifier = Modifier.weight(1f)) { arrived?.let { store.confirmPayday(it) } }
        }
        if (payday.shifts.isNotEmpty()) Caption("“Nothing this time” keeps the hours logged for the next payday.")
    }
}

/** One shift, before the payday that would pay it: hours, rate and day can change, or it can go. */
@Composable
fun EditShiftSheet(sheet: Sheet.EditShift, d: ProfileData, store: AppStore) {
    val shift = d.shifts.firstOrNull { it.id == sheet.id } ?: run { store.closeSheet(); return }
    val cur = d.settings.currency
    var hoursText by rememberSaveable { mutableStateOf(hoursText(shift.hours)) }
    var rateText by rememberSaveable { mutableStateOf(moneyText(shift.rate)) }
    var date by remember { mutableStateOf(shift.date) }
    val hours = parseHoursText(hoursText)
    val rate = parseMoney(rateText)?.takeIf { it > 0 }
    SheetScaffold(title = "Shift", onDismiss = store::closeSheet) {
        FieldGroup { FieldLabel("Hours"); TextField(hoursText, { hoursText = it }, prefix = "#", keyboard = KeyboardType.Decimal, label = "Hours") }
        FieldGroup { FieldLabel("Per hour"); TextField(rateText, { rateText = it }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = "Per hour") }
        FieldGroup { FieldLabel("Worked on"); DateField(date) { date = it } }
        ButtonRow {
            Button("Remove", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.removeShift(shift.id); store.closeSheet() }
            Button("Save", enabled = hours != null && rate != null, modifier = Modifier.weight(1f)) {
                store.editShift(Shift(shift.id, shift.profileId, date, hours!!, rate!!)); store.closeSheet()
            }
        }
    }
}

/**
 * Leaving hourly -- for salaried, or turning Income off -- with hours still logged (owner, 2026-10-26): confirm what
 * arrived for them now, or discard them. Nothing is left pending under a type that no longer uses it.
 */
@Composable
fun SettleShiftsSheet(sheet: Sheet.SettleShifts, d: ProfileData, store: AppStore) {
    val shifts = Hourly.pending(d)
    val cur = d.settings.currency
    val (est, before) = Hourly.estimate(d, shifts)
    var arrivedText by rememberSaveable { mutableStateOf(moneyText(est)) }
    val arrived = parseMoney(arrivedText)?.takeIf { it > 0 }
    val leaving = if (sheet.next == IncomeType.SALARIED) "Switching to salaried" else "Turning Income off"
    SheetScaffold(title = "Hours still logged", onDismiss = store::closeSheet) {
        Caption("$leaving leaves ${hoursShort(shifts.sumOf { it.hours })} with no payday to count them. Confirm what arrived for them now, dated today, or discard them.")
        FieldGroup {
            FieldLabel("What arrived")
            TextField(arrivedText, { arrivedText = it }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = "What arrived")
            Caption("Estimated at ${formatMoney(est, cur)}, ${estimateWord(before)}.", Modifier.padding(top = LocalTokens.current.spacing.u * 0.75f))
        }
        ButtonRow {
            Button("Discard the hours", ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.settleShifts(sheet.next, null) }
            Button("Confirm", enabled = arrived != null, modifier = Modifier.weight(1f)) { store.settleShifts(sheet.next, arrived) }
        }
    }
}
