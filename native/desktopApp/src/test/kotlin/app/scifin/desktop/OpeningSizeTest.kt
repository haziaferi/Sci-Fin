package app.scifin.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** §4.8's opening size: 1100 × 760 outer, never larger than the work area. */
class OpeningSizeTest {
    @Test fun `a large screen gets 1100 by 760`() = assertEquals(DpSize(1100.dp, 760.dp), openingSize(DpSize(1920.dp, 1032.dp)))

    @Test fun `a 1366 by 768 laptop keeps the width and loses the height the taskbar takes`() =
        assertEquals(DpSize(1100.dp, 720.dp), openingSize(DpSize(1366.dp, 720.dp)))

    @Test fun `1920 by 1080 at 150 percent is 1280 by 672 of work area`() =
        assertEquals(DpSize(1100.dp, 672.dp), openingSize(DpSize(1280.dp, 672.dp)))
}
