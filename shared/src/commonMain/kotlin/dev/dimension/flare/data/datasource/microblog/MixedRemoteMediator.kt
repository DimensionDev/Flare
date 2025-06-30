package dev.dimension.flare.data.datasource.microblog

import SnowflakeIdGenerator
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class MixedRemoteMediator(
    private val database: CacheDatabase,
    private val mediators: List<BaseTimelineRemoteMediator>,
) : BaseRemoteMediator<Int, DbPagingTimelineWithStatus>() {
    val pagingKey = "mixed_timeline"
    private var currentMediators = mediators

    @OptIn(ExperimentalPagingApi::class)
    override suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult =
        coroutineScope {
            if (loadType == LoadType.PREPEND) {
                MediatorResult.Success(endOfPaginationReached = true)
            } else {
                val response =
                    currentMediators
                        .map {
                            async { it to it.timeline(loadType, state) }
                        }.awaitAll()

                val timelineResult = response.flatMap { it.second.data }

                val mixedTimelineResult =
                    timelineResult
                        .sortedByDescending {
                            it.status.status.data.createdAt
                                .toEpochMilliseconds()
                        }.map {
                            it.copy(
                                timeline =
                                    it.timeline.copy(
                                        pagingKey = pagingKey,
                                        sortId = SnowflakeIdGenerator.nextId(),
                                    ),
                            )
                        }

                database.connect {
                    if (loadType == LoadType.REFRESH) {
                        currentMediators.forEach {
                            database.pagingTimelineDao().delete(pagingKey = it.pagingKey, accountType = it.accountType)
                            database.pagingTimelineDao().delete(pagingKey = pagingKey, accountType = it.accountType)
                        }
                    }

                    saveToDatabase(
                        database = database,
                        items = timelineResult,
                    )
                    saveToDatabase(
                        database = database,
                        items = mixedTimelineResult,
                    )
                }

                currentMediators =
                    response.mapNotNull {
                        if (it.second.endOfPaginationReached) {
                            null
                        } else {
                            it.first
                        }
                    }

                MediatorResult.Success(
                    endOfPaginationReached = currentMediators.isEmpty(),
                )
            }
        }
}
