package app.scifin.ui

import app.scifin.engine.currencySymbolOf
import app.scifin.engine.groupDigits
import app.scifin.engine.monthShort
import app.scifin.model.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import app.scifin.engine.moneyText as engineMoneyText

/*
 * The display layer's names for the one formatter in `engine/MoneyText.kt` (§12), which the
 * notification bodies also use: exact amounts, `.00` dropped from a whole
 * one, grouped thousands, a true minus sign, the currency symbol first.
 */

fun currencySymbol(code: String): String = currencySymbolOf(code)

/** `€950.40`, `€950`, `−€1,234.56` -- the real amount, never rounded. */
fun formatMoney(minor: Money, currency: String): String = engineMoneyText(minor, currency).also { FigureProbe.sink?.invoke(it) }

fun groupThousands(n: Long): String = groupDigits(n)

fun Month.shortName(): String = monthShort(ordinal + 1)

/** `Sep 14` */
fun formatDate(d: LocalDate): String = "${d.month.shortName()} ${d.day}"

/** `Sep 14, 2026` */
fun formatDateFull(d: LocalDate): String = "${d.month.shortName()} ${d.day}, ${d.year}"

/** A typed amount -- `1234`, `1234.5`, `1.234,56`, `12,50` -- to minor units; null when it is not a number. No sign is accepted (§3.6). */
fun parseMoney(text: String): Money? {
    val s = text.trim().filterNot { it == ' ' || it == ' ' || it == ' ' }
    if (s.isEmpty() || s.any { it == '-' || it == '−' || it == '+' }) return null
    // the last separator is the decimal one when it is followed by 1-2 digits; every other separator groups thousands
    val lastSep = s.indexOfLast { it == '.' || it == ',' }
    val (whole, frac) = if (lastSep >= 0 && s.length - lastSep - 1 in 1..2 && s.substring(lastSep + 1).all { it.isDigit() })
        s.substring(0, lastSep) to s.substring(lastSep + 1) else s to ""
    val digits = whole.filter { it != '.' && it != ',' }
    if (digits.isEmpty() && frac.isEmpty()) return null
    if (whole.any { it != '.' && it != ',' && !it.isDigit() }) return null
    // a grouping separator is followed by exactly three digits; anything else is not a number
    val groups = whole.split('.', ',')
    if (groups.size > 1 && (groups.first().length !in 1..3 || groups.drop(1).any { it.length != 3 })) return null
    val units = if (digits.isEmpty()) 0L else digits.toLongOrNull() ?: return null
    val cents = if (frac.isEmpty()) 0L else frac.padEnd(2, '0').toLong()
    if (units > (Long.MAX_VALUE - 99) / 100) return null
    return units * 100 + cents
}

/** Minor units as an editor's text: `1234.56`, no grouping, so it round-trips through [parseMoney]. */
fun moneyText(minor: Money): String {
    val neg = minor < 0; val abs = if (neg) -minor else minor
    val cents = abs % 100
    return (if (neg) "-" else "") + (abs / 100).toString() + (if (cents == 0L) "" else "." + cents.toString().padStart(2, '0'))
}
