package dev.dimension.flare.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.paging.CombinedLoadStates
import androidx.paging.ItemSnapshotList
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** Lightweight item information used by non-Compose paging front ends. */
@Immutable
public data class PagingPresentationItem internal constructor(
    public val key: String?,
    public val renderHash: Int,
)

/** A single contiguous replacement that transforms one presented list into the next. */
@Immutable
public class PagingPresentationReplacement internal constructor(
    public val startIndex: Int,
    public val removeCount: Int,
    private val insertedItems: List<PagingPresentationItem>,
) {
    public val insertedCount: Int
        get() = insertedItems.size

    public fun insertedItemAt(index: Int): PagingPresentationItem? = insertedItems.getOrNull(index)
}

/** Changes between two consecutive [PagingPresentationSnapshot] revisions. */
@Immutable
public class PagingPresentationChange internal constructor(
    public val baseRevision: Long,
    public val revision: Long,
    public val replacement: PagingPresentationReplacement?,
    private val reloadedItems: List<PagingPresentationItem>,
) {
    public val reloadedCount: Int
        get() = reloadedItems.size

    public fun reloadedItemAt(index: Int): PagingPresentationItem? = reloadedItems.getOrNull(index)
}

/**
 * Versioned, lightweight mirror of the list presented by Paging.
 *
 * UIKit normally consumes [change]. If it misses a revision it can rebuild from this snapshot
 * without traversing the full Kotlin object graph through [PagingState.Success.peek].
 */
@Immutable
public class PagingPresentationSnapshot internal constructor(
    public val revision: Long,
    private val presentedItems: List<PagingPresentationItem>,
    public val change: PagingPresentationChange?,
) {
    public val itemCount: Int
        get() = presentedItems.size

    public fun itemAt(index: Int): PagingPresentationItem? = presentedItems.getOrNull(index)
}

internal fun interface PagingPresentationMapper<T : Any> {
    fun map(item: T): PagingPresentationItem
}

/**
 * Paging front end that retains the existing get/peek/load-state behavior while also exposing
 * [PagingDataEvent] as a versioned presentation change set.
 */
@Stable
internal class EventAwarePagingItems<T : Any>(
    private val flow: Flow<PagingData<T>>,
    private val mapper: PagingPresentationMapper<T>,
) {
    private val pagingDataPresenter =
        object :
            PagingDataPresenter<T>(
                cachedPagingData =
                    if (flow is SharedFlow<PagingData<T>>) {
                        flow.replayCache.firstOrNull()
                    } else {
                        null
                    },
            ) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<T>) {
                updateItems(event)
            }
        }

    private val initialItems = pagingDataPresenter.snapshot()
    private val presentationTracker =
        PagingPresentationTracker(
            initialItems = initialItems.toPresentationItems(mapper),
        )

    var itemState: EventAwarePagingItemState<T> by
        mutableStateOf(
            EventAwarePagingItemState(
                items = initialItems,
                presentation = presentationTracker.snapshot,
            ),
        )
        private set

    var loadState: CombinedLoadStates by
        mutableStateOf(
            pagingDataPresenter.loadStateFlow.value
                ?: CombinedLoadStates(
                    refresh = InitialEventAwareLoadStates.refresh,
                    prepend = InitialEventAwareLoadStates.prepend,
                    append = InitialEventAwareLoadStates.append,
                    source = InitialEventAwareLoadStates,
                ),
        )
        private set

    fun access(index: Int) {
        if (index in 0 until pagingDataPresenter.size) {
            pagingDataPresenter[index]
        }
    }

    fun retry() {
        pagingDataPresenter.retry()
    }

    fun refresh() {
        pagingDataPresenter.refresh()
    }

    suspend fun refreshSuspend() {
        refresh()
        snapshotFlow { loadState }
            .distinctUntilChanged()
            .first { it.refresh is LoadState.Loading }
        snapshotFlow { loadState }
            .distinctUntilChanged()
            .first { it.refresh is LoadState.NotLoading || it.refresh is LoadState.Error }
    }

    suspend fun collectPagingData() {
        flow.collectLatest(pagingDataPresenter::collectFrom)
    }

    suspend fun collectLoadState() {
        pagingDataPresenter.loadStateFlow
            .filterNotNull()
            .collect { loadState = it }
    }

    private fun updateItems(event: PagingDataEvent<T>) {
        val items = pagingDataPresenter.snapshot()
        val presentation =
            when (event) {
                is PagingDataEvent.Append -> {
                    presentationTracker.appendOrRefresh(
                        startIndex = event.startIndex,
                        inserted = event.inserted.map(mapper::map),
                        newItems = items,
                        mapper = mapper,
                        hasPlaceholders =
                            event.oldPlaceholdersAfter != 0 ||
                                event.newPlaceholdersAfter != 0 ||
                                items.placeholdersBefore != 0,
                    )
                }

                is PagingDataEvent.Prepend -> {
                    presentationTracker.prependOrRefresh(
                        inserted = event.inserted.map(mapper::map),
                        newItems = items,
                        mapper = mapper,
                        hasPlaceholders =
                            event.oldPlaceholdersBefore != 0 ||
                                event.newPlaceholdersBefore != 0 ||
                                items.placeholdersAfter != 0,
                    )
                }

                is PagingDataEvent.DropAppend -> {
                    presentationTracker.removeOrRefresh(
                        startIndex = event.startIndex,
                        removeCount = event.dropCount,
                        newItems = items,
                        mapper = mapper,
                        hasPlaceholders =
                            event.oldPlaceholdersAfter != 0 ||
                                event.newPlaceholdersAfter != 0 ||
                                items.placeholdersBefore != 0,
                    )
                }

                is PagingDataEvent.DropPrepend -> {
                    presentationTracker.removeOrRefresh(
                        startIndex = 0,
                        removeCount = event.dropCount,
                        newItems = items,
                        mapper = mapper,
                        hasPlaceholders =
                            event.oldPlaceholdersBefore != 0 ||
                                event.newPlaceholdersBefore != 0 ||
                                items.placeholdersAfter != 0,
                    )
                }

                is PagingDataEvent.Refresh -> {
                    presentationTracker.refresh(items.toPresentationItems(mapper))
                }
            }
        itemState = EventAwarePagingItemState(items = items, presentation = presentation)
    }
}

