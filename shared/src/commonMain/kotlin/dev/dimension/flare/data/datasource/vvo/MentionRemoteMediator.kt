package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class MentionRemoteMediator(
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
    private val onClearMarker: () -> Unit,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "mention_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
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
                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }
                is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 0
            }

        val response =
            service.getMentionsAt(
                page = page,
            )
        if (request == PagingRequest.Refresh) {
            onClearMarker.invoke()
        }

        val data = response.data.orEmpty().map { it.render(accountKey) }
        return PagingResult(
            endOfPaginationReached = data.isEmpty(),
            data = data,
            nextKey = (page + 1).toString(),
        )
    }
}
