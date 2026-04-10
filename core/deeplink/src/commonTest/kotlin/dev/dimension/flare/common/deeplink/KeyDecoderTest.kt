package dev.dimension.flare.common.deeplink

import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyDecoderTest {
    @Test
    fun decodeElementIndex_skipsMissingKeys() {
        val decoder =
            KeyDecoder(
                arguments =
                    mapOf(
                        "second" to 2,
                        "third" to true,
                    ),
            )
        val descriptor =
            buildClassSerialDescriptor("TestDescriptor") {
                element<String>("first")
                element<Int>("second")
                element<Boolean>("third")
            }

        val firstIndex = decoder.decodeElementIndex(descriptor)
        assertEquals(1, firstIndex)
        assertEquals(2, decoder.decodeValue())

        val secondIndex = decoder.decodeElementIndex(descriptor)
        assertEquals(2, secondIndex)
        assertEquals(true, decoder.decodeValue())

        val doneIndex = decoder.decodeElementIndex(descriptor)
        assertEquals(CompositeDecoder.DECODE_DONE, doneIndex)
    }
}
