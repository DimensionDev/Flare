package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.isBottomEnd
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.http.encodeURLQueryComponent
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

@OptIn(ExperimentalPagingApi::class)
internal class SearchStatusPagingSource(
    private val service: XQTService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "search_status_$query"
    private var cursor: String? = null

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        println("initialQuery: $query")
        val response =
            when (loadType) {
                LoadType.REFRESH -> {
                    cursor = null
                    service
                        .getSearchTimeline(
                            variables =
                                SearchRequest(
                                    rawQuery = query,
                                    count = state.config.pageSize.toLong(),
                                ).encodeJson(),
                            referer = "https://${accountKey.host}/search?q=${query.encodeURLQueryComponent()}",
                        )
                }

                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.APPEND -> {
                    service.getSearchTimeline(
                        variables =
                            SearchRequest(
                                rawQuery = query,
                                count = state.config.pageSize.toLong(),
                                cursor = cursor,
                            ).encodeJson(),
                        referer = "https://${accountKey.host}/search?q=${query.encodeURLQueryComponent()}",
                    )
                }
            }.body()?.data?.searchByRawQuery?.searchTimeline?.timeline?.instructions.orEmpty()
        val tweets = response.tweets()
        cursor = response.cursor()

        val data = tweets.map { it.toDbPagingTimeline(accountKey, pagingKey) }

        return Result(
            endOfPaginationReached = response.isBottomEnd(),
            data = data,
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
