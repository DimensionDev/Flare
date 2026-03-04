package dev.dimension.flare.data.datasource.misskey

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.ui.model.UiHashtag

internal class TrendHashtagPagingSource(
    private val service: MisskeyService,
) : RemoteLoader<UiHashtag> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiHashtag> {
        if (request is PagingRequest.Prepend || request is PagingRequest.Append) {
            return PagingResult(
                endOfPaginationReached = true,
            )
        }
        val data =
            service.hashtagsTrend().map {
                UiHashtag(
                    hashtag = it.tag,
                    description = null,
                    searchContent = "#${it.tag}",
                )
            }
        return PagingResult(
            endOfPaginationReached = true,
            data = data,
        )
    }
}
