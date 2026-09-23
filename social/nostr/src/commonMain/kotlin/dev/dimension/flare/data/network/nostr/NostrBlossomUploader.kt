package dev.dimension.flare.data.network.nostr

import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.UploadMedia
import dev.dimension.flare.data.network.asContent
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.nullableFallbackJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.HashingSink
import okio.blackholeSink
import okio.buffer
import okio.use
import kotlin.io.encoding.Base64

internal class NostrBlossomUploader(
    private val buildAuthHeader: suspend (sha256: String) -> String,
    private val httpClient: HttpClient =
        ktorClient {
            install(ContentNegotiation) {
                nullableFallbackJson(JSON)
            }
            expectSuccess = false
        },
) {
    suspend fun upload(
        serverUrl: String,
        media: UploadMedia,
        altText: String?,
    ): UploadedMedia {
        val sha256 =
            HashingSink.sha256(blackholeSink()).let { hash ->
                hash.buffer().use { sink -> media.forEachChunk { bytes, count -> sink.write(bytes, 0, count) } }
                hash.hash.hex()
            }
        val mimeType = media.mimeType
        val response =
            httpClient.put(
                URLBuilder()
                    .takeFrom(serverUrl)
                    .appendPathSegments("upload")
                    .build(),
            ) {
                header(HttpHeaders.Authorization, buildAuthHeader(sha256))
                header(HttpHeaders.ContentType, mimeType)
                setBody(media.asContent())
            }
        if (response.status !in listOf(HttpStatusCode.OK, HttpStatusCode.Created)) {
            val detail =
                runCatching { response.bodyAsText() }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
            error("Blossom upload failed: ${response.status.value}${detail?.let { " $it" }.orEmpty()}")
        }
        val descriptor = response.body<BlobDescriptor>()
        return UploadedMedia(
            url = descriptor.url,
            mimeType = descriptor.type.ifBlank { mimeType },
            sha256 = descriptor.sha256.ifBlank { sha256 },
            size = descriptor.size.takeIf { it > 0 } ?: media.size,
            altText = altText?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    internal companion object {
        const val DEFAULT_SERVER_URL: String = "https://blossom.nostr.build/"
    }
}

@Serializable
internal data class BlobDescriptor(
    @SerialName("url")
    val url: String,
    @SerialName("sha256")
    val sha256: String = "",
    @SerialName("size")
    val size: Long = 0,
    @SerialName("type")
    val type: String = "",
)

internal data class UploadedMedia(
    val url: String,
    val mimeType: String,
    val sha256: String,
    val size: Long,
    val altText: String?,
)

internal fun buildBlossomAuthorizationHeader(eventJson: String): String = "Nostr ${Base64.encode(eventJson.encodeToByteArray())}"
