package dev.dimension.flare.ui.model

import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
class UiTranslatableTextSerializationTest {
    @Test
    fun protobufRoundTripPreservesOriginalAndTranslation() {
        val value =
            UiTranslatableText(
                original = "original".toUiPlainText(listOf("en")),
                translation = "译文".toUiPlainText(listOf("zh-CN")),
            )

        val decoded =
            ProtoBuf.decodeFromByteArray(
                UiTranslatableText.serializer(),
                ProtoBuf.encodeToByteArray(UiTranslatableText.serializer(), value),
            )

        assertEquals(value, decoded)
    }
}
