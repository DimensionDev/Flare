package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.VVO
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class CommentChildRemoteMediator(
    private val service: VVOService,
    private val commentKey: MicroBlogKey,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
    private val database: CacheDatabase,
) : RemoteMediator<Int, DbPagingTimelineWithStatusView>() {
    private var maxId: Long? = null
    private var page = 0

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatusView>,
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
                            .getHotFlowChild(
                                cid = commentKey.id,
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
                        page++
                        service.getHotFlowChild(
                            cid = commentKey.id,
                            maxId = maxId,
                        )
                    }
                }

            maxId = response.maxID?.takeIf { it != 0L }
            val status =
                response.data.orEmpty()
            VVO.saveComment(
                accountKey = accountKey,
                pagingKey = pagingKey,
                database = database,
                statuses = response.data.orEmpty(),
                sortIdProvider = {
                    val index = status.indexOf(it)
                    -(index + page * state.config.pageSize).toLong()
                },
            )
            MediatorResult.Success(
                endOfPaginationReached = maxId == null,
            )
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}
