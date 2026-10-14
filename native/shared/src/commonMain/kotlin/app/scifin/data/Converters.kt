@file:OptIn(ExperimentalTime::class)

package app.scifin.data

import androidx.room.TypeConverter
import app.scifin.model.AccountType
import app.scifin.model.Cadence
import app.scifin.model.DateMode
import app.scifin.model.Density
import app.scifin.model.GoalMode
import app.scifin.model.IncomeCadence
import app.scifin.model.RateType
import app.scifin.model.Theme
import app.scifin.model.ThemeMode
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * §12's storage form for the two kinds of time and the closed vocabularies. A LocalDate is ISO
 * text (`2026-09-14`), sortable as text; an Instant is epoch milliseconds. Enums store their
 * spec token, never their ordinal, so reordering an enum can never re-label stored rows.
 */
class Converters {
    @TypeConverter fun localDateToString(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun stringToLocalDate(s: String?): LocalDate? = s?.let { LocalDate.parse(it) }

    @TypeConverter fun instantToLong(i: Instant?): Long? = i?.toEpochMilliseconds()
    @TypeConverter fun longToInstant(l: Long?): Instant? = l?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter fun cadenceToString(c: Cadence?) = c?.token
    @TypeConverter fun stringToCadence(s: String?) = s?.let { Cadence.of(it) }
    @TypeConverter fun incomeCadenceToString(c: IncomeCadence?) = c?.token
    @TypeConverter fun stringToIncomeCadence(s: String?) = s?.let { IncomeCadence.of(it) }
    @TypeConverter fun dateModeToString(d: DateMode?) = d?.token
    @TypeConverter fun stringToDateMode(s: String?) = s?.let { DateMode.of(it) }
    @TypeConverter fun goalModeToString(g: GoalMode?) = g?.token
    @TypeConverter fun stringToGoalMode(s: String?) = s?.let { GoalMode.of(it) }
    @TypeConverter fun accountTypeToString(a: AccountType?) = a?.token
    @TypeConverter fun stringToAccountType(s: String?) = s?.let { AccountType.of(it) }
    @TypeConverter fun rateTypeToString(r: RateType?) = r?.token
    @TypeConverter fun stringToRateType(s: String?) = s?.let { RateType.of(it) }
    @TypeConverter fun themeToString(t: Theme?) = t?.token
    @TypeConverter fun stringToTheme(s: String?) = s?.let { Theme.of(it) }
    @TypeConverter fun themeModeToString(t: ThemeMode?) = t?.token
    @TypeConverter fun stringToThemeMode(s: String?) = s?.let { ThemeMode.of(it) }
    @TypeConverter fun densityToString(d: Density?) = d?.token
    @TypeConverter fun stringToDensity(s: String?) = s?.let { Density.of(it) }

    // §4.3's ImportState collections, as JSON text.
    @TypeConverter fun stringListToJson(l: List<String>?): String? = l?.let { Json.encodeToString(it) }
    @TypeConverter fun jsonToStringList(s: String?): List<String>? = s?.let { Json.decodeFromString<List<String>>(it) }
    @TypeConverter fun stringMapToJson(m: Map<String, String>?): String? = m?.let { Json.encodeToString(it) }
    @TypeConverter fun jsonToStringMap(s: String?): Map<String, String>? = s?.let { Json.decodeFromString<Map<String, String>>(it) }
}
