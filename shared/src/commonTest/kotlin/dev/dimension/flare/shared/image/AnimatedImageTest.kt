package dev.dimension.flare.shared.image

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimatedImageTest {
    @Test
    fun animatedPngAndWebpAreDetectedBeforeConvertingToJpeg() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10)
        assertTrue((png + chunk("acTL")).hasAnimatedChunks())
        assertFalse((png + chunk("IDAT") + chunk("acTL")).hasAnimatedChunks())
        val webp = "RIFF".encodeToByteArray() + ByteArray(4) + "WEBPVP8X".encodeToByteArray() + ByteArray(4)
        assertTrue((webp + byteArrayOf(2)).hasAnimatedChunks())
        assertFalse((webp + byteArrayOf(0)).hasAnimatedChunks())
    }

    @Test
    fun malformedLengthsAndTruncatedHeadersDoNotOverflowOrReadPastEnd() {
        assertFalse(byteArrayOf().hasAnimatedChunks())
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10)
        assertFalse((png + byteArrayOf(-1, -1, -1, -1) + "acTL".encodeToByteArray() + ByteArray(4)).hasAnimatedChunks())
        assertFalse((png + byteArrayOf(0, 0, 0, 1) + "acTL".encodeToByteArray() + ByteArray(4)).hasAnimatedChunks())
    }

    private fun chunk(type: String): ByteArray = ByteArray(4) + type.encodeToByteArray() + ByteArray(4)
}
