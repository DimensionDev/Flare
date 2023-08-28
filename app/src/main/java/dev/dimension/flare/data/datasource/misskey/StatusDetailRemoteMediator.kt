package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import coil.network.HttpException
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.save
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.repository.app.UiAccount
import dev.dimension.flare.model.MicroBlogKey
import java.io.IOException
import java.util.UUID

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val account: UiAccount.Misskey,
    private val pagingKey: String,
    private val statusOnly: Boolean,
) : RemoteMediator<Int, DbPagingTimelineWithStatus>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        return try {
            if (loadType == LoadType.PREPEND) {
                return MediatorResult.Success(
                    endOfPaginationReached = true,
                )
            }
            if (!database.pagingTimelineDao().exists(pagingKey, account.accountKey)) {
                database.statusDao().getStatus(statusKey, account.accountKey)?.let {
                    database.pagingTimelineDao()
                        .insertAll(
                            listOf(
                                DbPagingTimeline(
                                    _id = UUID.randomUUID().toString(),
                                    accountKey = account.accountKey,
                                    statusKey = statusKey,
                                    pagingKey = pagingKey,
                                    sortId = 0,
                                ),
                            ),
                        )
                }
            }
            val result = if (statusOnly) {
                val current = account.service.lookupStatus(
                    statusKey.id,
                )
                listOf(current)
            } else {
                val current = if (loadType == LoadType.REFRESH) {
                    account.service.lookupStatus(
                        statusKey.id,
                    )
                } else {
                    null
                }
                val lastItem = state.lastItemOrNull()
                    ?: return MediatorResult.Success(
                        endOfPaginationReached = true,
                    )
                val children = account.service.childrenTimeline(
                    statusKey.id,
                    count = state.config.pageSize,
                    until_id = lastItem.status.status.data.statusKey.id,
                ).orEmpty()
                listOfNotNull(current?.reply, current) + children
//                context.ancestors.orEmpty() + listOf(current) + context.descendants.orEmpty()
            }.filterNotNull()
            with(database) {
                with(result) {
                    save(account.accountKey, pagingKey) {
                        -result.indexOf(it).toLong()
                    }
                }
            }
            MediatorResult.Success(
                endOfPaginationReached = true,
            )
        } catch (e: IOException) {
            MediatorResult.Error(e)
        } catch (e: HttpException) {
            MediatorResult.Error(e)
        }
    }
}
