package dev.dimension.flare.common

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * Download Manager Helper - Simplified Version
 * Encapsulates Android's DownloadManager, provides simple video download functionality
 */
internal class VideoDownloadHelper(
    private val context: Context,
) {
    companion object {
        private const val TAG = "VideoDownloadHelper"
    }

    // System Download Manager
    private val downloadManager: DownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    // Map of download IDs to callbacks
    private val downloadCallbacks = mutableMapOf<Long, DownloadCallback>()

    // Broadcast receiver for download completion notifications
    private val downloadReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                Log.d(TAG, "Download completed: ${intent.action}")
                if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)

                    if (downloadId != -1L && downloadCallbacks.containsKey(downloadId)) {
                        val status = getDownloadStatus(downloadId)
                        val callback = downloadCallbacks[downloadId]

                        if (status.isCompleted) {
                            callback?.onDownloadSuccess(downloadId)
                        } else if (status.isFailed) {
                            callback?.onDownloadFailed(downloadId)
                        }

                        // Clear callback
                        downloadCallbacks.remove(downloadId)
                    }
                }
            }
        }

    /**
     * Minimal download callback interface
     */
    interface DownloadCallback {
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

    fun onResume() {
        // Register download completion broadcast receiver
        ContextCompat.registerReceiver(
            context,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun onPause() {
        context.unregisterReceiver(downloadReceiver)
    }

    /**
     * Start downloading a video
     * @param uri Video URL
     * @param fileName File name
     * @param callback Download callback
     * @return Download ID
     */
    fun downloadVideo(
        uri: String,
        fileName: String,
        callback: DownloadCallback? = null,
    ): Long {
        val request = createDownloadRequest(uri, fileName)
        val downloadId = downloadManager.enqueue(request)

        // Save callback
        if (callback != null) {
            downloadCallbacks[downloadId] = callback
        }

        Log.d(TAG, "Started download: $uri, id: $downloadId")
        return downloadId
    }

    /**
     * Create download request
     */
    private fun createDownloadRequest(
        uri: String,
        fileName: String,
    ): DownloadManager.Request {
        val request =
            DownloadManager
                .Request(uri.toUri())
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        // Set download location to public download directory
        request.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            fileName,
        )

        // Set network types
        request.setAllowedNetworkTypes(
            DownloadManager.Request.NETWORK_WIFI or
                DownloadManager.Request.NETWORK_MOBILE,
        )

        // Get MIME type using MimeTypeMap
        val mimeType = getMimeType(uri)
        if (mimeType != null) {
            request.setMimeType(mimeType)
        }

        // Allow metered networks and roaming
        request.setAllowedOverMetered(true)
        request.setAllowedOverRoaming(true)

        return request
    }

    /**
     * Get MIME type using MimeTypeMap
     */
    private fun getMimeType(url: String): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        } else {
            null
        }
    }

    /**
     * Get download status
     */
    private fun getDownloadStatus(downloadId: Long): DownloadStatus {
        val query = DownloadManager.Query().setFilterById(downloadId)

        try {
            downloadManager.query(query).use { cursor ->
                if (cursor.moveToFirst()) {
                    try {
                        val statusColumnIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        val statusCode = cursor.getInt(statusColumnIndex)
                        return DownloadStatus(statusCode)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting download status", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying download", e)
        }

        return DownloadStatus(DownloadManager.STATUS_FAILED)
    }

    /**
     * Cancel download
     * @param downloadId Download ID
     */
    fun cancelDownload(downloadId: Long) {
        downloadManager.remove(downloadId)
        downloadCallbacks.remove(downloadId)
    }

    /**
     * Release resources
     * Call this when download functionality is no longer needed (e.g., in Activity.onDestroy)
     */
    fun release() {
        try {
            context.unregisterReceiver(downloadReceiver)
            downloadCallbacks.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering download receiver", e)
        }
    }

    /**
     * Simplified download status class
     */
    private data class DownloadStatus(
        val statusCode: Int,
    ) {
        val isCompleted: Boolean get() = statusCode == DownloadManager.STATUS_SUCCESSFUL
        val isFailed: Boolean get() = statusCode == DownloadManager.STATUS_FAILED
    }
}
