package dev.dimension.flare.data.datasource.mastodon

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.mastodon.api.model.Trend
import dev.dimension.flare.ui.model.UiHashtag

internal class TrendHashtagPagingSource(
    private val loadTrends: suspend () -> List<Trend>,
) : RemoteLoader<UiHashtag> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiHashtag> {
        val response =
            when (request) {
                is PagingRequest.Prepend, is PagingRequest.Append -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                PagingRequest.Refresh -> {
                    loadTrends()
                        .map {
                            UiHashtag(
                                hashtag = it.name ?: "",
                                description = null,
                                searchContent = "#${it.name}",
                            )
                        }
                }
            }

        return PagingResult(
            data = response,
        )
    }
}
