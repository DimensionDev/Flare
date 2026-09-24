package dev.dimension.flare.common

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import okio.source
import java.io.File
import okio.Source as OkioSource

public actual class FileItem {
    private val source: Source
    public actual val name: String?
    public actual val type: FileType
    public actual val mimeType: String?

    public constructor(
        context: Context,
        uri: Uri,
    ) {
        this.name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment
        this.mimeType = context.contentResolver.getType(uri)
        this.type = resolveType(this.mimeType, uri)
        this.source = Source.UriSource(context, uri)
    }

    internal constructor(
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

    public actual suspend fun uploadMedia(): UploadMedia = UploadMedia.fromSource(name, mimeType, source.size(), source::open)

    internal sealed interface Source {
        suspend fun readBytes(): ByteArray

        fun open(): OkioSource

        fun size(): Long?

        data class UriSource(
            private val context: Context,
            private val uri: Uri,
        ) : Source {
            override fun open(): OkioSource =
                checkNotNull(context.contentResolver.openInputStream(uri)) { "Cannot read file: $uri" }.source()

            override fun size(): Long? =
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                    if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
                }

            override suspend fun readBytes(): ByteArray =
                context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes()
                } ?: throw IllegalStateException("Cannot read file: $uri")
        }

        data class PathSource(
            private val path: String,
        ) : Source {
            override fun open(): OkioSource = File(path).source()

            override fun size(): Long = File(path).length()

            override suspend fun readBytes(): ByteArray = File(path).readBytes()
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
