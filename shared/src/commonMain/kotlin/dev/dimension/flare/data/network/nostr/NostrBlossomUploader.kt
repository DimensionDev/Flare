package dev.dimension.flare.data.network.nostr

import dev.dimension.flare.common.FileType
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.ktorClient
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

internal class NostrBlossomUploader(
    private val buildAuthHeader: suspend (sha256: String) -> String,
    private val httpClient: HttpClient =
        ktorClient {
            install(ContentNegotiation) {
                json(JSON)
            }
            expectSuccess = false
        },
) {
    suspend fun upload(
        serverUrl: String,
        name: String?,
        bytes: ByteArray,
        fileType: FileType,
        altText: String?,
    ): UploadedMedia {
        val sha256 = bytes.sha256Hex()
        val mimeType = guessMimeType(name = name, fileType = fileType)
        val response =
            httpClient.put(
                URLBuilder()
                    .takeFrom(serverUrl)
                    .appendPathSegments("upload")
                    .build(),
            ) {
                header(HttpHeaders.Authorization, buildAuthHeader(sha256))
                header(HttpHeaders.ContentType, mimeType)
                setBody(bytes)
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
            size = descriptor.size.takeIf { it > 0 } ?: bytes.size.toLong(),
            altText = altText?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private suspend fun ByteArray.sha256Hex(): String {
        val hasher =
            CryptographyProvider
                .Default
                .get(SHA256)
                .hasher()
        return hasher
            .hash(this)
            .toHexString()
    }

    private fun guessMimeType(
        name: String?,
        fileType: FileType,
    ): String {
        val normalizedName = name?.lowercase().orEmpty()
        return when {
            normalizedName.endsWith(".jpg") || normalizedName.endsWith(".jpeg") -> ContentType.Image.JPEG.toString()
            normalizedName.endsWith(".png") -> ContentType.Image.PNG.toString()
            normalizedName.endsWith(".gif") -> ContentType.Image.GIF.toString()
            normalizedName.endsWith(".webp") -> "image/webp"
            normalizedName.endsWith(".avif") -> "image/avif"
            normalizedName.endsWith(".heic") -> "image/heic"
            normalizedName.endsWith(".heif") -> "image/heif"
            normalizedName.endsWith(".mp4") -> "video/mp4"
            normalizedName.endsWith(".mov") -> "video/quicktime"
            normalizedName.endsWith(".webm") -> "video/webm"
            normalizedName.endsWith(".m4v") -> "video/x-m4v"
            fileType == FileType.Image -> ContentType.Image.Any.toString()
            fileType == FileType.Video -> ContentType.Video.Any.toString()
            else -> ContentType.Application.OctetStream.toString()
        }
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
