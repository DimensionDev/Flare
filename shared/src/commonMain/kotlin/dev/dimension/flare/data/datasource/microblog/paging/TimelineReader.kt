package dev.dimension.flare.data.datasource.microblog.paging

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.dao.TimelineHistoryRow
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
    val target = MutableStateFlow<TimelineReadingPosition?>(null)
    private val head = MutableStateFlow<TimelineHistoryRow?>(null)
    private val seenHead = MutableStateFlow<TimelineHistoryRow?>(null)
    val current = MutableStateFlow<TimelineReadingPosition?>(null)
    val navigation = MutableStateFlow(0)
    val restorationWindow = MutableStateFlow<List<String>>(emptyList())
    val targetHidden = MutableStateFlow(false)
    val hasNewContent = MutableStateFlow(false)
    val isAtLatest = combine(head, current) { head, current -> head == null || current == null || head.itemKey == current.itemKey }

    suspend fun restore() {
        val dao = database.timelineHistoryDao()
        head.value = dao.head(history.key)
        seenHead.value = head.value
        val bookmark = dao.bookmark(tabId, history.key) ?: return
        val row =
            dao.row(history.key, bookmark.itemKey)
                ?: dao.following(history.key, bookmark.batchOrdinal, bookmark.position)
                ?: dao.preceding(history.key, bookmark.batchOrdinal, bookmark.position)
                ?: return
        val position = TimelineReadingPosition(row.itemKey, if (row.itemKey == bookmark.itemKey) bookmark.offset else 0.0)
        current.value = position
        target.value = position
    }

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
        store.savePosition(tabId, history, position)
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
        head.value?.takeIf { it.itemKey == itemKey }?.let {
            seenHead.value = it
            hasNewContent.value = false
        }
    }

    fun showLatest() {
        scope.launch {
            val row = database.timelineHistoryDao().head(history.key) ?: return@launch
            val itemKey = row.itemKey
            head.value = row
            acknowledgeHead(itemKey)
            val position = TimelineReadingPosition(itemKey, 0.0)
            current.value = position
            targetHidden.value = false
            target.value = position
            navigation.value += 1
            store.savePosition(tabId, history, position)
        }
    }
}
