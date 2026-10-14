package app.scifin.ui.components

import android.content.res.Configuration
import androidx.compose.material3.CalendarLocale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

actual fun englishCalendarLocale(): CalendarLocale = Locale.ENGLISH

/** The dialog's headline and its OK/Cancel strings read the configuration, so the configuration is English inside it. */
@Composable
actual fun EnglishLocaleScope(content: @Composable () -> Unit) {
    val base = LocalConfiguration.current
    val ctx = LocalContext.current
    val (config, enCtx) = remember(base, ctx) { Configuration(base).apply { setLocale(Locale.ENGLISH) }.let { it to ctx.createConfigurationContext(it) } }
    CompositionLocalProvider(LocalConfiguration provides config, LocalContext provides enCtx) { content() }
}
