@file:OptIn(ExperimentalComposeUiApi::class)

package app.scifin.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.SettingsPage
import app.scifin.app.Sheet
import app.scifin.app.Tab
import app.scifin.engine.Writes
import app.scifin.model.moneyOf
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.engine.SystemClock
import app.scifin.engine.newId
import app.scifin.model.Density
import app.scifin.model.Theme
import app.scifin.model.ThemeMode
import app.scifin.notify.NotificationPermission
import app.scifin.notify.NotificationScheduler
import app.scifin.notify.ReminderRegistration
import app.scifin.ui.SciFinApp
import app.scifin.ui.WidthClass
import app.scifin.ui.widthClassOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object NoScheduler : NotificationScheduler {
    override fun permissionState() = NotificationPermission.GRANTED
    override suspend fun requestPermission() = NotificationPermission.GRANTED
    override fun syncReminders(registrations: List<ReminderRegistration>) {}
    override fun setNewMonthNotification(enabled: Boolean) {}
    override fun restoreAfterBoot(reminders: List<ReminderRegistration>, newMonthEnabled: Boolean) {}
}

/**
 * Renders the frame headlessly: every theme × mode at phone width, the extreme scale and
 * density, and the three width classes of §4.8 -- bar, rail, rail -- writing PNGs under
 * build/frame-renders. It is the proof the plan asks for without a display.
 */
class FrameRenderTest {
    private fun store(theme: Theme = Theme.SWISS_BLUE, mode: ThemeMode = ThemeMode.LIGHT, density: Density = Density.COMFORTABLE, tab: Tab = Tab.BUDGET): AppStore {
        val db = buildInMemorySciFinDatabase()
        val store = AppStore(AppSession(db, NoScheduler, SystemClock, ::newId), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        runBlocking {
            store.start(); store.createProfile("Mine", theme, false).join(); store.completeOnboarding().join()
            store.dispatch { it.copy(settings = it.settings.copy(theme = theme, mode = mode, density = density)) }.join()
        }
        store.selectTab(tab)
        return store
    }

    private fun render(name: String, w: Int, h: Int, store: AppStore, fontScale: Float = 1f) {
        val out = File("build/frame-renders").apply { mkdirs() }
        ImageComposeScene(width = w, height = h, density = androidx.compose.ui.unit.Density(1f, fontScale = fontScale)).use { scene ->
            scene.setContent { SciFinApp(store, targetDp = 40, systemDark = false) }
            val bytes = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, "$name.png").writeBytes(bytes)
            assertTrue(bytes.size > 1000, "$name rendered nothing")
        }
    }

    @Test fun `every palette renders at phone width`() {
        for (t in Theme.entries) for (m in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) render("${t.token}-${m.token}", 360, 756, store(t, m))
        render("slate-xl-compact", 360, 756, store(density = Density.COMPACT), fontScale = 1.5f)   // xl is the platform's own text size now
    }

    @Test fun `the Savings row and its two forms render at phone and tablet width`() {
        val seeded = store().also { st -> runBlocking { st.dispatch { Writes.savingsQuickAdd(it, moneyOf(3067.0), null, false, SystemClock, newId()) }.join() } }
        render("savings-row", 360, 756, seeded)
        render("savings-row-unset", 360, 756, store())
        seeded.openSheet(Sheet.Entry("savings")); render("entry-360", 360, 756, seeded); render("entry-700", 700, 640, seeded)
        seeded.openSheet(Sheet.SavingsEditor); render("savings-editor-360", 360, 756, seeded); seeded.closeSheet()
    }

    @Test fun `screen 1 renders over the empty Budget home at 320, 360 and 700dp, and in a previewed theme`() {
        fun fresh(): AppStore {
            val db = buildInMemorySciFinDatabase()
            val st = AppStore(AppSession(db, NoScheduler, SystemClock, ::newId), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
            runBlocking { st.start() }
            return st
        }
        render("onboarding-360", 360, 756, fresh())
        render("onboarding-320", 320, 672, fresh())
        render("onboarding-700", 700, 700, fresh())
        fresh().also { it.previewTheme(Theme.TALLOW_TOME) }.let { render("onboarding-360-tallow", 360, 756, it) }
    }

    @Test fun `the Settings hub and its spokes render`() {
        val st = store(tab = Tab.SETTINGS)
        render("settings-hub", 360, 756, st)
        for (page in SettingsPage.entries) { st.selectTab(Tab.SETTINGS); st.push(Route.Settings(page)); render("settings-${page.name.lowercase()}", 360, 756, st) }
        st.selectTab(Tab.SETTINGS); st.push(Route.Settings(SettingsPage.INCOME))
        runBlocking { st.setIncomeEnabled(true).join(); st.updateSettings { it.copy(income = moneyOf(1450.0)) }.join() }
        render("settings-income-on", 360, 900, st)
        st.selectTab(Tab.SETTINGS); st.push(Route.Settings(SettingsPage.PROFILES)); st.openSheet(Sheet.DeleteProfile(st.state.value.data!!.profileId))
        render("settings-profiles-delete", 360, 756, st)
    }

    @Test fun `onboarding steps 2 and 4 render as pages without the tab bar`() {
        val db = buildInMemorySciFinDatabase()
        val st = AppStore(AppSession(db, NoScheduler, SystemClock, ::newId), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        runBlocking { st.start(); st.createProfile("Ana", Theme.TALLOW_TOME, false).join() }
        render("onboarding-step2", 360, 756, st)          // NoScheduler answers GRANTED: no prompt, so the page shows Continue
        st.advanceOnboarding(); render("tour-slide1", 360, 756, st)
        st.tourNext(); st.tourNext(); render("tour-slide3", 360, 756, st)
        st.tourNext(); st.tourNext(); render("tour-slide5-learn", 360, 756, st)
        st.tourNext(); render("tour-slide6", 360, 756, st)
        st.tourNext(); render("onboarding-step4", 360, 900, st)
    }

    @Test fun `the three width classes render bar, rail, rail`() {
        assertEquals(WidthClass.COMPACT, widthClassOf(599f)); assertEquals(WidthClass.MEDIUM, widthClassOf(600f)); assertEquals(WidthClass.EXPANDED, widthClassOf(840f))
        render("w360-budget", 360, 756, store())
        render("w700-reports", 700, 600, store(tab = Tab.REPORTS))
        render("w1100-settings", 1100, 760, store(tab = Tab.SETTINGS))
    }
}
