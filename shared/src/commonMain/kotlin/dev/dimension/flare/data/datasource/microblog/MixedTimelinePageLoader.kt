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
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey =
        "mixed:${policy.name}:" + sources.joinToString("") { "${it.pagingKey.length}:${it.pagingKey}" }
    override val collapseReplyChains = false

    @Serializable
    private data class SourcePage(
        val started: Boolean = false,
        val next: String? = null,
        val pending: List<UiTimelineV2> = emptyList(),
    ) {
        val canLoad: Boolean get() = !started || next != null
    }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
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

            suspend fun fetch(indices: List<Int>) {
                indices
                    .map { index ->
                        async {
                            val before = pages[index]
                            try {
                                val result =
                                    sources[index].load(
                                        pageSize,
                                        if (before.started) PagingRequest.Append(requireNotNull(before.next)) else PagingRequest.Refresh,
                                    )
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
                            pages[index] = SourcePage(true, result.nextKey, pages[index].pending + result.data)
                            result.continuationFailure?.let(failures::add)
                        }
                        error?.let(failures::add)
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
                    if (seen.add(item.stableItemKey)) selected += item
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
                    }.distinctBy { it.stableItemKey }
                pages.indices.forEach { pages[it] = pages[it].copy(pending = emptyList()) }
            }
            if (selected.isEmpty() && failures.size == attempted.size && failures.isNotEmpty()) throw failures.first()
            PagingResult(
                data = selected,
                nextKey = if (pages.any { it.canLoad || it.pending.isNotEmpty() }) Json.encodeToString(pages) else null,
                continuationFailure = failures.firstOrNull(),
            )
        }
}
