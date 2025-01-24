package dev.dimension.flare.data.network.rss

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.rss.model.Feed
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import nl.adaptivity.xmlutil.serialization.XML

internal object RssService {
    private val xml by lazy {
        XML {
            defaultPolicy {
                autoPolymorphic = true
                ignoreNamespaces()
                ignoreUnknownChildren()
            }
            defaultToGenericParser = true
        }
    }

    suspend fun fetch(url: String): Feed = xml.decodeFromString(Feed.serializer(), ktorClient().get(url).bodyAsText())
}
