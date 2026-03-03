package dev.dimension.flare.data.datasource.bluesky

import app.bsky.actor.GetSuggestionsQueryParams
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class TrendsUserPagingSource(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
) : RemoteLoader<UiProfile> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        val response =
            when (request) {
                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                PagingRequest.Refresh ->
                    service
                        .getSuggestions(
                            GetSuggestionsQueryParams(
                                limit = pageSize.toLong(),
                            ),
                        ).requireResponse()

                is PagingRequest.Append ->
                    service
                        .getSuggestions(
                            GetSuggestionsQueryParams(
                                limit = pageSize.toLong(),
                                cursor = request.nextKey,
                            ),
                        ).requireResponse()
            }

        return PagingResult(
            endOfPaginationReached = response.cursor == null,
            data = response.actors.map { it.render(accountKey) },
            nextKey = response.cursor,
        )
    }
}
