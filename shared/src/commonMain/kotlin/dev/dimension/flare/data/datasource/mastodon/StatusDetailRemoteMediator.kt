package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Mastodon
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineView
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.firstOrNull
import kotlin.uuid.Uuid

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val service: MastodonService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
    private val statusOnly: Boolean,
) : RemoteMediator<Int, DbPagingTimelineView>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineView>,
    ): MediatorResult {
        return try {
            if (loadType != LoadType.REFRESH) {
                return MediatorResult.Success(
                    endOfPaginationReached = true,
                )
            }
            if (!database.pagingTimelineDao().existsPaging(accountKey, pagingKey)) {
                database.statusDao().get(statusKey, accountKey).firstOrNull()?.let {
                    database
                        .pagingTimelineDao()
                        .insertAll(
                            listOf(
                                DbPagingTimeline(
                                    accountKey = accountKey,
                                    statusKey = statusKey,
                                    pagingKey = pagingKey,
                                    sortId = 0,
                                    _id = Uuid.random().toString(),
                                ),
                            ),
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
