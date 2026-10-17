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
import dev.dimension.flare.data.datasource.microblog.paging.TimelineOrder
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class MixedRemoteMediator(
    private val database: CacheDatabase,
    private val mediators: List<CacheableRemoteLoader<UiTimelineV2>>,
    private val mergePolicy: TimelineMergePolicy = TimelineMergePolicy.TimePerPage,
    private val sessionKey: String? = null,
    private val isRequestValid: suspend () -> Boolean = { true },
    private val sourceKeys: List<String> = mediators.map { it.pagingKey },
    private val readingHeadKey: String? = null,
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
        readingHeadKey: String? = null,
        isValid: suspend () -> Boolean,
    ): MixedRemoteMediator =
        MixedRemoteMediator(
            database,
            mediators.mapIndexed { index, loader ->
                if (loader is MixedRemoteMediator) {
                    loader.forReadingSession(
                        "$key/source/${sourceKeys[index].length}:${sourceKeys[index]}/",
                        readingHeadKey?.let { "$it/source/${sourceKeys[index].length}:${sourceKeys[index]}/" },
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
            readingHeadKey,
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
                        if (result.isFailure && readingHeadKey == null) {
                            database.pagingTimelineDao().insertPagingKey(DbPagingKey(subKey(subRequests[index].mediator)))
                        }
                    }
                }

                val hasMore = response.any { it.result.nextKey != null } || (readingHeadKey != null && results.any { it.isFailure })

                PagingResult(
                    data = mixedTimelineResult,
                    continuationError =
                        if (readingHeadKey != null) {
                            results.firstNotNullOfOrNull { it.exceptionOrNull() as? Exception }
                                ?: response.firstNotNullOfOrNull { it.result.continuationError }
                        } else {
                            null
                        },
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
                    timeSources.map { source ->
                        val previous =
                            readingHeadKey
                                ?.let { head ->
                                    database
                                        .pagingTimelineDao()
                                        .getPagingKey(
                                            head + source.stagingKey.removePrefix(pagingKey),
                                        )?.readingState
                                }?.let { Json.decodeFromString<ReadingSourceState>(it) }
                        TimeSourceState(
                            source,
                            ArrayDeque(),
                            initialized = false,
                            nextKey = null,
                            reading = if (readingHeadKey != null) ReadingSourceState(boundaryIds = previous?.headIds.orEmpty()) else null,
                        )
                    }
                } else {
                    loadTimeSourceStates()
                }
            markCommitted(timeSourceStates.flatMap { it.pending })
            val loadedStates = mutableListOf<TimeSourceState>()
            val failedStates = mutableSetOf<TimeSourceState>()
            val failures = mutableListOf<Throwable>()
            var childFailure: Exception? = null
            var learnedHead = false
            val selected = mutableListOf<DbPagingTimelineWithStatus>()
            val consumed = mutableListOf<DbPagingTimelineWithStatus>()
            while (selected.size < pageSize) {
                val blockedStates =
                    timeSourceStates.filter {
                        it.pending.isEmpty() && it.canLoad && it !in failedStates
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
                                        val overlap =
                                            if (state.source.mediator.timelineOrder == TimelineOrder.Continuous) {
                                                result.data.indexOfFirst { itemIdentity(it) in state.reading?.boundaryIds.orEmpty() }
                                            } else {
                                                -1
                                            }
                                        // The previous source continuation owns the overlapping suffix, including its buffered rows.
                                        val missing = if (overlap >= 0) result.data.take(overlap) else result.data
                                        val stagedItems =
                                            TimelinePagingMapper.toDb(
                                                data = missing,
                                                pagingKey = state.source.stagingKey,
                                                sortIds = missing.map(::timeSortId),
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
                            if (state.reading == null) {
                                state.initialized = true
                                state.nextKey = null
                            } else {
                                state.reading = state.reading?.copy(refreshPending = !state.initialized)
                            }
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
                                    readingState = response.state.reading?.let { Json.encodeToString(it) },
                                ),
                            )
                        }
                    }
                    responses.forEach { response ->
                        childFailure = childFailure ?: response.result.continuationError
                        val state = response.state
                        state.reading =
                            state.reading?.let { reading ->
                                val ids = response.result.data.map(::itemIdentity)
                                learnedHead = learnedHead || (reading.headIds.isEmpty() && ids.isNotEmpty())
                                reading.copy(
                                    headIds = reading.headIds.ifEmpty { ids.take(pageSize) },
                                    refreshPending = false,
                                    joined =
                                        reading.joined ||
                                            (
                                                state.source.mediator.timelineOrder == TimelineOrder.Continuous &&
                                                    ids.any { it in reading.boundaryIds }
                                            ),
                                )
                            }
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
                failedStates.forEach {
                    database.pagingTimelineDao().insertPagingKey(
                        DbPagingKey(it.source.stagingKey, nextKey = it.nextKey, readingState = it.reading?.let { Json.encodeToString(it) }),
                    )
                }
            }
            if (request is PagingRequest.Append && learnedHead && readingHeadKey != null) {
                pendingWrites.add { publishReadingHeads(readingHeadKey) }
            }
            failures.forEach { reportError?.invoke(it) }
            val hasMore =
                timeSourceStates.any {
                    it.pending.any { item -> item.timeline.statusId !in seenStatusIds } || it.canLoad
                }
            val completed = consumed + timeSourceStates.flatMap { it.pending.filter { item -> item.timeline.statusId in seenStatusIds } }
            PagingResult(
                data = selected.map(::timeStagingToUi),
                continuationError =
                    if (readingHeadKey !=
                        null
                    ) {
                        failures.filterIsInstance<Exception>().firstOrNull() ?: childFailure
                    } else {
                        null
                    },
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
            val reading =
                pagingKey?.readingState?.let { Json.decodeFromString<ReadingSourceState>(it) }
                    ?: readingHeadKey?.let { ReadingSourceState() }
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
                initialized = pagingKey != null && reading?.refreshPending != true,
                nextKey = pagingKey?.nextKey,
                reading = reading,
            )
        }

    internal suspend fun publishReadingHeads(targetKey: String) {
        if (ordersByTime) {
            val dao = database.pagingTimelineDao()
            for (source in timeSources) {
                val incoming =
                    dao
                        .getPagingKey(source.stagingKey)
                        ?.readingState
                        ?.let { Json.decodeFromString<ReadingSourceState>(it) } ?: continue
                if (incoming.headIds.isEmpty()) continue
                val key = targetKey + source.stagingKey.removePrefix(pagingKey)
                val previous = dao.getPagingKey(key) ?: DbPagingKey(key)
                val reading = previous.readingState?.let { Json.decodeFromString<ReadingSourceState>(it) } ?: ReadingSourceState()
                // A late recovery must not replace the head published by a newer refresh.
                if (reading.headIds != incoming.boundaryIds || reading.headIds == incoming.headIds) continue
                dao.insertPagingKey(previous.copy(readingState = Json.encodeToString(reading.copy(headIds = incoming.headIds))))
            }
        }
        mediators.forEachIndexed { index, child ->
            if (child is MixedRemoteMediator) {
                child.publishReadingHeads("$targetKey/source/${sourceKeys[index].length}:${sourceKeys[index]}/")
            }
        }
    }

    // The reading session publishes into a different namespace from each continuation's staging.
    internal suspend fun reclaimPublishedStaging(publishedKey: String) {
        checkRequestIsValid()
        val dao = database.pagingTimelineDao()
        if (ordersByTime) {
            timeSources.forEach { source ->
                dao.getByPagingKey(source.stagingKey).chunked(500).forEach { candidates ->
                    val published = dao.existingReadingStatusIds(publishedKey, candidates.map { it.statusId }.distinct()).toSet()
                    val consumed = candidates.filter { it.statusId in published }
                    if (consumed.isNotEmpty()) {
                        val ids = consumed.map { it._id }
                        dao.deletePresentationReferences(source.stagingKey, ids)
                        dao.deleteTimelineRows(source.stagingKey, ids)
                    }
                }
            }
        }
        mediators.filterIsInstance<MixedRemoteMediator>().forEach { it.reclaimPublishedStaging(publishedKey) }
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

    @Serializable
    private data class ReadingSourceState(
        val headIds: List<String> = emptyList(),
        val boundaryIds: List<String> = emptyList(),
        val joined: Boolean = false,
        val refreshPending: Boolean = false,
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
        var reading: ReadingSourceState? = null,
    ) {
        val canLoad: Boolean
            get() = reading?.joined != true && (!initialized || nextKey != null)

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
