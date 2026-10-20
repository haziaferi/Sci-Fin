package app.scifin.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.scifin.app.AppSession
import app.scifin.app.WindowsCopyPlace
import app.scifin.data.Integrity
import app.scifin.data.buildSciFinDatabase
import app.scifin.engine.SystemClock
import app.scifin.engine.newId
import app.scifin.notify.PlatformContext
import app.scifin.notify.WindowsNotificationScheduler
import app.scifin.notify.notificationScheduler
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Sheet
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import app.scifin.ui.SciFinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.awt.GraphicsEnvironment
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.time.LocalDateTime
import kotlin.system.exitProcess
import javax.imageio.ImageIO

private val ICON_LADDER = listOf(16, 24, 32, 48, 64, 128, 256).map { size ->
    ImageIO.read(object {}.javaClass.getResource("/icon/win-$size.png"))
}

/**
 * The Windows host. Data lives under %LOCALAPPDATA%\Sci-Fin (a per-user, non-roaming folder --
 * nothing here is meant to travel except through §4.7's backup file). §2.5: the click target
 * minimum is 40px here.
 *
 * `--sync-notifications` is the headless mode the two Task Scheduler entries invoke (§11.5): the
 * schedule is re-armed from stored state and the process exits without a window. A normal launch
 * does the one-time setup of the AUMID shortcut and those entries when they are absent. The
 * packaged launcher has no console, so what a launch did goes to `launch.log` in the data folder.
 */
fun main(args: Array<String>) {
    val dataDir = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Sci-Fin").apply { mkdirs() }
    val log: (String) -> Unit = { line -> runCatching { File(dataDir, "launch.log").appendText("${LocalDateTime.now().withNano(0)} $line\n") } }
    val exe = ProcessHandle.current().info().command().orElse("Sci-Fin.exe")
    val dbFile = File(dataDir, "scifin.db")
    val db = buildSciFinDatabase(dbFile, log)
    val scheduler = notificationScheduler(PlatformContext(exe))
    val session = AppSession(db, scheduler, SystemClock, ::newId, WindowsCopyPlace)
    log("launched as $exe with args [${args.joinToString(" ")}]")
    if ("--sync-notifications" in args) {
        runBlocking { session.restoreAfterBoot() }
        log("notifications re-armed from state; automatic copy run if due")
        exitProcess(0)
    }
    (scheduler as? WindowsNotificationScheduler)?.installIfNeeded(log)
    // one thread: each write is its own launch, and on a pool two keystrokes' writes could take the lock out of order
    val store = AppStore(session, db, CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1)), AwtFiles, Integrity.notice(dbFile), Integrity.copies(dbFile))
    runBlocking { store.start() }
    val textScale = parseTextScaleFactor(
        runCatching {
            ProcessBuilder("reg", "query", "HKCU\\Software\\Microsoft\\Accessibility", "/v", "TextScaleFactor")
                .redirectErrorStream(true).start().inputStream.bufferedReader().readText()
        }.getOrDefault(""),
    )
    log("text scale $textScale")

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Sci-Fin",
            state = rememberWindowState(size = openingSize(GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds.let { DpSize(it.width.dp, it.height.dp) })),
            // §3.6: Ctrl+N opens the entry on the last area and target used, as the phone's
            // floating + does; Esc closes any sheet Back closes, without writing
            onKeyEvent = { e ->
                val ui = store.state.value
                when {
                    e.type != KeyEventType.KeyDown -> false
                    e.isCtrlPressed && e.key == Key.N && !ui.locked && ui.sheet == null && ui.data != null && ui.route !is Route.Onboarding && ui.tutorialStop == null -> { store.openSheet(Sheet.Entry()); true }
                    e.key == Key.Escape && store.closesOnBack(ui) -> { store.closeSheet(); true }
                    else -> false
                }
            },
        ) {
            // The mark (design/icon/sci-fin-icon.svg) as the 16-256px ladder tools/build_icon.py renders,
            // each small frame with its own thickening. Window(icon=) is left unset on purpose: Compose
            // would paint one 192px image and Windows would downscale it into a smudge at 16px.
            LaunchedEffect(Unit) { window.iconImages = ICON_LADDER }
            // §4.7: the permission is re-read whenever the app comes back -- it may have been changed in Windows' settings --
            // and "lock after leaving" counts from the moment the window lost focus (another window, or minimised)
            LaunchedEffect(Unit) {
                window.addWindowFocusListener(object : WindowAdapter() {
                    override fun windowGainedFocus(e: WindowEvent) { store.refreshPermission(); store.appReturned() }
                    override fun windowLostFocus(e: WindowEvent) = store.appLeft()
                })
            }
            // §2.5: Compose Desktop fixes fontScale at 1
            // (CMP-6391), so the person's Windows text size is applied here, as read at launch.
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, textScale)) {
                SciFinApp(store, targetDp = 40)
            }
        }
    }
}

/**
 * §4.8's opening size: 1100 × 760
 * names the OUTER window, as AWT sets it, so the content is 1084 × 721. It shrinks to the screen's
 * work area where that is smaller -- a 1366 × 768 laptop leaves about 720 above the taskbar -- so
 * the window never opens running under it. The work area is in the same scaled units as dp.
 */
fun openingSize(workArea: DpSize): DpSize = DpSize(minOf(1100.dp, workArea.width), minOf(760.dp, workArea.height))

/**
 * §2.5: Windows' Settings › Accessibility › Text size, from `reg query`'s output -- a DWORD from 100
 * to 225, absent until the person first moves the slider. Anything unreadable is 100%.
 */
fun parseTextScaleFactor(regOutput: String): Float {
    val m = Regex("""TextScaleFactor\s+REG_DWORD\s+0x([0-9a-fA-F]+)""").find(regOutput) ?: return 1f
    val percent = m.groupValues[1].toInt(16)
    return if (percent in 100..225) percent / 100f else 1f
}
