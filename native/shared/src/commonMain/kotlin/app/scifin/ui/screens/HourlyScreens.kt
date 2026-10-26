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
import app.scifin.i18n.tr
import app.scifin.i18n.trf
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
fun hoursShort(h: Double): String = trf("{0} h", if (h % 1.0 == 0.0) h.toLong().toString() else h.toString())

/** Hours typed with a comma or a point; zero, negative or unreadable is no figure. */
fun parseHoursText(v: String): Double? = v.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

private fun hoursText(h: Double): String = if (h % 1.0 == 0.0) h.toLong().toString() else h.toString()

/** What the estimate is, in words the person can check against the payslip. */
private fun estimateWord(beforeDeductions: Boolean) = if (beforeDeductions) tr("before deductions") else tr("from your recent paydays")

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
                LedgerLabel(trf("Per hour (paid {0})", cadenceLabel(s.incomeCadence).lowercase()))
            }
        },
        figure = formatMoney(s.hourlyRate, cur), probeName = "income",
        under = { Caption(trf("{0} received so far this month", formatMoney(Income.receivedThisMonth(d, today), cur)), Modifier.padding(top = t.spacing.u * 0.5f)) },
    )
    val shifts = Hourly.pending(d)
    val next = Hourly.nextPayday(s, today)
    if (shifts.isEmpty()) {
        Caption(tr("No hours logged since the last payday.") + (next?.let { " " + trf("Next payday: {0}.", formatDate(it)) } ?: ""))
    } else {
        val (est, before) = Hourly.estimate(d, shifts)
        Caption(
            next?.let { day -> trf("{0} logged for the payday on {1} · about {2} {3}", hoursShort(shifts.sumOf { it.hours }), formatDate(day), formatMoney(est, cur), estimateWord(before)) }
                ?: trf("{0} logged · about {1} {2}", hoursShort(shifts.sumOf { it.hours }), formatMoney(est, cur), estimateWord(before)),
            Modifier.probeAs("shift-summary"),
        )
        LedgerGroup {
            for (sh in shifts) LedgerRow(
                onClick = { store.openSheet(Sheet.EditShift(sh.id)) },
                name = { LedgerLabel(formatDate(sh.date)) },
                figure = hoursShort(sh.hours),
                under = { Caption(trf("at {0} an hour", formatMoney(sh.rate, cur))) },
                clickLabel = tr("Edit this shift"),
            )
        }
    }
    Button(tr("Log hours"), ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth().probeAs("log-hours")) { store.openSheet(Sheet.Entry("savings", income = true)) }
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
            if (payday.shifts.isEmpty()) trf("Payday, {0} — no hours logged. Did anything arrive?", formatDate(payday.date))
            else trf("Payday, {0} — {1} logged. What arrived?", formatDate(payday.date), hoursShort(payday.hours)),
            color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold,
        )
        TextField(arrivedText, { arrivedText = it }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = tr("What arrived"))
        if (payday.shifts.isNotEmpty()) Caption(trf("Estimated at {0}, {1}. Change it to what actually arrived.", formatMoney(est, cur), estimateWord(before)))
        ButtonRow {
            Button(tr("Nothing this time"), ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.nothingThisTime() }
            Button(tr("Confirm"), enabled = arrived != null, modifier = Modifier.weight(1f)) { arrived?.let { store.confirmPayday(it) } }
        }
        if (payday.shifts.isNotEmpty()) Caption(tr("“Nothing this time” keeps the hours logged for the next payday."))
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
    SheetScaffold(title = tr("Shift"), onDismiss = store::closeSheet) {
        FieldGroup { FieldLabel(tr("Hours")); TextField(hoursText, { hoursText = it }, prefix = "#", keyboard = KeyboardType.Decimal, label = tr("Hours")) }
        FieldGroup { FieldLabel(tr("Per hour")); TextField(rateText, { rateText = it }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = tr("Per hour")) }
        FieldGroup { FieldLabel(tr("Worked on")); DateField(date) { date = it } }
        ButtonRow {
            Button(tr("Remove"), ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.removeShift(shift.id); store.closeSheet() }
            Button(tr("Save"), enabled = hours != null && rate != null, modifier = Modifier.weight(1f)) {
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
    val logged = hoursShort(shifts.sumOf { it.hours })
    SheetScaffold(title = tr("Hours still logged"), onDismiss = store::closeSheet) {
        Caption(
            if (sheet.next == IncomeType.SALARIED) trf("Switching to salaried leaves {0} with no payday to count them. Confirm what arrived for them now, dated today, or discard them.", logged)
            else trf("Turning Income off leaves {0} with no payday to count them. Confirm what arrived for them now, dated today, or discard them.", logged),
        )
        FieldGroup {
            FieldLabel(tr("What arrived"))
            TextField(arrivedText, { arrivedText = it }, prefix = currencySymbol(cur), keyboard = KeyboardType.Decimal, label = tr("What arrived"))
            Caption(trf("Estimated at {0}, {1}.", formatMoney(est, cur), estimateWord(before)), Modifier.padding(top = LocalTokens.current.spacing.u * 0.75f))
        }
        ButtonRow {
            Button(tr("Discard the hours"), ButtonKind.GHOST, modifier = Modifier.weight(1f)) { store.settleShifts(sheet.next, null) }
            Button(tr("Confirm"), enabled = arrived != null, modifier = Modifier.weight(1f)) { store.settleShifts(sheet.next, arrived) }
        }
    }
}
