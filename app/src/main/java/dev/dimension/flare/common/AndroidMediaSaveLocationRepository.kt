package dev.dimension.flare.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

internal enum class MediaSaveLocationMode {
    DefaultDownloads,
    CustomDirectory,
    AskEveryTime,
}

internal data class MediaSaveLocationState(
    val mode: MediaSaveLocationMode,
    val displayName: String?,
    val directoryUri: Uri?,
)

@Stable
@Single
internal class AndroidMediaSaveLocationRepository(
    @Provided private val context: Context,
) {
    private val prefs =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mutableState = MutableStateFlow(readState())
    val state: StateFlow<MediaSaveLocationState> = mutableState.asStateFlow()

    fun setDefaultDownloads() {
        prefs
            .edit()
            .putString(KEY_MODE, MediaSaveLocationMode.DefaultDownloads.name)
            .remove(KEY_DIRECTORY_URI)
            .remove(KEY_DISPLAY_NAME)
            .apply()
        mutableState.value = readState()
    }

    fun setAskEveryTime() {
        prefs
            .edit()
            .putString(KEY_MODE, MediaSaveLocationMode.AskEveryTime.name)
            .apply()
        mutableState.value = readState()
    }

    fun setCustomDirectory(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs
            .edit()
            .putString(KEY_MODE, MediaSaveLocationMode.CustomDirectory.name)
            .putString(KEY_DIRECTORY_URI, uri.toString())
            .putString(KEY_DISPLAY_NAME, displayName(uri))
            .apply()
        mutableState.value = readState()
    }

    fun clearCustomDirectoryIfMatches(uri: Uri) {
        if (mutableState.value.directoryUri == uri) {
            setDefaultDownloads()
        }
    }

    private fun readState(): MediaSaveLocationState {
        val mode =
            prefs
                .getString(KEY_MODE, MediaSaveLocationMode.DefaultDownloads.name)
                ?.let { runCatching { MediaSaveLocationMode.valueOf(it) }.getOrNull() }
                ?: MediaSaveLocationMode.DefaultDownloads
        val uri = prefs.getString(KEY_DIRECTORY_URI, null)?.let(Uri::parse)
        return MediaSaveLocationState(
            mode = mode,
            displayName = prefs.getString(KEY_DISPLAY_NAME, null),
            directoryUri = uri,
        )
    }

    private fun displayName(uri: Uri): String {
        val resolver = context.contentResolver
        runCatching {
            resolver
                .query(
                    DocumentsContract.buildDocumentUriUsingTree(
                        uri,
                        DocumentsContract.getTreeDocumentId(uri),
                    ),
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
        }
        return DocumentsContract
            .getTreeDocumentId(uri)
            .substringAfterLast(':')
            .takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
            ?: "Custom folder"
    }

    private companion object {
        const val PREFS_NAME = "media_save_location"
        const val KEY_MODE = "mode"
        const val KEY_DIRECTORY_URI = "directory_uri"
        const val KEY_DISPLAY_NAME = "display_name"
    }
}
