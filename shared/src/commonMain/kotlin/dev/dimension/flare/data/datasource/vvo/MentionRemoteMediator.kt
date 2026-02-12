package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
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
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> {
        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }

        val page =
            when (request) {
                PagingRequest.Refresh -> 0
                is PagingRequest.Prepend -> return PagingResult(
                    endOfPaginationReached = true,
                )
                is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 0
            }

        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    val result =
                        service
                            .getMentionsAt(
                                page = page,
                            )
                    onClearMarker.invoke()
                    result
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
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

        return PagingResult(
            endOfPaginationReached = response.data.isNullOrEmpty(),
            data = data,
            nextKey = (page + 1).toString(),
        )
    }
}
