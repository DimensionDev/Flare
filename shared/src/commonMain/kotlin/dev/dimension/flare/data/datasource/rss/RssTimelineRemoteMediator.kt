package dev.dimension.flare.data.datasource.rss

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.createDbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.network.rss.RssService
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.mapper.fromRss
import dev.dimension.flare.ui.model.mapper.parseRssDateToInstant
import dev.dimension.flare.ui.model.mapper.title
import dev.dimension.flare.ui.render.parseHtml
import kotlin.time.Clock

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
        request: Request,
    ): Result {
        val rssSource = fetchSource(url)
        val response = fetchFeed(url)
        val title = rssSource?.title ?: response.title
        val icon = rssSource?.icon ?: fetchIcon(url)
        val content =
            when (response) {
                is Feed.Atom ->
                    response.entries
                        .map {
                            StatusContent.Rss.RssContent.Atom(
                                it,
                                source = title,
                                icon = icon,
                                openInBrowser = rssSource?.openInBrowser ?: false,
                            )
                        }.map {
                            DbStatusWithUser(
                                user = null,
                                data =
                                    DbStatus(
                                        statusKey =
                                            MicroBlogKey.fromRss(
                                                it.data.links
                                                    .first()
                                                    .href,
                                            ),
                                        accountType = AccountType.Guest,
                                        userKey = null,
                                        content = StatusContent.Rss(it),
                                        text =
                                            it.data.content
                                                ?.value
                                                ?.let { html -> parseHtml(html) }
                                                ?.wholeText(),
                                        createdAt =
                                            (it.data.published ?: it.data.updated)
                                                ?.let { parseRssDateToInstant(it) }
                                                ?: Clock.System.now(),
                                    ),
                            )
                        }

                is Feed.RDF ->
                    response.items
                        .map {
                            StatusContent.Rss.RssContent.RDF(
                                it,
                                source = title,
                                icon = icon,
                                openInBrowser = rssSource?.openInBrowser ?: false,
                            )
                        }.map {
                            DbStatusWithUser(
                                user = null,
                                data =
                                    DbStatus(
                                        statusKey =
                                            MicroBlogKey.fromRss(
                                                it.data.link,
                                            ),
                                        accountType = AccountType.Guest,
                                        userKey = null,
                                        content = StatusContent.Rss(it),
                                        text =
                                            it.data.description
                                                ?.let { html -> parseHtml(html) }
                                                ?.wholeText(),
                                        createdAt =
                                            it.data.date?.let { parseRssDateToInstant(it) }
                                                ?: Clock.System.now(),
                                    ),
                            )
                        }

                is Feed.Rss20 ->
                    response.channel.items
                        .map {
                            StatusContent.Rss.RssContent.Rss20(
                                it,
                                source = title,
                                icon = icon,
                                openInBrowser = rssSource?.openInBrowser ?: false,
                            )
                        }.map {
                            DbStatusWithUser(
                                user = null,
                                data =
                                    DbStatus(
                                        statusKey =
                                            MicroBlogKey.fromRss(
                                                it.data.link,
                                            ),
                                        accountType = AccountType.Guest,
                                        userKey = null,
                                        content = StatusContent.Rss(it),
                                        text =
                                            it.data.description
                                                ?.let { html -> parseHtml(html) }
                                                ?.wholeText(),
                                        createdAt =
                                            it.data.pubDate
                                                ?.let {
                                                    parseRssDateToInstant(
                                                        it,
                                                    )
                                                }
                                                ?: Clock.System.now(),
                                    ),
                            )
                        }
            }.mapIndexed { index, status ->
                createDbPagingTimelineWithStatus(
                    accountType = AccountType.Guest,
                    pagingKey = pagingKey,
                    sortId = status.data.createdAt.toEpochMilliseconds(),
                    status = status,
                    references = mapOf(),
                )
            }

        return Result(
            endOfPaginationReached = true,
            data = content,
        )
    }
}
