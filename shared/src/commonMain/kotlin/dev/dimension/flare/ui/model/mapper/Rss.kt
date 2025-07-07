package dev.dimension.flare.ui.model.mapper

import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.render.toUi
import io.ktor.util.encodeBase64
import kotlin.time.Instant

internal fun StatusContent.Rss.RssContent.render(): UiTimeline =
    when (this) {
        is StatusContent.Rss.RssContent.Atom -> this.render()
        is StatusContent.Rss.RssContent.Rss20 -> this.render()
        is StatusContent.Rss.RssContent.RDF -> this.render()
    }

internal val Feed.title: String
    get() =
        when (this) {
            is Feed.Atom -> this.title.value
            is Feed.Rss20 -> this.channel.title
            is Feed.RDF -> this.channel.title
        }

internal fun StatusContent.Rss.RssContent.Atom.render(): UiTimeline =
    with(data) {
        val descHtml =
            content?.value?.let {
                Ksoup.parse(it)
            }
        val img = descHtml?.select("img")?.firstOrNull()?.attr("src") ?: media?.thumbnail?.url
        return UiTimeline(
            topMessage = null,
            content =
                UiTimeline.ItemContent.Feed(
                    title = title.value,
                    description = descHtml?.text(),
                    url = links.first().href,
                    image = img,
                    source = this@render.source,
                    createdAt =
                        published
                            ?.let { input -> runCatching { Instant.parse(input) }.getOrNull() }
                            ?.toUi(),
                ),
        )
    }

internal fun StatusContent.Rss.RssContent.Rss20.render(): UiTimeline =
    with(data) {
        val descHtml =
            description?.let {
                Ksoup.parse(it)
            }
        val img = descHtml?.select("img")?.firstOrNull()
        return UiTimeline(
            topMessage = null,
            content =
                UiTimeline.ItemContent.Feed(
                    title = title,
                    description = descHtml?.text(),
                    url = link,
                    image = img?.attr("src"),
                    source = this@render.source,
                    createdAt =
                        pubDate
                            ?.let { input -> runCatching { Instant.parse(input) }.getOrNull() }
                            ?.toUi(),
                ),
        )
    }

internal fun StatusContent.Rss.RssContent.RDF.render(): UiTimeline =
    with(data) {
        val descHtml =
            description.let {
                Ksoup.parse(it)
            }
        val img = descHtml.select("img").firstOrNull()
        return UiTimeline(
            topMessage = null,
            content =
                UiTimeline.ItemContent.Feed(
                    title = title,
                    description = descHtml.text(),
                    url = link,
                    image = img?.attr("src"),
                    source = source,
                    createdAt =
                        date
                            ?.let { input -> runCatching { Instant.parse(input) }.getOrNull() }
                            ?.toUi(),
                ),
        )
    }

internal fun MicroBlogKey.Companion.fromRss(url: String) =
    MicroBlogKey(
        id = url.encodeBase64(),
        host = "RSS",
    )
