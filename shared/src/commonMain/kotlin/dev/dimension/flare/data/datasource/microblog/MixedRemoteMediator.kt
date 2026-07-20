package dev.dimension.flare.data.datasource.microblog

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
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
    private val timeSources =
        mediators.mapIndexed { index, mediator ->
            TimeSource(mediator, "$pagingKey$TIME_STAGING_SUFFIX$index")
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
                resetTimeStaging()
            }

            val seenStatusIds =
                if (request is PagingRequest.Append) {
                    database
                        .pagingTimelineDao()
                        .getByPagingKey(pagingKey)
                        .mapTo(mutableSetOf()) { it.statusId }
                } else {
                    mutableSetOf()
                }
            if (seenStatusIds.isNotEmpty()) {
                database.connect {
                    timeSources.forEach { source ->
                        val committed =
                            database
                                .pagingTimelineDao()
                                .getByPagingKey(source.stagingKey)
                                .filter { it.statusId in seenStatusIds }
                        if (committed.isNotEmpty()) {
                            database
                                .pagingTimelineDao()
                                .deletePresentationReferences(source.stagingKey, committed.map { it.statusId })
                            database.pagingTimelineDao().delete(committed)
                        }
                    }
                }
            }

            val timeSourceStates = loadTimeSourceStates()
            val loadedStates = mutableListOf<TimeSourceState>()
            val selected = mutableListOf<DbPagingTimelineWithStatus>()
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

                    val responses =
                        statesToLoad
                            .map { state ->
                                val subRequest = state.nextRequest()
                                async {
                                    val result =
                                        runCatching {
                                            state.source.mediator.load(pageSize, subRequest)
                                        }.getOrElse {
                                            reportError?.invoke(it)
                                            PagingResult(endOfPaginationReached = true)
                                        }
                                    val stagedItems =
                                        result.data.map { item ->
                                            TimelinePagingMapper.toDb(
                                                data = item,
                                                pagingKey = state.source.stagingKey,
                                                sortId = timeSortId(item),
                                            )
                                        }
                                    TimeSubResponse(state, result, stagedItems)
                                }
                            }.awaitAll()

                    database.connect {
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
                if (seenStatusIds.add(item.timeline.statusId)) {
                    selected += item
                }
            }

            val hasMore =
                timeSourceStates.any {
                    it.pending.any { item -> item.timeline.statusId !in seenStatusIds } || it.canLoad
                }
            PagingResult(
                data = selected.map(::timeStagingToUi),
                nextKey = if (hasMore) MIXED_NEXT_KEY else null,
                previousKey = null,
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

    private suspend fun resetTimeStaging() {
        database.connect {
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
        if (mergePolicy == TimelineMergePolicy.Time) {
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
