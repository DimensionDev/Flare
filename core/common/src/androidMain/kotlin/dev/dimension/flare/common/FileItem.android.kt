package dev.dimension.flare.common

import android.content.Context
import android.net.Uri
import java.io.File

public actual class FileItem {
    private val source: Source
    public actual val name: String?
    public actual val type: FileType
    public actual val mimeType: String?

    public constructor(
        context: Context,
        uri: Uri,
    ) {
        this.name = uri.lastPathSegment
        this.mimeType = context.contentResolver.getType(uri)
        this.type = resolveType(this.mimeType, uri)
        this.source = Source.UriSource(context, uri)
    }

    public constructor(
        name: String?,
        type: FileType,
        path: String,
        mimeType: String? = null,
    ) : this(
        name = name,
        type = type,
        source = Source.PathSource(path),
        mimeType = mimeType,
    )

    public actual constructor(
        name: String?,
        type: FileType,
        loader: suspend () -> ByteArray,
        mimeType: String?,
    ) : this(
        name = name,
        type = type,
        source = Source.LoaderSource(loader),
        mimeType = mimeType,
    )

    private constructor(
        name: String?,
        type: FileType,
        source: Source,
        mimeType: String? = null,
    ) {
        this.name = name
        this.type = type
        this.source = source
        this.mimeType = mimeType
    }

    public actual suspend fun readBytes(): ByteArray = source.readBytes()

    private sealed interface Source {
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

        data class LoaderSource(
            private val loader: suspend () -> ByteArray,
        ) : Source {
            override suspend fun readBytes(): ByteArray = loader()
        }
    }

    private companion object {
        fun resolveType(
            mimeType: String?,
            uri: Uri,
        ): FileType =
            when {
                mimeType?.startsWith("image/") == true -> {
                    FileType.Image
                }

                mimeType?.startsWith("video/") == true -> {
                    FileType.Video
                }

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
