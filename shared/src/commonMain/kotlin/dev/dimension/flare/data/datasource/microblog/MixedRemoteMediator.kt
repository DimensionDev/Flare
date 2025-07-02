package dev.dimension.flare.data.datasource.microblog

import SnowflakeIdGenerator
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.uuid.Uuid

internal class MixedRemoteMediator(
    database: CacheDatabase,
    mediators: List<BaseTimelineRemoteMediator>,
) : BaseTimelineRemoteMediator(database = database) {
    override val pagingKey = "mixed_timeline"
    private var currentMediators = mediators

    @OptIn(ExperimentalPagingApi::class)
    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result =
        coroutineScope {
            if (loadType == LoadType.PREPEND) {
                Result(endOfPaginationReached = true)
            } else {
                val response =
                    currentMediators
                        .map {
                            async {
                                it to
                                    runCatching { it.timeline(loadType, state) }
                                        .getOrElse {
                                            // TODO: Handle errors for each mediator
                                            Result(
                                                endOfPaginationReached = true,
                                            )
                                        }
                            }
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
                                        _id = Uuid.random().toString(),
                                    ),
                            )
                        }

//                database.connect {
//                    if (loadType == LoadType.REFRESH) {
//                        currentMediators.forEach {
//                            database.pagingTimelineDao().delete(pagingKey = it.pagingKey)
//                        }
//                        database.pagingTimelineDao().delete(pagingKey = pagingKey)
//                    }
//
//                    saveToDatabase(
//                        database = database,
//                        items = timelineResult,
//                    )
//                    saveToDatabase(
//                        database = database,
//                        items = mixedTimelineResult,
//                    )
//                }

                currentMediators =
                    response.mapNotNull {
                        if (it.second.endOfPaginationReached) {
                            null
                        } else {
                            it.first
                        }
                    }

                Result(
                    endOfPaginationReached = currentMediators.isEmpty(),
                    data = mixedTimelineResult + timelineResult,
                )
            }
        }
}
