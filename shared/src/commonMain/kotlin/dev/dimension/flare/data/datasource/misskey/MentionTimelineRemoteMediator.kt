package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.NotesMentionsRequest
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class MentionTimelineRemoteMediator(
    private val accountKey: MicroBlogKey,
    private val service: MisskeyService,
    database: CacheDatabase,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "mention_$accountKey"

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
                    service.notesMentions(
                        NotesMentionsRequest(
                            limit = pageSize,
                        ),
                    )
                }

                is Request.Append -> {
                    service.notesMentions(
                        NotesMentionsRequest(
                            limit = pageSize,
                            untilId = request.nextKey,
                        ),
                    )
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
                ),
            nextKey = response.lastOrNull()?.id,
        )
    }
}
