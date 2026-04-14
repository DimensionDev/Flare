package dev.dimension.flare.common

import java.io.File

public actual class FileItem(
    private val file: File,
    internal actual val name: String? = file.name,
    internal actual val type: FileType = resolveType(file.name),
) {
    internal actual suspend fun readBytes(): ByteArray = file.readBytes()

    private companion object {
        fun resolveType(fileName: String): FileType {
            val lowerName = fileName.lowercase()
            return when {
                lowerName.endsWith(".jpg") ||
                    lowerName.endsWith(".jpeg") ||
                    lowerName.endsWith(".png") ||
                    lowerName.endsWith(".webp") ||
                    lowerName.endsWith(".gif") -> FileType.Image

                lowerName.endsWith(
                    ".mp4",
                ) ||
                    lowerName.endsWith(".mov") ||
                    lowerName.endsWith(".avi") ||
                    lowerName.endsWith(".mkv") -> FileType.Video

                else -> FileType.Other
            }
        }
    }
}
