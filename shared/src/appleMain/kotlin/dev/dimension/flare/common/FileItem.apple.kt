package dev.dimension.flare.common

actual class FileItem(
    actual val name: String?,
    private val data: ByteArray,
) {
    actual suspend fun readBytes(): ByteArray {
        return data
    }
}
