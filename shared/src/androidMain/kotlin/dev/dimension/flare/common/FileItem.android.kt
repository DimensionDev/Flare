package dev.dimension.flare.common

import android.content.Context
import android.net.Uri
import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlin.coroutines.CoroutineContext

actual class FileItem(
    private val context: Context,
    private val uri: Uri,
) {
    actual val name: String? = uri.lastPathSegment
    actual suspend fun readBytes(): ByteArray =
        context.contentResolver.openInputStream(uri)?.use {
            it.readBytes()
        } ?: throw IllegalStateException("Cannot read file: $uri")
}