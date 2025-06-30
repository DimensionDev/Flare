package dev.dimension.flare.data.datasource.rss

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.createDbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
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

@OptIn(ExperimentalPagingApi::class)
internal class RssTimelineRemoteMediator(
    private val url: String,
    private val cacheDatabase: CacheDatabase,
) : BaseTimelineRemoteMediator(
        database = cacheDatabase,
        accountType = AccountType.Guest,
    ) {
    override val pagingKey: String
        get() =
            buildString {
                append("rss_")
                append(url)
            }

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result =
        try {
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

            Result(
                endOfPaginationReached = true,
                data = content,
            )
        } catch (e: Exception) {
            Result(
                endOfPaginationReached = true,
                data = emptyList(),
            )
        }
}
