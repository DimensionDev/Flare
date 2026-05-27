package dev.dimension.flare.data.network.rss

import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.data.network.FaviconService
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.ui.model.mapper.link
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML

internal object RssService {
    private val xml by lazy {
        XML {
            defaultPolicy {
                autoPolymorphic = true
                ignoreUnknownChildren()
            }
            defaultToGenericParser = true
        }
    }
    private val client = ktorClient { }

    suspend fun fetch(url: String): Feed {
        val response = client.get(url).decodedText()
        return parseFeedText(response)
    }

    suspend fun detectLinkSources(url: String): List<String> =
        client
            .get(url)
            .decodedText()
            .let(Ksoup::parse)
            .getElementsByAttributeValueStarting("type", "application/")
            .filter {
                (
                    it.attr("type").contains("application/rss+xml", ignoreCase = true) ||
                        it.attr("type").contains("application/atom+xml", ignoreCase = true)
                )
            }.map { it.attr("href") }
            .filter { it.isNotBlank() }
            .distinct()
            .map {
                if (it.startsWith("//")) {
                    "https:$it"
                } else if (it.startsWith("/")) {
                    val baseUrl = Url(url)
                    "${baseUrl.protocol.name}://${baseUrl.host}$it"
                } else if (!it.startsWith("http")) {
                    val baseUrl = Url(url)
                    "${baseUrl.protocol.name}://${baseUrl.host}/$it"
                } else {
                    it
                }
            }.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("No RSS or Atom feeds found at the provided URL: $url")

    suspend fun fetchIcon(url: String): String? {
        val webContent =
            tryRun {
                client.get(url).decodedText()
            }.getOrNull() ?: return null
        val feed =
            tryRun {
                xml.decodeFromString<Feed>(webContent)
            }.getOrNull()
        val feedIcon =
            when (feed) {
                is Feed.Atom -> feed.icon
                is Feed.RDF -> null
                is Feed.Rss20 -> null
                null -> null
            }
        if (feedIcon != null) {
            return feedIcon
        }
        return FaviconService.fetchIcon(feed?.link ?: url)
    }

    private suspend fun HttpResponse.decodedText(): String {
        val bytes = body<ByteArray>()
        return decodeResponseBody(
            bytes = bytes,
            headerCharset =
                headers[HttpHeaders.ContentType]
                    ?.let(::detectHeaderCharset),
        )
    }
}

internal fun parseFeedText(text: String): Feed = RssServiceXml.decodeFromString(text)

internal fun decodeResponseBody(
    bytes: ByteArray,
    headerCharset: String?,
): String {
    val candidates =
        buildList {
            addCharset(headerCharset)
            addCharset(detectXmlCharset(bytes))
            addCharset(detectHtmlCharset(bytes))
            addCharset("utf-8")
            addCharset("iso-8859-1")
        }
    candidates.forEach { charset ->
        decodeBytes(bytes, charset)?.let {
            return it
        }
    }
    return bytes.decodeToString()
}

private fun MutableList<String>.addCharset(value: String?) {
    if (!value.isNullOrBlank() && none { it.equals(value, ignoreCase = true) }) {
        add(value)
    }
}

private fun detectXmlCharset(bytes: ByteArray): String? =
    XML_DECLARATION_ENCODING_REGEX.find(asciiPrefix(bytes, limit = 256))?.groupValues?.getOrNull(1)

private fun detectHtmlCharset(bytes: ByteArray): String? =
    HTML_META_CHARSET_REGEX.find(asciiPrefix(bytes, limit = 4096))?.groupValues?.getOrNull(1)

private fun detectHeaderCharset(contentType: String): String? = HEADER_CHARSET_REGEX.find(contentType)?.groupValues?.getOrNull(1)

private fun asciiPrefix(
    bytes: ByteArray,
    limit: Int,
): String =
    buildString(minOf(bytes.size, limit)) {
        bytes
            .take(limit)
            .forEach { byte ->
                val value = byte.toInt() and 0xff
                append(
                    when (value) {
                        0x09, 0x0a, 0x0d -> value.toChar()
                        in 0x20..0x7e -> value.toChar()
                        else -> ' '
                    },
                )
            }
    }

private val XML_DECLARATION_ENCODING_REGEX =
    Regex("""<\?xml[^>]*encoding\s*=\s*["']\s*([^"'\s>]+)\s*["']""", RegexOption.IGNORE_CASE)

private val HTML_META_CHARSET_REGEX =
    Regex("""<meta[^>]*charset\s*=\s*["']?\s*([^"'\s/>]+)""", RegexOption.IGNORE_CASE)

private val HEADER_CHARSET_REGEX =
    Regex("""(?:^|;)\s*charset\s*=\s*"?([^";\s]+)""", RegexOption.IGNORE_CASE)

private val RssServiceXml by lazy {
    XML {
        defaultPolicy {
            autoPolymorphic = true
            ignoreUnknownChildren()
        }
        defaultToGenericParser = true
    }
}
