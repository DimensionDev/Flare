package dev.dimension.flare.data.datasource.microblog

import SnowflakeIdGenerator
import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
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
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> =
        coroutineScope {
            if (request is PagingRequest.Prepend) {
                PagingResult(endOfPaginationReached = true)
            } else {
                if (request is PagingRequest.Refresh) {
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
                                }.getOrElse {
                                    PagingResult(endOfPaginationReached = true)
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
                        if (it.result.nextKey == null) {
                            null
                        } else {
                            it.mediator
                        }
                    }

                PagingResult(
                    endOfPaginationReached = currentMediators.isEmpty(),
                    data = mixedTimelineResult + timelineResult,
                    nextKey = if (currentMediators.isEmpty()) null else "mixed_next_key",
                    previousKey = null,
                )
            }
        }

    private suspend fun getSubRequest(
        request: PagingRequest,
        mediator: BaseTimelineRemoteMediator,
    ): SubRequest? =
        when (request) {
            is PagingRequest.Append -> {
                database
                    .pagingTimelineDao()
                    .getPagingKey(mediator.pagingKey)
                    ?.nextKey
                    ?.let(PagingRequest::Append)
            }

            is PagingRequest.Prepend ->
                database
                    .pagingTimelineDao()
                    .getPagingKey(mediator.pagingKey)
                    ?.prevKey
                    ?.let(PagingRequest::Prepend)

            is PagingRequest.Refresh -> PagingRequest.Refresh
        }?.let {
            SubRequest(mediator, it)
        }

    private suspend fun saveSubResponse(
        request: PagingRequest,
        subResponse: SubResponse,
    ) {
        val (mediator, result) = subResponse
        if (request is PagingRequest.Prepend && result.previousKey != null) {
            database.pagingTimelineDao().updatePagingKeyPrevKey(
                pagingKey = mediator.pagingKey,
                prevKey = result.previousKey,
            )
        } else if (request is PagingRequest.Append && result.nextKey != null) {
            database.pagingTimelineDao().updatePagingKeyNextKey(
                pagingKey = mediator.pagingKey,
                nextKey = result.nextKey,
            )
        } else if (request is PagingRequest.Refresh) {
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
        val request: PagingRequest,
    ) {
        suspend fun load(pageSize: Int) = mediator.timeline(pageSize, request)
    }

    private data class SubResponse(
        val mediator: BaseTimelineRemoteMediator,
        val result: PagingResult<DbPagingTimelineWithStatus>,
    )
}
