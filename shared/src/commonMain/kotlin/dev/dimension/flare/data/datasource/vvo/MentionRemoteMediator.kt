package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.VVO
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineView
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class MentionRemoteMediator(
    private val service: VVOService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
) : RemoteMediator<Int, DbPagingTimelineView>() {
    var page = 1

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineView>,
    ): MediatorResult {
        return try {
            val config = service.config()
            if (config.data?.login != true) {
                return MediatorResult.Error(
                    LoginExpiredException,
                )
            }
            val response =
                when (loadType) {
                    LoadType.REFRESH -> {
                        page = 0
                        service
                            .getMentionsAt(
                                page = page,
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
                        page++
                        service.getMentionsAt(
                            page = page,
                        )
                    }
                }

            VVO.saveStatus(
                accountKey = accountKey,
                pagingKey = pagingKey,
                database = database,
                statuses = response.data.orEmpty(),
            )

            MediatorResult.Success(
                endOfPaginationReached = response.data.isNullOrEmpty(),
            )
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}
