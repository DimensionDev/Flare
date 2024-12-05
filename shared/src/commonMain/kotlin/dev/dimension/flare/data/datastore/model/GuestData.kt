package dev.dimension.flare.data.datastore.model

import androidx.datastore.core.okio.OkioSerializer
import dev.dimension.flare.model.PlatformType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okio.BufferedSink
import okio.BufferedSource

private const val DEFAULT_GUEST_HOST = "mastodon.social"
private val DEFAULT_GUEST_PLATFORM_TYPE = PlatformType.Mastodon
internal val supportedGuestPlatforms = listOf(PlatformType.Mastodon)

@Serializable
internal data class GuestData(
    val host: String,
    val platformType: PlatformType,
)

@OptIn(ExperimentalSerializationApi::class)
internal data object GuestDataSerializer : OkioSerializer<GuestData> {
    override val defaultValue: GuestData
        get() = GuestData(DEFAULT_GUEST_HOST, DEFAULT_GUEST_PLATFORM_TYPE)

    override suspend fun readFrom(source: BufferedSource): GuestData = ProtoBuf.decodeFromByteArray(source.readByteArray())

    override suspend fun writeTo(
        t: GuestData,
        sink: BufferedSink,
    ) {
        sink.write(ProtoBuf.encodeToByteArray(t))
    }
}
