package dev.dimension.flare.data.network.bluesky

import app.bsky.embed.ExternalExternal
import app.bsky.feed.PostEmbedUnion
import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.readText
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
private const val MAX_CARDYB_RESPONSE_BYTES = 256L * 1024L
private const val MAX_HTML_BYTES = 2L * 1024L * 1024L
private const val MAX_IMAGE_DOWNLOAD_BYTES = 10L * 1024L * 1024L
private const val MAX_CARD_TITLE_LENGTH = 300
private const val MAX_CARD_DESCRIPTION_LENGTH = 1_000
private const val LINK_CARD_USER_AGENT = "Flare/1.0 (+https://flareapp.moe)"

private val whitespaceRegex = Regex("\\s+")
private val explicitSchemeRegex = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
private val hostWithPortRegex = Regex("^[^/?#\\s]+:\\d+(?:[/?#]|$)")

internal enum class LinkCardMetadataSource {
    Direct,
    Cardyb,
}

internal expect val platformLinkCardMetadataSource: LinkCardMetadataSource

internal data class BlueskyLinkCard(
    val uri: String,
    val title: String,
    val description: String,
    val imageUrl: String?,
)

internal class BlueskyLinkCardResolver(
    private val client: HttpClient = ktorClient(),
    private val metadataSource: LinkCardMetadataSource = platformLinkCardMetadataSource,
    private val metadataTimeoutMillis: Long? = LINK_CARD_TIMEOUT_MILLIS,
    private val imageTimeoutMillis: Long? = LINK_CARD_IMAGE_TIMEOUT_MILLIS,
) {
    suspend fun resolve(value: String): BlueskyLinkCard? {
        val uri = normalizeHttpUrl(value) ?: return null
        return bestEffort {
            withOptionalTimeout(metadataTimeoutMillis) {
                when (metadataSource) {
                    LinkCardMetadataSource.Direct -> resolveDirect(uri)
                    LinkCardMetadataSource.Cardyb -> resolveWithCardyb(uri)
                }
            }
        }
    }

    suspend fun fetchImage(value: String): ByteArray? {
        val uri = normalizeHttpUrl(value) ?: return null
        return bestEffort {
            withOptionalTimeout(imageTimeoutMillis) {
                val response = client.get(uri)
                if (!response.status.isSuccess()) {
                    response.bodyAsChannel().cancel()
                    return@withOptionalTimeout null
                }
                val contentType = response.contentType()
                if (contentType != null && contentType !in ContentType.Image) {
                    response.bodyAsChannel().cancel()
                    return@withOptionalTimeout null
                }
                response.readLimitedBytes(MAX_IMAGE_DOWNLOAD_BYTES)
            }
        }
    }

    private suspend fun resolveDirect(uri: String): BlueskyLinkCard? {
        val response =
            client.get(uri) {
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
                header(HttpHeaders.UserAgent, LINK_CARD_USER_AGENT)
            }
        if (!response.status.isSuccess()) {
            response.bodyAsChannel().cancel()
            return null
        }
        val contentType = response.contentType()
        if (contentType != null && !contentType.isHtml()) {
            response.bodyAsChannel().cancel()
            return null
        }
        val finalUrl =
            response.call.request.url
                .toString()
        return parseOpenGraph(
            html = response.readLimitedText(MAX_HTML_BYTES),
            baseUrl = finalUrl,
            cardUrl = uri,
        )
    }

    private suspend fun resolveWithCardyb(uri: String): BlueskyLinkCard? {
        val response =
            client.get(CARDYB_ENDPOINT) {
                url.parameters.append("url", uri)
            }
        if (!response.status.isSuccess()) {
            response.bodyAsChannel().cancel()
            return null
        }
        return parseCardybResponse(
            value = response.readLimitedText(MAX_CARDYB_RESPONSE_BYTES),
            cardUrl = uri,
        )
    }
}

internal fun parseOpenGraph(
    html: String,
    baseUrl: String,
    cardUrl: String = baseUrl,
): BlueskyLinkCard? {
    val normalizedCardUrl = normalizeHttpUrl(cardUrl) ?: return null
    val fallbackTitle = normalizedCardUrl.take(MAX_CARD_TITLE_LENGTH)
    val document = Ksoup.parse(html = html, baseUri = baseUrl)
    val metadata = Ksoup.parseMetaData(document)

    fun absoluteMetaUrl(selector: String): String? {
        val element = document.selectFirst(selector) ?: return null
        val value =
            element
                .absUrl("content")
                .ifBlank { element.attr("content") }
        return normalizeHttpUrl(value)
    }

    return BlueskyLinkCard(
        uri = normalizedCardUrl,
        title =
            sequenceOf(
                metadata.ogTitle,
                metadata.twitterTitle,
                metadata.title,
                metadata.htmlTitle,
            ).firstNotNullOfOrNull { it.cleaned(MAX_CARD_TITLE_LENGTH) }
                ?: fallbackTitle,
        description =
            sequenceOf(
                metadata.ogDescription,
                metadata.twitterDescription,
                metadata.description,
            ).firstNotNullOfOrNull { it.cleaned(MAX_CARD_DESCRIPTION_LENGTH) }
                .orEmpty(),
        imageUrl =
            absoluteMetaUrl("meta[property=\"og:image:secure_url\"]")
                ?: absoluteMetaUrl("meta[property=\"og:image:url\"]")
                ?: absoluteMetaUrl("meta[property=\"og:image\"]")
                ?: absoluteMetaUrl("meta[name=\"twitter:image\"]")
                ?: absoluteMetaUrl("meta[name=\"twitter:image:src\"]"),
    )
}

internal fun parseCardybResponse(
    value: String,
    cardUrl: String,
): BlueskyLinkCard? {
    val normalizedCardUrl = normalizeHttpUrl(cardUrl) ?: return null
    val fallbackTitle = normalizedCardUrl.take(MAX_CARD_TITLE_LENGTH)
    val response = JSON.decodeFromString<CardybResponse>(value)
    if (!response.error.isNullOrBlank()) return null
    return BlueskyLinkCard(
        uri = normalizedCardUrl,
        title = response.title.cleaned(MAX_CARD_TITLE_LENGTH) ?: fallbackTitle,
        description = response.description.cleaned(MAX_CARD_DESCRIPTION_LENGTH).orEmpty(),
        imageUrl = response.image?.let(::normalizeHttpUrl),
    )
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

private suspend fun HttpResponse.readLimitedText(maxBytes: Long): String {
    val charset = contentType()?.charset() ?: Charsets.UTF_8
    return readLimitedSource(maxBytes).readText(charset = charset)
}

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

private fun ContentType.isHtml(): Boolean =
    (contentType.equals("text", ignoreCase = true) && contentSubtype.equals("html", ignoreCase = true)) ||
        (
            contentType.equals("application", ignoreCase = true) &&
                contentSubtype.equals("xhtml+xml", ignoreCase = true)
        )

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

private suspend fun <T> withOptionalTimeout(
    timeoutMillis: Long?,
    block: suspend () -> T,
): T? =
    if (timeoutMillis == null) {
        block()
    } else {
        withTimeoutOrNull(timeoutMillis) {
            block()
        }
    }

@Serializable
private data class CardybResponse(
    val error: String? = null,
    val title: String? = null,
    val description: String? = null,
    val image: String? = null,
)
