@file:OptIn(ExperimentalMaterial3Api::class)

package app.scifin.ui.components

import app.scifin.i18n.I18n
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.CalendarLocale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.scifin.i18n.tr
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.LucideIcon
import app.scifin.ui.formatDateFull
import kotlinx.datetime.LocalDate
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerFormatter

private const val DAY_MS = 86_400_000L

/** A date control in the prototype's `.field` shape; tapping opens the platform-style picker (the prototype uses the browser's). */
@Composable
fun DateField(value: LocalDate?, placeholder: String = "", onChange: (LocalDate) -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = t.spacing.target).background(p.surf, RoundedCornerShape(t.radii.md))
            .border(t.strokes.ctl, p.bordStrong, RoundedCornerShape(t.radii.md)).clickable(role = Role.Button, onClickLabel = tr("Select date")) { open = true }.padding(horizontal = t.spacing.u * 1.5f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(value?.let { formatDateFull(it) } ?: placeholder, color = if (value != null) p.txt else p.txt3, fontSize = t.type.body)
        LucideIcon(Lucide.CALENDAR, tint = p.txt2, size = t.spacing.u * 2)
    }
    if (open) {
        // in the app's language, not the device's (§2.7): one language per screen
        val state = remember(I18n.current) { DatePickerState(locale = appCalendarLocale(), initialSelectedDateMillis = value?.let { it.toEpochDays() * DAY_MS }) }
        AppLocaleScope { DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onChange(LocalDate.fromEpochDays(it / DAY_MS)) }; open = false }) { Text(tr("Done")) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(tr("Cancel")) } },
        ) { DatePicker(state = state, dateFormatter = remember(I18n.current) { appFormatter() }, title = { Text(tr("Select date"), modifier = Modifier.padding(start = t.spacing.u * 3, top = t.spacing.u * 2)) }) } }
    }
}

/** The app's language as a calendar locale (§2.7), for the picker's month grid -- not the device's. */
expect fun appCalendarLocale(): CalendarLocale

/** Runs [content] under the app's language where the platform has a configuration for it (Android); a pass-through elsewhere. */
@Composable
expect fun AppLocaleScope(content: @Composable () -> Unit)

/** Material's formatter, with the platform locale it is handed swapped for the app's (the headline and the month/year line). */
private fun appFormatter(): DatePickerFormatter {
    val base = DatePickerDefaults.dateFormatter()
    val en = appCalendarLocale()
    return object : DatePickerFormatter {
        override fun formatDate(dateMillis: Long?, locale: CalendarLocale, forContentDescription: Boolean) = base.formatDate(dateMillis, en, forContentDescription)
        override fun formatMonthYear(monthMillis: Long?, locale: CalendarLocale) = base.formatMonthYear(monthMillis, en)
    }
}
