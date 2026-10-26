package app.scifin.ui.components

import androidx.compose.material3.CalendarLocale
import androidx.compose.runtime.Composable
import app.scifin.i18n.I18n
import java.util.Locale

actual fun appCalendarLocale(): CalendarLocale = Locale.forLanguageTag(I18n.current.tag)

/** Windows has no per-subtree configuration; the state's locale already governs the grid, and the JVM default governs the rest. */
@Composable
actual fun AppLocaleScope(content: @Composable () -> Unit) = content()
