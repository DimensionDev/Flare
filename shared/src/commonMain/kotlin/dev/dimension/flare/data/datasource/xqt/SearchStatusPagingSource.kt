package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.XQT
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

@OptIn(ExperimentalPagingApi::class)
internal class SearchStatusPagingSource(
    private val service: XQTService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
    private val query: String,
) : RemoteMediator<Int, DbPagingTimelineWithStatusView>() {
    private var cursor: String? = null

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatusView>,
    ): MediatorResult {
        return try {
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
                            ).also {
                                database.transaction {
                                    database.dbPagingTimelineQueries.deletePaging(accountKey, pagingKey)
                                }
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

            MediatorResult.Success(
                endOfPaginationReached = cursor == null,
            )
        } catch (e: Throwable) {
            e.printStackTrace()
            MediatorResult.Error(e)
        }
    }
}

@Serializable
data class SearchRequest(
    val rawQuery: String? = null,
    val count: Long? = null,
    val cursor: String? = null,
    @Required
    val querySource: String = "typed_query",
    @Required
    val product: String = "Top",
)
