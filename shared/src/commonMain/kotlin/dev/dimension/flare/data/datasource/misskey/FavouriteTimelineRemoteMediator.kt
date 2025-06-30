package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.AdminAdListRequest
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.datetime.Instant

@OptIn(ExperimentalPagingApi::class)
internal class FavouriteTimelineRemoteMediator(
    private val accountKey: MicroBlogKey,
    private val service: MisskeyService,
    private val database: CacheDatabase,
) : BaseTimelineRemoteMediator(
        database = database,
        accountType = AccountType.Specific(accountKey),
    ) {
    override val pagingKey: String
        get() =
            buildString {
                append("favourite_")
                append(accountKey.toString())
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
                    service.iFavorites(
                        AdminAdListRequest(
                            limit = state.config.pageSize,
                        ),
                    )
                }

                LoadType.APPEND -> {
                    val lastItem =
                        database.pagingTimelineDao().getLastPagingTimeline(pagingKey)
                            ?: return Result(
                                endOfPaginationReached = true,
                            )
                    service.iFavorites(
                        AdminAdListRequest(
                            limit = state.config.pageSize,
                            untilId = lastItem.timeline.statusKey.id,
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
        )
    }
}
