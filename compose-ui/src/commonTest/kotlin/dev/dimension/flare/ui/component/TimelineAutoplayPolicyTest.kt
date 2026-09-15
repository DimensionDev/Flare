package dev.dimension.flare.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimelineAutoplayPolicyTest {
    private fun video(
        id: String,
        group: String? = null,
        visible: Boolean = true,
        selected: Boolean = true,
        canStart: Boolean = true,
        distance: Float = 0f,
    ) = TimelineAutoplayPolicy.Candidate(id, group, visible, selected, canStart, distance)

    @Test
    fun scrollRetainsVisiblePlayerWithoutStartingAnother() {
        val policy = TimelineAutoplayPolicy()
        assertEquals("a", policy.select(listOf(video("a")), scrolling = false))
        val partial = video("a", canStart = false, distance = 500f)
        assertEquals("a", policy.select(listOf(partial, video("b")), scrolling = true))
        assertEquals("a", policy.select(listOf(partial, video("b")), scrolling = false))
        assertNull(policy.select(listOf(video("a", visible = false), video("b")), scrolling = true))
        assertEquals("b", policy.select(listOf(video("b")), scrolling = false))
    }

    @Test
    fun noNewPlayerStartsDuringScrollingOrBelowStartThreshold() {
        val policy = TimelineAutoplayPolicy()
        assertNull(policy.select(listOf(video("a")), scrolling = true))
        assertNull(policy.select(listOf(video("a", canStart = false)), scrolling = false))
        assertEquals("a", policy.select(listOf(video("a")), scrolling = false))
    }

    @Test
    fun userCarouselChoiceTakesOverOnlyAfterScrollingStops() {
        val policy = TimelineAutoplayPolicy()
        val candidates = listOf(video("a", group = "first"), video("b", group = "second", distance = 100f))
        assertEquals("a", policy.select(candidates, scrolling = false))
        policy.interactWithCarousel("second")
        assertEquals("a", policy.select(candidates, scrolling = true))
        assertEquals("b", policy.select(candidates, scrolling = false))
    }

    @Test
    fun selectedImageKeepsTimelineQuietUntilNextVerticalInteraction() {
        val policy = TimelineAutoplayPolicy()
        val a = video("a", group = "first")
        val unselected = video("b", group = "second", selected = false)
        assertEquals("a", policy.select(listOf(a), scrolling = false))
        policy.interactWithCarousel("second")
        assertEquals("a", policy.select(listOf(a, unselected), scrolling = true))
        assertNull(policy.select(listOf(a, unselected), scrolling = false))
        assertNull(policy.select(listOf(a, unselected, video("c")), scrolling = false))
        policy.verticalScrollBegan()
        assertNull(policy.select(listOf(a), scrolling = true))
        assertEquals("a", policy.select(listOf(a), scrolling = false))
    }

    @Test
    fun selectedImageInCurrentCarouselStopsVisibleSliver() {
        val policy = TimelineAutoplayPolicy()
        assertEquals("a", policy.select(listOf(video("a", group = "row")), scrolling = false))
        policy.interactWithCarousel("row")
        val sliver = video("a", group = "row", selected = false, canStart = false)
        assertEquals("a", policy.select(listOf(sliver), scrolling = true))
        assertNull(policy.select(listOf(sliver), scrolling = false))
    }

    @Test
    fun unselectedNeighborNeverStartsAndUnavailableChoiceDoesNotFallBack() {
        val policy = TimelineAutoplayPolicy()
        assertNull(policy.select(listOf(video("neighbor", selected = false)), scrolling = false))
        policy.interactWithCarousel("row")
        assertNull(policy.select(listOf(video("other"), video("chosen", group = "row", canStart = false)), scrolling = false))
        assertEquals("chosen", policy.select(listOf(video("other"), video("chosen", group = "row")), scrolling = false))
        assertNull(policy.select(listOf(video("chosen", group = "row", visible = false)), scrolling = true))
    }

    @Test
    fun automaticSelectionUsesClosestCandidateAndRetainsItOnTies() {
        val policy = TimelineAutoplayPolicy()
        assertEquals("near", policy.select(listOf(video("far", distance = 100f), video("near", distance = 5f)), scrolling = false))
        assertEquals("near", policy.select(listOf(video("far", distance = 5f), video("near", distance = 5f)), scrolling = false))
    }
}
