package app.scifin

import app.scifin.model.moneyOf
import app.scifin.ui.formatMoney
import app.scifin.ui.moneyText
import app.scifin.ui.parseMoney
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import app.scifin.app.TUTORIAL_STOPS
import app.scifin.engine.currencyNoun

/** The display layer's formatting: the prototype's `money()` shape, and the typed-amount parser §3.6's forms rely on. */
class FormatTest {
    @Test fun `money renders whole units, grouped, symbol first, true minus`() {
        assertEquals("€3,067", formatMoney(moneyOf(3067.0), "EUR"))
        // exact since 2026-09-25 (the owner's decision): never rounded, .00 dropped from a whole amount
        assertEquals("€3,067.49", formatMoney(306749, "EUR"))
        assertEquals("€3,067.50", formatMoney(306750, "EUR"))
        assertEquals("CHF 9.99", formatMoney(999, "CHF"))
        assertEquals("−$1,234", formatMoney(-123400, "USD"))
        assertEquals("€0", formatMoney(0, "EUR"))
    }

    @Test fun `typed amounts parse in both separator conventions and never with a sign`() {
        assertEquals(123400L, parseMoney("1234"))
        assertEquals(123450L, parseMoney("1234.5"))
        assertEquals(123456L, parseMoney("1.234,56"))
        assertEquals(123456L, parseMoney("1,234.56"))
        assertEquals(1250L, parseMoney("12,50"))
        assertEquals(50L, parseMoney(".5"))
        assertNull(parseMoney("")); assertNull(parseMoney("-5")); assertNull(parseMoney("abc")); assertNull(parseMoney("1.2.3.4"))
        assertEquals("1234.56", moneyText(123456)); assertEquals("1234", moneyText(123400)); assertEquals(123456L, parseMoney(moneyText(123456)))
    }

    @Test fun `copy names the Profile's own money, and the tour's stand-ins show only while unset (2026-09-25)`() {
        assertEquals("euro", currencyNoun("EUR")); assertEquals("dollar", currencyNoun("USD"))
        assertEquals("money", currencyNoun("CHF"))
        val unset = profileWith(savings = null).let { it.copy(settings = it.settings.copy(currency = "USD")) }
        assertEquals("Savings — $1,240", TUTORIAL_STOPS[0].mock!!(unset))
        assertNull(TUTORIAL_STOPS[0].mock!!(profileWith(savings = 50.0)))
        assertNull(TUTORIAL_STOPS[2].mock!!(unset.copy(settings = unset.settings.copy(incomeEnabled = true))))   // Income: stop 3 since the split
    }
}
