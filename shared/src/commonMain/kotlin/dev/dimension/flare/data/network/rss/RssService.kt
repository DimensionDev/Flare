package dev.dimension.flare.data.network.rss

import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.ui.model.mapper.link
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.xml.xml
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

    suspend fun fetch(url: String): Feed =
        ktorClient(config = {
            install(ContentNegotiation) {
                xml(xml, contentType = ContentType.Any)
            }
        }).get(url).body()

    suspend fun detectLinkSources(url: String): List<String> =
        ktorClient()
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
        val feed =
            tryRun {
                fetch(url)
            }.getOrNull()
        val feedIcon =
            when (feed) {
                is Feed.Atom -> feed.icon
                is Feed.RDF -> null
                is Feed.Rss20 -> null
                else -> null
            }
        if (feedIcon != null) {
            return feedIcon
        }
        val feedLink = feed?.link ?: url
        val parsedUrl = Url(feedLink)
        val favIcon = "https://${parsedUrl.host}/favicon.ico"
        val hasFavIcon =
            tryRun {
                val response = ktorClient().get(favIcon)
                if (response.status.value !in 200..299) {
                    throw Exception("Failed to fetch favicon: ${response.status}")
                }
            }
        if (hasFavIcon.isSuccess) {
            return favIcon
        }
        val html =
            tryRun {
                ktorClient().get(feedLink).bodyAsText()
            }.getOrNull() ?: return null
        val document = Ksoup.parse(html)
        val iconLink =
            document
                .select(
                    """
                    link[rel~=(?i)(?:^|\s)(?:icon|apple-touch-icon(?:-precomposed)?|mask-icon)(?:\s|$)],
                    link[rel~=(?i)^(?=.*\bshortcut\b)(?=.*\bicon\b).*$]
                    
                    """.trimIndent(),
                ).firstOrNull()
                ?: return null
        val iconHref = iconLink.attr("href").ifBlank { return null }
        return if (iconHref.startsWith("http")) {
            iconHref
        } else if (iconHref.startsWith("/")) {
            "https://${parsedUrl.host}$iconHref"
        } else {
            "https://${parsedUrl.host}/$iconHref"
        }
    }
}
