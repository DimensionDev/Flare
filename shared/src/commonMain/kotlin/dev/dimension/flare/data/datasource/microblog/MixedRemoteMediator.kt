package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.SortIdProvider
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.ui.model.UiTimelineV2

/** The outer mediator commits the complete continuation together with its visible page. */
internal class MixedRemoteMediator(
    private val database: CacheDatabase,
    private val mediators: List<CacheableRemoteLoader<UiTimelineV2>>,
    private val mergePolicy: TimelineMergePolicy = TimelineMergePolicy.TimePerPage,
) : CacheableRemoteLoader<UiTimelineV2>,
    SortIdProvider {
    override val pagingKey = "mixed-v2/" + pageLoader().pagingKey

    internal fun pageLoader(): MixedTimelinePageLoader =
        MixedTimelinePageLoader(
            mediators.map { if (it is MixedRemoteMediator) it.pageLoader() else it },
            mergePolicy,
        )

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val seen =
            if (request is PagingRequest.Append) {
                database.pagingTimelineDao().getStatusIdsWithInlineParents(pagingKey).toSet()
            } else {
                emptySet()
            }
        return MixedTimelinePageLoader(
            mediators.map { if (it is MixedRemoteMediator) it.pageLoader() else it },
            mergePolicy,
            exclude = { item ->
                seen.isNotEmpty() && TimelinePagingMapper.toDb(listOf(item), pagingKey).any { it.timeline.statusId in seen }
            },
        ).load(pageSize, request)
    }

    override suspend fun sortId(data: UiTimelineV2): Long? =
        if (mergePolicy == TimelineMergePolicy.Time) {
            val tieBreaker = (data.stableItemKey.hashCode().toLong() - Int.MIN_VALUE.toLong()) % 10_000L
            -data.createdAt.value.toEpochMilliseconds() * 10_000L + tieBreaker
        } else {
            null
        }
}
