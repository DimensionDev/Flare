package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import coil.network.HttpException
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.save
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.mastodon.MastodonException
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey
import java.io.IOException

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val service: MastodonService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
    private val pagingKey: String,
) : RemoteMediator<Int, DbPagingTimelineWithStatus>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        return try {
            val response = when (loadType) {
                LoadType.REFRESH -> service.userTimeline(
                    user_id = userKey.id,
                    count = state.config.pageSize,
                )

                LoadType.PREPEND -> {
                    val firstItem = state.firstItemOrNull()
                    service.userTimeline(
                        user_id = userKey.id,
                        count = state.config.pageSize,
                        min_id = firstItem?.status?.status?.data?.statusKey?.id,
                    )
                }

                LoadType.APPEND -> {
                    val lastItem = state.lastItemOrNull()
                        ?: return MediatorResult.Success(
                            endOfPaginationReached = true,
                        )
                    service.userTimeline(
                        user_id = userKey.id,
                        count = state.config.pageSize,
                        max_id = lastItem.status.status.data.statusKey.id,
                    )
                }
            }

            with(database) {
                with(response) {
                    save(accountKey, pagingKey)
                }
            }

            MediatorResult.Success(
                endOfPaginationReached = response.isEmpty(),
            )
        } catch (e: IOException) {
            MediatorResult.Error(e)
        } catch (e: HttpException) {
            MediatorResult.Error(e)
        } catch (e: MastodonException) {
            MediatorResult.Error(e)
        }
    }
}
