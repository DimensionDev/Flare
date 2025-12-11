package dev.dimension.flare.data.network.rss

import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.ui.model.mapper.link
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
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
        val response = client.get(url).bodyAsText()
        return xml.decodeFromString(response)
    }

    suspend fun detectLinkSources(url: String): List<String> =
        client
            .get(url)
            .bodyAsText()
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
            .takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("No RSS or Atom feeds found at the provided URL: $url")

    suspend fun fetchIcon(url: String): String? {
        val webContent =
            tryRun {
                client.get(url).bodyAsText()
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
        val feedLink = feed?.link ?: url
        val parsedUrl = Url(feedLink)
        val html =
            if (feed?.link != null && feed.link != url) {
                tryRun {
                    client.get(feedLink).bodyAsText()
                }.getOrNull() ?: return null
            } else {
                webContent
            }
        val document = Ksoup.parse(html)
        val icons =
            document
                .select(
                    """
                    link[rel~=(?i)(?:^|\s)(?:icon|apple-touch-icon(?:-precomposed)?|mask-icon)(?:\s|$)],
                    link[rel~=(?i)^(?=.*\bshortcut\b)(?=.*\bicon\b).*$]
                    """.trimIndent(),
                )
        val iconLink =
            icons.maxByOrNull {
                it
                    .attribute("sizes")
                    ?.value
                    ?.split('x')
                    ?.firstOrNull()
                    ?.toIntOrNull() ?: 0
            }
        if (iconLink == null) {
            val favIcon = "https://${parsedUrl.host}/favicon.ico"
            val hasFavIcon =
                tryRun {
                    val response =
                        client.get(favIcon)
                    if (response.status.value !in 200..299) {
                        throw Exception("Failed to fetch favicon: ${response.status}")
                    }
                }
            if (hasFavIcon.isSuccess) {
                return favIcon
            } else {
                return null
            }
        } else {
            val iconHref = iconLink.attr("href").ifBlank { return null }
            return if (iconHref.startsWith("http")) {
                iconHref
            } else if (iconHref.startsWith("/")) {
                if (iconHref.startsWith("//")) {
                    "https:$iconHref"
                } else {
                    "https://${parsedUrl.host}$iconHref"
                }
            } else {
                "https://${parsedUrl.host}/$iconHref"
            }
        }
    }
}
