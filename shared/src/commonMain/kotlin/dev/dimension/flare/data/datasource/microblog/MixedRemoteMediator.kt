package dev.dimension.flare.data.datasource.microblog

import SnowflakeIdGenerator
import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.uuid.Uuid

internal class MixedRemoteMediator(
    database: CacheDatabase,
    private val mediators: List<BaseTimelineRemoteMediator>,
) : BaseTimelineRemoteMediator(database = database) {
    override val pagingKey =
        buildString {
            append("mixed_timeline")
            mediators.forEach { mediator ->
                append(mediator.pagingKey)
            }
        }
    private var currentMediators = mediators

    @OptIn(ExperimentalPagingApi::class)
    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result =
        coroutineScope {
            if (request is Request.Prepend) {
                Result(endOfPaginationReached = true)
            } else {
                if (request is Request.Refresh) {
                    currentMediators = mediators
                }
                val response =
                    currentMediators
                        .map {
                            async {
                                it to
                                    runCatching { it.timeline(pageSize, request) }
                                        .onFailure { it.printStackTrace() }
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
                                        sortId = -SnowflakeIdGenerator.nextId(),
                                        _id = Uuid.random().toString(),
                                    ),
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

                Result(
                    endOfPaginationReached = currentMediators.isEmpty(),
                    data = mixedTimelineResult + timelineResult,
                )
            }
        }
}
