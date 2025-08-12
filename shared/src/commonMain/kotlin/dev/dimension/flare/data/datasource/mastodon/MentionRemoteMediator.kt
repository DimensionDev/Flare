package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDb
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.data.network.mastodon.api.model.NotificationTypes
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class MentionRemoteMediator(
    private val service: MastodonService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String = "mention_$accountKey"

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                Request.Refresh -> {
                    service
                        .notification(
                            limit = pageSize,
                            exclude_types = NotificationTypes.entries.filter { it != NotificationTypes.Mention },
                        )
                }

                is Request.Prepend -> {
                    service.notification(
                        limit = pageSize,
                        min_id = request.previousKey,
                        exclude_types = NotificationTypes.entries.filter { it != NotificationTypes.Mention },
                    )
                }

                is Request.Append -> {
                    service.notification(
                        limit = pageSize,
                        max_id = request.nextKey,
                        exclude_types = NotificationTypes.entries.filter { it != NotificationTypes.Mention },
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
            nextKey = response.next,
            previousKey = response.prev,
        )
    }
}
