package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.cache.CacheDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

internal class TimelineReadingPagingSource(
    private val database: CacheDatabase,
    private val session: TimelineReadingSession,
) : BasePagingSource<OffsetFromStartPagingKey, TimelinePageItem>() {
    private val pagingKey = session.activeKey
    private val loader = TimelineDbPageLoader(database, pagingKey, TimelineDbPageCache())
    private val subscription = loader.observeInvalidations(::invalidate)
    private val gapSubscription =
        CoroutineScope(PlatformDispatchers.IO).launch(start = CoroutineStart.UNDISPATCHED) {
            session.gapGeneration.drop(1).collect { invalidate() }
        }
    private val positionSubscription =
        CoroutineScope(PlatformDispatchers.IO).launch(start = CoroutineStart.UNDISPATCHED) {
            val initial =
                session.state.value.position
                    ?.requestId
            session.state.collect { state ->
                if (state.position != null && state.position.requestId != initial) invalidate()
            }
        }

    init {
        registerInvalidatedCallback {
            subscription.job.cancel()
            gapSubscription.cancel()
            positionSubscription.cancel()
        }
    }

    override suspend fun doLoad(params: LoadParams<OffsetFromStartPagingKey>): LoadResult<OffsetFromStartPagingKey, TimelinePageItem> {
        subscription.ready?.await()
        val dao = database.pagingTimelineDao()
        val key =
            (
                if (params is LoadParams.Refresh) {
                    session.state.value.position
                        ?.itemKey
                        ?.let(OffsetFromStartPagingKey::Around)
                } else {
                    null
                }
            )
                ?: params.key
                ?: database
                    .timelineReadingSessionDao()
                    .snapshot(session.record.key)
                    ?.anchorId
                    ?.let(OffsetFromStartPagingKey::Around)
        val offset =
            when (key) {
                is OffsetFromStartPagingKey.Append -> {
                    key.offset
                }

                is OffsetFromStartPagingKey.Before -> {
                    (key.offset - params.loadSize).coerceAtLeast(0)
                }

                is OffsetFromStartPagingKey.Around -> {
                    dao.readingItem(pagingKey, key.itemKey)?.let {
                        (dao.readingItemOffset(pagingKey, it.sortId) - params.loadSize / 2).coerceAtLeast(0)
                    } ?: 0
                }

                else -> {
                    0
                }
            }
        val limit = if (key is OffsetFromStartPagingKey.Before) key.offset - offset else params.loadSize
        val data = loader.load(offset, limit)
        return LoadResult.Page(
            data = session.withGapMarkers(data),
            prevKey = if (offset > 0) OffsetFromStartPagingKey.Before(offset) else null,
            nextKey = if (data.size == limit && limit > 0) OffsetFromStartPagingKey.Append(offset + data.size) else null,
        )
    }

    override fun getRefreshKey(state: PagingState<OffsetFromStartPagingKey, TimelinePageItem>): OffsetFromStartPagingKey? {
        session.state.value.position
            ?.let { return OffsetFromStartPagingKey.Around(it.itemKey) }
        val item = state.anchorPosition?.let(state::closestItemToPosition)?.baseItem ?: return null
        val anchor =
            if (item.isReadingGap) {
                state.pages
                    .asSequence()
                    .flatMap { it.data }
                    .map { it.baseItem }
                    .dropWhile { it.stableItemKey != item.stableItemKey }
                    .firstOrNull { !it.isReadingGap }
            } else {
                item
            }
        return anchor?.stableItemKey?.let(OffsetFromStartPagingKey::Around)
    }
}
