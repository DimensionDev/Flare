package dev.dimension.flare.data.datasource.bluesky

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.bsky.feed.GetTimelineQueryParams
import com.moriatsushi.koject.compose.rememberInject
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.network.bluesky.getService
import dev.dimension.flare.data.repository.app.UiAccount
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.mapper.toUi
import kotlinx.coroutines.flow.Flow

// @OptIn(ExperimentalPagingApi::class)
// internal class HomeTimelineRemoteMediator(
//    private val account: UiAccount.Bluesky,
//    private val database: CacheDatabase,
//    private val pagingKey: String
// ) : RemoteMediator<Int, DbPagingTimelineWithStatus>() {
//
//    var cursor: String? = null
//    override suspend fun load(
//        loadType: LoadType,
//        state: PagingState<Int, DbPagingTimelineWithStatus>
//    ): MediatorResult {
//        val service = account.getService()
//        return try {
//            val response = when (loadType) {
//                LoadType.PREPEND -> return MediatorResult.Success(
//                    endOfPaginationReached = true
//                )
//                LoadType.REFRESH -> {
//                    service.getTimeline(
//                        GetTimelineQueryParams(
//                            algorithm = "reverse-chronological",
//                            limit = state.config.pageSize.toLong(),
//                        )
//                    ).maybeResponse()
//                }
//
//                LoadType.APPEND -> {
//                    val lastItem = state.lastItemOrNull()
//                        ?: return MediatorResult.Success(
//                            endOfPaginationReached = true
//                        )
//                    service.getTimeline(
//                        GetTimelineQueryParams(
//                            algorithm = "reverse-chronological",
//                            limit = state.config.pageSize.toLong(),
//                            cursor = cursor
//                        )
//                    ).maybeResponse()
//                }
//            } ?: return MediatorResult.Success(
//                endOfPaginationReached = true
//            )
//            if (loadType == LoadType.REFRESH) {
//                database.pagingTimelineDao().delete(pagingKey, account.accountKey)
//            }
//            cursor = response.cursor
//            with(database) {
//                with(response.feed) {
//                    save(account.accountKey, pagingKey)
//                }
//            }
//
//            MediatorResult.Success(
//                endOfPaginationReached = response.feed.isEmpty()
//            )
//        } catch (e: IOException) {
//            MediatorResult.Error(e)
//        } catch (e: HttpException) {
//            MediatorResult.Error(e)
//        }
//    }
// }

private class HomeTimelinePagingSource(
    private val account: UiAccount.Bluesky
) : PagingSource<String, UiStatus>() {
    override fun getRefreshKey(state: PagingState<String, UiStatus>): String? {
        return null
    }

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiStatus> {
        try {
            val service = account.getService()
            val response = service.getTimeline(
                GetTimelineQueryParams(
                    algorithm = "reverse-chronological",
                    limit = params.loadSize.toLong(),
                    cursor = params.key
                )
            ).requireResponse()
            return LoadResult.Page(
                data = response.feed.map {
                    it.toUi(account.accountKey)
                },
                prevKey = null,
                nextKey = if (response.cursor != params.key) response.cursor else null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return LoadResult.Error(e)
        }
    }
}

@Composable
internal fun homeTimelineDataSource(
    account: UiAccount.Bluesky,
    pageSize: Int = 20,
    pagingKey: String = "home",
    database: CacheDatabase = rememberInject()
): Flow<PagingData<UiStatus>> {
    return remember(
        account.accountKey
    ) {
        Pager(
            config = PagingConfig(pageSize = pageSize)
        ) {
            HomeTimelinePagingSource(account)
        }.flow
    }
}
