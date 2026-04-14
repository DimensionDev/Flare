package dev.dimension.flare.data.datasource.misskey

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.NotesHybridTimelineRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

internal class HybridTimelineRemoteMediator(
    private val accountKey: MicroBlogKey,
    private val service: MisskeyService,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey = "hybrid_timeline_$accountKey"

    override val supportPrepend: Boolean
        get() = true

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                is PagingRequest.Prepend -> {
                    service.notesHybridTimeline(
                        NotesHybridTimelineRequest(
                            limit = pageSize,
                            sinceId = request.previousKey,
                        ),
                    )
                }

                PagingRequest.Refresh -> {
                    service.notesHybridTimeline(
                        NotesHybridTimelineRequest(
                            limit = pageSize,
                        ),
                    )
                }

                is PagingRequest.Append -> {
                    service.notesHybridTimeline(
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
