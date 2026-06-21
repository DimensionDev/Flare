package dev.dimension.flare.ui.screen.media

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@Composable
internal fun MediaScreen(
    uri: String,
    previewUrl: String?,
    customHeaders: Map<String, String>?,
    onDismiss: () -> Unit,
    toAltText: (UiMedia) -> Unit = {},
) {
    RawMediaScreen(
        medias =
            persistentListOf(
                UiMedia.Image(
                    url = uri,
                    previewUrl = previewUrl ?: uri,
                    description = null,
                    height = 0f,
                    width = 0f,
                    sensitive = false,
                    customHeaders = customHeaders?.toImmutableMap(),
                ),
            ),
        index = 0,
        preview = previewUrl,
        onDismiss = onDismiss,
        toAltText = toAltText,
        uriHandler = LocalUriHandler.current,
    )
}

@Composable
internal fun RawMediaScreen(
    medias: ImmutableList<UiMedia>,
    index: Int,
    preview: String?,
    onDismiss: () -> Unit,
    toAltText: (UiMedia) -> Unit,
    uriHandler: UriHandler,
) {
    MediaViewerScreen(
        medias = UiState.Success(medias),
        initialIndex = index.coerceIn(0, (medias.size - 1).coerceAtLeast(0)),
        preview = preview,
        onDismiss = onDismiss,
        toAltText = toAltText,
        uriHandler = uriHandler,
        fileName = { it.rawMediaFileName() },
    )
}

private fun UiMedia.rawMediaFileName(): String {
    val fallbackExtension =
        when (this) {
            is UiMedia.Audio -> "mp3"
            is UiMedia.Gif -> "gif"
            is UiMedia.Image -> "jpg"
            is UiMedia.Video -> "mp4"
        }
    val path = url.substringBefore("?").substringBefore("#")
    var fileName = path.substringAfterLast("/").substringAfterLast("\\")
    val lastAtIndex = fileName.lastIndexOf('@')
    val lastDotIndex = fileName.lastIndexOf('.')
    if (lastAtIndex > lastDotIndex && lastAtIndex < fileName.length - 1) {
        fileName = fileName.substring(0, lastAtIndex) + "." + fileName.substring(lastAtIndex + 1)
    }
    fileName = fileName.ifBlank { "media" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return if (fileName.contains(".")) {
        fileName
    } else {
        "$fileName.$fallbackExtension"
    }
}

private fun getMimeType(byteArray: ByteArray): String {
    val options =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
    BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, options)
    return options.outMimeType?.lowercase() ?: "image/jpeg"
}

internal fun saveByteArrayToDownloads(
    context: Context,
    byteArray: ByteArray,
    fileName: String,
    mimeType: String = getMimeType(byteArray),
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        try {
            context.contentResolver.openOutputStream(uri ?: return)?.use { outputStream ->
                outputStream.write(byteArray)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    } else {
        val directory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(directory, fileName)
        try {
            FileOutputStream(file).use { outputStream ->
                outputStream.write(byteArray)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
