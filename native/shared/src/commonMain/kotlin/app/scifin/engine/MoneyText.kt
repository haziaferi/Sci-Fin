package app.scifin.engine

import app.scifin.i18n.I18n
import app.scifin.i18n.Language
import app.scifin.i18n.tr
import app.scifin.model.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/*
 * The one place minor units and dates become text (§12), for screens and notification bodies
 * alike: the exact amount (`€950.40`, `.00` dropped: `€950`) and month-first dates (`Sep 30`, §3.11).
 * Each in the current language's own style (owner, 2026-10-26): Italian writes `1.450,50 €` and `30 set`.
 */

private val MONTHS_SHORT = mapOf(
    Language.EN to listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
    Language.IT to listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic"),
)

/** The current language's separators: thousands, then decimals. */
private fun separators(): Pair<Char, Char> = if (I18n.current == Language.IT) '.' to ',' else ',' to '.'

/** Whether the symbol follows the figure, as Italian writes it. */
fun symbolAfter(): Boolean = I18n.current == Language.IT

/** Symbol first where the currency has one the app knows; otherwise the code itself -- never another currency's symbol. */
fun currencySymbolOf(code: String): String = when (code) { "EUR" -> "€"; "USD" -> "$"; "GBP" -> "£"; else -> "$code " }

/** §5.1's copy names the Profile's own money: "the oldest euro", "the oldest dollar"; other codes read "money". */
fun currencyNoun(code: String): String = when (code) { "EUR" -> tr("euro"); "USD" -> tr("dollar"); "GBP" -> tr("pound"); else -> tr("money") }

/** `€1,234.50`, `€45`, `−€12.34`, `CHF 9.99`: exact, with a true minus sign and grouped thousands. */
fun moneyText(minor: Money, currency: String): String {
    val neg = minor < 0
    val abs = if (neg) -minor else minor
    val cents = abs % 100
    val body = groupDigits(abs / 100) + if (cents == 0L) "" else separators().second + cents.toString().padStart(2, '0')
    val sign = if (neg) "−" else ""
    return if (symbolAfter()) sign + body + "\u00A0" + currencySymbolOf(currency).trim() else sign + currencySymbolOf(currency) + body
}

fun groupDigits(n: Long): String {
    val s = n.toString()
    val sep = separators().first
    val sb = StringBuilder()
    for ((i, ch) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(sep)
        sb.append(ch)
    }
    return sb.toString()
}

/** A decimal figure that is not money -- hours, a rate -- with the current language's decimal mark. */
fun decimalText(text: String): String = text.replace('.', separators().second)

fun monthShort(monthNumber: Int): String = MONTHS_SHORT.getValue(if (I18n.current == Language.IT) Language.IT else Language.EN)[monthNumber - 1]

/** `Sep 30`; Italian `30 set` */
fun monthDay(d: LocalDate): String = if (I18n.current == Language.IT) "${d.day} ${monthShort(d.month.number)}" else "${monthShort(d.month.number)} ${d.day}"
