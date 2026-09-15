package dev.dimension.flare.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import io.github.kdroidfilter.composemediaplayer.VideoPlayerError
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SurfaceBindingManagerTest {
    @Test
    fun interruptedLoadStillRestoresPositionAndAllBindingsShareOnePlayer() =
        runTest {
            val fake = ControlledPlayer()
            var allocations = 0
            val manager =
                SurfaceBindingManager(this) {
                    allocations++
                    fake.player
                }
            val timeline = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), MediaPlaybackMemory())
            val observed = mutableListOf<VideoPlayerState?>()
            val a = manager.register("a", timeline) { observed += it }
            val b = manager.register("b", timeline) { observed += it }

            fun finishLoading() {
                Snapshot.sendApplyNotifications()
                runCurrent()
                fake.finishLoading()
                Snapshot.sendApplyNotifications()
                runCurrent()
            }

            a.setActive(true)
            finishLoading()
            assertTrue(fake.playing)
            fake.time = 37.0
            a.setActive(false)
            b.setActive(true)
            finishLoading()
            assertTrue(fake.playing)
            assertEquals(listOf("a", "b"), fake.opened)
            b.setActive(false)
            a.setActive(true)
            Snapshot.sendApplyNotifications()
            runCurrent()
            a.setActive(false)
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertFalse(fake.playing)
            assertEquals(0.0, fake.time)
            finishLoading()
            assertFalse(fake.playing)
            a.setActive(true)
            assertEquals(37.0, fake.time, 0.001)
            assertTrue(fake.playing)
            assertEquals(1, allocations)
            observed.filterNotNull().forEach { assertSame(fake.player, it) }
            a.dispose()
            timeline.close()
            assertFalse(fake.hasMedia)
            assertEquals(1, allocations)
            manager.close()
        }

    @Test
    fun rapidHandoffsSkipSupersededSourcesAndClosingAnotherTimelineKeepsPlayback() =
        runTest {
            val fake = ControlledPlayer()
            val manager = SurfaceBindingManager(this) { fake.player }
            val first = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), MediaPlaybackMemory())
            val second = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), MediaPlaybackMemory())
            val a = manager.register("a", first) {}
            val b = manager.register("b", second) {}
            val c = manager.register("c", second) {}
            a.setActive(true)
            b.setActive(true)
            c.setActive(true)
            assertEquals(listOf("a"), fake.opened)
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals(listOf("a", "c"), fake.opened)
            assertTrue(fake.played.isEmpty())
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals(listOf("c"), fake.played)
            first.close()
            assertTrue(fake.playing)
            assertTrue(fake.hasMedia)
            second.close()
            assertFalse(fake.hasMedia)
            manager.close()
        }

    @Test
    fun closingDuringLoadReleasesItsResultWithoutStartingPlayback() =
        runTest {
            val fake = ControlledPlayer()
            val manager = SurfaceBindingManager(this) { fake.player }
            val timeline = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), MediaPlaybackMemory())
            manager.register("a", timeline) {}.setActive(true)
            timeline.close()
            assertFalse(fake.playing)
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertTrue(fake.played.isEmpty())
            assertFalse(fake.hasMedia)
            manager.close()
        }

    @Test
    fun switchingSourceWaitsForTheNativeStopToComplete() =
        runTest {
            val fake = ControlledPlayer()
            val manager = SurfaceBindingManager(this) { fake.player }
            val timeline = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), MediaPlaybackMemory())
            val a = manager.register("a", timeline) {}
            val b = manager.register("b", timeline) {}
            a.setActive(true)
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            fake.deferStop = true
            b.setActive(true)
            assertEquals(listOf("a"), fake.opened)
            assertTrue(b.isPreparing)
            fake.finishStop()
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals(listOf("a", "b"), fake.opened)
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals(listOf("a", "b"), fake.played)
            assertFalse(b.isPreparing)
            fake.deferStop = false
            timeline.close()
            manager.close()
        }

    @Test
    fun failureOfAnOldLoadDoesNotBlockTheLatestRequest() =
        runTest {
            val fake = ControlledPlayer()
            val manager = SurfaceBindingManager(this) { fake.player }
            val timeline = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), MediaPlaybackMemory())
            manager.register("a", timeline) {}.setActive(true)
            manager.register("b", timeline) {}.setActive(true)
            fake.failLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals(listOf("a", "b"), fake.opened)
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals(listOf("b"), fake.played)
            timeline.close()
            manager.close()
        }

    @Test
    fun timelineAndViewerShareProgressAcrossPagesAndResumeAfterManualPause() =
        runTest {
            val fake = ControlledPlayer()
            var allocations = 0
            val manager =
                SurfaceBindingManager(this) {
                    allocations++
                    fake.player
                }
            val memory = MediaPlaybackMemory()
            val arbiter = VideoPlaybackArbiter()
            val timeline = TimelinePlaybackCoordinator(this, arbiter, memory)
            val viewer = TimelinePlaybackCoordinator(this, arbiter, memory)
            val inlineA = manager.register("a", timeline) {}
            val inlineB = manager.register("b", timeline) {}
            val detailA = manager.register("a", viewer, muted = false) {}
            val detailB = manager.register("b", viewer, muted = false) {}

            fun activate(binding: SurfaceBindingManager.Binding) {
                binding.setActive(true)
                fake.finishLoading()
                Snapshot.sendApplyNotifications()
                runCurrent()
            }

            activate(inlineA)
            fake.time = 37.0
            activate(detailA)
            assertEquals(37.0, fake.time, 0.001)
            detailA.seek(120f)
            fake.player.pause()
            activate(detailB)
            assertEquals(0.0, fake.time, 0.001)
            fake.time = 8.0
            activate(detailA)
            assertEquals(12.0, fake.time, 0.001)
            activate(detailB)
            assertEquals(8.0, fake.time, 0.001)
            fake.player.pause()
            viewer.close()
            activate(inlineB)
            assertEquals(8.0, fake.time, 0.001)
            assertTrue(fake.playing)
            assertEquals(12.0, memory.position("a"), 0.001)
            assertEquals(1, allocations)
            fake.time = 19.0
            fake.loading = true
            timeline.close()
            assertEquals(19.0, memory.position("b"), 0.001, "Buffering must not discard the current video's progress")
            manager.close()
        }

    @Test
    fun pendingSeekWinsOverStaleNativeTimeWhenClosingOrSwitchingImmediately() =
        runTest {
            val fake = ControlledPlayer()
            val manager = SurfaceBindingManager(this) { fake.player }
            val memory = MediaPlaybackMemory()
            val timeline = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), memory)
            val a = manager.register("a", timeline) {}
            val b = manager.register("b", timeline) {}
            a.setActive(true)
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            fake.time = 37.0
            fake.deferSeek = true
            a.seek(100f)
            b.setActive(true)
            assertEquals(10.0, memory.position("a"), 0.001)
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            a.setActive(true)
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            a.setActive(false)
            assertEquals(10.0, memory.position("a"), 0.001)
            a.setActive(true)
            a.seek(0f)
            timeline.close()
            assertEquals(0.0, memory.position("a"), 0.001)
            manager.close()
        }

    @Test
    fun seekingToTheEndDoesNotKeepAnOldPendingPositionAfterLooping() =
        runTest {
            val fake = ControlledPlayer()
            val manager = SurfaceBindingManager(this) { fake.player }
            val memory = MediaPlaybackMemory()
            val timeline = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), memory)
            val binding = manager.register("a", timeline) {}
            binding.setActive(true)
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            fake.time = 37.0
            binding.seek(1000f)
            fake.time = 3.0
            timeline.close()
            manager.close()
            assertEquals(3.0, memory.position("a"), 0.001)
        }

    private class ControlledPlayer {
        var loading by mutableStateOf(false)
        var hasMedia by mutableStateOf(false)
        private var error by mutableStateOf<VideoPlayerError?>(null)
        var time by mutableStateOf(0.0)
        var playing = false
        var deferStop = false
        var deferSeek = false
        private var userDragging = false
        val opened = mutableListOf<String>()
        val played = mutableListOf<String>()
        private var opening: String? = null
        private var loaded: String? = null

        fun finishLoading() {
            loaded = checkNotNull(opening)
            opening = null
            hasMedia = true
            loading = false
        }

        fun failLoading() {
            opening = null
            loading = false
            error = VideoPlayerError.SourceError("Loading failed")
        }

        fun finishStop() {
            hasMedia = false
            loading = false
            playing = false
            time = 0.0
            loaded = null
        }

        val player =
            Proxy.newProxyInstance(
                VideoPlayerState::class.java.classLoader,
                arrayOf(VideoPlayerState::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "isLoading" -> {
                        loading
                    }

                    "getHasMedia" -> {
                        hasMedia
                    }

                    "getError" -> {
                        error
                    }

                    "getCurrentTime" -> {
                        time
                    }

                    "getDuration" -> {
                        if (hasMedia) 100.0 else 0.0
                    }

                    "openUri" -> {
                        check(opening == null) { "Native opens must not overlap" }
                        opening = args[0] as String
                        opened += checkNotNull(opening)
                        loading = true
                        // Linux does not reset hasMedia when another URI is opened.
                        time = 0.0
                        playing = false
                        null
                    }

                    "seekTo" -> {
                        if (!deferSeek) {
                            val slider = args[0] as Float
                            time = if (slider == 1000f) 0.0 else slider.toDouble() / 10
                        }
                        null
                    }

                    "getUserDragging" -> {
                        userDragging
                    }

                    "setUserDragging" -> {
                        userDragging = args[0] as Boolean
                        null
                    }

                    "play" -> {
                        played += checkNotNull(loaded)
                        playing = true
                        null
                    }

                    "pause" -> {
                        playing = false
                        loading = false
                        null
                    }

                    "stop" -> {
                        check(opening == null) { "A pending native open can undo stop" }
                        if (!deferStop) finishStop()
                        null
                    }

                    "clearError" -> {
                        error = null
                        null
                    }

                    "setLoop", "setVolume", "dispose" -> {
                        null
                    }

                    else -> {
                        error("Unexpected player call: ${method.name}")
                    }
                }
            } as VideoPlayerState
    }
}
