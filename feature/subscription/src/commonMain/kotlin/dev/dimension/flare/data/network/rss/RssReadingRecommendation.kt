package dev.dimension.flare.data.network.rss

import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.network.rss.model.Feed
import io.ktor.client.plugins.ResponseException
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun recommendRssDisplayMode(
    feed: Feed,
    extract: suspend (String) -> DocumentData,
): RssDisplayMode? =
    withTimeoutOrNull(6_000) {
        val samples =
            feed
                .readingSamples()
                .distinctBy { it.first }
                .filter {
                    runCatching { Url(it.first).protocol.name in setOf("http", "https") }.getOrDefault(false)
                }.take(2)
                .toList()
        if (samples.isEmpty()) return@withTimeoutOrNull null
        var uncertain = false
        for ((url, _) in samples) {
            try {
                extract(url)
                return@withTimeoutOrNull RssDisplayMode.FULL_CONTENT
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!exception.isDefinitiveArticleFailure()) uncertain = true
            }
        }
        when {
            uncertain -> null
            samples.all { (_, contents) -> contents.any { it.hasRssContent() } } -> RssDisplayMode.DESCRIPTION_ONLY
            else -> RssDisplayMode.OPEN_IN_BROWSER
        }
    }

private fun Feed.readingSamples(): Sequence<Pair<String, List<String?>>> =
    when (this) {
        is Feed.Rss20 -> {
            channel.items.asSequence().map { it.link to listOf(it.contentEncoded, it.description) }
        }

        is Feed.Atom -> {
            entries.asSequence().map {
                (it.links.firstOrNull { link -> link.rel == null || link.rel == "alternate" }?.href ?: it.id) to
                    listOf(it.content?.value, it.summary?.value)
            }
        }

        is Feed.RDF -> {
            items.asSequence().map { it.link to listOf(it.contentEncoded, it.description) }
        }
    }

private fun Throwable.isDefinitiveArticleFailure(): Boolean =
    this is ArticleExtractionException ||
        (this is ResponseException && response.status.value in 300..499 && response.status.value !in setOf(408, 429)) ||
        isRssTransportSecurityFailure() ||
        (cause?.takeIf { it !== this }?.isDefinitiveArticleFailure() == true)

internal expect fun Throwable.isRssTransportSecurityFailure(): Boolean

internal fun String.toRssWebUrl(): String =
    trim().let {
        if (it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)) it else "https://$it"
    }
