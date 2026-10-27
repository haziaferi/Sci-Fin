@file:OptIn(ExperimentalComposeUiApi::class)

package app.scifin

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import app.scifin.app.AppSession
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.data.buildInMemorySciFinDatabase
import app.scifin.model.Theme
import app.scifin.notify.NotificationPermission
import app.scifin.ui.SciFinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * §11.5 step 2, as decided on 2026-09-15 after the device critique: the page explains first
 * and the OS prompt follows a tap. Showing the page must not, by itself, ask the OS -- when it
 * did, the rationale was only ever seen dimmed behind the system dialog.
 */
class PermissionStepTest {
    /** A scheduler that has not been asked yet and counts how often it is. */
    private class UnaskedScheduler : RecordingScheduler() {
        var requests = 0
        override fun permissionState() = NotificationPermission.NOT_ASKED
        override suspend fun requestPermission(): NotificationPermission { requests++; return NotificationPermission.GRANTED }
    }

    @Test fun `rendering step 2 shows the rationale and waits -- the OS is asked only on Continue`() {
        val db = buildInMemorySciFinDatabase(); val sched = UnaskedScheduler()
        val store = AppStore(AppSession(db, sched, FakeClock(LocalDate(2026, 9, 15)), Ids("p")::next), db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        runBlocking { store.start(); store.createProfile("Alex", Theme.SWISS_BLUE, forNewProfile = false).join() }
        assertEquals(Route.Onboarding(2), store.state.value.route)
        ImageComposeScene(width = 360, height = 800).use { scene ->
            scene.setContent { SciFinApp(store, targetDp = 48, systemDark = false) }
            scene.render()
            assertEquals(0, sched.requests, "the page asked the OS on arrival")
            assertEquals(Route.Onboarding(2), store.state.value.route, "the flow moved on without a tap")
        }
        runBlocking { store.requestNotificationPermissionAndContinue().join() }
        assertEquals(1, sched.requests)
        assertEquals(0, store.state.value.tourSlide)
    }
}
