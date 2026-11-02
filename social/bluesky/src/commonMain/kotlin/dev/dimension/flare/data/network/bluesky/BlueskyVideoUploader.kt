package dev.dimension.flare.data.network.bluesky

import dev.dimension.flare.common.UploadMedia
import dev.dimension.flare.data.network.asContent
import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.api.model.Blob
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Upload and processing finish before a post referencing the blob is created. */
internal class BlueskyVideoUploader(
    private val client: HttpClient = ktorClient(),
) {
    suspend fun upload(
        media: UploadMedia,
        did: String,
        token: String,
    ): Blob {
        media.validateBlueskyVideoInput()
        var job =
            client
                .post("$VIDEO_SERVICE/app.bsky.video.uploadVideo") {
                    bearerAuth(token)
                    parameter("did", did)
                    parameter("name", media.name)
                    setBody(media.asContent())
                }.videoJob()
        return withTimeout(30.minutes) {
            while (true) {
                // already_exists can return an error alongside a reusable blob.
                job.blob?.let { blob ->
                    val mimeType =
                        when (blob) {
                            is Blob.StandardBlob -> blob.mimeType
                            is Blob.LegacyBlob -> blob.mimeType
                        }
                    check(mimeType == "video/mp4") { "Bluesky video processing did not return an MP4 blob" }
                    return@withTimeout blob
                }
                check(job.error == null && job.state != "JOB_STATE_FAILED" && job.state != "JOB_STATE_COMPLETED") {
                    "Bluesky video processing failed: ${job.message ?: job.error ?: job.state}"
                }
                val id = checkNotNull(job.jobId) { "Bluesky video response did not include a job ID" }
                delay(1.seconds)
                job =
                    client
                        .get("$VIDEO_SERVICE/app.bsky.video.getJobStatus") {
                            parameter("jobId", id)
                        }.videoJob()
            }
            @Suppress("UNREACHABLE_CODE")
            error("Video processing did not finish")
        }
    }

    private suspend fun HttpResponse.videoJob(): VideoJob {
        val body = bodyAsText()
        val payload = BlueskyJson.parseToJsonElement(body).jsonObject
        val job = BlueskyJson.decodeFromJsonElement<VideoJob>(payload["jobStatus"] ?: payload)
        check(status.isSuccess() || job.blob != null) {
            "Bluesky video upload failed (${status.value}): ${job.message ?: job.error ?: body}"
        }
        return job
    }

    private companion object {
        const val VIDEO_SERVICE = "https://video.bsky.app/xrpc"
    }
}

// The video service processes original GIF/MOV input into MP4 for the post embed.
// https://github.com/bluesky-social/social-app/blob/main/src/lib/constants.ts
internal fun UploadMedia.validateBlueskyVideoInput() {
    validate("Bluesky", listOf("video/mp4", "video/quicktime", "image/gif"), 300_000_000)
}

@Serializable
private data class VideoJob(
    val jobId: String? = null,
    val state: String? = null,
    val blob: Blob? = null,
    val error: String? = null,
    val message: String? = null,
)
