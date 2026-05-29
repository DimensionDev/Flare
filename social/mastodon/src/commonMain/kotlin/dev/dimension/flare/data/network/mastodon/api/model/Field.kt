package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

@Serializable
internal data class Field(
    val name: String? = null,
    val value: String? = null,
    @SerialName("verified_at")
    @Serializable(with = DateSerializer::class)
    val verifiedAt: Instant? = null,
)

internal object DateSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Date", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val str = decoder.decodeString()
        return Instant.parse(str)
    }

    override fun serialize(
        encoder: Encoder,
        value: Instant,
    ) {
        encoder.encodeString(value.toString())
    }
}
