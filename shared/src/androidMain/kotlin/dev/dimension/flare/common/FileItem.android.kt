package dev.dimension.flare.common

import android.content.Context
import android.net.Uri
import java.io.File

public actual class FileItem {
    private val source: Source
    internal actual val name: String?
    internal actual val type: FileType

    public constructor(
        context: Context,
        uri: Uri,
    ) {
        this.name = uri.lastPathSegment
        this.type = resolveType(context = context, uri = uri)
        this.source = Source.UriSource(context, uri)
    }

    internal constructor(
        name: String?,
        type: FileType,
        source: Source,
    ) {
        this.name = name
        this.type = type
        this.source = source
    }

    internal actual suspend fun readBytes(): ByteArray = source.readBytes()

    internal sealed interface Source {
        suspend fun readBytes(): ByteArray

        data class UriSource(
            private val context: Context,
            private val uri: Uri,
        ) : Source {
            override suspend fun readBytes(): ByteArray =
                context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes()
                } ?: throw IllegalStateException("Cannot read file: $uri")
        }

        data class PathSource(
            private val path: String,
        ) : Source {
            override suspend fun readBytes(): ByteArray = File(path).readBytes()
        }
    }

    private companion object {
        fun resolveType(
            context: Context,
            uri: Uri,
        ): FileType {
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
}
