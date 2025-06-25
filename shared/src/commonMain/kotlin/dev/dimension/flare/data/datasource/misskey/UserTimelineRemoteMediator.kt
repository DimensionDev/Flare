package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Misskey
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.UsersNotesRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersShowRequest
import dev.dimension.flare.model.MicroBlogKey
import kotlin.time.Instant

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val accountKey: MicroBlogKey,
    private val service: MisskeyService,
    private val userKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val pagingKey: String,
    private val onlyMedia: Boolean = false,
    private val withReplies: Boolean = false,
    private val withPinned: Boolean = false,
) : BaseRemoteMediator<Int, DbPagingTimelineWithStatus>() {
    var pinnedIds = emptyList<String>()

    override suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        val response =
            when (loadType) {
                LoadType.PREPEND -> return MediatorResult.Success(
                    endOfPaginationReached = true,
                )

                LoadType.REFRESH -> {
                    val pinned =
                        if (withPinned) {
                            service
                                .usersShow(
                                    usersShowRequest =
                                        UsersShowRequest(
                                            userId = userKey.id,
                                        ),
                                ).let {
                                    pinnedIds = it.pinnedNoteIds
                                    it.pinnedNotes
                                }
                        } else {
                            emptyList()
                        }
                    pinned +
                        service
                            .usersNotes(
                                UsersNotesRequest(
                                    userId = userKey.id,
                                    limit = state.config.pageSize,
                                    withReplies = withReplies,
                                ).let {
                                    if (onlyMedia) {
                                        it.copy(
                                            withFiles = true,
                                            withRenotes = false,
                                            withReplies = false,
                                            withChannelNotes = true,
                                        )
                                    } else {
                                        it
                                    }
                                },
                            ).filter {
                                it.id !in pinnedIds
                            }
                }

                LoadType.APPEND -> {
                    val lastItem =
                        database.pagingTimelineDao().getLastPagingTimeline(pagingKey)
                            ?: return MediatorResult.Success(
                                endOfPaginationReached = true,
                            )
                    service
                        .usersNotes(
                            UsersNotesRequest(
                                userId = userKey.id,
                                limit = state.config.pageSize,
                                untilId = lastItem.timeline.statusKey.id,
                                withReplies = withReplies,
                            ).let {
                                if (onlyMedia) {
                                    it.copy(
                                        withFiles = true,
                                        withRenotes = false,
                                        withReplies = false,
                                        withChannelNotes = true,
                                    )
                                } else {
                                    it
                                }
                            },
                        ).filter {
                            it.id !in pinnedIds
                        }
                }
            } ?: return MediatorResult.Success(
                endOfPaginationReached = true,
            )
        if (loadType == LoadType.REFRESH) {
            database.pagingTimelineDao().delete(pagingKey = pagingKey, accountKey = accountKey)
        }

        Misskey.save(
            database = database,
            accountKey = accountKey,
            pagingKey = pagingKey,
            data = response,
            sortIdProvider = {
                if (it.id in pinnedIds) {
                    Long.MAX_VALUE
                } else {
                    Instant.parse(it.createdAt).toEpochMilliseconds()
                }
            },
        )

        return MediatorResult.Success(
            endOfPaginationReached = response.isEmpty(),
        )
    }
}
