@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package dev.dimension.flare.data.datasource.microblog.paging

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbTimelinePosition
import dev.dimension.flare.ui.model.TimelineReadingPosition
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Per-window coordinates; posts and gaps stay in the ordinary shared cache. */
internal class TimelineViewport(
    private val database: CacheDatabase,
    val pagingKey: String,
    private val tabId: String,
    private val scope: CoroutineScope,
    private val appScope: CoroutineScope,
    private val canSave: suspend () -> Boolean = { true },
) {
    val target = MutableStateFlow<TimelineReadingPosition?>(null)
    val current = MutableStateFlow<TimelineReadingPosition?>(null)
    val navigation = MutableStateFlow(0)
    val hasNewContent = MutableStateFlow(false)
    val isAtLatest = MutableStateFlow(true)
    private var sortId = 0L
    private var generation: String? = null
    private var headKey: String? = null
    private var seenHead: String? = null
    private var dirtyAt = 0L
    private var savedAt = -1L
    private var active = true
    private var visibility: ((UiTimelineV2) -> Boolean)? = null
    private var visibilityKey: Any? = null

    fun anchor(): OffsetFromStartPagingKey.Window? =
        (target.value ?: current.value)?.let { OffsetFromStartPagingKey.Window(it.itemKey, sortId) }

    suspend fun restore() {
        val dao = database.pagingTimelineDao()
        generation = dao.getPagingKey(pagingKey)?.generation
        refreshHead()
        val saved = dao.getPosition(tabId)?.takeIf { it.pagingKey == pagingKey }
        if (saved == null) {
            val first = visibleItem(0) ?: return
            sortId = first.sortId
            current.value = TimelineReadingPosition(first.baseItem.stableItemKey, 0.0)
            target.value = current.value
            return
        }
        val row = dao.getRowByItemKey(pagingKey, saved.itemKey)
        val offset = dao.getOffset(pagingKey, row?.sortId ?: saved.sortId)
        val item = nearestVisibleItem(offset) ?: return
        sortId = item.sortId
        val position =
            TimelineReadingPosition(
                item.baseItem.stableItemKey,
                if (item.baseItem.stableItemKey == saved.itemKey) saved.offset else 0.0,
            )
        current.value = position
        target.value = position
        dirtyAt = saved.updatedAt
        savedAt = saved.updatedAt
    }

    fun setVisibility(
        key: Any,
        predicate: (UiTimelineV2) -> Boolean,
    ) {
        if (visibilityKey == key) return
        visibilityKey = key
        visibility = predicate
        if (current.value != null) {
            scope.launch {
                reanchorIfHidden()
                refreshHead()
            }
        }
    }

    private fun TimelinePageItem.isVisible(): Boolean = baseItem !is UiTimelineV2.Gap && visibility?.invoke(baseItem) != false

    private suspend fun nearestVisibleItem(start: Int): TimelinePageItem? {
        val loader = TimelineDbPageLoader(database, pagingKey, TimelineDbPageCache(), windowed = true)
        var before = start
        var after = start
        var reachedEnd = false
        do {
            val next = if (reachedEnd) emptyList() else loader.load(after, 40)
            reachedEnd = next.size < 40
            val previousStart = (before - 40).coerceAtLeast(0)
            val previous = if (before > 0) loader.load(previousStart, before - previousStart) else emptyList()
            val candidates = previous.mapIndexed { i, item -> previousStart + i to item } + next.mapIndexed { i, item -> after + i to item }
            candidates.filter { it.second.isVisible() }.minByOrNull { kotlin.math.abs(it.first - start) }?.let { return it.second }
            before = previousStart
            after += next.size
        } while (before > 0 || !reachedEnd)
        return null
    }

    private suspend fun reanchorIfHidden() {
        val position = target.value ?: current.value ?: return
        val dao = database.pagingTimelineDao()
        val row = dao.getRowByItemKey(pagingKey, position.itemKey)
        val offset = dao.getOffset(pagingKey, row?.sortId ?: sortId)
        val item = nearestVisibleItem(offset)
        if (position != (target.value ?: current.value)) return
        if (item?.baseItem?.stableItemKey == position.itemKey) {
            sortId = item.sortId
            return
        }
        sortId = item?.sortId ?: 0
        current.value = item?.let { TimelineReadingPosition(it.baseItem.stableItemKey, 0.0) }
        target.value = current.value
        navigation.value += 1
    }

    private suspend fun visibleItem(start: Int): TimelinePageItem? {
        val loader = TimelineDbPageLoader(database, pagingKey, TimelineDbPageCache(), windowed = true)
        var offset = start
        while (true) {
            val page = loader.load(offset, 40)
            page.firstOrNull { it.isVisible() }?.let { return it }
            if (page.size < 40) return null
            offset += page.size
        }
    }

    suspend fun refreshHead() {
        reanchorIfHidden()
        val previous = headKey
        val first = visibleItem(0)?.baseItem?.stableItemKey
        headKey = first
        if (seenHead == null) seenHead = first
        if (current.value?.itemKey == first) seenHead = first
        if (first != previous && previous != null && first != current.value?.itemKey) {
            // Removing the old head is not new content.
            val old = database.pagingTimelineDao().getRowByItemKey(pagingKey, previous)
            val new = first?.let { database.pagingTimelineDao().getRowByItemKey(pagingKey, it) }
            if (old != null && new != null && new.sortId < old.sortId) hasNewContent.value = true
        }
        if (first == seenHead) hasNewContent.value = false
        isAtLatest.value = current.value == null || current.value?.itemKey == first
    }

    fun viewportChanged(
        itemKey: String,
        offset: Double,
    ) {
        if (!offset.isFinite() || target.value != null) return
        val position = TimelineReadingPosition(itemKey, offset)
        if (current.value != position) dirtyAt = nextPositionTime()
        if (current.value?.itemKey != itemKey) {
            scope.launch {
                val row = database.pagingTimelineDao().getRowByItemKey(pagingKey, itemKey)
                if (current.value?.itemKey == itemKey && row != null) sortId = row.sortId
            }
        }
        current.value = position
        if (itemKey == headKey) {
            seenHead = itemKey
            hasNewContent.value = false
        }
        isAtLatest.value = itemKey == headKey
    }

    fun positionChanged(
        itemKey: String,
        offset: Double,
    ) {
        if (!offset.isFinite()) return
        target.value = null
        viewportChanged(itemKey, offset)
        dirtyAt = nextPositionTime()
    }

    fun deactivate() {
        active = false
    }

    fun savePosition() {
        if (!active) return
        val position = current.value ?: return
        if (dirtyAt <= savedAt) return
        val timestamp = dirtyAt
        appScope.launch {
            try {
                database.connect {
                    val dao = database.pagingTimelineDao()
                    if (!active || !canSave() || dao.getPagingKey(pagingKey)?.generation != generation) return@connect
                    val row = dao.getRowByItemKey(pagingKey, position.itemKey) ?: return@connect
                    val previous = dao.getPosition(tabId)
                    if (previous != null && previous.updatedAt > timestamp) return@connect
                    dao.insertPosition(DbTimelinePosition(tabId, pagingKey, position.itemKey, row.sortId, position.offset, timestamp))
                }
                savedAt = maxOf(savedAt, timestamp)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error.printStackTrace()
            }
        }
    }

    fun showLatest() {
        val request = dirtyAt
        scope.launch {
            val item = visibleItem(0) ?: return@launch
            if (request != dirtyAt) return@launch
            sortId = item.sortId
            positionChanged(item.baseItem.stableItemKey, 0.0)
            target.value = current.value
            seenHead = item.baseItem.stableItemKey
            hasNewContent.value = false
            isAtLatest.value = true
            navigation.value += 1
        }
    }
}

private val positionTime = kotlin.concurrent.atomics.AtomicLong(0)

private fun nextPositionTime(): Long {
    while (true) {
        val previous = positionTime.load()
        val next = maxOf(previous + 1, Clock.System.now().toEpochMilliseconds() * 1000)
        if (positionTime.compareAndSet(previous, next)) return next
    }
}
