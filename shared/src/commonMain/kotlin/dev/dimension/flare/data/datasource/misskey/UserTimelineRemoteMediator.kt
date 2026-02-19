package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
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
    database: CacheDatabase,
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
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> {
        val response =
            when (request) {
                is PagingRequest.Prepend -> return PagingResult(
                    endOfPaginationReached = true,
                )

                PagingRequest.Refresh -> {
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
                                    limit = pageSize,
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

                is PagingRequest.Append -> {
                    service
                        .usersNotes(
                            UsersNotesRequest(
                                userId = userKey.id,
                                limit = pageSize,
                                untilId = request.nextKey,
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
            }

        return PagingResult(
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
                    pinnedProvider = {
                        it.id in pinnedIds
                    },
                ),
            nextKey = response.lastOrNull()?.id,
        )
    }
}
