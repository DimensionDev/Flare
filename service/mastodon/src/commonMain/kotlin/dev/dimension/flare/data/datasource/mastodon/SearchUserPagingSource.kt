package dev.dimension.flare.data.datasource.mastodon

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class SearchUserPagingSource(
    private val search: suspend (
        query: String,
        maxId: String?,
        limit: Int,
        type: String,
        following: Boolean,
        resolve: Boolean?,
    ) -> List<Account>,
    private val host: String,
    private val accountKey: MicroBlogKey?,
    private val query: String,
    private val following: Boolean = false,
    private val resolve: Boolean? = null,
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

                PagingRequest.Refresh -> {
                    search(query, null, pageSize, "accounts", following, resolve)
                }

                is PagingRequest.Append -> {
                    search(query, request.nextKey, pageSize, "accounts", following, resolve)
                }
            }

        return PagingResult(
            data = response.map { it.render(accountKey = accountKey, host = host) },
            nextKey =
                response.lastOrNull()?.id?.takeIf {
                    (request !is PagingRequest.Append || it != request.nextKey) && response.size == pageSize
                },
        )
    }
}
