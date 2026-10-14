package app.scifin

import androidx.compose.ui.unit.dp
import app.scifin.ui.components.columnsIn
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 4.8's column rule as arithmetic, tested away from anything it lays out.
 *
 * The rule is the largest N satisfying N*measure + (N-1)*gap <= avail, floored at 1. These cases
 * pin the boundaries, which is where a refactor would break it: one pixel either side of a column
 * appearing, and the phone case where one measure alone already exceeds the width.
 */
class ColumnRuleTest {
    private val m = 458.dp
    private val gap = 12.dp

    @Test fun `a phone gets one column, because the rule is a maximum and not a target`() {
        assertEquals(1, columnsIn(332.dp, m, gap))
        assertEquals(1, columnsIn(0.dp, m, gap))
    }

    @Test fun `the second column appears exactly when two measures and a gap fit`() {
        assertEquals(1, columnsIn(927.dp, m, gap))
        assertEquals(2, columnsIn(928.dp, m, gap))
    }

    @Test fun `the third likewise, at three measures and two gaps`() {
        assertEquals(2, columnsIn(1397.dp, m, gap))
        assertEquals(3, columnsIn(1398.dp, m, gap))
    }

    @Test fun `the default window fits two, which is what makes two the desktop default`() {
        // 1100x760 outer -> 1084 client, less 4.1's rail, less 2.5's page padding
        assertEquals(2, columnsIn(1084.dp - 88.dp - 32.dp, m, gap))
    }

    @Test fun `the measure grows with the text setting, so the columns thin out rather than reflow`() {
        // XL: 2.5's ramp puts the measure at 686dp, and the same window then holds one column
        assertEquals(1, columnsIn(1084.dp - 88.dp - 32.dp, 686.dp, gap))
        assertEquals(2, columnsIn(1904.dp - 88.dp - 32.dp, 686.dp, gap))
    }
}
