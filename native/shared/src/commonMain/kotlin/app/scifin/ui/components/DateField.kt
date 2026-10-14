@file:OptIn(ExperimentalMaterial3Api::class)

package app.scifin.ui.components

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
            .border(t.strokes.ctl, p.bordStrong, RoundedCornerShape(t.radii.md)).clickable(role = Role.Button, onClickLabel = "Select date") { open = true }.padding(horizontal = t.spacing.u * 1.5f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(value?.let { formatDateFull(it) } ?: placeholder, color = if (value != null) p.txt else p.txt3, fontSize = t.type.body)
        LucideIcon(Lucide.CALENDAR, tint = p.txt2, size = t.spacing.u * 2)
    }
    if (open) {
        // pinned to English until the app is localised: one language per screen
        val state = remember { DatePickerState(locale = englishCalendarLocale(), initialSelectedDateMillis = value?.let { it.toEpochDays() * DAY_MS }) }
        EnglishLocaleScope { DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onChange(LocalDate.fromEpochDays(it / DAY_MS)) }; open = false }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state, dateFormatter = remember { englishFormatter() }, title = { Text("Select date", modifier = Modifier.padding(start = t.spacing.u * 3, top = t.spacing.u * 2)) }) } }
    }
}

/** The English calendar locale, for the picker's month grid. */
expect fun englishCalendarLocale(): CalendarLocale

/** Runs [content] under an English platform configuration where the platform has one (Android); a pass-through elsewhere. */
@Composable
expect fun EnglishLocaleScope(content: @Composable () -> Unit)

/** Material's formatter, with the platform locale it is handed swapped for English (the headline and the month/year line). */
private fun englishFormatter(): DatePickerFormatter {
    val base = DatePickerDefaults.dateFormatter()
    val en = englishCalendarLocale()
    return object : DatePickerFormatter {
        override fun formatDate(dateMillis: Long?, locale: CalendarLocale, forContentDescription: Boolean) = base.formatDate(dateMillis, en, forContentDescription)
        override fun formatMonthYear(monthMillis: Long?, locale: CalendarLocale) = base.formatMonthYear(monthMillis, en)
    }
}
