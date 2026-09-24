package dev.dimension.flare.data.network.xqt

import dev.dimension.flare.common.UploadMedia
import dev.dimension.flare.data.network.xqt.api.MediaApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class XQTMediaUploader(
    private val service: MediaApi,
) {
    suspend fun upload(media: UploadMedia): String {
        media.validate(
            platform = "X",
            acceptedTypes = listOf("image/jpeg", "image/png", "image/gif", "video/mp4", "video/quicktime"),
            // Video limits depend on the account. INIT validates them before any chunks are sent.
            maxBytes =
                if (media.isGif) {
                    15L * 1024 * 1024
                } else if (!media.isVideo) {
                    5L * 1024 * 1024
                } else {
                    null
                },
        )
        val mediaId =
            checkNotNull(
                service
                    .initUpload(
                        mediaType = media.mimeType,
                        totalBytes = media.size.toString(),
                        category =
                            when {
                                media.isVideo -> "tweet_video"
                                media.isGif -> "tweet_gif"
                                else -> "tweet_image"
                            },
                    ).mediaIDString,
            ) { "X media upload could not be initialized" }
        var segment = 0
        media.forEachChunk(chunkSize = 512 * 1024) { bytes, count ->
            service.appendUpload(mediaId, (segment++).toString(), Base64.encode(bytes, 0, count))
        }
        return withTimeout(30.minutes) {
            var response = service.finalizeUpload(mediaId)
            while (true) {
                val processing =
                    response.processingInfo ?: return@withTimeout checkNotNull(response.mediaIDString) {
                        "X media upload did not return a media ID"
                    }
                when (processing.state) {
                    "succeeded" -> {
                        return@withTimeout mediaId
                    }

                    "failed" -> {
                        error("X media processing failed: ${processing.error?.message ?: processing.error?.name ?: "unknown error"}")
                    }

                    "pending", "in_progress" -> {
                        delay((processing.checkAfterSecs ?: 1).coerceAtLeast(1).seconds)
                        response = service.uploadStatus(mediaId)
                    }

                    else -> {
                        error("Unknown X media processing state: ${processing.state}")
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            mediaId
        }
    }
}
