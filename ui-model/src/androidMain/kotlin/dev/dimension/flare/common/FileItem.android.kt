package dev.dimension.flare.common

import android.content.Context
import android.net.Uri
import java.io.File
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.source
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
        this.name = uri.lastPathSegment
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

    public actual fun openSource(): OkioSource = source.openSource()

    public actual suspend fun size(): Long = source.size()

    internal sealed interface Source {
        suspend fun readBytes(): ByteArray

        fun openSource(): OkioSource

        suspend fun size(): Long

        data class UriSource(
            private val context: Context,
            private val uri: Uri,
        ) : Source {
            override suspend fun readBytes(): ByteArray =
                context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes()
                } ?: throw IllegalStateException("Cannot read file: $uri")

            override fun openSource(): OkioSource =
                context.contentResolver.openInputStream(uri)?.source()
                    ?: throw IllegalStateException("Cannot read file: $uri")

            override suspend fun size(): Long {
                val declared = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                if (declared >= 0) return declared
                return openSource().use { source ->
                    val buffer = Buffer()
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer, 8_192)
                        if (read == -1L) break
                        total += read
                        buffer.clear()
                    }
                    total
                }
            }
        }

        data class PathSource(
            private val path: String,
        ) : Source {
            override suspend fun readBytes(): ByteArray = File(path).readBytes()

            override fun openSource(): okio.Source = FileSystem.SYSTEM.source(path.toPath())

            override suspend fun size(): Long = File(path).length()
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

public actual fun fileItemFromStorage(
    path: String,
    name: String?,
    type: FileType,
    mimeType: String?,
    loader: suspend () -> ByteArray,
): FileItem =
    FileItem(
        name = name,
        type = type,
        source = FileItem.Source.PathSource(path),
        mimeType = mimeType,
    )
