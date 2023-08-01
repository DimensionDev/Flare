package dev.dimension.flare.data.datasource.mastodon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.map
import coil.network.HttpException
import com.moriatsushi.koject.compose.rememberInject
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.save
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.data.repository.app.UiAccount
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.mapper.toUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val service: MastodonService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
    private val pagingKey: String
) : RemoteMediator<Int, DbPagingTimelineWithStatus>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>
    ): MediatorResult {
        return try {
            val response = when (loadType) {
                LoadType.REFRESH -> service.userTimeline(user_id = userKey.id, count = state.config.pageSize)
                LoadType.PREPEND -> {
                    val firstItem = state.firstItemOrNull()
                    service.userTimeline(
                        user_id = userKey.id,
                        count = state.config.pageSize,
                        min_id = firstItem?.status?.status?.data?.statusKey?.id
                    )
                }

                LoadType.APPEND -> {
                    val lastItem = state.lastItemOrNull()
                        ?: return MediatorResult.Success(
                            endOfPaginationReached = true
                        )
                    service.userTimeline(
                        user_id = userKey.id,
                        count = state.config.pageSize,
                        max_id = lastItem.status.status.data.statusKey.id
                    )
                }
            }

            with(database) {
                with(response) {
                    save(accountKey, pagingKey)
                }
            }

            MediatorResult.Success(
                endOfPaginationReached = response.isEmpty()
            )
        } catch (e: IOException) {
            MediatorResult.Error(e)
        } catch (e: HttpException) {
            MediatorResult.Error(e)
        }
    }
}

@OptIn(ExperimentalPagingApi::class)
@Composable
internal fun userTimelineDataSource(
    account: UiAccount.Mastodon,
    userKey: MicroBlogKey = account.accountKey,
    pageSize: Int = 20,
    pagingKey: String = "user_$userKey",
    database: CacheDatabase = rememberInject()
): Flow<PagingData<UiStatus>> {
    return remember(
        account.accountKey,
        userKey
    ) {
        Pager(
            config = PagingConfig(pageSize = pageSize),
            remoteMediator = UserTimelineRemoteMediator(
                account.service,
                database,
                account.accountKey,
                userKey,
                pagingKey
            )
        ) {
            database.pagingTimelineDao().getPagingSource(pagingKey, account.accountKey)
        }.flow.map {
            it.map {
                it.toUi()
            }
        }
    }
}
