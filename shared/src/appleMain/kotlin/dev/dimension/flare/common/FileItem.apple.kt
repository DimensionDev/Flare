package dev.dimension.flare.common

public actual class FileItem(
    internal actual val name: String?,
    private val data: ByteArray,
    internal actual val type: FileType,
) {
    internal actual suspend fun readBytes(): ByteArray = data
}
