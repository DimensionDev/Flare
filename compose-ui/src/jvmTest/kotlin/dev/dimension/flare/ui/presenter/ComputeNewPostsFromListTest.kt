package dev.dimension.flare.ui.presenter

import dev.dimension.flare.ui.model.UiTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

class ComputeNewPostsFromListTest {
    private fun makeItem(key: String): UiTimeline = UiTimeline(topMessage = null, content = null, dbKey = key)

    @Test
    fun noLastViewedInNewPage_doesNotShowIndicator() {
        val previousTopKey = makeItem("post-100").itemKey
        val refreshed = listOf(makeItem("post-200"), makeItem("post-199"), makeItem("post-198"))

        val (show, count, keys) = computeNewPostsFromList(previousTopKey, 3, refreshed, isAtTheTop = true)

        assertFalse(show)
        assertEquals(0, count)
        assertEquals(0, keys.size)
    }

    @Test
    fun lastViewedInNewPage_showsIndicator() {
        val refreshed = listOf(makeItem("new-2"), makeItem("new-1"), makeItem("post-42"), makeItem("post-41"))
        val previousTopKey = refreshed[2].itemKey

        val (show, count, keys) = computeNewPostsFromList(previousTopKey, 1, refreshed, isAtTheTop = false)

        assertTrue(show)
        assertEquals(2, count)
        assertEquals(2, keys.size)
    }

    @Test
    fun lastViewedInNewPage_showsCorrectCount() {
        val refreshed = (1..5).map { makeItem("new-$it") } + listOf(makeItem("orig-1"))
        val previousTopKey = refreshed.last().itemKey

        val (show, count, _) = computeNewPostsFromList(previousTopKey, 1, refreshed, isAtTheTop = false)

        assertTrue(show)
        assertEquals(5, count)
    }

    @Test
    fun newPostsCount_decreaseOnScroll() {
        // as the user scrolls up through new posts
        // the New Posts count will decrease

        val refreshed = (1..5).map { makeItem("new-$it") } + listOf(makeItem("orig-1"))
        val previousTopKey = refreshed.last().itemKey

        val (show, insertedPostCount, _) = computeNewPostsFromList(previousTopKey, 1, refreshed, isAtTheTop = false)
        assertTrue(show)
        assertEquals(5, insertedPostCount)

        // Simulate old index before refresh
        val oldIndex = 0

        // Simulate observed UI index that already includes insertedPostCount items: observed = old + insertedPostCount
        val observedFirstVisibleIndex = oldIndex + insertedPostCount

        // Use the production helper to derive the baseline index (what the presenter will use)
        val derivedLastRefreshIndex = deriveLastRefreshIndex(observedFirstVisibleIndex, insertedPostCount)

        // Now simulate the user scrolling 1 item toward the top (firstVisibleIndex decreases by 1)
        val newFirstVisibleIndex = observedFirstVisibleIndex - 1

        // Presenter's calc with production-derived baseline: calc = firstVisibleIndex - derivedBaseline
        val calc = newFirstVisibleIndex - derivedLastRefreshIndex
        // Presenter's visible count update (mirrors production): it will set newPostsCount = min(calc, insertedPostCount)
        val visibleCountAfterScroll = min(calc, insertedPostCount)

        // Expectation (what should happen in correct logic): visible count should be insertedPostCount - 1
        val expected = insertedPostCount - 1

        // Now assert production-driven behavior decreases as the user scrolls
        assertEquals(expected, visibleCountAfterScroll)
    }

    @Test
    fun deriveLastRefreshIndex_recoversLastViewed() {
        // pre-refresh index should be zero (refresh occurs at top),
        // observed has shifted by the insertedPostCount items,
        // deriveLastRefreshIndex should recover last viewed index.
        val lastViewedIndex = 0
        val insertedPostCount = 5
        val observedFirstVisibleIndex = lastViewedIndex + insertedPostCount
        val derived = deriveLastRefreshIndex(observedFirstVisibleIndex, insertedPostCount)
        assertEquals(lastViewedIndex, derived)
    }

    @Test
    fun deriveLastRefreshIndex_noLastViewedInNewPage() {
        // when the number of fetched posts is so large
        // that the last-viewed post is not present in them
        // then we can't maintain position and the helper should
        // return `observedFirstVisibleIndex` as a conservative, in-bounds baseline.
        val observedFirstVisibleIndex = 5
        val insertedPostCount = 500
        val derived = deriveLastRefreshIndex(observedFirstVisibleIndex, insertedPostCount)
        assertEquals(observedFirstVisibleIndex, derived)
    }
}
