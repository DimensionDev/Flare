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
}
