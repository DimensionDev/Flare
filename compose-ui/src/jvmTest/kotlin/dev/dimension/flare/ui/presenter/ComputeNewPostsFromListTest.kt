package dev.dimension.flare.ui.presenter

import dev.dimension.flare.ui.model.UiTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

        val (show, count, keys) = computeNewPostsFromList(previousTopKey, 1, refreshed, isAtTheTop = false)

        assertTrue(show)
        assertEquals(5, count)
    }
}
