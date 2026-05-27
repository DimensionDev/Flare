package dev.dimension.flare.data.datasource.rss

import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.rss.RssService
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.model.mapper.title

internal class RssTimelineRemoteMediator(
    private val url: String,
    private val fetchFeed: suspend (String) -> Feed = RssService::fetch,
    private val fetchIcon: suspend (String) -> String? = RssService::fetchIcon,
    private val fetchSource: suspend (String) -> UiRssSource?,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String
        get() =
            buildString {
                append("rss_")
                append(url)
            }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val rssSource = fetchSource(url)
        val response = fetchFeed(url)
        val title = rssSource?.title ?: response.title
        val icon = rssSource?.favIcon ?: fetchIcon(url)
        val displayMode = rssSource?.displayMode ?: RssDisplayMode.FULL_CONTENT
        val content =
            when (response) {
                is Feed.Atom -> {
                    response.entries.map {
                        it.render(
                            sourceName = title,
                            sourceIcon = icon,
                            displayMode = displayMode,
                            sourceLanguage = null,
                        )
                    }
                }

                is Feed.RDF -> {
                    response.items.map {
                        it.render(
                            sourceName = title,
                            sourceIcon = icon,
                            displayMode = displayMode,
                            sourceLanguage = null,
                        )
                    }
                }

                is Feed.Rss20 -> {
                    response.channel.items.map {
                        it.render(
                            sourceName = title,
                            sourceIcon = icon,
                            displayMode = displayMode,
                            sourceLanguage = response.channel.language,
                        )
                    }
                }
            }

        return PagingResult(
            endOfPaginationReached = true,
            data = content,
        )
    }
}
