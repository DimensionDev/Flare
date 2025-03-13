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
import kotlinx.coroutines.flow.firstOrNull
import kotlin.uuid.Uuid

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val service: MisskeyService,
    private val pagingKey: String,
    private val statusOnly: Boolean,
) : RemoteMediator<Int, DbPagingTimelineWithStatus>() {
    private var page = 1

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
            if (!database.pagingTimelineDao().existsPaging(accountKey, pagingKey)) {
                database.statusDao().get(statusKey, accountKey).firstOrNull()?.let {
                    database
                        .pagingTimelineDao()
                        .insertAll(
                            listOf(
                                DbPagingTimeline(
                                    accountKey = accountKey,
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
                            )
                    listOf(current)
                } else {
                    val current =
                        if (loadType == LoadType.REFRESH) {
                            page = 0
                            service
                                .notesShow(
                                    IPinRequest(noteId = statusKey.id),
                                )
                        } else {
                            page++
                            null
                        }
                    val lastItem =
                        database.pagingTimelineDao().getLastPagingTimeline(pagingKey)?.takeIf {
                            it.timeline.statusKey != statusKey
                        }
                    val children =
                        service
                            .notesChildren(
                                NotesChildrenRequest(
                                    noteId = statusKey.id,
                                    untilId = lastItem?.timeline?.statusKey?.id,
                                    limit = state.config.pageSize,
                                ),
                            ).orEmpty()
                    listOfNotNull(current?.reply, current) + children
                }.filterNotNull()
            Misskey.save(
                database = database,
                accountKey = accountKey,
                pagingKey = pagingKey,
                data = result,
                sortIdProvider = {
                    val index = result.indexOf(it)
                    -(index + page * state.config.pageSize).toLong()
                },
            )
            MediatorResult.Success(
                endOfPaginationReached = true,
            )
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}
