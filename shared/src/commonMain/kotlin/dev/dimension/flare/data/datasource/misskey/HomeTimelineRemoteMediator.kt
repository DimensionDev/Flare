package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.NotesHybridTimelineRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class HomeTimelineRemoteMediator(
    private val accountKey: MicroBlogKey,
    private val service: MisskeyService,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey = "home_$accountKey"

    override val supportPrepend: Boolean
        get() = true

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                is PagingRequest.Prepend -> {
                    service.notesTimeline(
                        NotesHybridTimelineRequest(
                            limit = pageSize,
                            sinceId = request.previousKey,
                        ),
                    )
                }

                PagingRequest.Refresh -> {
                    service.notesTimeline(
                        NotesHybridTimelineRequest(
                            limit = pageSize,
                        ),
                    )
                }

                is PagingRequest.Append -> {
                    service.notesTimeline(
                        NotesHybridTimelineRequest(
                            limit = pageSize,
                            untilId = request.nextKey,
                        ),
                    )
                }
            }

        return PagingResult(
            endOfPaginationReached = response.isEmpty(),
            data =
                response.render(accountKey),
            nextKey = response.lastOrNull()?.id,
            previousKey = response.firstOrNull()?.id,
        )
    }
}
