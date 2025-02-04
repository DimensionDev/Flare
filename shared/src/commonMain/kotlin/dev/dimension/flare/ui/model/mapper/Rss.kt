package dev.dimension.flare.ui.model.mapper

import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.render.toUi
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
        val descHtml =
            it.content?.value?.let {
                Ksoup.parse(it)
            }
        val img = descHtml?.select("img")?.firstOrNull()?.attr("src") ?: it.media?.thumbnail?.url
        UiTimeline(
            topMessage = null,
            content =
                UiTimeline.ItemContent.Feed(
                    title = it.title.value,
                    description = descHtml?.text(),
                    url = it.links.first().href,
                    image = img,
                    createdAt = it.published?.let { input -> runCatching { Instant.parse(input) }.getOrNull() }?.toUi(),
                ),
        )
    }

private fun Feed.Rss20.renderRss20(): List<UiTimeline> =
    this.channel.items.map {
        val descHtml =
            it.description?.let {
                Ksoup.parse(it)
            }
        val img = descHtml?.select("img")?.firstOrNull()
        UiTimeline(
            topMessage = null,
            content =
                UiTimeline.ItemContent.Feed(
                    title = it.title,
                    description = descHtml?.text(),
                    url = it.link,
                    image = img?.attr("src"),
                    createdAt = it.pubDate?.let { input -> runCatching { Instant.parse(input) }.getOrNull() }?.toUi(),
                ),
        )
    }

private fun Feed.RDF.renderRdf(): List<UiTimeline> =
    this.items.map {
        val descHtml =
            it.description.let {
                Ksoup.parse(it)
            }
        val img = descHtml.select("img").firstOrNull()
        UiTimeline(
            topMessage = null,
            content =
                UiTimeline.ItemContent.Feed(
                    title = it.title,
                    description = descHtml.text(),
                    url = it.link,
                    image = img?.attr("src"),
                    createdAt = it.date?.let { input -> runCatching { Instant.parse(input) }.getOrNull() }?.toUi(),
                ),
        )
    }
