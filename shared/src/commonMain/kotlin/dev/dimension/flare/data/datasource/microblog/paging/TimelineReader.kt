package dev.dimension.flare.data.datasource.microblog.paging

import androidx.room3.withReadTransaction
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.dao.DbTimelinePageIdentity
import dev.dimension.flare.data.database.cache.dao.TimelineHistoryRow
import dev.dimension.flare.data.database.cache.dao.getTimelinePageInCurrentTransaction
import dev.dimension.flare.ui.model.TimelineReadingPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** A small per-view bookmark. The list and all its continuations belong to [TimelineHistory]. */
internal class TimelineReader(
    private val tabId: String,
    val history: TimelineHistory,
    private val database: CacheDatabase,
    private val store: TimelineHistoryStore,
    private val scope: CoroutineScope,
) {
    private val bindingEpoch = store.tabEpoch(tabId)
    private var binding: String? = null
    val target = MutableStateFlow<TimelineReadingPosition?>(null)
    private val head = MutableStateFlow<TimelineHistoryRow?>(null)
    private val seenHead = MutableStateFlow<TimelineHistoryRow?>(null)
    val current = MutableStateFlow<TimelineReadingPosition?>(null)
    val navigation = MutableStateFlow(0)
    val restorationWindow = MutableStateFlow<List<String>>(emptyList())
    val targetHidden = MutableStateFlow(false)
    val hasNewContent = MutableStateFlow(false)
    val isAtLatest = combine(head, current) { head, current -> head == null || current == null || head.itemKey == current.itemKey }

    private var visibleHeadLoaded = false
    private var cachedVisibleHead: TimelineHistoryRow? = null
    private var visibilityKey: Any? = null
    var isVisible: (dev.dimension.flare.ui.model.UiTimelineV2) -> Boolean = { true }
        private set

    fun setVisibility(
        key: Any,
        predicate: (dev.dimension.flare.ui.model.UiTimelineV2) -> Boolean,
    ) {
        if (visibilityKey == key) return
        visibilityKey = key
        isVisible = predicate
        invalidateVisibleHead()
        navigation.value += 1
    }

    suspend fun restore() {
        binding = store.bind(tabId, history, bindingEpoch)
        val dao = database.timelineHistoryDao()
        head.value = visibleHead()
        seenHead.value = head.value
        val bookmark = dao.bookmark(tabId, history.key) ?: return
        if (bookmark.seenItemKey != null && bookmark.seenOrdinal != null && bookmark.seenPosition != null) {
            seenHead.value = dao.row(history.key, bookmark.seenItemKey)
                ?: TimelineHistoryRow(bookmark.seenItemKey, null, "", bookmark.seenOrdinal, bookmark.seenPosition)
        }
        val row =
            dao.row(history.key, bookmark.itemKey)
                ?: dao.following(history.key, bookmark.batchOrdinal, bookmark.position)
                ?: dao.preceding(history.key, bookmark.batchOrdinal, bookmark.position)
                ?: return
        val position = TimelineReadingPosition(row.itemKey, if (row.itemKey == bookmark.itemKey) bookmark.offset else 0.0)
        current.value = position
        target.value = position
    }

    fun invalidateVisibleHead() {
        visibleHeadLoaded = false
    }

    suspend fun pageItems(rows: List<TimelineHistoryRow>): Map<String, TimelinePageItem> {
        val ids = rows.mapNotNull { it.timelineId }
        if (ids.isEmpty()) return emptyMap()
        val hydrated = database.pagingTimelineDao().getTimelinePageInCurrentTransaction(history.key, 0, ids.size, ids)
        return TimelinePagingMapper
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
    }

    suspend fun visibleHead(): TimelineHistoryRow? =
        database.withReadTransaction {
            if (!visibleHeadLoaded) {
                cachedVisibleHead = null
                var offset = 0
                while (true) {
                    val rows = database.timelineHistoryDao().page(history.key, offset, 100)
                    val items = pageItems(rows)
                    val visible = rows.firstOrNull { row -> items[row.itemKey]?.baseItem?.let(isVisible) == true }
                    if (visible != null || rows.size < 100) {
                        cachedVisibleHead = visible
                        break
                    }
                    offset += rows.size
                }
                visibleHeadLoaded = true
            }
            cachedVisibleHead
        }

    suspend fun refreshHead() {
        seenHead.value?.let { seen ->
            database.timelineHistoryDao().row(history.key, seen.itemKey)?.let { seenHead.value = it }
        }
        headChanged(visibleHead())
    }

    fun isGapVisible(row: TimelineHistoryRow): Boolean =
        head.value?.let {
            it.ordinal < row.ordinal || (it.ordinal == row.ordinal && it.position < row.position)
        } == true

    fun headChanged(row: TimelineHistoryRow?) {
        head.value = row
        if (row != null && (seenHead.value == null || current.value?.itemKey == row.itemKey)) seenHead.value = row
        val seen = seenHead.value
        // Like the new-posts banner, a prepend announces content once. Merely
        // scrolling away or removing the head must not announce old content.
        hasNewContent.value =
            row != null && seen != null && (row.ordinal < seen.ordinal || (row.ordinal == seen.ordinal && row.position < seen.position))
    }

    fun positionChanged(
        itemKey: String,
        offset: Double,
    ) {
        if (TimelineHistory.isGap(itemKey) || !offset.isFinite()) return
        val position = TimelineReadingPosition(itemKey, offset)
        current.value = position
        acknowledgeHead(itemKey)
        // Only native scroll observers call this, once a user scroll has settled.
        // Saving outlives the screen so leaving a tab cannot cancel its final bookmark.
        store.savePosition(tabId, history, position, seenHead.value, binding)
    }

    fun viewportChanged(
        itemKey: String,
        offset: Double,
    ) {
        if (!TimelineHistory.isGap(itemKey) && offset.isFinite()) {
            current.value = TimelineReadingPosition(itemKey, offset)
            acknowledgeHead(itemKey)
        }
    }

    private fun acknowledgeHead(itemKey: String) {
        head.value?.takeIf { it.itemKey == itemKey && it != seenHead.value }?.let {
            seenHead.value = it
            hasNewContent.value = false
            store.savePosition(tabId, history, null, it, binding)
        }
    }

    fun showLatest() {
        scope.launch {
            invalidateVisibleHead()
            val row = visibleHead() ?: return@launch
            val itemKey = row.itemKey
            head.value = row
            acknowledgeHead(itemKey)
            val position = TimelineReadingPosition(itemKey, 0.0)
            current.value = position
            targetHidden.value = false
            target.value = position
            navigation.value += 1
            store.savePosition(tabId, history, position, seenHead.value, binding)
        }
    }
}
