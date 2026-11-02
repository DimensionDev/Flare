package dev.dimension.flare.shared.image

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

public actual fun imageMetadata(bytes: ByteArray): ImageMetadata? =
    ImageIO.createImageInputStream(bytes.inputStream())?.use { stream ->
        val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return@use null
        try {
            reader.input = stream
            ImageMetadata(
                reader.getWidth(0),
                reader.getHeight(0),
                bytes.jpegOrientation(),
                reader.getNumImages(true) > 1 || bytes.hasAnimatedChunks(),
            )
        } finally {
            reader.dispose()
        }
    }

private fun ByteArray.jpegOrientation(): Int {
    if (size < 4 || this[0] != 0xff.toByte() || this[1] != 0xd8.toByte()) return 1
    var offset = 2
    // ImageIO's metadata parser rejects some JPEGs that its pixel decoder accepts.
    while (offset < size) {
        if (this[offset++] != 0xff.toByte()) return 1
        while (offset < size && this[offset] == 0xff.toByte()) offset++
        if (offset == size) return 1
        val marker = this[offset++].toInt() and 255
        if (marker == 0xda || marker == 0xd9) break
        if (marker == 0x01 || marker in 0xd0..0xd8) continue
        if (offset > size - 2) return 1
        val length = ((this[offset].toInt() and 255) shl 8) or (this[offset + 1].toInt() and 255)
        if (length < 2 || length > size - offset) return 1
        if (marker == 0xe1) copyOfRange(offset + 2, offset + length).exifOrientation()?.let { return it }
        offset += length
    }
    return 1
}

private fun ByteArray.exifOrientation(): Int? =
    runCatching {
        if (size < 14 || copyOfRange(0, 6).decodeToString() != "Exif\u0000\u0000") return@runCatching null
        val data = ByteBuffer.wrap(this, 6, size - 6).slice()
        data.order(
            when (data.getShort(0).toInt() and 0xffff) {
                0x4949 -> ByteOrder.LITTLE_ENDIAN
                0x4d4d -> ByteOrder.BIG_ENDIAN
                else -> return@runCatching null
            },
        )
        if (data.getShort(2).toInt() != 42) return@runCatching null
        val directory = data.getInt(4)
        if (directory !in 8..(data.limit() - 2)) return@runCatching null
        val count = data.getShort(directory).toInt() and 0xffff
        if (count > (data.limit() - directory - 2) / 12) return@runCatching null
        repeat(count) { index ->
            val offset = directory + 2 + index * 12
            if (data.getShort(offset).toInt() and 0xffff == 0x0112 && data.getShort(offset + 2).toInt() == 3 &&
                data.getInt(offset + 4) == 1
            ) {
                return@runCatching (data.getShort(offset + 8).toInt() and 0xffff).takeIf { it in 1..8 }
            }
        }
        null
    }.getOrNull()
