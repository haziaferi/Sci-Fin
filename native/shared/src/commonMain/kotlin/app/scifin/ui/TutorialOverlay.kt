@file:OptIn(ExperimentalFoundationApi::class)

package app.scifin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.scifin.app.AppStore
import app.scifin.app.TUTORIAL_STOPS
import app.scifin.model.ProfileData
import kotlin.math.roundToInt

/*
 * §11.6's overlay: a scrim with a hole over the anchored element, an accent ring around it
 * (the app's one "look here" mark, shared with §2.1's tick), and the window beside it. The
 * app underneath stays live: only the window takes taps.
 */

/** Where each anchored element currently sits, in the root's coordinates. */
val LocalTutorialAnchors = staticCompositionLocalOf<SnapshotStateMap<String, Rect>?> { null }

/** The anchor the tour is on right now, so that element can scroll itself into view. */
val LocalTutorialActive = compositionLocalOf<String?> { null }

/** Marks a real element as one the tour can point at; while it is the active stop it scrolls into view. */
@Composable
fun Modifier.tutAnchor(id: String): Modifier {
    val anchors = LocalTutorialAnchors.current ?: return this
    val active = LocalTutorialActive.current == id
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(active) { if (active) requester.bringIntoView() }
    // an element that leaves composition takes its position with it, or a stop would ring a stale spot
    DisposableEffect(id) { onDispose { anchors.remove(id) } }
    return this.bringIntoViewRequester(requester).onGloballyPositioned { anchors[id] = it.boundsInRoot() }
}

@Composable
fun TutorialOverlay(stopIndex: Int, anchors: SnapshotStateMap<String, Rect>, data: ProfileData?, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val stop = TUTORIAL_STOPS[stopIndex]
    val density = LocalDensity.current
    // Anchors are in root coordinates (window top-left, behind the system bars); the overlay is
    // inset, so subtract its own origin.
    var origin by remember { mutableStateOf(Offset.Zero) }
    var cardH by remember { mutableStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        val rect = stop.anchor?.let { anchors[it] }?.translate(-origin.x, -origin.y)
        val pad = with(density) { (t.spacing.u * 0.75f).toPx() }
        val hole = rect?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }
        // the scrim with its hole -- drawn, so it takes no taps: the app underneath is live
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(SCRIM.copy(alpha = 0.5f))
            if (hole != null) {
                val r = t.radii.xl.toPx()
                drawRoundRect(Color.Transparent, topLeft = hole.topLeft, size = hole.size, cornerRadius = CornerRadius(r), blendMode = BlendMode.Clear)
                drawRoundRect(p.acc, topLeft = hole.topLeft, size = hole.size, cornerRadius = CornerRadius(r), style = Stroke(t.strokes.emph.toPx()))
            }
        }
        // the window: below the ring when there is room, above it otherwise, centred when there is no anchor
        val bubbleW = minOf(with(density) { (t.spacing.u * 31.25f).toPx() }, constraints.maxWidth - with(density) { (t.spacing.pad * 2).toPx() })
        val gutter = with(density) { (t.spacing.u * 1.25f).toPx() }
        val (x, yc) = tutorialWindowAt(
            hole, bubbleW, cardH.toFloat(), gutter,
            constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(),
            firstGuess = with(density) { (t.spacing.u * 21.25f).toPx() },
        )
        Column(
            Modifier.offset { IntOffset(x.roundToInt(), yc.roundToInt()) }
                .onSizeChanged { cardH = it.height }
                .width(with(density) { bubbleW.toDp() })
                .background(p.surf, RoundedCornerShape(t.radii.lg)).clickable(enabled = false) {}
                .padding(horizontal = t.spacing.u * 1.625f, vertical = t.spacing.u * 1.5f),
        ) {
            Text("STOP ${stopIndex + 1} OF ${TUTORIAL_STOPS.size}", color = p.txt2, fontSize = t.type.caption, fontWeight = FontWeight.Bold, letterSpacing = t.type.caption * 0.05, modifier = Modifier.padding(bottom = t.spacing.u / 2))
            data?.let { stop.mock?.invoke(it) }?.let { m ->
                Row(Modifier.fillMaxWidth().background(p.surf2, RoundedCornerShape(t.radii.sm)).padding(horizontal = t.spacing.u * 1.25f, vertical = t.spacing.gap), horizontalArrangement = Arrangement.spacedBy(t.spacing.u / 2)) {
                    Text(m, color = p.txt2, fontSize = t.type.caption); Text("(illustrative)", color = p.txt2.copy(alpha = 0.6f), fontSize = t.type.caption)
                }
                Spacer(Modifier.height(t.spacing.gap))
            }
            for ((i, line) in stop.lines.withIndex()) Text(line, color = p.txt, fontSize = t.type.body, lineHeight = t.type.body * 1.5, modifier = Modifier.padding(bottom = if (i < stop.lines.lastIndex) t.spacing.gap else 0.dp))
            Row(Modifier.fillMaxWidth().padding(top = t.spacing.u * 1.25f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Skip", color = p.txt2, fontSize = t.type.caption, fontWeight = FontWeight.Bold, modifier = Modifier.heightIn(min = t.spacing.target).clickable { store.tutorialFinish() }.semantics { role = Role.Button }.padding(vertical = t.spacing.u * 0.75f))
                // §2.5: the tap area is the full target; the pill drawn inside it stays a unit shorter
                Box(Modifier.heightIn(min = t.spacing.target).clickable { store.tutorialNext() }.semantics { role = Role.Button }, contentAlignment = Alignment.Center) {
                    Box(Modifier.heightIn(min = t.spacing.target - t.spacing.u).background(p.acc, CircleShape).padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 0.75f), contentAlignment = Alignment.Center) {
                        Text(if (stopIndex == TUTORIAL_STOPS.lastIndex) "Done" else "Next", color = p.onAcc, fontSize = t.type.caption, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Where the tour's window sits, in the overlay's own coordinates (§11.6).
 *
 * Below the ring when the window genuinely fits there, above it otherwise, centred when the
 * stop has no anchor -- and always inside the frame. [cardH] is 0 on the first frame and
 * measured after, so a tall stop is placed by its real height; [firstGuess] covers only the first frame.
 */
internal fun tutorialWindowAt(
    hole: Rect?,
    bubbleW: Float,
    cardH: Float,
    gutter: Float,
    maxW: Float,
    maxH: Float,
    firstGuess: Float,
): Pair<Float, Float> {
    if (hole == null) return Pair((maxW - bubbleW) / 2, maxH * 0.3f)
    val need = if (cardH > 0f) cardH + gutter else firstGuess
    val x = hole.left.coerceIn(gutter, maxOf(gutter, maxW - bubbleW - gutter))
    val y = if (maxH - hole.bottom > need) hole.bottom + gutter else maxOf(gutter, hole.top - need - gutter)
    // §2.5: nothing leaves the frame. Whatever the anchor does, Skip and Next stay inside it.
    val yMax = maxOf(gutter, maxH - cardH - gutter)
    return Pair(x, y.coerceIn(gutter, yMax))
}
