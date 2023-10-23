package dev.dimension.flare.data.datasource.bluesky

// import androidx.paging.ExperimentalPagingApi
// import androidx.paging.LoadType
// import androidx.paging.PagingSource
// import androidx.paging.PagingState
// import androidx.paging.RemoteMediator
// import app.bsky.feed.GetAuthorFeedQueryParams
// import coil.network.HttpException
// import dev.dimension.flare.data.database.cache.CacheDatabase
// import dev.dimension.flare.data.database.cache.mapper.save
// import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
// import dev.dimension.flare.data.network.bluesky.getService
// import dev.dimension.flare.data.repository.app.UiAccount
// import dev.dimension.flare.model.MicroBlogKey
// import dev.dimension.flare.ui.model.UiStatus
// import dev.dimension.flare.ui.model.mapper.toUi
// import sh.christian.ozone.api.AtIdentifier
// import java.io.IOException
//
// @OptIn(ExperimentalPagingApi::class)
// internal class UserTimelineRemoteMediator(
//    private val account: UiAccount.Bluesky,
//    private val database: CacheDatabase,
//    private val userKey: MicroBlogKey,
//    private val pagingKey: String,
// ) : RemoteMediator<Int, DbPagingTimelineWithStatus>() {
//    var cursor: String? = null
//    override suspend fun load(
//        loadType: LoadType,
//        state: PagingState<Int, DbPagingTimelineWithStatus>,
//    ): MediatorResult {
//        val service = account.getService()
//        return try {
//            val response = when (loadType) {
//                LoadType.REFRESH -> service.getAuthorFeed(
//                    GetAuthorFeedQueryParams(
//                        limit = state.config.pageSize.toLong(),
//                        actor = AtIdentifier(userKey.id),
//                    ),
//                ).maybeResponse()
//
//                LoadType.PREPEND -> {
//                    return MediatorResult.Success(
//                        endOfPaginationReached = true,
//                    )
//                }
//
//                LoadType.APPEND -> {
//                    service.getAuthorFeed(
//                        GetAuthorFeedQueryParams(
//                            limit = state.config.pageSize.toLong(),
//                            cursor = cursor,
//                            actor = AtIdentifier(userKey.id),
//                        ),
//                    ).maybeResponse()
//                }
//            } ?: return MediatorResult.Success(
//                endOfPaginationReached = true,
//            )
//
//            cursor = response.cursor
//            with(database) {
//                with(response.feed) {
//                    save(account.accountKey, pagingKey)
//                }
//            }
//
//            MediatorResult.Success(
//                endOfPaginationReached = response.feed.isEmpty(),
//            )
//        } catch (e: IOException) {
//            MediatorResult.Error(e)
//        } catch (e: HttpException) {
//            MediatorResult.Error(e)
//        }
//    }
// }
//
// internal class UserTimelinePagingSource(
//    private val account: UiAccount.Bluesky,
//    private val userKey: MicroBlogKey,
// ) : PagingSource<String, UiStatus>() {
//    override fun getRefreshKey(state: PagingState<String, UiStatus>): String? {
//        return null
//    }
//
//    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiStatus> {
//        try {
//            val service = account.getService()
//            val response = service.getAuthorFeed(
//                GetAuthorFeedQueryParams(
//                    limit = params.loadSize.toLong(),
//                    cursor = params.key,
//                    actor = AtIdentifier(userKey.id),
//                ),
//            ).maybeResponse() ?: return LoadResult.Error(
//                IOException("response is null"),
//            )
//
//            return LoadResult.Page(
//                data = response.feed.map {
//                    it.toUi(account.accountKey)
//                },
//                prevKey = null,
//                nextKey = if (response.cursor != params.key) response.cursor else null,
//            )
//        } catch (e: Exception) {
//            e.printStackTrace()
//            return LoadResult.Error(e)
//        }
//    }
// }
