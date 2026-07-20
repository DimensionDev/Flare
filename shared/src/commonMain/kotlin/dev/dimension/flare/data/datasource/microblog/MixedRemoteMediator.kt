package dev.dimension.flare.data.datasource.microblog

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.ReportableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.SortIdProvider
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class MixedRemoteMediator(
    private val database: CacheDatabase,
    private val mediators: List<CacheableRemoteLoader<UiTimelineV2>>,
    private val mergePolicy: TimelineMergePolicy = TimelineMergePolicy.TimePerPage,
) : CacheableRemoteLoader<UiTimelineV2>,
    ReportableRemoteLoader,
    SortIdProvider {
    override val pagingKey =
        buildString {
            append("mixed_timeline")
            mediators.forEach { mediator ->
                append(mediator.pagingKey)
            }
        }
    private var currentMediators = mediators
    private var timeSourceStates = mediators.map(::TimeSourceState)
    private val emittedTimeItemIds = mutableSetOf<String>()
    private val timeItemComparator =
        Comparator<UiTimelineV2> { first, second ->
            val timeComparison =
                second.createdAt.value
                    .toEpochMilliseconds()
                    .compareTo(first.createdAt.value.toEpochMilliseconds())
            if (timeComparison != 0) {
                timeComparison
            } else {
                itemIdentity(first).compareTo(itemIdentity(second))
            }
        }

    override var reportError: ((Throwable) -> Unit)? = null

    @OptIn(ExperimentalPagingApi::class)
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> =
        coroutineScope {
            if (request is PagingRequest.Prepend) {
                PagingResult(endOfPaginationReached = true)
            } else if (mergePolicy == TimelineMergePolicy.Time) {
                loadTimeOrdered(pageSize, request)
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

                val mixedTimelineResult = merge(response)

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
                    nextKey = if (currentMediators.isEmpty()) null else MIXED_NEXT_KEY,
                    previousKey = null,
                )
            }
        }

    private suspend fun loadTimeOrdered(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> =
        coroutineScope {
            if (request is PagingRequest.Refresh) {
                timeSourceStates = mediators.map(::TimeSourceState)
                emittedTimeItemIds.clear()
            }

            val loadedStates = mutableListOf<TimeSourceState>()
            val data = mutableListOf<UiTimelineV2>()
            while (data.size < pageSize) {
                val blockedStates =
                    timeSourceStates.filter {
                        it.pending.isEmpty() && it.canLoad
                    }
                if (blockedStates.isNotEmpty()) {
                    val statesToLoad =
                        blockedStates.filter { state ->
                            loadedStates.none { it === state }
                        }
                    if (statesToLoad.isEmpty()) {
                        // ponytail: Let Paging append again instead of chasing sparse or empty remote pages in one load.
                        break
                    }

                    val responses =
                        statesToLoad
                            .map { state ->
                                val subRequest = state.nextRequest()
                                async {
                                    val result =
                                        runCatching {
                                            state.mediator.load(pageSize, subRequest)
                                        }.getOrElse {
                                            reportError?.invoke(it)
                                            PagingResult(endOfPaginationReached = true)
                                        }
                                    TimeSubResponse(state, result)
                                }
                            }.awaitAll()

                    database.connect {
                        responses.forEach { response ->
                            database.pagingTimelineDao().insertPagingKey(
                                dev.dimension.flare.data.database.cache.model.DbPagingKey(
                                    pagingKey = subKey(response.state.mediator),
                                    nextKey = response.result.nextKey,
                                    prevKey = response.result.previousKey,
                                ),
                            )
                        }
                    }
                    responses.forEach { response ->
                        response.state.initialized = true
                        response.state.nextKey = response.result.nextKey
                        response.state.pending.addAll(response.result.data.sortedWith(timeItemComparator))
                    }
                    loadedStates += statesToLoad
                    continue
                }

                val source =
                    timeSourceStates
                        .filter { it.pending.isNotEmpty() }
                        .minWithOrNull(
                            Comparator { first, second ->
                                timeItemComparator.compare(first.pending.first(), second.pending.first())
                            },
                        ) ?: break
                val item = source.pending.removeFirst()
                if (emittedTimeItemIds.add(itemIdentity(item))) {
                    data += item
                }
            }

            val hasMore =
                timeSourceStates.any {
                    it.pending.isNotEmpty() || it.canLoad
                }
            PagingResult(
                data = data,
                nextKey = if (hasMore) MIXED_NEXT_KEY else null,
                previousKey = null,
            )
        }

    override suspend fun sortId(data: UiTimelineV2): Long? =
        if (mergePolicy == TimelineMergePolicy.Time) {
            val createdAt = data.createdAt.value.toEpochMilliseconds()
            val tieBreaker =
                (itemIdentity(data).hashCode().toLong() - Int.MIN_VALUE.toLong()) % SORT_ID_TIE_BUCKET
            -createdAt * SORT_ID_TIE_BUCKET + tieBreaker
        } else {
            null
        }

    private fun merge(response: List<SubResponse>): List<UiTimelineV2> =
        when (mergePolicy) {
            TimelineMergePolicy.Time,
            TimelineMergePolicy.TimePerPage,
            -> {
                response
                    .flatMap { it.result.data }
                    .sortedByDescending {
                        it.createdAt.value.toEpochMilliseconds()
                    }
            }

            TimelineMergePolicy.Staggered -> {
                response
                    .map { it.result.data }
                    .interleave()
            }
        }.distinctBy(::itemIdentity)

    private fun List<List<UiTimelineV2>>.interleave(): List<UiTimelineV2> {
        val maxSize = maxOfOrNull { it.size } ?: return emptyList()
        return buildList {
            repeat(maxSize) { index ->
                this@interleave.forEach { items ->
                    items.getOrNull(index)?.let(::add)
                }
            }
        }
    }

    private fun itemIdentity(item: UiTimelineV2): String = item.itemKey ?: "${item.accountType}_${item.statusKey}"

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

            is PagingRequest.Prepend -> {
                database
                    .pagingTimelineDao()
                    .getPagingKey(subKey(mediator))
                    ?.prevKey
                    ?.let(PagingRequest::Prepend)
            }

            is PagingRequest.Refresh -> {
                PagingRequest.Refresh
            }
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

    private class TimeSourceState(
        val mediator: CacheableRemoteLoader<UiTimelineV2>,
    ) {
        val pending = ArrayDeque<UiTimelineV2>()
        var initialized = false
        var nextKey: String? = null

        val canLoad: Boolean
            get() = !initialized || nextKey != null

        fun nextRequest(): PagingRequest =
            if (initialized) {
                PagingRequest.Append(checkNotNull(nextKey))
            } else {
                PagingRequest.Refresh
            }
    }

    private data class TimeSubResponse(
        val state: TimeSourceState,
        val result: PagingResult<UiTimelineV2>,
    )

    private companion object {
        private const val MIXED_NEXT_KEY = "mixed_next_key"
        private const val SORT_ID_TIE_BUCKET = 10_000L
    }
}
