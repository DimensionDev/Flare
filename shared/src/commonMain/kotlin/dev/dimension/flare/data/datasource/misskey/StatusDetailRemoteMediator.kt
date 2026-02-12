package dev.dimension.flare.data.datasource.misskey

import SnowflakeIdGenerator
import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.IPinRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesChildrenRequest
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.firstOrNull

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val service: MisskeyService,
    private val statusOnly: Boolean,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String =
        buildString {
            append("status_detail_")
            if (statusOnly) {
                append("status_only_")
            }
            append(statusKey.toString())
            append("_")
            append(accountKey.toString())
        }

    override suspend fun timeline(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> {
        val result =
            when (request) {
                is PagingRequest.Append -> {
                    if (statusOnly) {
                        return PagingResult(
                            endOfPaginationReached = true,
                        )
                    }
                    service
                        .notesChildren(
                            NotesChildrenRequest(
                                noteId = statusKey.id,
                                untilId = request.nextKey.takeIf { it.isNotEmpty() },
                                limit = pageSize,
                            ),
                        )
                }

                is PagingRequest.Prepend ->
                    return PagingResult(
                        endOfPaginationReached = true,
                    )

                PagingRequest.Refresh -> {
                    if (!database.pagingTimelineDao().existsPaging(accountKey, pagingKey)) {
                        val status =
                            database
                                .statusDao()
                                .get(statusKey, AccountType.Specific(accountKey))
                                .firstOrNull()
                        status?.let {
                            database.connect {
                                database
                                    .pagingTimelineDao()
                                    .insertAll(
                                        listOf(
                                            DbPagingTimeline(
                                                accountType = AccountType.Specific(accountKey),
                                                statusKey = statusKey,
                                                pagingKey = pagingKey,
                                                sortId = 0,
                                            ),
                                        ),
                                    )
                            }
                        }
                    }
                    val current =
                        service
                            .notesShow(
                                IPinRequest(noteId = statusKey.id),
                            )
                    if (statusOnly) {
                        listOf(current)
                    } else {
                        listOfNotNull(current.reply, current)
                    }
                }
            }

        return PagingResult(
            endOfPaginationReached = statusOnly || result.isEmpty(),
            data =
                result.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    sortIdProvider = {
                        -SnowflakeIdGenerator.nextId()
                    },
                ),
            nextKey =
                if (request == PagingRequest.Refresh) {
                    ""
                } else {
                    result.lastOrNull()?.id
                },
        )
    }
}
