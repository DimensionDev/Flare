package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.firstOrNull

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val service: MastodonService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val statusOnly: Boolean,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String =
        buildString {
            append("status_detail_")
            if (statusOnly) {
                append("status_only_")
            }
            append(statusKey.toString())
            append("_")
            append(accountKey.toString())
        }

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val result =
            when (request) {
                is Request.Append -> {
                    if (statusOnly) {
                        return Result(
                            endOfPaginationReached = true,
                        )
                    } else {
                        val context =
                            service.context(
                                statusKey.id,
                            )
                        val current =
                            service.lookupStatus(
                                statusKey.id,
                            )
                        context.ancestors.orEmpty() + listOf(current) + context.descendants.orEmpty()
                    }
                }
                is Request.Prepend ->
                    return Result(
                        endOfPaginationReached = true,
                    )
                Request.Refresh -> {
                    val exists = database.pagingTimelineDao().existsPaging(accountKey, pagingKey)
                    if (!exists) {
                        val status = database.statusDao().get(statusKey, AccountType.Specific(accountKey)).firstOrNull()
                        status?.let {
                            database.connect {
                                database
                                    .pagingTimelineDao()
                                    .insertAll(
                                        listOf(
                                            DbPagingTimeline(
                                                accountType = AccountType.Specific(accountKey),
                                                statusKey = statusKey,
                                                pagingKey = pagingKey,
                                                sortId = 0,
                                            ),
                                        ),
                                    )
                            }
                        }
                    }
                    val current =
                        service.lookupStatus(
                            statusKey.id,
                        )
                    listOf(current)
                }
            }
        val shouldLoadMore = !(request is Request.Append || statusOnly)

        return Result(
            endOfPaginationReached = !shouldLoadMore,
            data =
                result.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ) {
                    -result.indexOf(it).toLong()
                },
            nextKey = if (shouldLoadMore) pagingKey else null,
        )
    }
}
