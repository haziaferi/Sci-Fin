package app.scifin

import app.scifin.model.Density
import app.scifin.ui.RADII
import app.scifin.ui.STROKES
import app.scifin.ui.iconSizes
import app.scifin.ui.screenScale
import app.scifin.ui.spacing
import app.scifin.ui.tokensFor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * §2.5's screen factor, decided 2026-09-23 for the phone: width ÷ 360dp, floored at 0.85, no
 * ceiling, multiplying the spacing unit and icon sizes and nothing else. The three things it
 * must *not* touch are the point of half of these.
 */
class ScaleTest {

    private fun near(a: Float, b: Float, eps: Float = 0.001f) =
        assertTrue(abs(a - b) < eps, "expected $b, got $a")

    @Test fun `the factor is the width over the 360dp reference, floored at 0 point 85`() {
        near(screenScale(360f), 1f)
        near(screenScale(320f), 320f / 360f)          // 0.889 — above the floor, so the floor does not bind
        near(screenScale(412f), 412f / 360f)          // 1.144
        near(screenScale(480f), 480f / 360f)          // 1.333, the top of the phone range
        near(screenScale(240f), 0.85f, 0.0001f)       // the floor, for a width no phone actually has
        assertTrue(screenScale(480f) > 1f, "no ceiling below 600dp: a large phone grows")
    }

    @Test fun `at 600dp and above the factor is 1 until §4-8's revisit settles the desktop`() {
        for (w in listOf(600f, 840f, 1100f, 1440f)) {
            near(screenScale(w), 1f)
        }
    }

    @Test fun `density and the factor multiply, with u floored at 6dp`() {
        // comfortable (8dp) never reaches the floor anywhere in the phone range
        near(spacing(Density.COMFORTABLE, 48, screenScale(360f)).u.value, 8f)
        near(spacing(Density.COMFORTABLE, 48, screenScale(320f)).u.value, 8f * (320f / 360f))
        near(spacing(Density.COMFORTABLE, 48, screenScale(480f)).u.value, 8f * (480f / 360f))
        // compact (6dp) on a 320dp phone would be 5.33; the floor is what stops it
        near(spacing(Density.COMPACT, 48, screenScale(320f)).u.value, 6f)
        near(spacing(Density.COMPACT, 48, screenScale(480f)).u.value, 6f * (480f / 360f))
        // pad and gap keep their ratios whatever the factor does
        val s = spacing(Density.COMFORTABLE, 48, screenScale(412f))
        near(s.pad.value, s.u.value * 1.75f)
        near(s.gap.value, s.u.value)
    }

    @Test fun `touch targets never move, on any axis`() {
        for (w in listOf(240f, 320f, 360f, 480f, 1100f)) {
            for (d in Density.entries) {
                assertEquals(48f, spacing(d, 48, screenScale(w)).target.value, "target moved at ${w}dp / $d")
                assertEquals(40f, spacing(d, 40, screenScale(w)).target.value, "target moved at ${w}dp / $d")
            }
        }
    }

    @Test fun `icons take the factor and stay in their four steps`() {
        val at360 = iconSizes(screenScale(360f))
        assertEquals(listOf(12f, 14f, 16f, 20f), listOf(at360.xs, at360.sm, at360.md, at360.lg).map { it.value })
        val at480 = iconSizes(screenScale(480f))
        near(at480.md.value, 16f * (480f / 360f))
        assertTrue(at480.xs.value < at480.sm.value && at480.sm.value < at480.md.value && at480.md.value < at480.lg.value)
    }

    @Test fun `radii and strokes are off the screen axis, which is the 2026-09-23 correction`() {
        val small = tokensFor(Fixture.profile.settings, systemDark = false, targetDp = 48, screenScale = screenScale(320f))
        val large = tokensFor(Fixture.profile.settings, systemDark = false, targetDp = 48, screenScale = screenScale(480f))
        assertEquals(RADII, small.radii); assertEquals(RADII, large.radii)
        assertEquals(STROKES, small.strokes); assertEquals(STROKES, large.strokes)
        // the hairline is the reason: 0.5dp × 0.89 is a stroke that may not paint at all
        assertEquals(0.5f, small.strokes.hair.value)
        // and the things that *do* scale, did
        assertTrue(large.spacing.u.value > small.spacing.u.value, "spacing did not follow the width")
        assertTrue(large.icons.md.value > small.icons.md.value, "icons did not follow the width")
    }
}
