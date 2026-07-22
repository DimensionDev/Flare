package dev.dimension.flare.ui.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
class UiTimelineV2VisibilitySerializationTest {
    @Test
    fun protobufKeepsLegacyChannelValue() {
        val decoded =
            ProtoBuf.decodeFromByteArray(
                StoredVisibility.serializer(),
                byteArrayOf(0x08, 0x04),
            )

        assertEquals(UiTimelineV2.Post.Visibility.Channel, decoded.visibility)
    }
}

@Serializable
private data class StoredVisibility(
    val visibility: UiTimelineV2.Post.Visibility,
)
