package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.NotesSearchRequest
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class SearchStatusRemoteMediator(
    private val service: MisskeyService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String =
        buildString {
            append("search_")
            append(query)
            append(accountKey.toString())
        }

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                Request.Refresh -> {
                    service
                        .notesSearch(
                            NotesSearchRequest(
                                query = query,
                                limit = pageSize,
                            ),
                        )
                }

                is Request.Append -> {
                    service.notesSearch(
                        NotesSearchRequest(
                            query = query,
                            limit = pageSize,
                            untilId = request.nextKey,
                        ),
                    )
                }
            } ?: emptyList()

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
