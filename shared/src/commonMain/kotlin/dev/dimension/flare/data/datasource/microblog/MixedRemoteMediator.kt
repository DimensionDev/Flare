package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.ReportableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.SortIdProvider
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class MixedRemoteMediator(
    private val database: CacheDatabase,
    private val mediators: List<CacheableRemoteLoader<UiTimelineV2>>,
    private val mergePolicy: TimelineMergePolicy = TimelineMergePolicy.TimePerPage,
    private val sessionKey: String? = null,
    private val isRequestValid: suspend () -> Boolean = { true },
    private val sourceKeys: List<String> = mediators.map { it.pagingKey },
) : CacheableRemoteLoader<UiTimelineV2>,
    ReportableRemoteLoader,
    SortIdProvider {
    override val pagingKey =
        sessionKey ?: buildString {
            append("mixed_timeline")
            mediators.forEach { mediator ->
                append(mediator.pagingKey)
            }
        }
    private val timeSources =
        mediators.mapIndexed { index, mediator ->
            TimeSource(mediator, if (sessionKey != null) "${sourcePrefix(index)}staging" else "$pagingKey$TIME_STAGING_SUFFIX$index")
        }

    override var reportError: ((Throwable) -> Unit)? = null

    internal val ordersByTime: Boolean get() = mergePolicy == TimelineMergePolicy.Time

    internal fun forReadingSession(
        key: String,
        isValid: suspend () -> Boolean,
    ): MixedRemoteMediator =
        MixedRemoteMediator(
            database,
            mediators.mapIndexed { index, loader ->
                if (loader is MixedRemoteMediator) {
                    loader.forReadingSession(
                        "$key/source/${sourceKeys[index].length}:${sourceKeys[index]}/",
                        isValid,
                    )
                } else {
                    loader
                }
            },
            mergePolicy,
            key,
            isValid,
            sourceKeys,
        )

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val pendingWrites = mutableListOf<suspend () -> Unit>()
        val result = load(pageSize, request, pendingWrites)
        // Child cursors, staging and the outer page must commit together in both modes.
        return result.copy(saveInTransaction = {
            checkRequestIsValid()
            pendingWrites.forEach { it() }
            result.saveInTransaction?.invoke()
        })
    }

    private suspend fun load(
        pageSize: Int,
        request: PagingRequest,
        pendingWrites: MutableList<suspend () -> Unit>,
    ): PagingResult<UiTimelineV2> =
        coroutineScope {
            if (request is PagingRequest.Prepend) {
                PagingResult(endOfPaginationReached = true)
            } else if (mergePolicy == TimelineMergePolicy.Time) {
                loadTimeOrdered(pageSize, request, pendingWrites)
            } else {
                val subRequests = mediators.mapNotNull { getSubRequest(request, it) }
                val results =
                    subRequests
                        .map { subRequest ->
                            async {
                                loadSource(subRequest.mediator, pageSize, subRequest.request).map {
                                    SubResponse(subRequest, it)
                                }
                            }
                        }.awaitAll()

                val response = results.mapNotNull { it.getOrNull() }
                if (response.isEmpty()) results.firstOrNull()?.getOrThrow()
                results.mapNotNull { it.exceptionOrNull() }.forEach { reportError?.invoke(it) }
                val mixedTimelineResult = merge(response)

                pendingWrites.add {
                    if (request is PagingRequest.Refresh) {
                        mediators.forEach { database.pagingTimelineDao().deletePagingKey(subKey(it)) }
                    }
                    response.forEach {
                        it.result.saveInTransaction?.invoke()
                        saveSubResponse(it)
                    }
                    results.forEachIndexed { index, result ->
                        if (result.isFailure) {
                            database.pagingTimelineDao().insertPagingKey(DbPagingKey(subKey(subRequests[index].mediator)))
                        }
                    }
                }

                val hasMore = response.any { it.result.nextKey != null }

                PagingResult(
                    data = mixedTimelineResult,
                    nextKey = if (hasMore) MIXED_NEXT_KEY else null,
                    previousKey = null,
                )
            }
        }

    private suspend fun loadTimeOrdered(
        pageSize: Int,
        request: PagingRequest,
        pendingWrites: MutableList<suspend () -> Unit>,
    ): PagingResult<UiTimelineV2> =
        coroutineScope {
            if (request is PagingRequest.Refresh) {
                resetTimeStaging(pendingWrites)
            }

            val seenStatusIds = mutableSetOf<String>()

            suspend fun markCommitted(items: List<DbPagingTimelineWithStatus>) {
                if (request is PagingRequest.Append) {
                    // Check only candidates, including collapsed parents, rather than the retained history.
                    items.map { it.timeline.statusId }.distinct().chunked(500).forEach {
                        seenStatusIds += database.pagingTimelineDao().existingReadingStatusIds(pagingKey, it)
                    }
                }
            }

            val timeSourceStates =
                if (request is PagingRequest.Refresh) {
                    timeSources.map { TimeSourceState(it, ArrayDeque(), initialized = false, nextKey = null) }
                } else {
                    loadTimeSourceStates()
                }
            markCommitted(timeSourceStates.flatMap { it.pending })
            val loadedStates = mutableListOf<TimeSourceState>()
            val failedStates = mutableSetOf<TimeSourceState>()
            val failures = mutableListOf<Throwable>()
            val selected = mutableListOf<DbPagingTimelineWithStatus>()
            val consumed = mutableListOf<DbPagingTimelineWithStatus>()
            while (selected.size < pageSize) {
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

                    val results =
                        statesToLoad
                            .map { state ->
                                val subRequest = state.nextRequest()
                                async {
                                    loadSource(state.source.mediator, pageSize, subRequest).map { result ->
                                        val stagedItems =
                                            TimelinePagingMapper.toDb(
                                                data = result.data,
                                                pagingKey = state.source.stagingKey,
                                                sortIds = result.data.map(::timeSortId),
                                            )
                                        TimeSubResponse(state, result, stagedItems)
                                    }
                                }
                            }.awaitAll()

                    val responses = results.mapNotNull { it.getOrNull() }
                    markCommitted(responses.flatMap { it.stagedItems })
                    results.forEachIndexed { index, result ->
                        result.exceptionOrNull()?.let {
                            val state = statesToLoad[index]
                            state.initialized = true
                            state.nextKey = null
                            failedStates += state
                            failures += it
                        }
                    }
                    pendingWrites.add {
                        responses.forEach { it.result.saveInTransaction?.invoke() }
                        responses
                            .flatMap { it.stagedItems }
                            .takeIf { it.isNotEmpty() }
                            ?.let { saveToDatabase(database, it) }
                        responses.forEach { response ->
                            database.pagingTimelineDao().insertPagingKey(
                                DbPagingKey(
                                    pagingKey = response.state.source.stagingKey,
                                    nextKey = response.result.nextKey,
                                    prevKey = response.result.previousKey,
                                ),
                            )
                        }
                    }
                    responses.forEach { response ->
                        response.state.initialized = true
                        response.state.nextKey = response.result.nextKey
                        response.state.pending.addAll(response.stagedItems.sortedBy { it.timeline.sortId })
                    }
                    loadedStates += statesToLoad
                    continue
                }

                val source =
                    timeSourceStates
                        .filter { it.pending.isNotEmpty() }
                        .minByOrNull {
                            it.pending
                                .first()
                                .timeline
                                .sortId
                        } ?: break
                val item = source.pending.removeFirst()
                consumed += item
                if (seenStatusIds.add(item.timeline.statusId)) {
                    selected += item
                }
            }

            if (selected.isEmpty() && failures.isNotEmpty() && loadedStates.size == failedStates.size) {
                throw failures.first()
            }
            pendingWrites.add {
                failedStates.forEach { database.pagingTimelineDao().insertPagingKey(DbPagingKey(it.source.stagingKey)) }
            }
            failures.forEach { reportError?.invoke(it) }
            val hasMore =
                timeSourceStates.any {
                    it.pending.any { item -> item.timeline.statusId !in seenStatusIds } || it.canLoad
                }
            val completed = consumed + timeSourceStates.flatMap { it.pending.filter { item -> item.timeline.statusId in seenStatusIds } }
            PagingResult(
                data = selected.map(::timeStagingToUi),
                nextKey = if (hasMore) MIXED_NEXT_KEY else null,
                previousKey = null,
                saveInTransaction = {
                    // Nested sources have no outer timeline rows of their own to mark these as seen.
                    completed.map { it.timeline }.groupBy { it.pagingKey }.forEach { (key, rows) ->
                        database.pagingTimelineDao().deletePresentationReferences(key, rows.map { it._id })
                        database.pagingTimelineDao().delete(rows)
                    }
                },
            )
        }

    private suspend fun loadTimeSourceStates(): List<TimeSourceState> =
        timeSources.map { source ->
            val pagingKey = database.pagingTimelineDao().getPagingKey(source.stagingKey)
            TimeSourceState(
                source = source,
                pending =
                    ArrayDeque(
                        database.pagingTimelineDao().getTimelinePage(
                            pagingKey = source.stagingKey,
                            offset = 0,
                            limit = Int.MAX_VALUE,
                        ),
                    ),
                initialized = pagingKey != null,
                nextKey = pagingKey?.nextKey,
            )
        }

    private fun resetTimeStaging(pendingWrites: MutableList<suspend () -> Unit>) {
        pendingWrites.add {
            timeSources.forEach { source ->
                database.pagingTimelineDao().deletePresentationReferences(source.stagingKey)
                database.pagingTimelineDao().delete(source.stagingKey)
                database.pagingTimelineDao().deletePagingKey(source.stagingKey)
                database.pagingTimelineDao().deletePagingKey(subKey(source.mediator))
            }
        }
    }

    private fun timeStagingToUi(item: DbPagingTimelineWithStatus): UiTimelineV2 =
        TimelinePagingMapper.toUi(
            item = item,
            pagingKey = item.timeline.pagingKey,
            translationDisplayOptions = TIME_STAGING_TRANSLATION_OPTIONS,
        )

    override suspend fun sortId(data: UiTimelineV2): Long? =
        if (ordersByTime) {
            timeSortId(data)
        } else {
            null
        }

    private fun timeSortId(data: UiTimelineV2): Long {
        val createdAt = data.createdAt.value.toEpochMilliseconds()
        val tieBreaker =
            (itemIdentity(data).hashCode().toLong() - Int.MIN_VALUE.toLong()) % SORT_ID_TIE_BUCKET
        return -createdAt * SORT_ID_TIE_BUCKET + tieBreaker
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
                val key = database.pagingTimelineDao().getPagingKey(subKey(mediator))
                if (key == null) PagingRequest.Refresh else key.nextKey?.let(PagingRequest::Append)
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

    private suspend fun saveSubResponse(subResponse: SubResponse) {
        val (subRequest, result) = subResponse
        val (mediator, request) = subRequest
        if (request is PagingRequest.Prepend && result.previousKey != null) {
            database.pagingTimelineDao().updatePagingKeyPrevKey(
                pagingKey = subKey(mediator),
                prevKey = result.previousKey,
            )
        } else if (request is PagingRequest.Append) {
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

    private fun subKey(mediator: CacheableRemoteLoader<UiTimelineV2>) =
        if (sessionKey != null) "${sourcePrefix(mediators.indexOf(mediator))}cursor" else "mixed_${mediator.pagingKey}"

    private fun sourcePrefix(index: Int) = "$sessionKey/source/${sourceKeys[index].length}:${sourceKeys[index]}/"

    private suspend fun checkRequestIsValid() {
        if (!isRequestValid()) throw CancellationException("Reading session was removed")
    }

    private suspend fun loadSource(
        source: CacheableRemoteLoader<UiTimelineV2>,
        pageSize: Int,
        request: PagingRequest,
    ): Result<PagingResult<UiTimelineV2>> {
        if (source is ReportableRemoteLoader) source.reportError = reportError
        return runCatching { source.load(pageSize, request) }.onFailure {
            if (it is CancellationException) throw it
        }
    }

    private data class SubRequest(
        val mediator: CacheableRemoteLoader<UiTimelineV2>,
        val request: PagingRequest,
    )

    private data class SubResponse(
        val subRequest: SubRequest,
        val result: PagingResult<UiTimelineV2>,
    )

    private data class TimeSource(
        val mediator: CacheableRemoteLoader<UiTimelineV2>,
        val stagingKey: String,
    )

    private class TimeSourceState(
        val source: TimeSource,
        val pending: ArrayDeque<DbPagingTimelineWithStatus>,
        var initialized: Boolean,
        var nextKey: String?,
    ) {
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
        val stagedItems: List<DbPagingTimelineWithStatus>,
    )

    private companion object {
        private const val MIXED_NEXT_KEY = "mixed_next_key"
        private const val SORT_ID_TIE_BUCKET = 10_000L
        private const val TIME_STAGING_SUFFIX = ":time_staging:"
        private val TIME_STAGING_TRANSLATION_OPTIONS =
            TranslationDisplayOptions(
                translationEnabled = false,
                autoDisplayEnabled = false,
                providerCacheKey = "",
            )
    }
}