internal data class EventAwarePagingItemState<T : Any>(
    val items: ItemSnapshotList<T>,
    val presentation: PagingPresentationSnapshot,
)

@Composable
internal fun <T : Any> Flow<PagingData<T>>.collectAsEventAwarePagingItems(mapper: PagingPresentationMapper<T>): EventAwarePagingItems<T> {
    val pagingItems = remember(this) { EventAwarePagingItems(flow = this, mapper = mapper) }
    LaunchedEffect(pagingItems) {
        pagingItems.collectPagingData()
    }
    LaunchedEffect(pagingItems) {
        pagingItems.collectLoadState()
    }
    return pagingItems
}

private fun <T : Any> ItemSnapshotList<T>.toPresentationItems(
    mapper: PagingPresentationMapper<T>,
): PersistentList<PagingPresentationItem> {
    val result = persistentListOf<PagingPresentationItem>().builder()
    with(result) {
        repeat(placeholdersBefore) {
            add(PlaceholderPresentationItem)
        }
        items.forEach { add(mapper.map(it)) }
        repeat(placeholdersAfter) {
            add(PlaceholderPresentationItem)
        }
    }
    return result.build()
}

internal class PagingPresentationTracker(
    initialItems: PersistentList<PagingPresentationItem> = persistentListOf(),
) {
    private var items = initialItems
    private var revision = 0L

    var snapshot: PagingPresentationSnapshot =
        PagingPresentationSnapshot(
            revision = revision,
            presentedItems = items,
            change = null,
        )
        private set

    fun <T : Any> appendOrRefresh(
        startIndex: Int,
        inserted: List<PagingPresentationItem>,
        newItems: ItemSnapshotList<T>,
        mapper: PagingPresentationMapper<T>,
        hasPlaceholders: Boolean,
    ): PagingPresentationSnapshot =
        if (
            !hasPlaceholders &&
            startIndex == items.size &&
            newItems.size == items.size + inserted.size
        ) {
            replace(startIndex = startIndex, removeCount = 0, inserted = inserted)
        } else {
            refresh(newItems.toPresentationItems(mapper))
        }

    fun <T : Any> prependOrRefresh(
        inserted: List<PagingPresentationItem>,
        newItems: ItemSnapshotList<T>,
        mapper: PagingPresentationMapper<T>,
        hasPlaceholders: Boolean,
    ): PagingPresentationSnapshot =
        if (!hasPlaceholders && newItems.size == items.size + inserted.size) {
            replace(startIndex = 0, removeCount = 0, inserted = inserted)
        } else {
            refresh(newItems.toPresentationItems(mapper))
        }

    fun <T : Any> removeOrRefresh(
        startIndex: Int,
        removeCount: Int,
        newItems: ItemSnapshotList<T>,
        mapper: PagingPresentationMapper<T>,
        hasPlaceholders: Boolean,
    ): PagingPresentationSnapshot =
        if (
            !hasPlaceholders &&
            startIndex >= 0 &&
            removeCount >= 0 &&
            startIndex + removeCount <= items.size &&
            newItems.size == items.size - removeCount
        ) {
            replace(startIndex = startIndex, removeCount = removeCount, inserted = emptyList())
        } else {
            refresh(newItems.toPresentationItems(mapper))
        }

    fun refresh(nextItems: PersistentList<PagingPresentationItem>): PagingPresentationSnapshot {
        val previousItems = items
        var prefixSize = 0
        val sharedSize = minOf(previousItems.size, nextItems.size)
        while (
            prefixSize < sharedSize &&
            previousItems[prefixSize].sameIdentityAs(nextItems[prefixSize])
        ) {
            prefixSize++
        }

        var suffixSize = 0
        while (
            suffixSize < sharedSize - prefixSize &&
            previousItems[previousItems.lastIndex - suffixSize]
                .sameIdentityAs(nextItems[nextItems.lastIndex - suffixSize])
        ) {
            suffixSize++
        }

        val previousChangedEnd = previousItems.size - suffixSize
        val nextChangedEnd = nextItems.size - suffixSize
        val replacement =
            if (prefixSize != previousChangedEnd || prefixSize != nextChangedEnd) {
                PagingPresentationReplacement(
                    startIndex = prefixSize,
                    removeCount = previousChangedEnd - prefixSize,
                    insertedItems = nextItems.subList(prefixSize, nextChangedEnd),
                )
            } else {
                null
            }

        val reloaded =
            buildList {
                for (index in 0 until prefixSize) {
                    addIfChanged(previousItems[index], nextItems[index])
                }
                for (offset in 0 until suffixSize) {
                    val previous = previousItems[previousItems.lastIndex - offset]
                    val next = nextItems[nextItems.lastIndex - offset]
                    addIfChanged(previous, next)
                }
            }
        return commit(nextItems, replacement, reloaded)
    }

    private fun replace(
        startIndex: Int,
        removeCount: Int,
        inserted: List<PagingPresentationItem>,
    ): PagingPresentationSnapshot {
        if (removeCount == 0 && inserted.isEmpty()) {
            return snapshot
        }
        val nextItems =
            items.builder().run {
                repeat(removeCount) {
                    removeAt(startIndex)
                }
                addAll(startIndex, inserted)
                build()
            }
        return commit(
            nextItems = nextItems,
            replacement =
                PagingPresentationReplacement(
                    startIndex = startIndex,
                    removeCount = removeCount,
                    insertedItems = inserted,
                ),
            reloadedItems = emptyList(),
        )
    }

    private fun commit(
        nextItems: PersistentList<PagingPresentationItem>,
        replacement: PagingPresentationReplacement?,
        reloadedItems: List<PagingPresentationItem>,
    ): PagingPresentationSnapshot {
        if (replacement == null && reloadedItems.isEmpty()) {
            return snapshot
        }
        val baseRevision = revision
        revision++
        items = nextItems
        snapshot =
            PagingPresentationSnapshot(
                revision = revision,
                presentedItems = items,
                change =
                    PagingPresentationChange(
                        baseRevision = baseRevision,
                        revision = revision,
                        replacement = replacement,
                        reloadedItems = reloadedItems,
                    ),
            )
        return snapshot
    }

    private fun MutableList<PagingPresentationItem>.addIfChanged(
        previous: PagingPresentationItem,
        next: PagingPresentationItem,
    ) {
        if (previous.renderHash != next.renderHash) {
            add(next)
        }
    }
}

private fun PagingPresentationItem.sameIdentityAs(other: PagingPresentationItem): Boolean = key == other.key

private val PlaceholderPresentationItem = PagingPresentationItem(key = null, renderHash = 0)

private val IncompleteEventAwareLoadState = LoadState.NotLoading(endOfPaginationReached = false)
private val InitialEventAwareLoadStates =
    LoadStates(
        refresh = LoadState.Loading,
        prepend = IncompleteEventAwareLoadState,
        append = IncompleteEventAwareLoadState,
    )
