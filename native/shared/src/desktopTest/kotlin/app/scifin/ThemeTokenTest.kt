package app.scifin

import app.scifin.model.Theme
import kotlin.test.Test
import kotlin.test.assertEquals

/** §2.5: the four themes decided 2026-10-07 -- stored tokens, labels, and every earlier token read as a theme, never rejected. */
class ThemeTokenTest {
    @Test fun `the four tokens and their labels`() {
        assertEquals(listOf("swiss-blue", "daylight", "tallow-tome", "dusk"), Theme.entries.map { it.token })
        assertEquals(listOf("Swiss Blue", "Daylight", "Tallow & Tome", "Dusk"), Theme.entries.map { it.label })
    }

    @Test fun `retired and legacy tokens resolve to the nearest theme by hue, unknown ones to Swiss Blue`() {
        assertEquals(Theme.SWISS_BLUE, Theme.of("slate"))
        assertEquals(Theme.SWISS_BLUE, Theme.of("tide"))
        assertEquals(Theme.SWISS_BLUE, Theme.of("tidalkeep"))
        assertEquals(Theme.DAYLIGHT, Theme.of("wabi"))
        assertEquals(Theme.DAYLIGHT, Theme.of("wabisabi"))
        assertEquals(Theme.DUSK, Theme.of("mauve"))
        assertEquals(Theme.SWISS_BLUE, Theme.of("neon"))
    }
}
