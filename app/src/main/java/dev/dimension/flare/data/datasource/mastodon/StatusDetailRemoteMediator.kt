package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import coil.network.HttpException
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.save
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.mastodon.MastodonException
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey
import java.io.IOException
import java.util.UUID

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val service: MastodonService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
    private val statusOnly: Boolean,
) : RemoteMediator<Int, DbPagingTimelineWithStatus>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        return try {
            if (loadType != LoadType.REFRESH) {
                return MediatorResult.Success(
                    endOfPaginationReached = true,
                )
            }
            if (!database.pagingTimelineDao().exists(pagingKey, accountKey)) {
                database.statusDao().getStatus(statusKey, accountKey)?.let {
                    database.pagingTimelineDao()
                        .insertAll(
                            listOf(
                                DbPagingTimeline(
                                    _id = UUID.randomUUID().toString(),
                                    accountKey = accountKey,
                                    statusKey = statusKey,
                                    pagingKey = pagingKey,
                                    sortId = 0,
                                ),
                            ),
                        )
                }
            }
            val result = if (statusOnly) {
                val current = service.lookupStatus(
                    statusKey.id,
                )
                listOf(current)
            } else {
                val context = service.context(
                    statusKey.id,
                )
                val current = service.lookupStatus(
                    statusKey.id,
                )
                context.ancestors.orEmpty() + listOf(current) + context.descendants.orEmpty()
            }

            with(database) {
                with(result) {
                    save(accountKey, pagingKey) {
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
        } catch (e: MastodonException) {
            MediatorResult.Error(e)
        }
    }
}
