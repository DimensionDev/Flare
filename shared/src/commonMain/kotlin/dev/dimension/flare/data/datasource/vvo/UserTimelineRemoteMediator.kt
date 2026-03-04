package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.vvo
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val userKey: MicroBlogKey,
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
    private val mediaOnly: Boolean,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String =
        buildString {
            append("user_timeline")
            if (mediaOnly) {
                append("_mediaOnly")
            }
            append(accountKey.toString())
            append(userKey.toString())
        }

    private var containerid: String? = null

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (mediaOnly) {
            return PagingResult(
                endOfPaginationReached = true,
            )
        }

        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }

        if (containerid == null) {
            containerid =
                service
                    .getContainerIndex(type = "uid", value = userKey.id)
                    .data
                    ?.tabsInfo
                    ?.tabs
                    ?.firstOrNull {
                        it.tabType == vvo
                    }?.containerid
        }

        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.getContainerIndex(
                        type = "uid",
                        value = userKey.id,
                        containerId = containerid,
                    )
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service.getContainerIndex(
                        type = "uid",
                        value = userKey.id,
                        containerId = containerid,
                        sinceId = request.nextKey,
                    )
                }
            }

        val data =
            response.data
                ?.cards
                ?.mapNotNull { it.mblog }
                .orEmpty()
                .map { it.render(accountKey) }

        return PagingResult(
            endOfPaginationReached = response.data?.cardlistInfo?.sinceID == null,
            data = data,
            nextKey =
                response.data
                    ?.cardlistInfo
                    ?.sinceID
                    ?.toString(),
        )
    }
}
