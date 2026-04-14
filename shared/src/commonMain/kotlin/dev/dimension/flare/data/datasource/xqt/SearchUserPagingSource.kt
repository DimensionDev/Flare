package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.isBottomEnd
import dev.dimension.flare.data.database.cache.mapper.users
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render
import io.ktor.http.encodeURLQueryComponent

internal class SearchUserPagingSource(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : RemoteLoader<UiProfile> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        val cursor =
            when (request) {
                PagingRequest.Refresh -> {
                    null
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    request.nextKey
                }
            }
        val response =
            service
                .getSearchTimeline(
                    variables =
                        SearchRequest(
                            rawQuery = query,
                            count = pageSize.toLong(),
                            cursor = cursor,
                            product = "People",
                        ).encodeJson(),
                    referer = "https://${accountKey.host}/search?q=${query.encodeURLQueryComponent()}",
                ).body()
                ?.data
                ?.searchByRawQuery
                ?.searchTimeline
                ?.timeline
                ?.instructions
                .orEmpty()
        val nextKey = response.cursor()
        val users = response.users()
        return PagingResult(
            data = users.map { it.render(accountKey = accountKey) },
            nextKey = if (response.isBottomEnd() || users.isEmpty()) null else nextKey,
        )
    }
}
