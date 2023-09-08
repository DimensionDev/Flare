package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.bsky.notification.ListNotificationsQueryParams
import coil.network.HttpException
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.saveNotification
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.bluesky.getService
import dev.dimension.flare.data.repository.app.UiAccount
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.mapper.toUi
import java.io.IOException

@OptIn(ExperimentalPagingApi::class)
internal class NotificationRemoteMediator(
    private val account: UiAccount.Bluesky,
    private val database: CacheDatabase,
    private val pagingKey: String,
) : RemoteMediator<Int, DbPagingTimelineWithStatus>() {
    private var cursor: String? = null
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        val service = account.getService()
        return try {
            val response = when (loadType) {
                LoadType.REFRESH -> {
                    service.listNotifications(
                        ListNotificationsQueryParams(
                            limit = state.config.pageSize.toLong(),
                        ),
                    ).maybeResponse()
                }

                LoadType.APPEND -> {
                    service.listNotifications(
                        ListNotificationsQueryParams(
                            limit = state.config.pageSize.toLong(),
                            cursor = cursor,
                        ),
                    ).maybeResponse()
                }

                else -> {
                    return MediatorResult.Success(
                        endOfPaginationReached = true,
                    )
                }
            } ?: return MediatorResult.Success(
                endOfPaginationReached = true,
            )

            cursor = response.cursor
            with(database) {
                with(response.notifications) {
                    saveNotification(account.accountKey, pagingKey)
                }
            }

            MediatorResult.Success(
                endOfPaginationReached = response.notifications.isEmpty(),
            )
        } catch (e: IOException) {
            MediatorResult.Error(e)
        } catch (e: HttpException) {
            MediatorResult.Error(e)
        }
    }
}

internal class NotificationPagingSrouce(
    private val account: UiAccount.Bluesky,
) : androidx.paging.PagingSource<String, UiStatus>() {
    override fun getRefreshKey(state: PagingState<String, UiStatus>): String? {
        return null
    }

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiStatus> {
        val service = account.getService()
        return try {
            val response = service.listNotifications(
                ListNotificationsQueryParams(
                    limit = params.loadSize.toLong(),
                    cursor = params.key,
                ),
            ).maybeResponse() ?: return LoadResult.Error(
                IOException("response is null"),
            )

            LoadResult.Page(
                data = response.notifications.map {
                    it.toUi(account.accountKey)
                },
                prevKey = null,
                nextKey = if (response.cursor != params.key) response.cursor else null,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return LoadResult.Error(e)
        }
    }
}
