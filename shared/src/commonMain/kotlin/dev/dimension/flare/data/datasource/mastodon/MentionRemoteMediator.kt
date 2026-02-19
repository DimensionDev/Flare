package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDb
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
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
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service
                        .notification(
                            limit = pageSize,
                            exclude_types = NotificationTypes.entries.filter { it != NotificationTypes.Mention },
                        )
                }

                is PagingRequest.Prepend -> {
                    service.notification(
                        limit = pageSize,
                        min_id = request.previousKey,
                        exclude_types = NotificationTypes.entries.filter { it != NotificationTypes.Mention },
                    )
                }

                is PagingRequest.Append -> {
                    service.notification(
                        limit = pageSize,
                        max_id = request.nextKey,
                        exclude_types = NotificationTypes.entries.filter { it != NotificationTypes.Mention },
                    )
                }
            }

        return PagingResult(
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
