package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.isBottomEnd
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import io.ktor.http.encodeURLQueryComponent
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

internal class SearchStatusPagingSource(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : RemoteLoader<UiTimelineV2> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
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
                        ).encodeJson(),
                    referer = "https://${accountKey.host}/search?q=${query.encodeURLQueryComponent()}",
                ).body()
                ?.data
                ?.searchByRawQuery
                ?.searchTimeline
                ?.timeline
                ?.instructions
                .orEmpty()
        val tweets = response.tweets()
        return PagingResult(
            endOfPaginationReached = response.isBottomEnd(),
            data = tweets.mapNotNull { it.render(accountKey) },
            nextKey = response.cursor(),
        )
    }
}

@Serializable
internal data class SearchRequest(
    val rawQuery: String? = null,
    val count: Long? = null,
    val cursor: String? = null,
    @Required
    val querySource: String = "typed_query",
    @Required
    val product: String = "Top",
)
