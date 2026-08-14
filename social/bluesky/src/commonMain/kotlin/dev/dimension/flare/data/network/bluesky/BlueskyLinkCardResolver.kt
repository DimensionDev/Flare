package dev.dimension.flare.data.network.bluesky

import app.bsky.embed.ExternalExternal
import app.bsky.feed.PostEmbedUnion
import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import io.ktor.utils.io.core.remaining
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import sh.christian.ozone.api.Uri
import sh.christian.ozone.api.model.Blob
import app.bsky.embed.External as ExternalEmbed

private const val CARDYB_ENDPOINT = "https://cardyb.bsky.app/v1/extract"
private const val LINK_CARD_TIMEOUT_MILLIS = 10_000L
private const val LINK_CARD_IMAGE_TIMEOUT_MILLIS = 15_000L
private const val MAX_IMAGE_DOWNLOAD_BYTES = 10L * 1024L * 1024L
private const val MAX_CARD_TITLE_LENGTH = 300
private const val MAX_CARD_DESCRIPTION_LENGTH = 1_000

private val whitespaceRegex = Regex("\\s+")
private val explicitSchemeRegex = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
private val hostWithPortRegex = Regex("^[^/?#\\s]+:\\d+(?:[/?#]|$)")

internal data class BlueskyLinkCard(
    val uri: String,
    val title: String,
    val description: String,
    val imageUrl: String?,
)

internal class BlueskyLinkCardResolver(
    private val client: HttpClient = ktorClient(),
) {
    suspend fun resolve(value: String): BlueskyLinkCard? {
        val uri = normalizeHttpUrl(value) ?: return null
        return bestEffort {
            withTimeoutOrNull(LINK_CARD_TIMEOUT_MILLIS) {
                val response =
                    client.get(CARDYB_ENDPOINT) {
                        url.parameters.append("url", uri)
                    }
                if (!response.status.isSuccess()) {
                    response.bodyAsChannel().cancel()
                    return@withTimeoutOrNull null
                }
                val body = response.body<CardybResponse>()
                if (!body.error.isNullOrBlank()) return@withTimeoutOrNull null
                BlueskyLinkCard(
                    uri = uri,
                    title = body.title.cleaned(MAX_CARD_TITLE_LENGTH) ?: uri.take(MAX_CARD_TITLE_LENGTH),
                    description = body.description.cleaned(MAX_CARD_DESCRIPTION_LENGTH).orEmpty(),
                    imageUrl = body.image?.let(::normalizeHttpUrl),
                )
            }
        }
    }

    suspend fun fetchImage(value: String): ByteArray? {
        val uri = normalizeHttpUrl(value) ?: return null
        return bestEffort {
            withTimeoutOrNull(LINK_CARD_IMAGE_TIMEOUT_MILLIS) {
                val response = client.get(uri)
                if (!response.status.isSuccess()) {
                    response.bodyAsChannel().cancel()
                    return@withTimeoutOrNull null
                }
                val contentType = response.contentType()
                if (contentType != null && contentType !in ContentType.Image) {
                    response.bodyAsChannel().cancel()
                    return@withTimeoutOrNull null
                }
                response.readLimitedBytes(MAX_IMAGE_DOWNLOAD_BYTES)
            }
        }
    }
}

internal fun normalizeHttpUrl(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    val candidate =
        when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            "://" in trimmed -> return null
            explicitSchemeRegex.containsMatchIn(trimmed) && !hostWithPortRegex.containsMatchIn(trimmed) -> return null
            else -> "https://$trimmed"
        }
    val parsed = runCatching { Url(candidate) }.getOrNull() ?: return null
    if (parsed.protocol.name != "http" && parsed.protocol.name != "https") return null
    if (parsed.host.isBlank()) return null
    return parsed.toString()
}

internal fun BlueskyLinkCard.toExternalEmbed(thumb: Blob?): PostEmbedUnion.External =
    PostEmbedUnion.External(
        ExternalEmbed(
            external =
                ExternalExternal(
                    uri = Uri(uri),
                    title = title,
                    description = description,
                    thumb = thumb,
                ),
        ),
    )

private suspend fun HttpResponse.readLimitedBytes(maxBytes: Long): ByteArray = readLimitedSource(maxBytes).readByteArray()

private suspend fun HttpResponse.readLimitedSource(maxBytes: Long) =
    bodyAsChannel().let { channel ->
        val contentLength = headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > maxBytes) {
            channel.cancel()
            error("Link card response is too large")
        }
        val source = channel.readRemaining(maxBytes + 1)
        if (source.remaining > maxBytes) {
            channel.cancel()
            error("Link card response is too large")
        }
        source
    }

private fun String?.cleaned(maxLength: Int): String? =
    this
        ?.replace(whitespaceRegex, " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.take(maxLength)

private suspend fun <T> bestEffort(block: suspend () -> T): T? =
    try {
        block()
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Exception) {
        null
    }

@Serializable
private data class CardybResponse(
    val error: String? = null,
    val title: String? = null,
    val description: String? = null,
    val image: String? = null,
)
