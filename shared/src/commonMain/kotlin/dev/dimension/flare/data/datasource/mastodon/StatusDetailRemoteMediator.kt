package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
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
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        if (loadType != LoadType.REFRESH) {
            return Result(
                endOfPaginationReached = true,
            )
        }
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
        val result =
            if (statusOnly) {
                val current =
                    service.lookupStatus(
                        statusKey.id,
                    )
                listOf(current)
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

        return Result(
            endOfPaginationReached = true,
            data =
                result.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ) {
                    -result.indexOf(it).toLong()
                },
        )
    }
}
