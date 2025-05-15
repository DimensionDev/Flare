package dev.dimension.flare.data.datastore.model

import androidx.datastore.core.okio.OkioSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okio.BufferedSink
import okio.BufferedSource

private const val DEFAULT_SERVER_URL = "https://api.flare.moe"

@Serializable
internal data class FlareConfig(
    val serverUrl: String,
)

@OptIn(ExperimentalSerializationApi::class)
internal data object FlareConfigSerializer : OkioSerializer<FlareConfig> {
    override val defaultValue: FlareConfig
        get() = FlareConfig(DEFAULT_SERVER_URL)

    override suspend fun readFrom(source: BufferedSource): FlareConfig = ProtoBuf.decodeFromByteArray(source.readByteArray())

    override suspend fun writeTo(
        t: FlareConfig,
        sink: BufferedSink,
    ) {
        sink.write(ProtoBuf.encodeToByteArray(t))
    }
}
