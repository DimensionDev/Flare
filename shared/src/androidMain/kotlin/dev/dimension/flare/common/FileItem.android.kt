package dev.dimension.flare.common

import android.content.Context
import android.net.Uri

public actual class FileItem(
    private val context: Context,
    private val uri: Uri,
) {
    internal actual val name: String? = uri.lastPathSegment

    internal actual suspend fun readBytes(): ByteArray =
        context.contentResolver.openInputStream(uri)?.use {
            it.readBytes()
        } ?: throw IllegalStateException("Cannot read file: $uri")

    internal actual val type: FileType
        get() {
            val mimeType = context.contentResolver.getType(uri)
            return when {
                mimeType?.startsWith("image/") == true -> FileType.Image
                mimeType?.startsWith("video/") == true -> FileType.Video
                else -> {
                    val extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                    val type =
                        android.webkit.MimeTypeMap
                            .getSingleton()
                            .getMimeTypeFromExtension(extension?.lowercase())
                    when {
                        type?.startsWith("image/") == true -> FileType.Image
                        type?.startsWith("video/") == true -> FileType.Video
                        else -> FileType.Other
                    }
                }
            }
        }
}
