package dev.dimension.flare.ui.model

import dev.dimension.flare.common.PagingState
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class TimelineSnapshotTest {
    @Test
    fun queriesBuildOneLazyIndexAndKeepTheFirstDuplicate() {
        for (size in listOf(240, 2400, 24000)) {
            val rows = CountingList((0 until size).map(::post).toImmutableList())
            val snapshot = TimelineSnapshot(PagingState.Success.ImmutableSuccess(rows))
            assertEquals(0, rows.reads)
            assertEquals("post-0", snapshot.firstReadingKey)
            assertEquals(size, rows.reads)
            repeat(100) {
                assertEquals("post-0", snapshot.firstReadingKey)
                assertEquals(size - 1, snapshot.readingIndexOf("post-${size - 1}"))
            }
            assertEquals(size, rows.reads)
        }
        assertEquals(0, snapshot(post(0), post(1), post(0)).noticeIndexOf("post-0"))
    }

    @Test
    fun oldAndNewSnapshotsKeepTheirOwnFilteredIndices() {
        val old = snapshot(post(4), post(5), post(6))
        val current = snapshot(post(1), gap(), post(4), post(6))
        assertEquals(0, old.noticeIndexOf("post-4"))
        assertEquals(1, old.noticeIndexOf("post-5"))
        assertEquals(2, current.noticeIndexOf("post-4"))
        assertEquals(-1, current.noticeIndexOf("post-5"))
        assertEquals(-1, current.readingIndexOf("reading-gap:1"))
        assertEquals("post-1", current.firstReadingKey)
    }

    @Test
    fun itemsWithoutNoticeKeysRemainReadableButDoNotConsumeTheNotice() {
        val item = post(0).copy(itemKey = null)
        val snapshot = snapshot(item, post(1))
        assertEquals(0, snapshot.readingIndexOf(item.stableItemKey))
        assertEquals(-1, snapshot.noticeIndexOf(item.stableItemKey))
        assertEquals(1, snapshot.noticeIndexOf("post-1"))
        assertEquals(listOf("post-1"), snapshot.noticeKeys)
    }

    @Test
    fun restorationWaitsForPublishedHeadAndRefreshCompletion() {
        val position = TimelineReadingPosition("post-4", -37.25, 1, latest = true)
        val old = snapshot(post(3), post(4))
        val current = snapshot(post(4), post(3))
        assertEquals(-1, old.restorationIndex(position, refreshing = false))
        val leadingGap = snapshot(gap(), post(4))
        assertEquals("post-4", leadingGap.firstReadingKey)
        assertEquals(-1, leadingGap.restorationIndex(position, refreshing = false))
        assertEquals(-1, current.restorationIndex(position, refreshing = true))
        assertEquals(0, current.restorationIndex(position, refreshing = false))
        assertEquals(1, old.restorationIndex(position.copy(latest = false), refreshing = false))
        assertEquals(-1, current.restorationIndex(position.copy(itemKey = "missing"), refreshing = false))
        assertFalse(TimelineReadingPosition("reading-gap:1", 0.0, 2).canRestore(0, false))
    }

    @Test
    fun contentChangesIncludingGapLabelsAreNotHiddenByStableKeys() {
        val first = snapshot(post(1), gap())
        assertTrue(first.hasSameContent(snapshot(post(1), gap())))
        assertFalse(first.hasSameContent(snapshot(post(1), gap(loading = true))))
        assertFalse(first.hasSameContent(snapshot(post(1).copy(title = "edited"), gap())))
        assertFalse(first.hasSameContent(snapshot(gap(), post(1))))
    }

    private fun snapshot(vararg rows: UiTimelineV2) =
        TimelineSnapshot(PagingState.Success.ImmutableSuccess(rows.toList().toImmutableList()))

    private fun post(index: Int) =
        UiTimelineV2.Feed(
            title = "post-$index",
            description = null,
            url = "https://example.com/$index",
            createdAt = Instant.fromEpochMilliseconds(0).toUi(),
            source = UiTimelineV2.Feed.Source("test", null),
            accountType = AccountType.Guest,
            itemKey = "post-$index",
        )

    private fun gap(loading: Boolean = false) =
        UiTimelineV2.Message(
            statusKey = MicroBlogKey("gap", "test"),
            icon = UiIcon.Info,
            type =
                UiTimelineV2.Message.Type.Localized(
                    if (loading) {
                        UiTimelineV2.Message.Type.Localized.MessageId.ReadingGapLoading
                    } else {
                        UiTimelineV2.Message.Type.Localized.MessageId.ReadingGap
                    },
                ),
            createdAt = Instant.fromEpochMilliseconds(0).toUi(),
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Guest,
            itemKey = "reading-gap:1",
        )

    private class CountingList(
        private val rows: ImmutableList<UiTimelineV2>,
    ) : ImmutableList<UiTimelineV2> by rows {
        var reads = 0

        override fun get(index: Int): UiTimelineV2 = rows[index].also { reads++ }
    }
}
