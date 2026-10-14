package app.scifin.engine

import app.scifin.model.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/*
 * The one place minor units and dates become text (§12), for screens and notification bodies
 * alike: the exact amount (`€950.40`, `.00` dropped: `€950`) and month-first dates (`Sep 30`, §3.11).
 */

private val MONTHS_SHORT = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Symbol first where the currency has one the app knows; otherwise the code itself -- never another currency's symbol. */
fun currencySymbolOf(code: String): String = when (code) { "EUR" -> "€"; "USD" -> "$"; "GBP" -> "£"; else -> "$code " }

/** §5.1's copy names the Profile's own money: "the oldest euro", "the oldest dollar"; other codes read "money". */
fun currencyNoun(code: String): String = when (code) { "EUR" -> "euro"; "USD" -> "dollar"; "GBP" -> "pound"; else -> "money" }

/** `€1,234.50`, `€45`, `−€12.34`, `CHF 9.99`: exact, with a true minus sign and grouped thousands. */
fun moneyText(minor: Money, currency: String): String {
    val neg = minor < 0
    val abs = if (neg) -minor else minor
    val cents = abs % 100
    val body = groupDigits(abs / 100) + if (cents == 0L) "" else "." + cents.toString().padStart(2, '0')
    return (if (neg) "−" else "") + currencySymbolOf(currency) + body
}

fun groupDigits(n: Long): String {
    val s = n.toString()
    val sb = StringBuilder()
    for ((i, ch) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
        sb.append(ch)
    }
    return sb.toString()
}

fun monthShort(monthNumber: Int): String = MONTHS_SHORT[monthNumber - 1]

/** `Sep 30` */
fun monthDay(d: LocalDate): String = "${monthShort(d.month.number)} ${d.day}"
