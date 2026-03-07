package dev.dimension.flare.data.datastore.model

import androidx.datastore.core.okio.OkioSerializer
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okio.BufferedSink
import okio.BufferedSource

@Serializable
internal data class ComposeConfigData(
    val visibility: UiTimelineV2.Post.Visibility =
        UiTimelineV2.Post.Visibility.Public,
)

@OptIn(ExperimentalSerializationApi::class)
internal data object ComposeConfigDataSerializer : OkioSerializer<ComposeConfigData> {
    override val defaultValue: ComposeConfigData
        get() = ComposeConfigData()

    override suspend fun readFrom(source: BufferedSource): ComposeConfigData = ProtoBuf.decodeFromByteArray(source.readByteArray())

    override suspend fun writeTo(
        t: ComposeConfigData,
        sink: BufferedSink,
    ) {
        sink.write(ProtoBuf.encodeToByteArray(t))
    }
}
