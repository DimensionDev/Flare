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
            Json.decodeFromString<List<SourcePage>>(nextKey).mapIndexed { index, page ->
                ContinuationIdentity(
                    page.started,
                    page.next?.let { (sources[index] as? MixedTimelinePageLoader)?.continuationIdentity(it) ?: it },
                    page.pending.map { it.stableItemKey },
                    page.sourceHeads,
                )
            },
        )

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> = loadPage(pageSize, request) { _, _ -> false }

    suspend fun loadPage(
        pageSize: Int,
        request: PagingRequest,
        joinsTimeline: suspend (String, List<UiTimelineV2>) -> Boolean,
    ): PagingResult<UiTimelineV2> =
        coroutineScope {
            if (request is PagingRequest.Prepend) return@coroutineScope PagingResult()
            val pages =
                when (request) {
                    PagingRequest.Refresh -> List(sources.size) { SourcePage() }
                    is PagingRequest.Append -> Json.decodeFromString<List<SourcePage>>(request.nextKey)
                    is PagingRequest.Prepend -> error("Handled above")
                }.toMutableList()
            require(pages.size == sources.size) { "Mixed timeline sources changed" }
            val attempted = mutableSetOf<Int>()
            val failures = mutableListOf<Throwable>()
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
                                        source.loadPage(pageSize, subRequest, joinsTimeline)
                                    } else {
                                        source.load(pageSize, subRequest)
                                    }
                                val boundaryIndex =
                                    if (source.canJoinTimeline) {
                                        response.data.indexOfFirst { joinsTimeline(source.pagingKey, listOf(it)) }
                                    } else {
                                        -1
                                    }
                                val result =
                                    if (boundaryIndex >= 0) {
                                        response.copy(
                                            data =
                                                if (subRequest is PagingRequest.Append) {
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
            if (policy == TimelineMergePolicy.Time) {
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
                val pending = pages.map { it.pending }
                selected +=
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
                    }.distinctBy { it.stableItemKey }.filterNot { exclude(it) }
                pages.indices.forEach { pages[it] = pages[it].copy(pending = emptyList()) }
            }
            if (selected.isEmpty() && failedAttempts == attempted.size && failures.isNotEmpty() &&
                (request is PagingRequest.Refresh || pages.any { it.canLoad })
            ) {
                throw failures.first()
            }
            val published = selected.mapTo(hashSetOf()) { it.stableItemKey }
            PagingResult(
                data = selected,
                nextKey = if (pages.any { it.canLoad || it.pending.isNotEmpty() }) Json.encodeToString(pages) else null,
                continuationFailure = continuationFailure,
                sourceFailures = failures.distinct(),
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
