@file:OptIn(ExperimentalTime::class)

package app.scifin.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** §12: `loggedAt` is an instant, stored and serialised as epoch milliseconds. */
object InstantMillisSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("app.scifin.Instant", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.toEpochMilliseconds())
    override fun deserialize(decoder: Decoder): Instant = Instant.fromEpochMilliseconds(decoder.decodeLong())
}
