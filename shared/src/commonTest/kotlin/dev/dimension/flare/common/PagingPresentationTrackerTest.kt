package dev.dimension.flare.common

import androidx.paging.ItemSnapshotList
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PagingPresentationTrackerTest {
    @Test
    fun unchangedRefreshKeepsRevisionAndSnapshot() {
        val items = persistentListOf(item("a"), item("b"))
        val tracker = PagingPresentationTracker(initialItems = items)
        val initial = tracker.snapshot

        val refreshed = tracker.refresh(items)

        assertSame(initial, refreshed)
        assertEquals(0L, refreshed.revision)
        assertNull(refreshed.change)
    }

    @Test
    fun contentRefreshOnlyReloadsChangedIdentity() {
        val tracker =
            PagingPresentationTracker(
                initialItems = persistentListOf(item("a"), item("b"), item("c")),
            )

        val refreshed =
            tracker.refresh(
                persistentListOf(item("a"), item("b", renderHash = 2), item("c")),
            )

        val change = requireNotNull(refreshed.change)
        assertEquals(0L, change.baseRevision)
        assertEquals(1L, change.revision)
        assertNull(change.replacement)
        assertEquals(1, change.reloadedCount)
        assertEquals("b", change.reloadedItemAt(0)?.key)
        assertEquals(2, change.reloadedItemAt(0)?.renderHash)
    }

    @Test
    fun topInsertionKeepsStableSuffixOutsideReplacement() {
        val tracker =
            PagingPresentationTracker(
                initialItems = persistentListOf(item("a"), item("b"), item("c")),
            )

        val refreshed =
            tracker.refresh(
                persistentListOf(item("new"), item("a"), item("b"), item("c", renderHash = 2)),
            )

        val change = requireNotNull(refreshed.change)
        val replacement = requireNotNull(change.replacement)
        assertEquals(0, replacement.startIndex)
        assertEquals(0, replacement.removeCount)
        assertEquals(1, replacement.insertedCount)
        assertEquals("new", replacement.insertedItemAt(0)?.key)
        assertEquals(listOf("c"), reloadedKeys(change))
    }

    @Test
    fun appendFastPathOnlyPublishesInsertedPage() {
        val tracker =
            PagingPresentationTracker(
                initialItems = persistentListOf(item("a"), item("b")),
            )
        val mapper = PagingPresentationMapper<String> { item(it) }

        val appended =
            tracker.appendOrRefresh(
                startIndex = 2,
                inserted = listOf(item("c"), item("d")),
                newItems = ItemSnapshotList(0, 0, listOf("a", "b", "c", "d")),
                mapper = mapper,
                hasPlaceholders = false,
            )

        val replacement = requireNotNull(appended.change?.replacement)
        assertEquals(2, replacement.startIndex)
        assertEquals(0, replacement.removeCount)
        assertEquals(listOf("c", "d"), insertedKeys(replacement))
    }

    @Test
    fun prependFastPathOnlyPublishesInsertedPage() {
        val tracker =
            PagingPresentationTracker(
                initialItems = persistentListOf(item("c"), item("d")),
            )
        val mapper = PagingPresentationMapper<String> { item(it) }

        val prepended =
            tracker.prependOrRefresh(
                inserted = listOf(item("a"), item("b")),
                newItems = ItemSnapshotList(0, 0, listOf("a", "b", "c", "d")),
                mapper = mapper,
                hasPlaceholders = false,
            )

        val replacement = requireNotNull(prepended.change?.replacement)
        assertEquals(0, replacement.startIndex)
        assertEquals(0, replacement.removeCount)
        assertEquals(listOf("a", "b"), insertedKeys(replacement))
    }

    @Test
    fun dropFastPathPublishesRemovedRange() {
        val tracker =
            PagingPresentationTracker(
                initialItems = persistentListOf(item("a"), item("b"), item("c"), item("d")),
            )
        val mapper = PagingPresentationMapper<String> { item(it) }

        val dropped =
            tracker.removeOrRefresh(
                startIndex = 2,
                removeCount = 2,
                newItems = ItemSnapshotList(0, 0, listOf("a", "b")),
                mapper = mapper,
                hasPlaceholders = false,
            )

        val replacement = requireNotNull(dropped.change?.replacement)
        assertEquals(2, replacement.startIndex)
        assertEquals(2, replacement.removeCount)
        assertEquals(0, replacement.insertedCount)
    }

    @Test
    fun consecutiveChangesFormRevisionChain() {
        val tracker = PagingPresentationTracker(initialItems = persistentListOf(item("a")))
        val mapper = PagingPresentationMapper<String> { item(it) }

        val appended =
            tracker.appendOrRefresh(
                startIndex = 1,
                inserted = listOf(item("b")),
                newItems = ItemSnapshotList(0, 0, listOf("a", "b")),
                mapper = mapper,
                hasPlaceholders = false,
            )
        val refreshed = tracker.refresh(persistentListOf(item("a", renderHash = 2), item("b")))

        assertEquals(0L, appended.change?.baseRevision)
        assertEquals(1L, appended.revision)
        assertEquals(1L, refreshed.change?.baseRevision)
        assertEquals(2L, refreshed.revision)
        assertEquals(listOf("a"), reloadedKeys(requireNotNull(refreshed.change)))
    }

    @Test
    fun invalidAppendHintFallsBackToSnapshotDiff() {
        val tracker =
            PagingPresentationTracker(
                initialItems = persistentListOf(item("a"), item("b")),
            )
        val mapper = PagingPresentationMapper<String> { item(it) }

        val refreshed =
            tracker.appendOrRefresh(
                startIndex = 99,
                inserted = listOf(item("c")),
                newItems = ItemSnapshotList(0, 0, listOf("a", "b", "c")),
                mapper = mapper,
                hasPlaceholders = false,
            )

        val replacement = requireNotNull(refreshed.change?.replacement)
        assertEquals(2, replacement.startIndex)
        assertEquals(listOf("c"), insertedKeys(replacement))
    }
}

private fun item(
    key: String,
    renderHash: Int = 1,
): PagingPresentationItem = PagingPresentationItem(key = key, renderHash = renderHash)

private fun insertedKeys(replacement: PagingPresentationReplacement): List<String?> =
    List(replacement.insertedCount) { replacement.insertedItemAt(it)?.key }

private fun reloadedKeys(change: PagingPresentationChange): List<String?> = List(change.reloadedCount) { change.reloadedItemAt(it)?.key }
