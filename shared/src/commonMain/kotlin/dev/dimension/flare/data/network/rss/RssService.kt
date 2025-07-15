package dev.dimension.flare.data.network.rss

import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.rss.model.Feed
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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
}
