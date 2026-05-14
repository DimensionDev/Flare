package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.AntennasNotesRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class AntennasTimelineRemoteMediator(
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
    private val id: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey = "antennas_${id}_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service
                        .antennasNotes(
                            AntennasNotesRequest(
                                antennaId = id,
                                limit = pageSize,
                            ),
                        )
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service
                        .antennasNotes(
                            AntennasNotesRequest(
                                antennaId = id,
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
        )
    }
}
