package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.ui.model.UiHashtag

internal class TrendHashtagPagingSource(
    private val service: XQTService,
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
            service
                .getGuide(count = pageSize)
                .timeline
                ?.instructions
                ?.asSequence()
                ?.mapNotNull {
                    it.addEntries?.entries
                }?.flatten()
                ?.mapNotNull {
                    it.content?.timelineModule?.items
                }?.flatten()
                ?.mapNotNull {
                    it.item?.content?.trend
                }?.map {
                    UiHashtag(
                        hashtag = it.name.orEmpty(),
                        description = null,
                        searchContent = it.name.orEmpty(),
                    )
                }?.toList()
                .orEmpty()
        return PagingResult(
            data = data,
            endOfPaginationReached = true,
        )
    }
}
