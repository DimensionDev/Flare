package dev.dimension.flare.ui.component

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimelinePlaybackCoordinatorTest {
    @Test
    fun returningToVisibleIdleVideoSkipsDebounceButNeverStartsDuringScroll() =
        runTest {
            val arbiter = VideoPlaybackArbiter()
            val timeline = TimelinePlaybackCoordinator(this, arbiter)
            val events = mutableListOf<Boolean>()
            timeline.register("video") { events += it }
            timeline.update(TimelineAutoplayPolicy.Candidate("video", visible = true, canStart = true, distance = 0f))
            advanceTimeBy(200)
            runCurrent()
            val viewer = TimelinePlaybackCoordinator(this, arbiter)
            viewer.present()
            assertEquals(listOf(true, false), events)
            viewer.close()
            assertEquals(listOf(true, false, true), events)
            val secondViewer = TimelinePlaybackCoordinator(this, arbiter)
            secondViewer.present()
            timeline.setScrolling("vertical", scrolling = true, vertical = true)
            secondViewer.close()
            assertEquals(listOf(true, false, true, false), events)
            timeline.close()
        }

    @Test
    fun topVideoViewerResumesAfterImageViewerClosesDespiteBackgroundInteraction() =
        runTest {
            val arbiter = VideoPlaybackArbiter()
            val video = TimelinePlaybackCoordinator(this, arbiter)
            val image = TimelinePlaybackCoordinator(this, arbiter)
            val events = mutableListOf<Boolean>()
            video.register("video") { events += it }
            video.present()
            video.update(TimelineAutoplayPolicy.Candidate("video", visible = true, canStart = true, distance = 0f))
            advanceTimeBy(200)
            runCurrent()
            image.present()
            arbiter.interacted(Any())
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf(true, false), events)
            image.close()
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf(true, false, true), events)
            video.close()
        }

    @Test
    fun nestedScrollWaitsForBothAxesThenStopsOldPlayerBeforeStartingNew() =
        runTest {
            val playback = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter())
            val events = mutableListOf<String>()
            val playing = mutableSetOf<String>()
            for (id in listOf("a", "b")) {
                playback.register(id) { active ->
                    events += "$id:$active"
                    if (active) playing.add(id) else playing.remove(id)
                    assertTrue(playing.size <= 1)
                }
            }
            playback.update(TimelineAutoplayPolicy.Candidate("a", "first", true, canStart = true, distance = 0f))
            playback.update(TimelineAutoplayPolicy.Candidate("b", "second", true, canStart = true, distance = 10f))
            advanceTimeBy(199)
            runCurrent()
            assertTrue(events.isEmpty())
            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf("a:true"), events)

            playback.setScrolling("vertical", scrolling = true, vertical = true)
            playback.setScrolling("second", scrolling = true, vertical = false)
            playback.setScrolling("second", scrolling = false, vertical = false)
            advanceTimeBy(1000)
            assertEquals(listOf("a:true"), events)
            playback.setScrolling("vertical", scrolling = false, vertical = true)
            advanceTimeBy(199)
            runCurrent()
            assertEquals(listOf("a:true"), events)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf("a:true", "a:false", "b:true"), events)
            playback.close()
            assertTrue(playing.isEmpty())
        }

    @Test
    fun offscreenPauseIsImmediateAndReentryDoesNotStartDuringScroll() =
        runTest {
            val playback = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter())
            val events = mutableListOf<Boolean>()
            playback.register("video") { events += it }
            val visible = TimelineAutoplayPolicy.Candidate("video", visible = true, canStart = true, distance = 0f)
            playback.update(visible)
            advanceTimeBy(200)
            runCurrent()
            playback.setScrolling("vertical", scrolling = true, vertical = true)
            playback.update(visible.copy(canStart = false))
            assertEquals(listOf(true), events)
            playback.update(visible.copy(visible = false))
            assertEquals(listOf(true, false), events)
            playback.update(visible)
            advanceTimeBy(1000)
            assertEquals(listOf(true, false), events)
            playback.setScrolling("vertical", scrolling = false, vertical = true)
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf(true, false, true), events)
            playback.close()
        }

    @Test
    fun closingTimelineCancelsPendingAutoplay() =
        runTest {
            val playback = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter())
            val events = mutableListOf<Boolean>()
            playback.register("video") { events += it }
            playback.update(TimelineAutoplayPolicy.Candidate("video", visible = true, canStart = true, distance = 0f))
            playback.close()
            advanceTimeBy(1000)
            assertTrue(events.isEmpty())
        }

    @Test
    fun multipleTimelinesSharePlaybackAndOnlyUserInteractionCanTakeOver() =
        runTest {
            val arbiter = VideoPlaybackArbiter()
            val first = TimelinePlaybackCoordinator(this, arbiter)
            val second = TimelinePlaybackCoordinator(this, arbiter)
            val events = mutableListOf<String>()
            first.register("a") { events += "a:$it" }
            second.register("b") { events += "b:$it" }
            val a = TimelineAutoplayPolicy.Candidate("a", visible = true, canStart = true, distance = 10f)
            val b = TimelineAutoplayPolicy.Candidate("b", "carousel", true, canStart = true, distance = 0f)
            first.update(a)
            advanceTimeBy(200)
            runCurrent()
            second.update(b)
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf("a:true"), events)
            second.setScrolling("carousel", scrolling = true, vertical = false)
            advanceTimeBy(500)
            assertEquals(listOf("a:true"), events)
            second.setScrolling("carousel", scrolling = false, vertical = false)
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf("a:true", "a:false", "b:true"), events)
            first.update(a.copy(distance = 0f))
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf("a:true", "a:false", "b:true"), events)
            second.close()
            advanceTimeBy(200)
            runCurrent()
            assertEquals("a:true", events.last())
            first.close()
        }

    @Test
    fun imageChoiceAndImageOnlyViewerKeepOtherTimelinesQuiet() =
        runTest {
            val arbiter = VideoPlaybackArbiter()
            val timeline = TimelinePlaybackCoordinator(this, arbiter)
            val other = TimelinePlaybackCoordinator(this, arbiter)
            val events = mutableListOf<Boolean>()
            val video = TimelineAutoplayPolicy.Candidate("a", visible = true, canStart = true, distance = 0f)
            timeline.register("a") { events += it }
            timeline.update(video)
            advanceTimeBy(200)
            runCurrent()
            other.setScrolling("image-carousel", scrolling = true, vertical = false)
            other.setScrolling("image-carousel", scrolling = false, vertical = false)
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf(true, false), events)
            timeline.update(video.copy(distance = 1f))
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf(true, false), events)
            other.present()
            timeline.setScrolling("vertical", scrolling = true, vertical = true)
            timeline.setScrolling("vertical", scrolling = false, vertical = true)
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf(true, false), events)
            other.close()
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf(true, false, true), events)
            timeline.close()
        }
}
