package dev.dimension.flare.ui.component

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class MediaPlaybackMemoryTest {
    @Test
    fun progressSurvivesPageClosureAndRewindsReplaceThePreviousPosition() =
        runTest {
            val memory = MediaPlaybackMemory()
            val first = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), memory)
            first.savePosition("video", 37.0)
            first.close()
            val second = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), memory)
            assertEquals(37.0, second.position("video"))
            second.savePosition("video", 12.0)
            second.savePosition("video", Double.NaN)
            assertEquals(12.0, memory.position("video"))
            second.savePosition("video", 0.0)
            assertEquals(0.0, memory.position("video"))
            assertEquals(0.0, MediaPlaybackMemory().position("video"))
            second.close()
        }

    @Test
    fun returnSelectionSurvivesUnmountAndDoesNotChangeOtherCollections() {
        val selections = TimelineMediaSelections()
        var selected = "a"
        selections.register("first", listOf("a", "b")) { selected = it }
        selections.register("other", listOf("c", "d")) { fail("Unrelated collection") }
        selections.remove("first")
        selections.returned(listOf("a", "b"), "b")
        selections.register("remounted", listOf("a", "b")) { selected = it }
        assertEquals("b", selected)
        selections.returned(listOf("a", "b"), "c")
        assertEquals("b", selected)
        selected = "a"
        selections.remove("remounted")
        selections.register("again", listOf("a", "b")) { selected = it }
        assertEquals("a", selected, "An applied return must not override a later user selection")
    }

    @Test
    fun viewerSavesAndReturnsSelectionToItsOriginBeforeAutoplayResumes() {
        val arbiter = VideoPlaybackArbiter()
        val timeline = Any()
        val other = Any()
        val viewer = Any()
        val events = mutableListOf<String>()
        arbiter.register(timeline, {}, { events += "resume" }, { _, uri -> events += uri })
        arbiter.register(other, {}, {}, { _, _ -> fail("Wrong timeline") })
        arbiter.register(viewer, { events += "save-progress" }, {})
        arbiter.interacted(timeline)
        arbiter.present(viewer)
        assertTrue(arbiter.acquire(viewer))
        arbiter.remove(viewer, listOf("a", "b"), "b")
        assertEquals(listOf("save-progress", "b", "resume"), events)
        assertFalse(arbiter.acquire(other))
        assertTrue(arbiter.acquire(timeline))
    }

    @Test
    fun returnedVideoWaitsForVisibilityAndSelectedImageBlocksFallback() {
        val policy = TimelineAutoplayPolicy()
        val a = TimelineAutoplayPolicy.Candidate("a", "post", visible = true, canStart = true, distance = 0f, mediaUri = "a")
        val b =
            TimelineAutoplayPolicy.Candidate(
                "b",
                "post",
                visible = true,
                selected = false,
                canStart = false,
                distance = 20f,
                mediaUri = "b",
            )
        policy.returnedToMedia("post", "b")
        assertNull(policy.select(listOf(a, b), scrolling = false))
        assertEquals("b", policy.select(listOf(a, b.copy(canStart = true)), scrolling = false))
        policy.returnedToMedia("post", "image")
        assertNull(policy.select(listOf(a, b.copy(canStart = true)), scrolling = false))
        policy.verticalScrollBegan()
        assertEquals("a", policy.select(listOf(a, b), scrolling = false))
    }
}
