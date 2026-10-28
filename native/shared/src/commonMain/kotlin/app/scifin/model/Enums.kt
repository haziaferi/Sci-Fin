package app.scifin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * §12's closed vocabularies. Each enum carries the exact token the spec (and the prototype)
 * writes, so the stored column, the backup file (§4.7) and the spec all read the same word.
 */

@Serializable
enum class Cadence(val token: String) {
    @SerialName("weekly") WEEKLY("weekly"), @SerialName("biweekly") BIWEEKLY("biweekly"), @SerialName("semimonthly") SEMIMONTHLY("semimonthly"),
    @SerialName("monthly") MONTHLY("monthly"), @SerialName("bimonthly") BIMONTHLY("bimonthly"), @SerialName("yearly") YEARLY("yearly");

    /** §3.11: date mode applies only to the month-anchored cadences. */
    val monthAnchored: Boolean get() = this == MONTHLY || this == BIMONTHLY || this == YEARLY

    companion object { fun of(token: String) = entries.first { it.token == token } }
}

@Serializable
enum class IncomeCadence(val token: String) {
    @SerialName("weekly") WEEKLY("weekly"), @SerialName("biweekly") BIWEEKLY("biweekly"), @SerialName("semimonthly") SEMIMONTHLY("semimonthly"), @SerialName("monthly") MONTHLY("monthly");
    companion object { fun of(token: String) = entries.first { it.token == token } }
}

/** §3.10 (owner, 2026-10-26): a known paycheck, or pay that exists only once hours are worked. */
@Serializable
enum class IncomeType(val token: String) {
    @SerialName("salaried") SALARIED("salaried"), @SerialName("hourly") HOURLY("hourly");
    companion object { fun of(token: String) = entries.first { it.token == token } }
}

@Serializable
enum class DateMode(val token: String) {
    @SerialName("interval") INTERVAL("interval"), @SerialName("fixedDate") FIXED_DATE("fixedDate");
    companion object { fun of(token: String) = entries.first { it.token == token } }
}

@Serializable
enum class GoalMode(val token: String) {
    @SerialName("date") DATE("date"), @SerialName("amount") AMOUNT("amount");
    companion object { fun of(token: String) = entries.first { it.token == token } }
}

@Serializable
enum class AccountType(val token: String) {
    @SerialName("credit") CREDIT("credit"), @SerialName("loan") LOAN("loan");
    companion object { fun of(token: String) = entries.first { it.token == token } }
}

@Serializable
enum class RateType(val token: String) {
    @SerialName("fixed") FIXED("fixed"), @SerialName("variable") VARIABLE("variable"), @SerialName("interestOnly") INTEREST_ONLY("interestOnly");
    companion object { fun of(token: String) = entries.first { it.token == token } }
}

@Serializable
enum class ThemeMode(val token: String) {
    @SerialName("system") SYSTEM("system"), @SerialName("light") LIGHT("light"), @SerialName("dark") DARK("dark");
    companion object { fun of(token: String) = entries.firstOrNull { it.token == token } ?: SYSTEM }
}

/** §2.5: the four themes. The token is what Settings stores; the palettes live in the UI layer. Swiss Blue is the default. */
@Serializable
enum class Theme(val token: String) {
    @SerialName("swiss-blue") SWISS_BLUE("swiss-blue"), @SerialName("daylight") DAYLIGHT("daylight"),
    @SerialName("tallow-tome") TALLOW_TOME("tallow-tome"), @SerialName("dusk") DUSK("dusk");

    /** The name shown in Appearance: a name, so it stays in English in every language (owner, 2026-10-28; §2.7). */
    val label: String get() = when (this) {
        SWISS_BLUE -> "Swiss Blue"; DAYLIGHT -> "Daylight"; TALLOW_TOME -> "Tallow & Tome"; DUSK -> "Dusk"
    }

    companion object {
        /** §2.5: a stored or imported token from an earlier theme set is read as the nearest new theme by hue, never rejected. */
        private val legacy = mapOf(
            "slate" to SWISS_BLUE, "tide" to SWISS_BLUE, "tidalkeep" to SWISS_BLUE, "tidal" to SWISS_BLUE,
            "wabi" to DAYLIGHT, "wabisabi" to DAYLIGHT, "mauve" to DUSK,
        )
        fun of(token: String) = entries.firstOrNull { it.token == token } ?: legacy[token] ?: SWISS_BLUE
    }
}

/** §2.5: the spacing unit `u` in px; `pad` = 1.75u and `gap` = 1u derive from it. */
@Serializable
enum class Density(val token: String, val unitPx: Int) {
    @SerialName("comfortable") COMFORTABLE("comfortable", 8), @SerialName("compact") COMPACT("compact", 6);
    companion object { fun of(token: String) = entries.firstOrNull { it.token == token } ?: COMFORTABLE }
}

/** §3.11: the only lead times a due-date reminder may carry. */
val REMINDER_LEAD_DAYS: List<Int> = listOf(1, 3, 7, 14)
