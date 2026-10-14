package app.scifin.ui.components

import androidx.compose.material3.CalendarLocale
import androidx.compose.runtime.Composable
import java.util.Locale

actual fun englishCalendarLocale(): CalendarLocale = Locale.ENGLISH

/** Windows has no per-subtree configuration; the state's locale already governs the grid, and the JVM default governs the rest. */
@Composable
actual fun EnglishLocaleScope(content: @Composable () -> Unit) = content()
