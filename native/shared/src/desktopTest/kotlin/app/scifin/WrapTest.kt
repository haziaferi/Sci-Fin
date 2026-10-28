@file:OptIn(ExperimentalComposeUiApi::class)

package app.scifin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.use
import app.scifin.model.Settings
import app.scifin.ui.SciFinTheme
import app.scifin.ui.components.AmountText
import app.scifin.ui.components.fitLabelSize
import app.scifin.ui.components.settingRowStacks
import app.scifin.ui.formatMoney
import app.scifin.ui.tokensFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * §2.5 at text size xl on a 360dp phone (the 2026-09-15 device critique): an amount split
 * "€1, / 095", a tab label split "Setting / s", and a SettingRow whose label column collapsed
 * to ~63dp beside its pill. Each contract here is the one that stops that.
 */
class WrapTest {
    private fun layoutIn(widthDp: Int, content: @androidx.compose.runtime.Composable ((TextLayoutResult) -> Unit) -> Unit): TextLayoutResult {
        var result: TextLayoutResult? = null
        // xl text is now the PLATFORM's font scale (the in-app setting was removed, 2026-09-25): 1.5, as xl was
        val tokens = tokensFor(Settings("p-1"), systemDark = false, targetDp = 48)
        ImageComposeScene(width = widthDp, height = 240, density = androidx.compose.ui.unit.Density(1f, fontScale = 1.5f)).use { scene ->
            scene.setContent { SciFinTheme(tokens) { Box(Modifier.width(widthDp.dp)) { content { result = it } } } }
            scene.render()
        }
        return result ?: error("no text laid out")
    }

    @Test fun `the harness reproduces the break -- a plain Text wraps the same amount in the same column`() {
        val r = layoutIn(56) { onLayout -> androidx.compose.material3.Text(formatMoney(109500, "EUR"), fontSize = 24.sp, onTextLayout = onLayout) }
        assertTrue(r.lineCount > 1, "plain Text did not wrap; the AmountText test below proves nothing")
    }

    @Test fun `an amount stays on one line however narrow its column`() {
        val r = layoutIn(56) { onLayout -> AmountText(formatMoney(109500, "EUR"), fontSize = 24.sp, onTextLayout = onLayout) }
        assertEquals(1, r.lineCount, "€1,095 wrapped")
    }

    @Test fun `the tab bar picks one label size for all five -- the largest step at which the widest fits, never below 9sp`() {
        val widthPerSpPerChar = 0.6f   // a fake measurer: width grows with size and length, like real glyphs do
        val measure = { text: String, size: androidx.compose.ui.unit.TextUnit -> text.length * size.value * widthPerSpPerChar }
        val labels = listOf("Budget", "Activity", "Science", "Reports", "Settings")
        assertEquals(13.5f, fitLabelSize(labels, limitPx = 200f, max = 13.5.sp, measure).value)          // fits as is
        assertEquals(10.0f, fitLabelSize(labels, limitPx = 48f, max = 13.5.sp, measure).value)           // "Settings" (8 chars) needs 8*0.6*s <= 48 -> s <= 10
        assertEquals(9.0f, fitLabelSize(labels, limitPx = 10f, max = 13.5.sp, measure).value)            // the floor
    }

    @Test fun `a SettingRow stacks its control under the label when the label would fall below its floor`() {
        // 360dp phone, 16dp padding each side, ~200dp pill at xl: label would get ~108dp -> stack
        assertTrue(settingRowStacks(rowWidthDp = 328f, controlWidthDp = 200f, labelFloorDp = 150f))
        // the same row with a 44dp switch: label keeps ~264dp -> side by side
        assertFalse(settingRowStacks(rowWidthDp = 328f, controlWidthDp = 44f, labelFloorDp = 150f))
    }
}
