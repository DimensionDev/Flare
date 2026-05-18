package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class TrendsUserPagingSource(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
) : RemoteLoader<UiProfile> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend || request is PagingRequest.Append) {
            return PagingResult(
                endOfPaginationReached = true,
            )
        }
        val data =
            service
                .getUserRecommendations(
                    limit = pageSize,
                    userId = accountKey.id,
                ).mapNotNull {
                    if (it.user != null && it.userID != null) {
                        User(
                            legacy = it.user,
                            restId = it.userID,
                        ).render(accountKey = accountKey)
                    } else {
                        null
                    }
                }
        return PagingResult(
            data = data,
            endOfPaginationReached = true,
        )
    }
}
