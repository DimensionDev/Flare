package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.UsersNotesRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersShowRequest
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.datetime.Instant

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val accountKey: MicroBlogKey,
    private val service: MisskeyService,
    private val userKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val onlyMedia: Boolean = false,
    private val withReplies: Boolean = false,
    private val withPinned: Boolean = false,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    var pinnedIds = emptyList<String>()

    override val pagingKey: String
        get() =
            buildString {
                append("user_timeline_")
                if (withReplies) {
                    append("replies")
                }
                if (withPinned) {
                    append("pinned")
                }
                if (onlyMedia) {
                    append("media")
                }
                append(accountKey.toString())
                append(userKey.toString())
            }

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val response =
            when (loadType) {
                LoadType.PREPEND -> return Result(
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
                            ?: return Result(
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
            } ?: return Result(
                endOfPaginationReached = true,
            )

        return Result(
            endOfPaginationReached = response.isEmpty(),
            data =
                response.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    sortIdProvider = {
                        if (it.id in pinnedIds) {
                            Long.MAX_VALUE
                        } else {
                            Instant.parse(it.createdAt).toEpochMilliseconds()
                        }
                    },
                ),
        )
    }
}
