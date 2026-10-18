package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.PagingState
import androidx.room3.withReadTransaction
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.dao.DbTimelinePageIdentity
import dev.dimension.flare.data.database.cache.dao.getTimelinePageInCurrentTransaction
import dev.dimension.flare.data.datasource.microblog.paging.TimelineReader
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.TimelineReadingPosition
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.time.Instant

internal data class TimelineHistoryPagingKey(
    val itemKey: String,
    val direction: Direction = Direction.Around,
) {
    enum class Direction { Before, After, Around }
}

/** Loads only the local window around a stable item. An internal gap is an ordinary row. */
internal class TimelineHistoryPagingSource(
    private val database: CacheDatabase,
    private val reader: TimelineReader,
    private val onLoadGap: (String) -> Unit,
) : BasePagingSource<TimelineHistoryPagingKey, TimelinePageItem>() {
    private val history = reader.history
    private val observers = CoroutineScope(SupervisorJob() + PlatformDispatchers.IO)

    init {
        observers.launch(start = CoroutineStart.UNDISPATCHED) {
            database.invalidationTracker
                .createFlow(
                    "DbTimelineEntry",
                    "DbTimelineBatch",
                    "DbTimelineHistory",
                    "DbPagingTimeline",
                    "DbStatus",
                    "status_reference",
                    "timeline_item_presentation_reference",
                    "DbTranslation",
                    emitInitialState = false,
                ).collect { invalidate() }
        }
        observers.launch(start = CoroutineStart.UNDISPATCHED) { history.activity.drop(1).collect { invalidate() } }
        observers.launch(start = CoroutineStart.UNDISPATCHED) { reader.navigation.drop(1).collect { invalidate() } }
        registerInvalidatedCallback { observers.cancel() }
    }

    override suspend fun doLoad(params: LoadParams<TimelineHistoryPagingKey>): LoadResult<TimelineHistoryPagingKey, TimelinePageItem> =
        database.withReadTransaction {
            val dao = database.timelineHistoryDao()
            val key = params.key ?: reader.current.value?.let { TimelineHistoryPagingKey(it.itemKey) }
            val anchor = key?.let { dao.row(history.key, it.itemKey) }
            val index = anchor?.let { dao.offset(history.key, it.ordinal, it.position) } ?: 0
            val offset =
                when (key?.direction) {
                    TimelineHistoryPagingKey.Direction.Before -> (index - params.loadSize).coerceAtLeast(0)
                    TimelineHistoryPagingKey.Direction.After -> index + 1
                    else -> (index - params.loadSize / 3).coerceAtLeast(0)
                }
            val limit = if (key?.direction == TimelineHistoryPagingKey.Direction.Before) index - offset else params.loadSize
            val rows = dao.page(history.key, offset, limit + 1)
            val window = rows.take(limit)
            if (params is LoadParams.Refresh && reader.target.value != null) {
                reader.restorationWindow.value = window.filter { it.timelineId != null }.map { it.itemKey }
            }
            val ids = window.mapNotNull { it.timelineId }
            val hydrated = database.pagingTimelineDao().getTimelinePageInCurrentTransaction(history.key, 0, limit, ids)
            val items =
                TimelinePagingMapper
                    .toPageItems(
                        hydrated,
                        hydrated.map { item ->
                            DbTimelinePageIdentity(
                                item.timeline.statusId,
                                item.timeline.sortId,
                                item.timeline.messageRenderHash,
                                item.timeline.contentRevision,
                                item.timeline._id,
                            )
                        },
                        history.key,
                        mutableMapOf(),
                    ).associateBy { it.baseItem.stableItemKey }
            reader.head.value = dao.head(history.key)?.itemKey
            if (reader.current.value == null) {
                window.firstOrNull { it.timelineId != null }?.let {
                    reader.current.value = TimelineReadingPosition(it.itemKey, 0.0)
                }
            }
            LoadResult.Page(
                data =
                    window.mapNotNull { row ->
                        if (row.timelineId != null) items[row.itemKey] else gap(row.itemKey, row.batchId)
                    },
                prevKey =
                    window.firstOrNull()?.takeIf { offset > 0 }?.let {
                        TimelineHistoryPagingKey(it.itemKey, TimelineHistoryPagingKey.Direction.Before)
                    },
                nextKey =
                    window.lastOrNull()?.takeIf { rows.size > limit }?.let {
                        TimelineHistoryPagingKey(it.itemKey, TimelineHistoryPagingKey.Direction.After)
                    },
            )
        }

    private fun gap(
        itemKey: String,
        batchId: String,
    ): TimelinePageItem {
        val activity = history.activity.value
        val type =
            when (batchId) {
                in activity.loading -> UiTimelineV2.Message.Type.Localized.MessageId.LoadingMissingPosts
                in activity.failures -> UiTimelineV2.Message.Type.Localized.MessageId.RetryMissingPosts
                else -> UiTimelineV2.Message.Type.Localized.MessageId.LoadMissingPosts
            }
        val message =
            UiTimelineV2.Message(
                statusKey = MicroBlogKey(batchId, "local"),
                icon = UiIcon.More,
                type = UiTimelineV2.Message.Type.Localized(type),
                createdAt = Instant.fromEpochMilliseconds(activity.revision).toUi(),
                clickEvent = ClickEvent.Noop,
                accountType = AccountType.Guest,
                itemKey = itemKey,
                onActivate = { if (batchId !in history.activity.value.loading) onLoadGap(batchId) },
            )
        return TimelinePageItem(DbTimelinePageIdentity(itemKey, 0, message.renderHash, 0), message, emptyMap())
    }

    override fun getRefreshKey(state: PagingState<TimelineHistoryPagingKey, TimelinePageItem>): TimelineHistoryPagingKey? =
        (reader.current.value?.itemKey ?: state.anchorPosition?.let { state.closestItemToPosition(it)?.baseItem?.stableItemKey })
            ?.let { TimelineHistoryPagingKey(it) }
}
