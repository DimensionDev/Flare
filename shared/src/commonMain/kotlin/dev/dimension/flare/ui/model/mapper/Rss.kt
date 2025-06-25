package dev.dimension.flare.ui.model.mapper

import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.render.toUi
import io.ktor.util.encodeBase64
import kotlinx.datetime.Instant

internal fun Feed.render(): List<UiTimeline> =
    when (this) {
        is Feed.Atom -> renderAtom()
        is Feed.Rss20 -> renderRss20()
        is Feed.RDF -> renderRdf()
    }

internal val Feed.title: String
    get() =
        when (this) {
            is Feed.Atom -> this.title.value
            is Feed.Rss20 -> this.channel.title
            is Feed.RDF -> this.channel.title
        }

private fun Feed.Atom.renderAtom(): List<UiTimeline> =
    this.entries.map {
        it.render()
    }

internal fun Feed.Atom.Entry.render(): UiTimeline {
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
                createdAt =
                    published
                        ?.let { input -> runCatching { Instant.parse(input) }.getOrNull() }
                        ?.toUi(),
            ),
    )
}

private fun Feed.Rss20.renderRss20(): List<UiTimeline> =
    this.channel.items.map {
        it.render()
    }

internal fun Feed.Rss20.Item.render(): UiTimeline {
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
                createdAt =
                    pubDate
                        ?.let { input -> runCatching { Instant.parse(input) }.getOrNull() }
                        ?.toUi(),
            ),
    )
}

private fun Feed.RDF.renderRdf(): List<UiTimeline> =
    this.items.map {
        it.render()
    }

internal fun Feed.RDF.Item.render(): UiTimeline {
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
