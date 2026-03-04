package dev.dimension.flare.data.datasource.misskey

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.UsersSearchRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class SearchUserPagingSource(
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : RemoteLoader<UiProfile> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        val offset =
            when (request) {
                PagingRequest.Refresh -> 0
                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }
                is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 0
            }
        val response =
            service.usersSearch(
                UsersSearchRequest(
                    query = query,
                    limit = pageSize,
                    offset = offset,
                ),
            )
        return PagingResult(
            data = response.map { it.render(accountKey) },
            nextKey = if (response.isEmpty()) null else (offset + pageSize).toString(),
        )
    }
}
