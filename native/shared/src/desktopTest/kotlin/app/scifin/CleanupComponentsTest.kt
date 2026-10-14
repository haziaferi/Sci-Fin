@file:OptIn(ExperimentalComposeUiApi::class)

package app.scifin

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density as UnitDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.use
import app.scifin.model.Settings
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.components.DisclosureNote
import app.scifin.ui.components.Dropdown
import app.scifin.ui.components.Segmented
import app.scifin.ui.components.SettingRow
import app.scifin.ui.components.SheetScaffold
import app.scifin.ui.components.Switch
import app.scifin.ui.components.longestWordPx
import app.scifin.ui.tokensFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The 2026-10-10 cleanup's component fixes: what a screen reader is given, and where a control answers a tap. */
class CleanupComponentsTest {
    private fun scene(width: Int = 360, height: Int = 800, content: @Composable () -> Unit): ImageComposeScene =
        ImageComposeScene(width = width, height = height, density = UnitDensity(1f)).also { s ->
            s.setContent { CompositionLocalProvider(LocalTokens provides tokensFor(Settings(profileId = "p"), systemDark = false, targetDp = 48)) { content() } }
            s.render()
        }

    private fun ImageComposeScene.nodes(merged: Boolean = true): List<SemanticsNode> {
        val out = ArrayList<SemanticsNode>()
        fun walk(n: SemanticsNode) { out += n; n.children.forEach(::walk) }
        semanticsOwners.forEach { walk(if (merged) it.rootSemanticsNode else it.unmergedRootSemanticsNode) }
        return out
    }

    private fun SemanticsNode.texts(): List<String> = config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()

    private fun ImageComposeScene.tap(o: Offset) { sendPointerEvent(PointerEventType.Press, o); sendPointerEvent(PointerEventType.Release, o); render() }

    @Test fun `a sheet's text is not merged into its scrim or body, and the scrim offers a labelled Close`() {
        var dismissed = 0
        scene { SheetScaffold(title = "Title", onDismiss = { dismissed++ }) { Text("Body text") } }.use { s ->
            val all = s.nodes()
            val body = all.filter { "Body text" in it.texts() }
            assertTrue(body.isNotEmpty(), "the body text is in the tree")
            for (n in body) {
                assertFalse(SemanticsActions.OnClick in n.config, "the body text sits on no clickable node")
                assertFalse("Title" in n.texts(), "title and body are separate nodes")
            }
            val close = all.firstOrNull { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Close" }
            assertNotNull(close, "the scrim's dismissal is a labelled action")
            assertEquals(Role.Button, close.config.getOrNull(SemanticsProperties.Role))
            val title = all.first { "Title" in it.texts() }
            assertTrue(SemanticsProperties.Heading in title.config, "the sheet title is a heading")

            val b = body.first().boundsInRoot
            s.tap(b.center)
            assertEquals(0, dismissed, "a tap on the sheet does not dismiss it")
            s.tap(Offset(180f, 20f))
            assertEquals(1, dismissed, "a tap on the scrim does")
        }
    }

    @Test fun `a sheet that cannot be dismissed offers no click at all`() {
        scene { SheetScaffold(title = "Title", onDismiss = null) { Text("Body text") } }.use { s ->
            assertTrue(s.nodes().none { SemanticsActions.OnClick in it.config })
        }
    }

    @Test fun `a switch in a setting row is named by the row's title`() {
        scene { SettingRow(Lucide.REPEAT, "Auto-update the balance", "Each cycle") { Switch(false) {} } }.use { s ->
            val sw = s.nodes().single { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch }
            assertEquals(listOf("Auto-update the balance"), sw.config.getOrNull(SemanticsProperties.ContentDescription))
        }
    }

    @Test fun `a segmented option answers a tap above its drawn box, inside the track`() {
        var picked: String? = null
        scene { Segmented(listOf("a" to "Alpha", "b" to "Beta"), "b", { picked = it }) }.use { s ->
            assertTrue(s.nodes(merged = false).any { SemanticsProperties.SelectableGroup in it.config }, "the options form one group")
            s.tap(Offset(60f, 1f))   // in the track's 0.375u padding, above the drawn option
            assertEquals("a", picked)
        }
    }

    @Test fun `a non-breaking space joins two words into one unbreakable run`() {
        var joined = -1; var whole = -2
        val style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
        scene {
            joined = longestWordPx("ab cd", style)
            whole = rememberTextMeasurer().measure("ab cd", style).size.width
        }.close()
        assertEquals(whole, joined)
    }

    @Test fun `the disclosure reports expand and collapse, and the dropdown its role`() {
        scene { DisclosureNote("Summary", "Detail", open = false) {} }.use { s ->
            assertTrue(s.nodes().any { SemanticsActions.Expand in it.config && SemanticsActions.Collapse !in it.config })
        }
        scene { DisclosureNote("Summary", "Detail", open = true) {} }.use { s ->
            assertTrue(s.nodes().any { SemanticsActions.Collapse in it.config && SemanticsActions.Expand !in it.config })
        }
        scene { Dropdown(listOf("x" to "Ex"), "x", {}) }.use { s ->
            assertTrue(s.nodes().any { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList })
        }
    }
}
