package app.scifin

import app.scifin.engine.Import
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** §4.3's header matching (audit 2026-09-25): a header naming two things must not be read as the wrong one. */
class ImportHeaderTest {
    @Test fun `a Value date column is a date, not the amount`() {
        val rows = Import.parseCSV("Booking date,Value date,Description,Amount\n01/09/2026,02/09/2026,Coffee,-3.50\n")
        assertEquals(1, rows.size)
        assertEquals(LocalDate(2026, 9, 1), rows[0].date)
        assertEquals(-350L, rows[0].amount)
    }

    @Test fun `a Credit card number column is not the credit column`() {
        val rows = Import.parseCSV("Date,Credit card number,Description,Debit,Credit\n2026-09-01,4111 1111,Rent,800.00,\n2026-09-02,4111 1111,Refund,,12.00\n")
        assertEquals(listOf(-80000L, 1200L), rows.map { it.amount })
    }

    @Test fun `one-word compounds still match their key`() {
        val rows = Import.parseCSV("Buchungsdatum;Verwendungszweck;Umsatzbetrag\n01.09.2026;Miete;-800,00\n")
        assertEquals(1, rows.size); assertEquals(-80000L, rows[0].amount); assertEquals("Miete", rows[0].description)
    }
}
