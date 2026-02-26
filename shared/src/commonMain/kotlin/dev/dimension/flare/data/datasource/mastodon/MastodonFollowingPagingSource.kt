package dev.dimension.flare.data.datasource.mastodon

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.mastodon.api.AccountResources
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class MastodonFollowingPagingSource(
    private val service: AccountResources,
    private val accountKey: MicroBlogKey?,
    private val host: String,
    private val userKey: MicroBlogKey,
) : RemoteLoader<UiProfile> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service
                        .following(
                            id = userKey.id,
                            limit = pageSize,
                        )
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service.following(
                        id = userKey.id,
                        limit = pageSize,
                        max_id = request.nextKey,
                    )
                }
            }

        return PagingResult(
            endOfPaginationReached = response.isEmpty() || response.next == null,
            data = response.map { it.render(accountKey = accountKey, host = host) },
            nextKey = response.next,
        )
    }
}
