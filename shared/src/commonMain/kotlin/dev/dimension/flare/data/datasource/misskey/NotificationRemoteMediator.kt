package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDb
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.INotificationsRequest
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class NotificationRemoteMediator(
    private val accountKey: MicroBlogKey,
    private val service: MisskeyService,
    database: CacheDatabase,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "notification_$accountKey"

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                is Request.Prepend -> return Result(
                    endOfPaginationReached = true,
                )
                Request.Refresh -> {
                    service.iNotifications(
                        INotificationsRequest(
                            limit = pageSize,
                        ),
                    )
                }

                is Request.Append -> {
                    service.iNotifications(
                        INotificationsRequest(
                            limit = pageSize,
                            untilId = request.nextKey,
                        ),
                    )
                }
            } ?: return Result(
                endOfPaginationReached = true,
            )

        return Result(
            endOfPaginationReached = response.isEmpty(),
            data =
                response.toDb(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
            nextKey = response.lastOrNull()?.id,
        )
    }
}
