package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Misskey
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.IPinRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesChildrenRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import kotlinx.coroutines.flow.firstOrNull
import kotlin.uuid.Uuid

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val account: UiAccount.Misskey,
    private val service: MisskeyService,
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
            if (!database.pagingTimelineDao().existsPaging(account.accountKey, pagingKey)) {
                database.statusDao().get(statusKey, account.accountKey).firstOrNull()?.let {
                    database
                        .pagingTimelineDao()
                        .insertAll(
                            listOf(
                                DbPagingTimeline(
                                    accountKey = account.accountKey,
                                    statusKey = statusKey,
                                    pagingKey = pagingKey,
                                    sortId = 0,
                                    _id = Uuid.random().toString(),
                                ),
                            ),
                        )
                }
            }
            val result =
                if (statusOnly) {
                    val current =
                        service
                            .notesShow(
                                IPinRequest(noteId = statusKey.id),
                            ).body()
                    listOf(current)
                } else {
                    val current =
                        if (loadType == LoadType.REFRESH) {
                            service
                                .notesShow(
                                    IPinRequest(noteId = statusKey.id),
                                ).body()
                        } else {
                            null
                        }
                    val lastItem =
                        state.lastItemOrNull()
                            ?: return MediatorResult.Success(
                                endOfPaginationReached = true,
                            )
                    val children =
                        service
                            .notesChildren(
                                NotesChildrenRequest(
                                    noteId = statusKey.id,
                                    untilId = lastItem.timeline.statusKey.id,
                                    limit = state.config.pageSize,
                                ),
                            ).body()
                            .orEmpty()
                    listOfNotNull(current?.reply, current) + children
//                context.ancestors.orEmpty() + listOf(current) + context.descendants.orEmpty()
                }.filterNotNull()
            Misskey.save(
                database = database,
                accountKey = account.accountKey,
                pagingKey = pagingKey,
                data = result,
            )
            MediatorResult.Success(
                endOfPaginationReached = true,
            )
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}
