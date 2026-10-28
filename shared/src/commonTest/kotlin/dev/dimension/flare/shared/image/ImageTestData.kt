package dev.dimension.flare.shared.image

import kotlin.io.encoding.Base64

internal val transparentTestPng: ByteArray
    get() = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAXpeqz8AAAAASUVORK5CYII=")

internal val wideTestPng: ByteArray
    get() =
        Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAEAEAAAABCAYAAACx4wBCAAAAJ0lEQVR4nO3BMQEAAADCoPVPbQ0PoAAAAAAAAAAAAAAAAAAAAIALA0AFAAFdEb2kAAAAAElFTkSuQmCC",
        )

internal fun ByteArray.withTestExifOrientation(
    orientation: Int,
    beforeJfif: Boolean = false,
): ByteArray {
    val exif = "45786966000049492a0008000000010012010300010000000100000000000000".hexToByteArray()
    exif[24] = orientation.toByte()
    // JFIF requires APP0 immediately after SOI; insert EXIF after it when present.
    val offset =
        if (!beforeJfif && this[2] == 0xff.toByte() && this[3] == 0xe0.toByte()) {
            4 + ((this[4].toInt() and 255) shl 8) + (this[5].toInt() and 255)
        } else {
            2
        }
    return copyOfRange(0, offset) + byteArrayOf(0xff.toByte(), 0xe1.toByte(), 0, (exif.size + 2).toByte()) + exif +
        copyOfRange(offset, size)
}
