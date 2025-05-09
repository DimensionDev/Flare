package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.XQT
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.xqtHost
import io.ktor.http.encodeURLQueryComponent
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

@OptIn(ExperimentalPagingApi::class)
internal class SearchStatusPagingSource(
    private val service: XQTService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
    private val query: String,
) : BaseRemoteMediator<Int, DbPagingTimelineWithStatus>() {
    private var cursor: String? = null

    override suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
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
                            referer = "https://$xqtHost/search?q=${query.encodeURLQueryComponent()}",
                        ).also {
                            database.pagingTimelineDao().delete(pagingKey = pagingKey, accountKey = accountKey)
                        }
                }
                LoadType.PREPEND -> {
                    return MediatorResult.Success(
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
                        referer = "https://$xqtHost/search?q=${query.encodeURLQueryComponent()}",
                    )
                }
            }.body()?.data?.searchByRawQuery?.searchTimeline?.timeline?.instructions.orEmpty()
        val tweets = response.tweets()
        cursor = response.cursor()

        XQT.save(
            accountKey = accountKey,
            pagingKey = pagingKey,
            database = database,
            tweet = tweets,
        )

        return MediatorResult.Success(
            endOfPaginationReached = tweets.isEmpty(),
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
