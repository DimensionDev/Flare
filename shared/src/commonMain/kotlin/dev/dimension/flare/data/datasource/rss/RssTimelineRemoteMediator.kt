package dev.dimension.flare.data.datasource.rss

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.rss.RssService
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.model.mapper.title

@OptIn(ExperimentalPagingApi::class)
internal class RssTimelineRemoteMediator(
    private val url: String,
    private val cacheDatabase: CacheDatabase,
    private val fetchFeed: suspend (String) -> Feed = RssService::fetch,
    private val fetchIcon: suspend (String) -> String? = RssService::fetchIcon,
    private val fetchSource: suspend (String) -> DbRssSources?,
) : BaseTimelineRemoteMediator(
        database = cacheDatabase,
    ) {
    override val pagingKey: String
        get() =
            buildString {
                append("rss_")
                append(url)
            }

    override suspend fun timeline(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val rssSource = fetchSource(url)
        val response = fetchFeed(url)
        val title = rssSource?.title ?: response.title
        val icon = rssSource?.icon ?: fetchIcon(url)
        val content =
            when (response) {
                is Feed.Atom ->
                    response.entries.map {
                        it.render(
                            sourceName = title,
                            sourceIcon = icon,
                            openInBrowser = rssSource?.openInBrowser ?: false,
                        )
                    }

                is Feed.RDF ->
                    response.items.map {
                        it.render(
                            sourceName = title,
                            sourceIcon = icon,
                            openInBrowser = rssSource?.openInBrowser ?: false,
                        )
                    }

                is Feed.Rss20 ->
                    response.channel.items.map {
                        it.render(
                            sourceName = title,
                            sourceIcon = icon,
                            openInBrowser = rssSource?.openInBrowser ?: false,
                        )
                    }
            }

        return PagingResult(
            endOfPaginationReached = true,
            data = content,
        )
    }
}
