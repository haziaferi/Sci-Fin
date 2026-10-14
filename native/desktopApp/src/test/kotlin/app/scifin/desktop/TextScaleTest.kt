package app.scifin.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

/** §2.5's Windows text size (Decided 2026-09-15, built 2026-09-26): read from `reg query`'s output. */
class TextScaleTest {
    private fun reg(hex: String) = "\r\nHKEY_CURRENT_USER\\Software\\Microsoft\\Accessibility\r\n    TextScaleFactor    REG_DWORD    0x$hex\r\n\r\n"

    @Test fun `150 percent reads as 1_5`() = assertEquals(1.5f, parseTextScaleFactor(reg("96")))

    @Test fun `the slider's top, 225 percent, reads as 2_25`() = assertEquals(2.25f, parseTextScaleFactor(reg("e1")))

    @Test fun `a value never set, or out of range, is 100 percent`() {
        assertEquals(1f, parseTextScaleFactor("ERROR: The system was unable to find the specified registry key or value."))
        assertEquals(1f, parseTextScaleFactor(reg("32")))                      // 50: not a value Windows writes
    }
}
