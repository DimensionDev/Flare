package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.AdminAdListRequest
import dev.dimension.flare.model.MicroBlogKey
import kotlin.time.Instant

@OptIn(ExperimentalPagingApi::class)
internal class FavouriteTimelineRemoteMediator(
    private val accountKey: MicroBlogKey,
    private val service: MisskeyService,
    database: CacheDatabase,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String
        get() =
            buildString {
                append("favourite_")
                append(accountKey.toString())
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
                    service.iFavorites(
                        AdminAdListRequest(
                            limit = pageSize,
                        ),
                    )
                }

                is PagingRequest.Append -> {
                    service.iFavorites(
                        AdminAdListRequest(
                            limit = pageSize,
                            untilId = request.nextKey,
                        ),
                    )
                }
            }

        val notes = response.map { it.note }
        val data =
            notes.toDbPagingTimeline(
                accountKey = accountKey,
                pagingKey = pagingKey,
                sortIdProvider = {
                    response.find { note -> note.noteId == it.id }?.createdAt?.let {
                        Instant.parse(it).toEpochMilliseconds()
                    } ?: 0
                },
            )

        return PagingResult(
            endOfPaginationReached = response.isEmpty(),
            data = data,
            nextKey = response.lastOrNull()?.noteId,
        )
    }
}
