package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Misskey
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.INotificationsRequest
import dev.dimension.flare.ui.model.UiAccount

@OptIn(ExperimentalPagingApi::class)
internal class NotificationRemoteMediator(
    private val account: UiAccount.Misskey,
    private val service: MisskeyService,
    private val database: CacheDatabase,
    private val pagingKey: String,
) : RemoteMediator<Int, DbPagingTimelineWithStatusView>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatusView>,
    ): MediatorResult {
        return try {
            val response =
                when (loadType) {
                    LoadType.PREPEND -> return MediatorResult.Success(
                        endOfPaginationReached = true,
                    )
                    LoadType.REFRESH -> {
                        service.iNotifications(
                            INotificationsRequest(
                                limit = state.config.pageSize,
                            ),
                        )
                    }

                    LoadType.APPEND -> {
                        val lastItem =
                            state.lastItemOrNull()
                                ?: return MediatorResult.Success(
                                    endOfPaginationReached = true,
                                )
                        val items = database.dbPagingTimelineQueries.getPaging(
                            account_key = account.accountKey,
                            paging_key = pagingKey,
                            limit = 100,
                            offset = 0,
                        ).executeAsList()
                        val items2 = database.dbPagingTimelineQueries.getPage(
                            account_key = account.accountKey,
                            paging_key = pagingKey,
                            limit = 100,
                            offset = 0,
                        ).executeAsList()
                        require(items.isNotEmpty())
                        require(items2.isNotEmpty())
                        service.iNotifications(
                            INotificationsRequest(
                                limit = state.config.pageSize,
                                untilId = lastItem.timeline_status_key.id,
                            ),
                        )
                    }
                }.body() ?: return MediatorResult.Success(
                    endOfPaginationReached = true,
                )
            if (loadType == LoadType.REFRESH) {
                database.dbPagingTimelineQueries.deletePaging(account.accountKey, pagingKey)
            }
            Misskey.save(
                database = database,
                accountKey = account.accountKey,
                pagingKey = pagingKey,
                data = response,
            )
            MediatorResult.Success(
                endOfPaginationReached = response.isEmpty(),
            )
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}
