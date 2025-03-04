package dev.dimension.flare.common

import java.io.File

public actual class FileItem(
    private val file: File,
) {
    internal actual val name: String? = file.name

    internal actual suspend fun readBytes(): ByteArray = file.readBytes()
}
