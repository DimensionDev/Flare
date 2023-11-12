package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Mastodon
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val service: MastodonService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
    private val statusOnly: Boolean,
) : RemoteMediator<Int, DbPagingTimelineWithStatusView>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatusView>,
    ): MediatorResult {
        return try {
            if (loadType != LoadType.REFRESH) {
                return MediatorResult.Success(
                    endOfPaginationReached = true,
                )
            }
            if (!database.dbPagingTimelineQueries.existsPaging(accountKey, pagingKey).executeAsOne()) {
                database.dbStatusQueries.get(statusKey, accountKey).executeAsOneOrNull()?.let {
                    database.dbPagingTimelineQueries
                        .insert(
                            account_key = accountKey,
                            status_key = statusKey,
                            paging_key = pagingKey,
                            sort_id = 0,
                        )
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

            Mastodon.save(
                database = database,
                accountKey = accountKey,
                pagingKey = pagingKey,
                data = result,
            ) {
                -result.indexOf(it).toLong()
            }

            MediatorResult.Success(
                endOfPaginationReached = true,
            )
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}
