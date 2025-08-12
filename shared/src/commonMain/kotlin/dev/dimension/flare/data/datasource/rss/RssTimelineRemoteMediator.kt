package dev.dimension.flare.data.datasource.rss

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
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
import dev.dimension.flare.ui.model.mapper.title
import dev.dimension.flare.ui.render.parseHtml
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.parse
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalPagingApi::class)
internal class RssTimelineRemoteMediator(
    private val url: String,
    private val cacheDatabase: CacheDatabase,
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
        val response = RssService.fetch(url)
        val content =
            when (response) {
                is Feed.Atom ->
                    response.entries
                        .map {
                            StatusContent.Rss.RssContent.Atom(
                                it,
                                source = response.title.value,
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
                                            it.data.published?.let { Instant.parse(it) }
                                                ?: Clock.System.now(),
                                    ),
                            )
                        }

                is Feed.RDF ->
                    response.items
                        .map {
                            StatusContent.Rss.RssContent.RDF(
                                it,
                                source = response.title,
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
                                                .let { html -> parseHtml(html) }
                                                .wholeText(),
                                        createdAt =
                                            it.data.date?.let { Instant.parse(it) }
                                                ?: Clock.System.now(),
                                    ),
                            )
                        }

                is Feed.Rss20 ->
                    response.channel.items
                        .map {
                            StatusContent.Rss.RssContent.Rss20(
                                it,
                                source = response.title,
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
                                                    Instant.parse(
                                                        it,
                                                        DateTimeComponents.Formats.RFC_1123,
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
                    sortId = -index.toLong(),
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
