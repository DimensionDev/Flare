package dev.dimension.flare.data.datasource.microblog

import SnowflakeIdGenerator
import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.uuid.Uuid

internal class MixedRemoteMediator(
    private val database: CacheDatabase,
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
                        .mapNotNull {
                            getSubRequest(request, it)
                        }.map { subRequest ->
                            async {
                                runCatching {
                                    subRequest.load(pageSize)
                                }.onFailure {
                                    it.printStackTrace()
                                }.getOrElse {
                                    Result(endOfPaginationReached = true)
                                }.let {
                                    SubResponse(subRequest.mediator, it)
                                }
                            }
                        }.awaitAll()

                val timelineResult = response.flatMap { it.result.data }

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

                database.connect {
                    response.forEach {
                        saveSubResponse(request, it)
                    }
                }

                currentMediators =
                    response.mapNotNull {
                        if (it.result.endOfPaginationReached) {
                            null
                        } else {
                            it.mediator
                        }
                    }

                Result(
                    endOfPaginationReached = currentMediators.isEmpty(),
                    data = mixedTimelineResult + timelineResult,
                    nextKey = if (currentMediators.isEmpty()) null else "mixed_next_key",
                    previousKey = null,
                )
            }
        }

    private suspend fun getSubRequest(
        request: Request,
        mediator: BaseTimelineRemoteMediator,
    ): SubRequest? =
        when (request) {
            is Request.Append -> {
                database
                    .pagingTimelineDao()
                    .getPagingKey(mediator.pagingKey)
                    ?.nextKey
                    ?.let(Request::Append)
            }

            is Request.Prepend ->
                database
                    .pagingTimelineDao()
                    .getPagingKey(mediator.pagingKey)
                    ?.prevKey
                    ?.let(Request::Prepend)

            is Request.Refresh -> Request.Refresh
        }?.let {
            SubRequest(mediator, it)
        }

    private suspend fun saveSubResponse(
        request: Request,
        subResponse: SubResponse,
    ) {
        val (mediator, result) = subResponse
        if (request is Request.Prepend && result.previousKey != null) {
            database.pagingTimelineDao().updatePagingKeyPrevKey(
                pagingKey = mediator.pagingKey,
                prevKey = result.previousKey,
            )
        } else if (request is Request.Append && result.nextKey != null) {
            database.pagingTimelineDao().updatePagingKeyNextKey(
                pagingKey = mediator.pagingKey,
                nextKey = result.nextKey,
            )
        } else if (request is Request.Refresh) {
            database.pagingTimelineDao().deletePagingKey(mediator.pagingKey)
            database.pagingTimelineDao().insertPagingKey(
                dev.dimension.flare.data.database.cache.model.DbPagingKey(
                    pagingKey = mediator.pagingKey,
                    nextKey = result.nextKey,
                    prevKey = result.previousKey,
                ),
            )
        }
    }

    private data class SubRequest(
        val mediator: BaseTimelineRemoteMediator,
        val request: Request,
    ) {
        suspend fun load(pageSize: Int) = mediator.timeline(pageSize, request)
    }

    private data class SubResponse(
        val mediator: BaseTimelineRemoteMediator,
        val result: Result,
    )
}
