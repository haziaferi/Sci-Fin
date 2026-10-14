@file:OptIn(ExperimentalTime::class)

package app.scifin.engine

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The real clock: §12's `date` is the device-local day, `loggedAt` a UTC instant. */
object SystemClock : Clock {
    override fun today() = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    override fun now(): Instant = kotlin.time.Clock.System.now()
}

/** §12: ids are UUID strings, unique across Profiles by construction. */
fun newId(): String = UUID.randomUUID().toString()
