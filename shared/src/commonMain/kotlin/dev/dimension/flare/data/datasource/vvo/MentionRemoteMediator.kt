package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType

@OptIn(ExperimentalPagingApi::class)
internal class MentionRemoteMediator(
    private val service: VVOService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val onClearMarker: () -> Unit,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "mention_$accountKey"

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }

        val page =
            when (request) {
                Request.Refresh -> 0
                is Request.Prepend -> return Result(
                    endOfPaginationReached = true,
                )
                is Request.Append -> request.nextKey.toIntOrNull() ?: 0
            }

        val response =
            when (request) {
                Request.Refresh -> {
                    val result =
                        service
                            .getMentionsAt(
                                page = page,
                            )
                    onClearMarker.invoke()
                    result
                }

                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                is Request.Append -> {
                    service.getMentionsAt(
                        page = page,
                    )
                }
            }

        val statuses = response.data.orEmpty()
        val data =
            statuses.map { status ->
                status.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                )
            }

        return Result(
            endOfPaginationReached = response.data.isNullOrEmpty(),
            data = data,
            nextKey = (page + 1).toString(),
        )
    }
}
