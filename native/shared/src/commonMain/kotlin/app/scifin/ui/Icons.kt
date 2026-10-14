package app.scifin.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The prototype draws its icons from lucide v1.8.0 (ISC, (c) Lucide Contributors) and embeds
 * the nodes it uses; these are the same nodes, transcribed to ImageVector so the native app
 * shows the icons the interaction reference shows -- one stroke, 2 units on a 24 grid, round
 * caps and joins. Circles, rects and lines are written as the equivalent path commands.
 */
enum class Lucide(val key: String, private vararg val paths: String) {
    WALLET("wallet", "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1", "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4"),
    LIGHTBULB("lightbulb", "M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5", "M9 18h6", "M10 22h4"),
    BAR_CHART_3("bar-chart-3", "M3 3v16a2 2 0 0 0 2 2h16", "M18 17V9", "M13 17V5", "M8 17v-3"),
    SETTINGS("settings", "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915", "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z"),
    SHOPPING_CART("shopping-cart", "M9 21a1 1 0 1 1-2 0a1 1 0 1 1 2 0z", "M20 21a1 1 0 1 1-2 0a1 1 0 1 1 2 0z", "M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12"),
    UTENSILS_CROSSED("utensils-crossed", "m16 2-2.3 2.3a3 3 0 0 0 0 4.2l1.8 1.8a3 3 0 0 0 4.2 0L22 8", "M15 15 3.3 3.3a4.2 4.2 0 0 0 0 6l7.3 7.3c.7.7 2 .7 2.8 0L15 15Zm0 0 7 7", "m2.1 21.8 6.4-6.3", "m19 5-7 7"),
    BUS("bus", "M8 6v6", "M15 6v6", "M2 12h19.6", "M18 18h3s.5-1.7.8-2.8c.1-.4.2-.8.2-1.2 0-.4-.1-.8-.2-1.2l-1.4-5C20.1 6.8 19.1 6 18 6H4a2 2 0 0 0-2 2v10h3", "M9 18a2 2 0 1 1-4 0a2 2 0 1 1 4 0z", "M9 18h5", "M18 18a2 2 0 1 1-4 0a2 2 0 1 1 4 0z"),
    USER_CIRCLE_2("user-circle-2", "M17.925 20.056a6 6 0 0 0-11.851.001", "M16 11a4 4 0 1 1-8 0a4 4 0 1 1 8 0z", "M22 12a10 10 0 1 1-20 0a10 10 0 1 1 20 0z"),
    CHEVRON_RIGHT("chevron-right", "m9 18 6-6-6-6"),
    CHECK("check", "M20 6 9 17l-5-5"),
    MOON("moon", "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401"),
    SUN("sun", "M16 12a4 4 0 1 1-8 0a4 4 0 1 1 8 0z", "M12 2v2", "M12 20v2", "m4.93 4.93 1.41 1.41", "m17.66 17.66 1.41 1.41", "M2 12h2", "M20 12h2", "m6.34 17.66-1.41 1.41", "m19.07 4.93-1.41 1.41"),
    TYPE("type", "M12 4v16", "M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2", "M9 20h6"),
    PALETTE("palette", "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z", "M14 6.5a0.5 0.5 0 1 1-1 0a0.5 0.5 0 1 1 1 0z", "M18 10.5a0.5 0.5 0 1 1-1 0a0.5 0.5 0 1 1 1 0z", "M7 12.5a0.5 0.5 0 1 1-1 0a0.5 0.5 0 1 1 1 0z", "M9 7.5a0.5 0.5 0 1 1-1 0a0.5 0.5 0 1 1 1 0z"),
    INFO("info", "M22 12a10 10 0 1 1-20 0a10 10 0 1 1 20 0z", "M12 16v-4", "M12 8h.01"),
    X("x", "M18 6 6 18", "m6 6 12 12"),
    CALENDAR("calendar", "M8 2v4", "M16 2v4", "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-14a2 2 0 0 1-2-2v-14a2 2 0 0 1 2-2z", "M3 10h18"),
    ROTATE_CCW("rotate-ccw", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5"),
    SHIELD("shield", "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"),
    DATABASE("database", "M21 5a9 3 0 1 1-18 0a9 3 0 1 1 18 0z", "M3 5V19A9 3 0 0 0 21 19V5", "M3 12A9 3 0 0 0 21 12"),
    SLIDERS_HORIZONTAL("sliders-horizontal", "M10 5H3", "M12 19H3", "M14 3v4", "M16 17v4", "M21 12h-9", "M21 19h-5", "M21 5h-7", "M8 10v4", "M8 12H3"),
    EYE("eye", "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0", "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z"),
    EYE_OFF("eye-off", "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49", "M14.084 14.158a3 3 0 0 1-4.242-4.242", "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143", "m2 2 20 20"),
    ZAP("zap", "M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z"),
    ZAP_OFF("zap-off", "M10.513 4.856 13.12 2.17a.5.5 0 0 1 .86.46l-1.377 4.317", "M15.656 10H20a1 1 0 0 1 .78 1.63l-1.72 1.773", "M16.273 16.273 10.88 21.83a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14H4a1 1 0 0 1-.78-1.63l4.507-4.643", "m2 2 20 20"),
    TAG("tag", "M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z", "M8 7.5a0.5 0.5 0 1 1-1 0a0.5 0.5 0 1 1 1 0z"),
    LAYOUT_GRID("layout-grid", "M4 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z", "M15 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z", "M15 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z", "M4 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z"),
    COMPASS("compass", "M22 12a10 10 0 1 1-20 0a10 10 0 1 1 20 0z", "m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z"),
    TELESCOPE("telescope", "m10.065 12.493-6.18 1.318a.934.934 0 0 1-1.108-.702l-.537-2.15a1.07 1.07 0 0 1 .691-1.265l13.504-4.44", "m13.56 11.747 4.332-.924", "m16 21-3.105-6.21", "M16.485 5.94a2 2 0 0 1 1.455-2.425l1.09-.272a1 1 0 0 1 1.212.727l1.515 6.06a1 1 0 0 1-.727 1.213l-1.09.272a2 2 0 0 1-2.425-1.455z", "m6.158 8.633 1.114 4.456", "m8 21 3.105-6.21", "M14 13a2 2 0 1 1-4 0a2 2 0 1 1 4 0z"),
    PLUS("plus", "M5 12h14", "M12 5v14"),
    CHEVRON_LEFT("chevron-left", "m15 18-6-6 6-6"),
    BANKNOTE("banknote", "M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-16a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z", "M14 12a2 2 0 1 1-4 0a2 2 0 1 1 4 0z", "M6 12h.01M18 12h.01"),
    BELL("bell", "M10.268 21a2 2 0 0 0 3.464 0", "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326"),
    SMARTPHONE("smartphone", "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2h-10a2 2 0 0 1-2-2v-16a2 2 0 0 1 2-2z", "M12 18h.01"),
    CREDIT_CARD("credit-card", "M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-16a2 2 0 0 1-2-2v-10a2 2 0 0 1 2-2z", "M2 10L22 10"),
    TRENDING_UP("trending-up", "M16 7h6v6", "m22 7-8.5 8.5-5-5L2 17"),
    LANDMARK("landmark", "M10 18v-7", "M11.12 2.198a2 2 0 0 1 1.76.006l7.866 3.847c.476.233.31.949-.22.949H3.474c-.53 0-.695-.716-.22-.949z", "M14 18v-7", "M18 18v-7", "M3 22h18", "M6 18v-7"),
    REPEAT("repeat", "m17 2 4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "m7 22-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3"),
    ARROW_UP_RIGHT("arrow-up-right", "M7 7h10v10", "M7 17 17 7"),
    ARROW_DOWN_RIGHT("arrow-down-right", "m7 7 10 10", "M17 7v10H7"),
    PIGGY_BANK("piggy-bank", "M11 17h3v2a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1v-3a3.16 3.16 0 0 0 2-2h1a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1h-1a5 5 0 0 0-2-4V3a4 4 0 0 0-3.2 1.6l-.3.4H11a6 6 0 0 0-6 6v1a5 5 0 0 0 2 4v3a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1z", "M16 10h.01", "M2 8v1a2 2 0 0 0 2 2h1"),
    LOCK("lock", "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-14a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z", "M7 11V7a5 5 0 0 1 10 0v4"),
    USERS("users", "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", "M16 3.128a4 4 0 0 1 0 7.744", "M22 21v-2a4 4 0 0 0-3-3.87", "M13 7a4 4 0 1 1-8 0a4 4 0 1 1 8 0z"),
    PENCIL("pencil", "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z", "m15 5 4 4"),
    TRASH_2("trash-2", "M10 11v6", "M14 11v6", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M3 6h18", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"),
    CALENDAR_CLOCK("calendar-clock", "M16 14v2.2l1.6 1", "M16 2v4", "M21 7.5V6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h3.5", "M3 10h5", "M8 2v4", "M22 16a6 6 0 1 1-12 0a6 6 0 1 1 12 0z");

    companion object {
        /** The prototype's icon by its lucide name; null for a name the set does not carry. */
        fun byKey(key: String?): Lucide? = key?.let { k -> entries.firstOrNull { it.key == k } }
    }

    val vector: ImageVector by lazy {
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            for (d in paths) addPath(
                pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }
}

@Composable
fun LucideIcon(icon: Lucide, tint: Color, size: Dp = LocalTokens.current.icons.lg, modifier: Modifier = Modifier) {
    Icon(icon.vector, contentDescription = null, tint = tint, modifier = modifier.size(size))
}
