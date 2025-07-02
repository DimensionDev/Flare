package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.AntennasNotesRequest
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class AntennasTimelineRemoteMediator(
    private val service: MisskeyService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val id: String,
) : BaseTimelineRemoteMediator(
        database = database,
        accountType = AccountType.Specific(accountKey),
    ) {
    override val pagingKey = "antennas_${id}_$accountKey"

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val response =
            when (loadType) {
                LoadType.REFRESH -> {
                    service
                        .antennasNotes(
                            AntennasNotesRequest(
                                antennaId = id,
                                limit = state.config.pageSize,
                            ),
                        )
                }

                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.APPEND -> {
                    val lastItem =
                        database.pagingTimelineDao().getLastPagingTimeline(pagingKey)
                            ?: return Result(
                                endOfPaginationReached = true,
                            )
                    service
                        .antennasNotes(
                            AntennasNotesRequest(
                                antennaId = id,
                                limit = state.config.pageSize,
                                untilId = lastItem.timeline.statusKey.id,
                            ),
                        )
                }
            }

        return Result(
            endOfPaginationReached = response.isEmpty(),
            data =
                response.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
        )
    }
}
