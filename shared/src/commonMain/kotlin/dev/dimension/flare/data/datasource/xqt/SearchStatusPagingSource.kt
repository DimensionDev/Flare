package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.isBottomEnd
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.http.encodeURLQueryComponent
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

@OptIn(ExperimentalPagingApi::class)
internal class SearchStatusPagingSource(
    private val service: XQTService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "search_status_$query"

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                Request.Refresh -> {
                    service
                        .getSearchTimeline(
                            variables =
                                SearchRequest(
                                    rawQuery = query,
                                    count = pageSize.toLong(),
                                ).encodeJson(),
                            referer = "https://${accountKey.host}/search?q=${query.encodeURLQueryComponent()}",
                        )
                }

                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                is Request.Append -> {
                    service.getSearchTimeline(
                        variables =
                            SearchRequest(
                                rawQuery = query,
                                count = pageSize.toLong(),
                                cursor = request.nextKey,
                            ).encodeJson(),
                        referer = "https://${accountKey.host}/search?q=${query.encodeURLQueryComponent()}",
                    )
                }
            }.body()?.data?.searchByRawQuery?.searchTimeline?.timeline?.instructions.orEmpty()
        val tweets = response.tweets()

        val data = tweets.mapNotNull { it.toDbPagingTimeline(accountKey, pagingKey) }

        return Result(
            endOfPaginationReached = response.isBottomEnd(),
            data = data,
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
