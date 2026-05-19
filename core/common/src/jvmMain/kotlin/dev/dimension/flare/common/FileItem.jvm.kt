package dev.dimension.flare.common

import java.io.File
import java.net.URLConnection
import java.nio.file.Files

public actual class FileItem private constructor(
    private val loader: suspend () -> ByteArray,
    public actual val name: String?,
    public actual val type: FileType,
    public actual val mimeType: String?,
) {
    public constructor(
        file: File,
        name: String? = file.name,
        type: FileType = resolveType(file.name),
        mimeType: String? = resolveMimeType(file),
    ) : this({ file.readBytes() }, name, type, mimeType)

    public actual constructor(
        name: String?,
        type: FileType,
        loader: suspend () -> ByteArray,
        mimeType: String?,
    ) : this(loader, name, type, mimeType)

    public actual suspend fun readBytes(): ByteArray = loader()

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

        fun resolveMimeType(file: File): String? =
            runCatching { Files.probeContentType(file.toPath()) }.getOrNull()
                ?: URLConnection.guessContentTypeFromName(file.name)
    }
}
