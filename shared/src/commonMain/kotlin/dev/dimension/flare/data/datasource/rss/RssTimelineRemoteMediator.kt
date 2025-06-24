package dev.dimension.flare.data.datasource.rss

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.createDbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.network.rss.RssService
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.mapper.fromRss
import dev.dimension.flare.ui.render.parseHtml

@OptIn(ExperimentalPagingApi::class)
internal class RssTimelineRemoteMediator(
    private val url: String,
    private val pagingKey: String,
    private val cacheDatabase: CacheDatabase,
) : BaseRemoteMediator<Int, DbPagingTimelineWithStatus>() {
    override suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult =
        try {
            val response = RssService.fetch(url)
            val content =
                when (response) {
                    is Feed.Atom ->
                        response.entries.map { StatusContent.RSS.Atom(it) }.map {
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
                                        content = it,
                                        text =
                                            it.data.content
                                                ?.value
                                                ?.let { html -> parseHtml(html) }
                                                ?.wholeText(),
                                    ),
                            )
                        }

                    is Feed.RDF ->
                        response.items.map { StatusContent.RSS.RDF(it) }.map {
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
                                        content = it,
                                        text =
                                            it.data.description
                                                .let { html -> parseHtml(html) }
                                                .wholeText(),
                                    ),
                            )
                        }
                    is Feed.Rss20 ->
                        response.channel.items.map { StatusContent.RSS.Rss20(it) }.map {
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
                                        content = it,
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
            cacheDatabase.useWriterConnection {
                it.immediateTransaction {
                    cacheDatabase.pagingTimelineDao().delete(pagingKey = pagingKey)
                    saveToDatabase(cacheDatabase, content)
                }
            }
            MediatorResult.Success(endOfPaginationReached = true)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
}
