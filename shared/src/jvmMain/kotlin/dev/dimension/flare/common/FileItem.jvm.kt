package dev.dimension.flare.common

import java.io.File

public actual class FileItem(
    private val file: File,
) {
    internal actual val name: String? = file.name

    internal actual suspend fun readBytes(): ByteArray = file.readBytes()

    internal actual val type: FileType
        get() {
            val mimeType =
                try {
                    java.nio.file.Files
                        .probeContentType(file.toPath())
                } catch (e: Exception) {
                    null
                }

            if (mimeType != null) {
                if (mimeType.startsWith("image")) return FileType.Image
                if (mimeType.startsWith("video")) return FileType.Video
            }

            val lowerName = file.name.lowercase()
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
