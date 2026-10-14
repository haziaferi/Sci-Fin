package app.scifin

import androidx.compose.ui.geometry.Rect
import app.scifin.ui.tutorialWindowAt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * §11.6's window has to stay inside the frame. On the device walk of 2026-09-23 it did not:
 * at stop 2 the Skip/Next row was drawn under the navigation bar, where a tap goes to the
 * system and never reaches the app, and the tour could be neither advanced nor skipped --
 * on first run, with no way out but to leave. These pin the two reasons it happened.
 */
class TutorialWindowTest {

    private val gutter = 30f
    private val w = 1080f
    private val h = 2184f          // the OnePlus' frame: 2412 less a 96px status bar and a 132px navigation bar
    private val bubble = 900f

    private fun fits(y: Float, cardH: Float) = y >= 0f && y + cardH <= h

    @Test
    fun `the window stays inside the frame however low the anchor sits`() {
        val tall = 740f            // stop 2's measured height on the device
        for (top in 0..2000 step 25) {
            val hole = Rect(60f, top.toFloat(), 1020f, top + 380f)
            val (x, y) = tutorialWindowAt(hole, bubble, tall, gutter, w, h, firstGuess = 510f)
            assertTrue(fits(y, tall), "a $tall px window at anchor top $top was placed at $y, outside 0..$h")
            assertTrue(x >= 0f && x + bubble <= w, "x $x leaves the frame")
        }
    }

    @Test
    fun `a window taller than the room below the ring goes above it, not off the bottom`() {
        // The device's stop 2: 205dp of room below the ring, a 247dp window. The old fixed
        // 170dp guess called that "room" and placed it below, off the frame.
        val hole = Rect(60f, 1150f, 1020f, 1560f)
        val tall = 740f
        val (_, y) = tutorialWindowAt(hole, bubble, tall, gutter, w, h, firstGuess = 510f)
        assertTrue(y + tall <= hole.top, "expected the window above the ring, got $y with the ring at ${hole.top}")
    }

    @Test
    fun `before it has been measured the window still lands inside the frame`() {
        val hole = Rect(60f, 1800f, 1020f, 2100f)
        val (_, y) = tutorialWindowAt(hole, bubble, cardH = 0f, gutter = gutter, maxW = w, maxH = h, firstGuess = 510f)
        assertTrue(y in 0f..h, "first-frame placement $y is outside 0..$h")
    }

    @Test
    fun `a stop with no anchor is centred across, and high enough to read`() {
        val (x, y) = tutorialWindowAt(null, bubble, 740f, gutter, w, h, firstGuess = 510f)
        assertTrue(x == (w - bubble) / 2, "expected a centred window, got $x")
        assertTrue(fits(y, 740f), "centred placement $y leaves the frame")
    }
}
