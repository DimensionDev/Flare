package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class BookmarkTimelineRemoteMediator(
    private val service: MastodonService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "bookmark_$accountKey"

    override val supportPrepend: Boolean
        get() = true

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service
                        .bookmarks(
                            limit = pageSize,
                        )
                }

                is PagingRequest.Prepend -> {
                    service.bookmarks(
                        limit = pageSize,
                        min_id = request.previousKey,
                    )
                }

                is PagingRequest.Append -> {
                    service.bookmarks(
                        limit = pageSize,
                        max_id = request.nextKey,
                    )
                }
            }

        return PagingResult(
            endOfPaginationReached = response.isEmpty() || response.next == null,
            data = response.render(accountKey),
            nextKey = response.next,
            previousKey = response.prev,
        )
    }
}
