package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDb
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.data.network.mastodon.api.model.MarkerUpdate
import dev.dimension.flare.data.network.mastodon.api.model.UpdateContent
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class NotificationRemoteMediator(
    private val service: MastodonService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val onClearMarker: () -> Unit,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String = "notification_$accountKey"

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val response =
            when (loadType) {
                LoadType.REFRESH -> {
                    service
                        .notification(
                            limit = state.config.pageSize,
                        ).also { notifications ->
                            notifications.firstOrNull()?.id?.let { id ->
                                service.updateMarker(MarkerUpdate(notifications = UpdateContent(id)))
                                onClearMarker.invoke()
                            }
                        }
                }

                LoadType.PREPEND -> {
                    val firstItem = state.firstItemOrNull()
                    service.notification(
                        limit = state.config.pageSize,
                        min_id = firstItem?.timeline?.statusKey?.id,
                    )
                }

                LoadType.APPEND -> {
                    val lastItem =
                        database.pagingTimelineDao().getLastPagingTimeline(pagingKey)
                            ?: return Result(
                                endOfPaginationReached = true,
                            )
                    service.notification(
                        limit = state.config.pageSize,
                        max_id = lastItem.timeline.statusKey.id,
                    )
                }
            }

        return Result(
            endOfPaginationReached = response.isEmpty(),
            data =
                response.toDb(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
        )
    }
}
