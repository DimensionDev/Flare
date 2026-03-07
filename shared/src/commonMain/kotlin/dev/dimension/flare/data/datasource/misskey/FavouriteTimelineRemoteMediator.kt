package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.AdminAdListRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class FavouriteTimelineRemoteMediator(
    private val accountKey: MicroBlogKey,
    private val service: MisskeyService,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String
        get() =
            buildString {
                append("favourite_")
                append(accountKey.toString())
            }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                is PagingRequest.Prepend -> return PagingResult(
                    endOfPaginationReached = true,
                )

                PagingRequest.Refresh -> {
                    service.iFavorites(
                        AdminAdListRequest(
                            limit = pageSize,
                        ),
                    )
                }

                is PagingRequest.Append -> {
                    service.iFavorites(
                        AdminAdListRequest(
                            limit = pageSize,
                            untilId = request.nextKey,
                        ),
                    )
                }
            }

        val notes = response.map { it.note }
        val data =
            notes.render(accountKey)

        return PagingResult(
            endOfPaginationReached = response.isEmpty(),
            data = data,
            nextKey = response.lastOrNull()?.noteId,
        )
    }
}
