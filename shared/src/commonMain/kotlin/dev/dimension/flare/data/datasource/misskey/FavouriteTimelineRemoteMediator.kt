package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
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
        request: Request,
    ): Result {
        val response =
            when (request) {
                is Request.Prepend -> return Result(
                    endOfPaginationReached = true,
                )

                Request.Refresh -> {
                    service.iFavorites(
                        AdminAdListRequest(
                            limit = pageSize,
                        ),
                    )
                }

                is Request.Append -> {
                    service.iFavorites(
                        AdminAdListRequest(
                            limit = pageSize,
                            untilId = request.nextKey,
                        ),
                    )
                }
            } ?: return Result(
                endOfPaginationReached = true,
            )

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

        return Result(
            endOfPaginationReached = response.isEmpty(),
            data = data,
            nextKey = response.lastOrNull()?.noteId,
        )
    }
}
