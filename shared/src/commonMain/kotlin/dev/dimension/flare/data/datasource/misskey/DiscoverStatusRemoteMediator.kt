package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.NotesFeaturedRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class DiscoverStatusRemoteMediator(
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "discover_status_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.notesFeatured(NotesFeaturedRequest(limit = pageSize))
                }

                is PagingRequest.Append -> {
                    service.notesFeatured(
                        NotesFeaturedRequest(
                            limit = pageSize,
                            untilId = request.nextKey,
                        ),
                    )
                }

                else -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }
            }

        return PagingResult(
            endOfPaginationReached = true,
            data =
                response.render(accountKey),
            nextKey = response.lastOrNull()?.id,
        )
    }
}
