package dev.dimension.flare.common

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.OptIn
import androidx.compose.runtime.Stable
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import dev.dimension.flare.ui.component.Media3VideoCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Saves remote media to Downloads, reusing the shared Media3 playback cache when possible.
 */
@Stable
@Single
@OptIn(UnstableApi::class)
internal class VideoDownloadHelper(
    @Provided private val context: Context,
    private val media3VideoCacheManager: Media3VideoCacheManager,
) {
    companion object {
        private const val TAG = "VideoDownloadHelper"
    }

    private val nextDownloadId = AtomicLong()

    /**
     * Minimal download callback interface
     */
    interface DownloadCallback {
        /**
         * Download started because the shared playback cache was not ready.
         * @param downloadId Download ID
         */
        fun onDownloadStarted(downloadId: Long) = Unit

        /**
         * Download success callback
         * @param downloadId Download ID
         */
        fun onDownloadSuccess(downloadId: Long)

        /**
         * Download failure callback
         * @param downloadId Download ID
         */
        fun onDownloadFailed(downloadId: Long)
    }

    fun onResume() = Unit

    fun onPause() = Unit

    /**
     * Save a media file. Full progressive cache is exported first; otherwise missing bytes are
     * fetched through the same Media3 cache and written to Downloads.
     */
    suspend fun downloadVideo(
        uri: String,
        fileName: String,
        customHeaders: Map<String, String>? = null,
        callback: DownloadCallback? = null,
    ): Long {
        val downloadId = nextDownloadId.incrementAndGet()
        val success =
            saveMedia(
                uri = uri,
                fileName = fileName,
                customHeaders = customHeaders,
                onDownloadStarted = {
                    withContext(Dispatchers.Main) {
                        callback?.onDownloadStarted(downloadId)
                    }
                },
            )
        withContext(Dispatchers.Main) {
            if (success) {
                callback?.onDownloadSuccess(downloadId)
            } else {
                callback?.onDownloadFailed(downloadId)
            }
        }
        return downloadId
    }

    /**
     * Cancel download
     * @param downloadId Download ID
     */
    fun cancelDownload(downloadId: Long) {
        Log.d(TAG, "Ignoring cancel for completed or non-queued save: $downloadId")
    }

    /**
     * Release resources
     */
    fun release() = Unit

    private suspend fun saveMedia(
        uri: String,
        fileName: String,
        customHeaders: Map<String, String>?,
        onDownloadStarted: suspend () -> Unit,
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (isAdaptiveStream(uri)) {
                Log.w(TAG, "Adaptive streams cannot be exported as a single media file: $uri")
                return@withContext false
            }

            val headers = customHeaders?.takeIf { it.isNotEmpty() }?.toMap()
            val mimeType = getMimeType(uri = uri, fileName = fileName)

            if (media3VideoCacheManager.isFullyCached(uri = uri, customHeaders = headers)) {
                val savedFromCache =
                    saveToDownloads(fileName = fileName, mimeType = mimeType) { outputStream ->
                        exportToStream(
                            uri = uri,
                            customHeaders = headers,
                            outputStream = outputStream,
                            cacheOnly = true,
                        )
                    }
                if (savedFromCache) {
                    Log.d(TAG, "Saved from Media3 cache: $uri")
                    return@withContext true
                }
            }

            onDownloadStarted()

            saveToDownloads(fileName = fileName, mimeType = mimeType) { outputStream ->
                exportToStream(
                    uri = uri,
                    customHeaders = headers,
                    outputStream = outputStream,
                    cacheOnly = false,
                )
            }
        }

    private fun exportToStream(
        uri: String,
        customHeaders: Map<String, String>?,
        outputStream: OutputStream,
        cacheOnly: Boolean,
    ): Boolean {
        val dataSource =
            if (cacheOnly) {
                media3VideoCacheManager
                    .cacheOnlyDataSourceFactory(customHeaders)
                    .createDataSource()
            } else {
                media3VideoCacheManager
                    .downloadingDataSourceFactory(customHeaders)
                    .createDataSourceForDownloading()
            }
        return try {
            dataSource.open(
                DataSpec
                    .Builder()
                    .setUri(uri)
                    .build(),
            )
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = dataSource.read(buffer, 0, buffer.size)
                if (bytesRead == C.RESULT_END_OF_INPUT) {
                    break
                }
                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
            outputStream.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export media: $uri", e)
            false
        } finally {
            runCatching {
                dataSource.close()
            }
        }
    }

    private fun saveToDownloads(
        fileName: String,
        mimeType: String,
        writer: (OutputStream) -> Boolean,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStoreDownloads(fileName = fileName, mimeType = mimeType, writer = writer)
        } else {
            saveToPublicDownloads(fileName = fileName, writer = writer)
        }

    private fun saveToMediaStoreDownloads(
        fileName: String,
        mimeType: String,
        writer: (OutputStream) -> Boolean,
    ): Boolean {
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val contentResolver = context.contentResolver
        val outputUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        val success =
            try {
                contentResolver.openOutputStream(outputUri)?.use(writer) == true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write MediaStore output: $outputUri", e)
                false
            }

        if (success) {
            val publishValues =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
            contentResolver.update(outputUri, publishValues, null, null)
        } else {
            contentResolver.delete(outputUri, null, null)
        }
        return success
    }

    private fun saveToPublicDownloads(
        fileName: String,
        writer: (OutputStream) -> Boolean,
    ): Boolean {
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!directory.exists() && !directory.mkdirs()) {
            return false
        }
        val file = File(directory, fileName)
        val success =
            try {
                FileOutputStream(file).use(writer)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write public Downloads output: $file", e)
                false
            }
        if (!success) {
            file.delete()
        }
        return success
    }

    private fun isAdaptiveStream(uri: String): Boolean {
        val normalized = uri.substringBefore("?").substringBefore("#").lowercase(Locale.ROOT)
        val extension = getExtension(uri)
        return extension == "m3u8" || extension == "mpd" || normalized.endsWith(".ism/manifest")
    }

    private fun getMimeType(
        uri: String,
        fileName: String,
    ): String {
        val extension = getExtension(fileName) ?: getExtension(uri)
        return extension
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
            ?: when (extension) {
                "mov" -> "video/quicktime"
                "webm" -> "video/webm"
                "m4v", "mp4" -> "video/mp4"
                "gif" -> "image/gif"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "aac" -> "audio/aac"
                "wav" -> "audio/wav"
                else -> "application/octet-stream"
            }
    }

    private fun getExtension(value: String): String? {
        val cleanValue = value.substringBefore("?").substringBefore("#")
        val fromMimeTypeMap = MimeTypeMap.getFileExtensionFromUrl(cleanValue)
        if (fromMimeTypeMap.isNotBlank()) {
            return fromMimeTypeMap.lowercase(Locale.ROOT)
        }
        val name = Uri.parse(cleanValue).lastPathSegment ?: cleanValue.substringAfterLast("/")
        val separatorIndex = maxOf(name.lastIndexOf('.'), name.lastIndexOf('@'))
        return name
            .takeIf { separatorIndex >= 0 && separatorIndex < name.length - 1 }
            ?.substring(separatorIndex + 1)
            ?.lowercase(Locale.ROOT)
    }
}
