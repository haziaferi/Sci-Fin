@file:OptIn(ExperimentalComposeUiApi::class)

package app.scifin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density as UnitDensity
import androidx.compose.ui.use
import app.scifin.model.Settings
import app.scifin.model.Theme
import app.scifin.ui.LocalProbe
import app.scifin.ui.LocalTokens
import app.scifin.ui.components.ThemeGrid
import app.scifin.ui.tokensFor
import kotlin.test.Test

import kotlin.test.assertTrue

/**
 * §2.5's theme picker (Decided 2026-10-07): two cards per row, never taller than the touch target,
 * every name on one line and whole -- at the narrowest phone, the largest Android text size, and a
 * Windows window, which has the smaller target.
 */
class ThemeGridTest {
    @Test fun `cards stay at the target height and every name fits on one line`() {
        for ((width, fontScale, target) in listOf(Triple(320, 1f, 48), Triple(320, 1.8f, 48), Triple(320, 2f, 48), Triple(360, 2f, 48), Triple(412, 1.5f, 48), Triple(1084, 1f, 40))) {
            val cards = ArrayList<Map<String, Float>>(); val fits = ArrayList<Map<String, Float>>()
            ImageComposeScene(width = width, height = 400, density = UnitDensity(1f, fontScale = fontScale)).use { scene ->
                scene.setContent {
                    CompositionLocalProvider(
                        LocalTokens provides tokensFor(Settings(profileId = "p"), systemDark = false, targetDp = target, screenScale = 1f, fontScale = fontScale),
                        LocalProbe provides { n, v -> if (n == "theme-card") cards += v else if (n == "theme-name-fit") fits += v },
                    ) { ThemeGrid(Theme.SWISS_BLUE) {} }
                }
                scene.render()
            }
            val at = "$width wide at ${fontScale}x"
            assertTrue(cards.size >= Theme.entries.size, "every card laid out, $at: ${cards.size}")
            assertTrue(cards.all { it.getValue("h") == target.toFloat() }, "card heights $at: ${cards.map { it["h"] }}")
            assertTrue(fits.size >= Theme.entries.size && fits.all { it.getValue("lines") == 1f && it.getValue("overflow") == 0f }, "names $at: $fits")
        }
    }
}
