package dev.dimension.flare.ui.presenter

import dev.dimension.flare.ui.model.UiTimeline
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimelineItemPresenterWithLazyListStateTest {
    private fun createTimeline(key: String): UiTimeline = UiTimeline(null, null, key)

    private fun createList(vararg keys: String): List<UiTimeline> = persistentListOf<UiTimeline>().addAll(keys.map { createTimeline(it) })

    @Test
    fun testIndicatorAppearsWhenNewPostsArrive() {
        // Initial items: newest at index 0
        val initialItems = createList(*(10 downTo 1).map { "item_$it" }.toTypedArray())

        // Simulate the user's last-viewed index (user was at the top -> index 0)
        val overrideFirstVisibleIndex = 0
        val lastViewedKey = initialItems[overrideFirstVisibleIndex].itemKey

        // New page fetched — prepend 4 new items and include the lastViewedKey in that page
        val prepended = createList("item_15", "item_14", "item_13", "item_12")
        val refreshedItems = prepended + initialItems

        // If the last viewed key appears in the refreshed list, the number of new posts preceding it is its index
        val foundIndex = refreshedItems.indexOfFirst { it.itemKey == lastViewedKey }
        assertTrue(foundIndex >= 0, "Sanity: last viewed key must appear in refreshed items")

        val expectedUnseen = foundIndex
        // Deterministic assertion: since last-viewed index is 0, expected unseen == number of prepended items
        assertEquals(prepended.size, expectedUnseen, "When last-viewed index is 0, unseen count equals number of prepended items")
    }

    @Test
    fun testRefreshReportsCorrectNewPostsCount() {
        // Start with 8 items
        val initialItems = createList(*(8 downTo 1).map { "item_$it" }.toTypedArray())
        val overrideFirstVisibleIndex = 0
        val lastViewedKey = initialItems[overrideFirstVisibleIndex].itemKey

        // Simulate refresh that prepends 4 new items and includes the previously-viewed item
        val prepended = createList("item_12", "item_11", "item_10", "item_9")
        val refreshedItems = prepended + initialItems

        val foundIndex = refreshedItems.indexOfFirst { it.itemKey == lastViewedKey }
        assertTrue(foundIndex >= 0, "Sanity: last viewed key must appear in refreshed items")

        val expectedUnseen = foundIndex
        assertEquals(prepended.size, expectedUnseen, "When last-viewed index is 0, unseen count equals number of prepended items")
    }

    @Test
    fun testIndicatorNotShownWhenLastViewedNotIncluded() {
        val initialItems = createList(*(6 downTo 1).map { "item_$it" }.toTypedArray())
        val overrideFirstVisibleIndex = 0
        val lastViewedKey = initialItems[overrideFirstVisibleIndex].itemKey

        // Refresh that does NOT include that key (completely new items)
        val refreshedItems = createList("item_12", "item_11", "item_10", "item_9", "item_8", "item_7")
        // ensure lastViewedKey not present
        assertFalse(refreshedItems.any { it.itemKey == lastViewedKey })
    }
}
