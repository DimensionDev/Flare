package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.NotesUserListTimelineRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class ListTimelineRemoteMediator(
    private val listId: String,
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey = "list_${accountKey}_$listId"

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
                        .notesUserListTimeline(
                            NotesUserListTimelineRequest(
                                listId = listId,
                                limit = pageSize,
                                withRenotes = true,
                                allowPartial = true,
                            ),
                        )
                }

                is PagingRequest.Prepend -> {
                    service
                        .notesUserListTimeline(
                            NotesUserListTimelineRequest(
                                listId = listId,
                                limit = pageSize,
                                sinceId = request.previousKey,
                                withRenotes = true,
                                allowPartial = true,
                            ),
                        )
                }

                is PagingRequest.Append -> {
                    service
                        .notesUserListTimeline(
                            NotesUserListTimelineRequest(
                                listId = listId,
                                limit = pageSize,
                                untilId = request.nextKey,
                                withRenotes = true,
                                allowPartial = true,
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
