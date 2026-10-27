package app.scifin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.TOUR_SLIDES
import app.scifin.app.TUTORIAL_STOPS
import app.scifin.app.TutorialStop
import app.scifin.app.UiState
import app.scifin.i18n.tr
import app.scifin.i18n.trf
import kotlin.math.roundToInt

/*
 * §11.6 (owner, 2026-10-27): onboarding's tour as a slideshow. Each slide holds one stop, or two short
 * ones, and each stop is a live miniature of its section -- the real screen, drawn in the Profile's own
 * theme, language and data and cut to that section -- with the stop's copy under it. Nothing in a
 * miniature takes a tap; the slide's own controls are the only live ones.
 */

@Composable
fun TourSlideshow(slide: Int, state: UiState, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val group = TOUR_SLIDES[slide]
    val last = slide == TOUR_SLIDES.lastIndex
    // the ground takes every tap that misses the slide's controls, so nothing beneath is reached
    Box(Modifier.fillMaxSize().background(p.bg).clickable(enabled = false) {}, contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = t.measure).fillMaxWidth().verticalScroll(rememberScrollState()).padding(t.spacing.pad)) {
            Text(trf("{0} OF {1}", slide + 1, TOUR_SLIDES.size), color = p.txt2, fontSize = t.type.caption, fontWeight = FontWeight.Bold, letterSpacing = t.type.caption * 0.05, modifier = Modifier.padding(bottom = t.spacing.gap))
            for ((k, i) in group.withIndex()) key(i) {
                val stop = TUTORIAL_STOPS[i]
                Column(if (k > 0) Modifier.padding(top = t.spacing.u * 2.5f) else Modifier) {
                    Text(stop.place, color = p.acc, fontSize = t.type.caption, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = t.spacing.u * 0.75f))
                    // one stop gets the slide's whole picture; a pair shares it
                    Miniature(stop, state, store, maxHeight = if (group.size == 1) t.spacing.u * 41f else t.spacing.u * 21f)
                    state.data?.let { stop.mock?.invoke(it) }?.let { m ->
                        Row(Modifier.fillMaxWidth().padding(top = t.spacing.gap).background(p.surf2, RoundedCornerShape(t.radii.sm)).padding(horizontal = t.spacing.u * 1.25f, vertical = t.spacing.gap), horizontalArrangement = Arrangement.spacedBy(t.spacing.u / 2)) {
                            Text(m, color = p.txt2, fontSize = t.type.caption); Text(tr("(illustrative)"), color = p.txt2.copy(alpha = 0.6f), fontSize = t.type.caption)
                        }
                    }
                    for (line in stop.lines) Text(line, color = p.txt, fontSize = t.type.body, lineHeight = t.type.body * 1.5, modifier = Modifier.padding(top = t.spacing.u * 1.25f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = t.spacing.u * 2.5f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Skip"), color = p.txt2, fontSize = t.type.caption, fontWeight = FontWeight.Bold, modifier = Modifier.heightIn(min = t.spacing.target).clickable { store.tourFinish() }.semantics { role = Role.Button }.padding(vertical = t.spacing.u * 0.75f))
                Row(horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 0.75f), modifier = Modifier.clearAndSetSemantics {}) {
                    for (k in TOUR_SLIDES.indices) Box(Modifier.size(t.spacing.u * 0.75f).background(if (k == slide) p.acc else p.bordStrong, CircleShape))
                }
                TourPill(if (last) tr("Done") else tr("Next")) { store.tourNext() }
            }
        }
    }
}

/**
 * The stop's real screen, laid out at full height and shifted so its section sits at the top of a
 * frame no taller than [maxHeight]. The section's place is read from its own `tutAnchor`, measured
 * inside this copy of the screen; the stop with no anchor (Learn) shows its screen from the top.
 */
@Composable
private fun Miniature(stop: TutorialStop, state: UiState, store: AppStore, maxHeight: Dp) {
    val t = LocalTokens.current; val p = t.palette
    val density = LocalDensity.current
    val anchors = remember { mutableStateMapOf<String, Rect>() }
    var origin by remember { mutableStateOf<Offset?>(null) }
    val padPx = with(density) { t.spacing.u.toPx() }
    val maxPx = with(density) { maxHeight.toPx() }
    val section = stop.anchor?.let { a -> anchors[a]?.let { r -> origin?.let { o -> r.translate(-o.x, -o.y) } } }
    val top = section?.let { (it.top - padPx).coerceAtLeast(0f) } ?: 0f
    val height = section?.let { minOf(it.height + padPx * 2, maxPx) } ?: maxPx
    val view = state.copy(
        routes = buildList { add(Route.TabRoute(stop.tab, stop.sub)); stop.settingsPage?.let { add(Route.Settings(it)) } },
        sheet = null, tutorialStop = null, tourSlide = null,
    )
    Box(
        Modifier.fillMaxWidth().height(with(density) { height.toDp() }).clip(RoundedCornerShape(t.radii.lg))
            .border(t.strokes.hair, p.bord, RoundedCornerShape(t.radii.lg)).background(p.bg)
            // a picture, not a second app: it takes no taps, and a screen reader hears the slide's copy instead
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } } }
            .clearAndSetSemantics { contentDescription = stop.place },
    ) {
        Box(
            Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).requiredHeight(t.spacing.u * 300f)
                .offset { IntOffset(0, -top.roundToInt()) }
                .onGloballyPositioned { origin = it.positionInRoot() }
                // until the section is measured the frame would show the screen's top: keep it blank instead
                .alpha(if (stop.anchor == null || section != null) 1f else 0f),
        ) {
            CompositionLocalProvider(LocalTutorialAnchors provides anchors, LocalTutorialUnclipped provides true, LocalTutorialActive provides null, LocalWidthClass provides WidthClass.COMPACT, LocalProbe provides null) {
                RouteScreen(view.route, view, store)
            }
        }
    }
}
