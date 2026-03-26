package dev.dimension.flare.data.datasource.microblog

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.ReportableRemoteLoader
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class MixedRemoteMediator(
    private val database: CacheDatabase,
    private val mediators: List<CacheableRemoteLoader<UiTimelineV2>>,
) : CacheableRemoteLoader<UiTimelineV2>,
    ReportableRemoteLoader {
    override val pagingKey =
        buildString {
            append("mixed_timeline")
            mediators.forEach { mediator ->
                append(mediator.pagingKey)
            }
        }
    private var currentMediators = mediators

    override var reportError: ((Throwable) -> Unit)? = null

    @OptIn(ExperimentalPagingApi::class)
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> =
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
                                    reportError?.invoke(it)
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
                            it.createdAt.value.toEpochMilliseconds()
                        }.distinctBy { item ->
                            // A mixed timeline can receive the same logical item from multiple
                            // sub timelines (for example home + list). Keep one copy so Compose
                            // never receives duplicate lazy keys for the same item.
                            item.itemKey
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
                    data = mixedTimelineResult,
                    nextKey = if (currentMediators.isEmpty()) null else "mixed_next_key",
                    previousKey = null,
                )
            }
        }

    private suspend fun getSubRequest(
        request: PagingRequest,
        mediator: CacheableRemoteLoader<UiTimelineV2>,
    ): SubRequest? =
        when (request) {
            is PagingRequest.Append -> {
                database
                    .pagingTimelineDao()
                    .getPagingKey(subKey(mediator))
                    ?.nextKey
                    ?.let(PagingRequest::Append)
            }

            is PagingRequest.Prepend ->
                database
                    .pagingTimelineDao()
                    .getPagingKey(subKey(mediator))
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
                pagingKey = subKey(mediator),
                prevKey = result.previousKey,
            )
        } else if (request is PagingRequest.Append && result.nextKey != null) {
            database.pagingTimelineDao().updatePagingKeyNextKey(
                pagingKey = subKey(mediator),
                nextKey = result.nextKey,
            )
        } else if (request is PagingRequest.Refresh) {
            database.pagingTimelineDao().deletePagingKey(subKey(mediator))
            database.pagingTimelineDao().insertPagingKey(
                dev.dimension.flare.data.database.cache.model.DbPagingKey(
                    pagingKey = subKey(mediator),
                    nextKey = result.nextKey,
                    prevKey = result.previousKey,
                ),
            )
        }
    }

    private fun subKey(mediator: CacheableRemoteLoader<UiTimelineV2>) = "mixed_${mediator.pagingKey}"

    private data class SubRequest(
        val mediator: CacheableRemoteLoader<UiTimelineV2>,
        val request: PagingRequest,
    ) {
        suspend fun load(pageSize: Int) = mediator.load(pageSize, request)
    }

    private data class SubResponse(
        val mediator: CacheableRemoteLoader<UiTimelineV2>,
        val result: PagingResult<UiTimelineV2>,
    )
}
