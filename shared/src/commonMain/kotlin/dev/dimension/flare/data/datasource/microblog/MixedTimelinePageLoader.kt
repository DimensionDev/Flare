package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Self-contained mixed continuations: at most one pending remote page per source. */
internal class MixedTimelinePageLoader(
    private val sources: List<CacheableRemoteLoader<UiTimelineV2>>,
    private val policy: TimelineMergePolicy,
    private val exclude: suspend (UiTimelineV2) -> Boolean = { false },
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey =
        "mixed:${policy.name}:" + sources.joinToString("") { "${it.pagingKey.length}:${it.pagingKey}" }
    override val collapseReplyChains = false
    val ordersByTime: Boolean get() = policy == TimelineMergePolicy.Time

    @Serializable
    private data class SourcePage(
        val started: Boolean = false,
        val next: String? = null,
        val pending: List<UiTimelineV2> = emptyList(),
        val sourceHeads: Map<String, List<String>> = emptyMap(),
    ) {
        val canLoad: Boolean get() = !started || next != null
    }

    @Serializable
    private data class ContinuationIdentity(
        val started: Boolean,
        val next: String?,
        val pending: List<String>,
        val sourceHeads: Map<String, List<String>>,
    )

    fun continuationIdentity(nextKey: String): String =
        Json.encodeToString(
            decodeContinuation(nextKey).mapIndexed { index, page ->
                ContinuationIdentity(
                    page.started,
                    page.next?.let { (sources[index] as? MixedTimelinePageLoader)?.continuationIdentity(it) ?: it },
                    page.pending.map { it.stableItemKey },
                    page.sourceHeads,
                )
            },
        )

    /** Time gaps retain unfinished ordered cursors with an old boundary; buffered posts follow the ordinary tail. */
    fun splitContinuation(
        nextKey: String?,
        boundarySources: Set<String>,
        orderedOnly: Boolean = ordersByTime,
    ): Pair<String?, String?> {
        if (nextKey == null) return null to null
        if (boundarySources.isEmpty()) return null to nextKey
        if (!orderedOnly && allSourcesHaveBoundary(boundarySources, orderedOnly)) return nextKey to null
        val gap = mutableListOf<SourcePage>()
        val tail = mutableListOf<SourcePage>()
        decodeContinuation(nextKey).forEachIndexed { index, page ->
            val source = sources[index]
            when {
                source is MixedTimelinePageLoader -> {
                    val (gapNext, tailNext) = source.splitContinuation(page.next, boundarySources, orderedOnly)
                    gap += page.copy(next = gapNext, pending = if (orderedOnly) emptyList() else page.pending)
                    tail += page.copy(next = tailNext)
                }

                source.pagingKey in boundarySources && (!orderedOnly || (source.canJoinTimeline && page.canLoad)) -> {
                    gap += if (orderedOnly) page.copy(pending = emptyList()) else page
                    tail += if (orderedOnly) page.copy(next = null) else SourcePage(started = true)
                }

                else -> {
                    gap += SourcePage(started = true)
                    tail += page
                }
            }
        }
        return encodeContinuation(gap) to encodeContinuation(tail)
    }

    private fun allSourcesHaveBoundary(
        boundarySources: Set<String>,
        orderedOnly: Boolean,
    ): Boolean =
        sources.all { source ->
            if (source is MixedTimelinePageLoader) {
                source.allSourcesHaveBoundary(boundarySources, orderedOnly)
            } else {
                source.pagingKey in boundarySources && (!orderedOnly || source.canJoinTimeline)
            }
        }

    fun mergeTailContinuation(
        nextKey: String?,
        updates: String?,
        refreshedRecommendations: Set<String> = emptySet(),
        preserveRecommendations: Boolean = false,
    ): String? {
        val previous = nextKey?.let(::decodeContinuation) ?: List(sources.size) { SourcePage(started = true) }
        val incoming = updates?.let(::decodeContinuation) ?: List(sources.size) { SourcePage(started = true) }
        val merged =
            previous.mapIndexed { index, page ->
                val update = incoming[index]
                val source = sources[index]
                val pending =
                    (page.pending + update.pending).distinctBy { it.stableItemKey }.let {
                        if (ordersByTime) it.sortedByDescending { item -> item.createdAt.value } else it
                    }
                when {
                    source is MixedTimelinePageLoader && (update.next != null || refreshedRecommendations.isNotEmpty()) -> {
                        val next = source.mergeTailContinuation(page.next, update.next, refreshedRecommendations, preserveRecommendations)
                        // Compare nested buffers with this page before selecting the next item by time.
                        val buffered = next?.takeIf { ordersByTime }?.let(source::drainPending)
                        page.copy(
                            started = true,
                            next = if (buffered != null) buffered.nextKey else next,
                            pending =
                                if (buffered ==
                                    null
                                ) {
                                    pending
                                } else {
                                    mergePending(listOf(pending, buffered.data)).distinctBy { it.stableItemKey }
                                },
                            sourceHeads = page.sourceHeads + buffered?.sourceHeads.orEmpty(),
                        )
                    }

                    source.pagingKey in refreshedRecommendations -> {
                        // Replace the recommendation cursor while retaining already downloaded posts.
                        update.copy(pending = pending)
                    }

                    preserveRecommendations && !source.canJoinTimeline -> {
                        // Old gaps cannot revive a replaced or exhausted recommendation cursor.
                        page
                    }

                    !page.canLoad && update.canLoad -> {
                        page.copy(
                            next = update.next,
                            pending = pending,
                            sourceHeads = update.sourceHeads + page.sourceHeads,
                        )
                    }

                    ordersByTime || preserveRecommendations -> {
                        page.copy(
                            pending = pending,
                            sourceHeads = if (update.pending.isEmpty()) page.sourceHeads else page.sourceHeads + update.sourceHeads,
                        )
                    }

                    else -> {
                        page
                    }
                }
            }
        return if (merged == previous) nextKey else encodeContinuation(merged)
    }

    private fun encodeContinuation(pages: List<SourcePage>): String? =
        if (pages.any { it.canLoad || it.pending.isNotEmpty() }) Json.encodeToString(pages) else null

    private fun decodeContinuation(nextKey: String): List<SourcePage> =
        Json.decodeFromString<List<SourcePage>>(nextKey).also {
            require(it.size == sources.size) { "Mixed timeline sources changed" }
        }

    private fun closeJoinedSources(
        pages: List<SourcePage>,
        boundary: Map<String, List<String>>,
    ): List<SourcePage> {
        if (boundary.isEmpty()) return pages
        return pages.mapIndexed { index, page ->
            val source = sources[index]
            when {
                source is MixedTimelinePageLoader && page.next != null -> {
                    val nested = source.decodeContinuation(page.next)
                    val remaining = source.closeJoinedSources(nested, boundary)
                    if (remaining == nested) page else page.copy(next = source.encodeContinuation(remaining))
                }

                source.canJoinTimeline &&
                    page.sourceHeads[source.pagingKey].orEmpty().any {
                        it in boundary[source.pagingKey].orEmpty()
                    }
                -> {
                    // Older gaps can retain a history cursor after their fetched page already crossed this boundary.
                    page.copy(next = null)
                }

                else -> {
                    page
                }
            }
        }
    }

    private fun mergePending(pending: List<List<UiTimelineV2>>): List<UiTimelineV2> =
        when (policy) {
            TimelineMergePolicy.Staggered -> {
                buildList {
                    repeat(pending.maxOfOrNull { it.size } ?: 0) { row ->
                        pending.forEach { it.getOrNull(row)?.let(::add) }
                    }
                }
            }

            else -> {
                pending.flatten().sortedByDescending { it.createdAt.value }
            }
        }

    fun drainPending(nextKey: String): PagingResult<UiTimelineV2> {
        val pages =
            decodeContinuation(nextKey).mapIndexed { index, page ->
                val nested = sources[index] as? MixedTimelinePageLoader
                if (nested == null || page.next == null) {
                    page
                } else {
                    val drained = nested.drainPending(page.next)
                    page.copy(
                        next = drained.nextKey,
                        pending = page.pending + drained.data,
                        sourceHeads =
                            drained.sourceHeads + page.sourceHeads,
                    )
                }
            }
        return PagingResult(
            data = mergePending(pages.map { it.pending }),
            nextKey = encodeContinuation(pages.map { it.copy(pending = emptyList()) }),
            sourceHeads = pages.flatMap { it.sourceHeads.entries }.associate { it.toPair() },
        )
    }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> = loadPage(pageSize, request)

    suspend fun loadPage(
        pageSize: Int,
        request: PagingRequest,
        cachedItemKeys: suspend (List<UiTimelineV2>) -> Set<String> = { emptySet() },
        refreshWithoutGap: Set<String> = emptySet(),
        publishAll: Boolean = false,
        boundary: Map<String, List<String>> = emptyMap(),
        timeOrdered: Boolean = ordersByTime,
        canPublish: suspend (UiTimelineV2) -> Boolean = { true },
    ): PagingResult<UiTimelineV2> =
        coroutineScope {
            if (request is PagingRequest.Prepend) return@coroutineScope PagingResult()
            val pages =
                when (request) {
                    PagingRequest.Refresh -> List(sources.size) { SourcePage() }
                    is PagingRequest.Append -> closeJoinedSources(decodeContinuation(request.nextKey), boundary)
                    is PagingRequest.Prepend -> error("Handled above")
                }.toMutableList()
            if (publishAll) {
                pages.indices.forEach { index ->
                    val page = pages[index]
                    val nested = sources[index] as? MixedTimelinePageLoader
                    if (nested != null && page.next != null) {
                        val drained = nested.drainPending(page.next)
                        pages[index] =
                            page.copy(
                                next = drained.nextKey,
                                pending = page.pending + drained.data,
                                sourceHeads = drained.sourceHeads + page.sourceHeads,
                            )
                    }
                }
            }
            val attempted = mutableSetOf<Int>()
            val failures = mutableListOf<Throwable>()
            val refreshedSources = mutableSetOf<String>()
            val refreshedRecommendations = mutableSetOf<String>()
            var failedAttempts = 0
            var continuationFailure: Throwable? = null

            suspend fun fetch(indices: List<Int>) {
                indices
                    .map { index ->
                        async {
                            val before = pages[index]
                            try {
                                val source = sources[index]
                                val subRequest =
                                    if (before.started) {
                                        PagingRequest.Append(
                                            requireNotNull(before.next),
                                        )
                                    } else {
                                        PagingRequest.Refresh
                                    }
                                val response =
                                    if (source is MixedTimelinePageLoader) {
                                        source.loadPage(
                                            pageSize,
                                            subRequest,
                                            cachedItemKeys,
                                            refreshWithoutGap,
                                            publishAll,
                                            boundary,
                                            timeOrdered,
                                            canPublish,
                                        )
                                    } else {
                                        source.load(pageSize, subRequest)
                                    }
                                val boundaryIndex =
                                    if (source.canJoinTimeline) {
                                        response.data.indexOfFirst { it.stableItemKey in boundary[source.pagingKey].orEmpty() }
                                    } else {
                                        -1
                                    }
                                val refreshRecommendation = timeOrdered && subRequest is PagingRequest.Refresh && !source.canJoinTimeline
                                val pageIsCached =
                                    !refreshRecommendation && source !is MixedTimelinePageLoader && boundaryIndex < 0 &&
                                        source.pagingKey !in refreshWithoutGap &&
                                        response.nextKey != null && response.data.isNotEmpty() &&
                                        response.continuationFailure == null && response.sourceFailures.isEmpty() &&
                                        cachedItemKeys(response.data).containsAll(response.data.map { it.stableItemKey })
                                val result =
                                    if (boundaryIndex >= 0 || pageIsCached ||
                                        (!refreshRecommendation && source.pagingKey in refreshWithoutGap)
                                    ) {
                                        response.copy(
                                            data =
                                                if (boundaryIndex >= 0 && subRequest is PagingRequest.Append) {
                                                    response.data.take(
                                                        boundaryIndex + 1,
                                                    )
                                                } else {
                                                    response.data
                                                },
                                            nextKey = null,
                                        )
                                    } else {
                                        response
                                    }
                                check(!before.started || result.nextKey != before.next || result.data.isNotEmpty()) {
                                    "Timeline continuation did not advance"
                                }
                                Triple(index, result, null)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                Triple(index, null, error)
                            }
                        }
                    }.awaitAll()
                    .forEach { (index, result, error) ->
                        attempted += index
                        if (result != null) {
                            val previous = pages[index]
                            val source = sources[index]
                            refreshedSources += result.refreshedSources
                            refreshedRecommendations += result.refreshedRecommendations
                            if (timeOrdered && request is PagingRequest.Refresh && source !is MixedTimelinePageLoader &&
                                !source.canJoinTimeline &&
                                result.continuationFailure == null && result.sourceFailures.isEmpty()
                            ) {
                                refreshedRecommendations += source.pagingKey
                            }
                            if (request is PagingRequest.Refresh && source.refreshGapOncePerSession &&
                                result.continuationFailure == null && result.sourceFailures.isEmpty()
                            ) {
                                refreshedSources += source.pagingKey
                            }
                            val fetchedHeads =
                                if (source is MixedTimelinePageLoader) {
                                    result.sourceHeads
                                } else {
                                    mapOf(source.pagingKey to result.data.map { it.stableItemKey })
                                }
                            val heads = fetchedHeads + previous.sourceHeads.filterValues { it.isNotEmpty() }
                            val fetched = if (ordersByTime) result.data.sortedByDescending { it.createdAt.value } else result.data
                            pages[index] = SourcePage(true, result.nextKey, previous.pending + fetched, heads)
                            failures += result.sourceFailures
                            result.continuationFailure?.let {
                                failures += it
                                continuationFailure = continuationFailure ?: it
                            }
                        }
                        if (error != null) {
                            failedAttempts++
                            // A failed first page has no continuation. Retry it on
                            // the next refresh, not through a fabricated gap. An
                            // already issued continuation stays available for retry.
                            if (!pages[index].started) {
                                pages[index] = pages[index].copy(started = true)
                            } else {
                                continuationFailure = continuationFailure ?: error
                            }
                            failures += error
                        }
                    }
            }

            val selected = mutableListOf<UiTimelineV2>()
            if (policy == TimelineMergePolicy.Time && !publishAll) {
                val seen = mutableSetOf<String>()
                while (selected.size < pageSize) {
                    fetch(pages.indices.filter { pages[it].pending.isEmpty() && pages[it].canLoad && it !in attempted })
                    val index =
                        pages.indices
                            .filter { pages[it].pending.isNotEmpty() }
                            .maxByOrNull {
                                pages[it]
                                    .pending
                                    .first()
                                    .createdAt.value
                            } ?: break
                    val item = pages[index].pending.first()
                    pages[index] = pages[index].copy(pending = pages[index].pending.drop(1))
                    if (!exclude(item) && seen.add(item.stableItemKey)) selected += item
                    // A successful sparse source needs another user-initiated page before
                    // its unknown head can be compared. Failed sources do not hide successes.
                    if (pages.indices.any { pages[it].pending.isEmpty() && pages[it].canLoad && it in attempted } &&
                        failures.isEmpty()
                    ) {
                        break
                    }
                }
            } else {
                fetch(pages.indices.filter { pages[it].pending.isEmpty() && pages[it].canLoad })
                val ready =
                    pages.indices.map { index ->
                        val (published, remaining) = pages[index].pending.partition { canPublish(it) }
                        pages[index] = pages[index].copy(pending = remaining)
                        published
                    }
                selected +=
                    mergePending(ready)
                        .distinctBy { it.stableItemKey }
                        .filterNot { exclude(it) }
            }
            val cachedPending = cachedItemKeys(pages.flatMap { it.pending })
            if (cachedPending.isNotEmpty() || refreshWithoutGap.isNotEmpty()) {
                val published = selected.mapTo(hashSetOf()) { it.stableItemKey }
                pages.indices.forEach { index ->
                    val publishNow =
                        cachedPending +
                            pages[index]
                                .sourceHeads
                                .filterKeys { it in refreshWithoutGap }
                                .values
                                .flatten()
                    val (ready, remaining) =
                        pages[index].pending.partition {
                            it.stableItemKey in cachedPending || (it.stableItemKey in publishNow && canPublish(it))
                        }
                    // Publish fetched featured posts before discarding their refresh continuation.
                    selected += ready.filter { published.add(it.stableItemKey) && !exclude(it) }
                    pages[index] = pages[index].copy(pending = remaining)
                }
                if (ordersByTime) selected.sortByDescending { it.createdAt.value }
            }
            if (selected.isEmpty() && failedAttempts == attempted.size && failures.isNotEmpty() &&
                (request is PagingRequest.Refresh || pages.any { it.canLoad })
            ) {
                throw failures.first()
            }
            val published = selected.mapTo(hashSetOf()) { it.stableItemKey }
            PagingResult(
                data = selected,
                nextKey = encodeContinuation(pages),
                continuationFailure = continuationFailure,
                sourceFailures = failures.distinct(),
                refreshedSources = refreshedSources,
                refreshedRecommendations = refreshedRecommendations,
                sourceHeads =
                    pages
                        .flatMap { it.sourceHeads.entries }
                        .associate {
                            it.key to
                                it.value.filter { key ->
                                    key in published
                                }
                        }.filterValues { it.isNotEmpty() },
            )
        }
}
