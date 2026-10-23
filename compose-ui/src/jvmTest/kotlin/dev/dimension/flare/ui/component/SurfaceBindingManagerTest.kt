package dev.dimension.flare.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.datastore.core.DataStore
import com.sun.net.httpserver.HttpServer
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.network.MediaPlaybackSource
import dev.dimension.flare.data.network.MediaProxy
import dev.dimension.flare.data.network.ProxyNetwork
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import io.github.kdroidfilter.composemediaplayer.VideoPlayerError
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SurfaceBindingManagerTest {
    @Test
    fun playbackDiagnosticsKeepRelayCauseWithoutLeakingMediaUrls() {
        val error = VideoPlayerError.SourceError("Native failed at https://player.example/video?token=native-secret")
        assertEquals(
            "Video playback failed (SourceError): Target returned HTTP 503",
            playbackFailureDiagnostic(error, "Target returned HTTP 503"),
        )
        val redacted =
            playbackFailureDiagnostic(error, "Request failed at HTTPS://user:password@cdn.example/video?token=secret\nCookie: secret")
        assertEquals("Video playback failed (SourceError): Request failed at [redacted URL]", redacted)
        assertEquals("Video playback failed (SourceError): Native failed at [redacted URL]", playbackFailureDiagnostic(error, null))
    }

    @Test
    fun relaySurvivesSurfaceDisposalAndHandoffUntilTheNativeBufferIsReleased() =
        runTest {
            withProxyRepository { repository ->
                repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT))
                val server = mediaServer()
                val fake = ControlledPlayer()
                val manager = SurfaceBindingManager(this) { fake.player }
                val memory = MediaPlaybackMemory()
                val arbiter = VideoPlaybackArbiter()
                val timeline = TimelinePlaybackCoordinator(this, arbiter, memory)
                val viewer = TimelinePlaybackCoordinator(this, arbiter, memory)
                val media = UiMedia.Video("http://127.0.0.1:${server.address.port}/video.m3u8", "", null, 0f, 0f, proxy = ProxyRef.Global)
                val source = MediaProxy.playbackSource(media)
                try {
                    val binding = manager.register(source, timeline) {}
                    binding.setActive(true)
                    fake.finishLoading()
                    Snapshot.sendApplyNotifications()
                    runCurrent()
                    val segment = relayGet(source.url).second.lineSequence().first { it.startsWith("http") }
                    binding.dispose()
                    MediaProxy.release(source)
                    assertEquals(200, relayGet(segment).first, "The retained native player still needs its HLS children")

                    val replacement = MediaProxy.playbackSource(media)
                    val detail = manager.register(replacement, viewer) {}
                    detail.setActive(true)
                    assertEquals(listOf(source.url), fake.opened, "A warm handoff must reuse the same native media")
                    detail.dispose()
                    MediaProxy.release(replacement)
                    advanceTimeBy(5000)
                    runCurrent()
                    assertFalse(fake.hasMedia)
                    assertEquals(502, relayGet(segment).first, "Releasing the native buffer must retire its HLS children")
                } finally {
                    timeline.close()
                    viewer.close()
                    manager.close()
                    server.stop(0)
                }
            }
        }

    @Test
    fun idleExpiryReleasesFailedOrPendingNativeLoadsAndTheirRelay() =
        runTest {
            withProxyRepository { repository ->
                repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT))
                val server = mediaServer()
                try {
                    for (failed in listOf(false, true)) {
                        val fake = ControlledPlayer()
                        val manager = SurfaceBindingManager(this) { fake.player }
                        val timeline = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), MediaPlaybackMemory())
                        val media =
                            UiMedia.Video(
                                "http://127.0.0.1:${server.address.port}/video.m3u8",
                                "",
                                null,
                                0f,
                                0f,
                                proxy = ProxyRef.Global,
                            )
                        val source = MediaProxy.playbackSource(media)
                        try {
                            val binding = manager.register(source, timeline) {}
                            binding.setActive(true)
                            if (failed) {
                                fake.failLoading()
                                Snapshot.sendApplyNotifications()
                                runCurrent()
                            }
                            binding.dispose()
                            MediaProxy.release(source)
                            assertEquals(200, relayGet(source.url).first)
                            advanceTimeBy(5000)
                            runCurrent()
                            assertEquals(502, relayGet(source.url).first, "Failed and pending native loads must release their relay")
                            if (!failed) assertEquals(1, fake.disposals, "Expiring an unfinished native open must cancel its worker")
                        } finally {
                            timeline.close()
                            manager.close()
                        }
                    }
                } finally {
                    server.stop(0)
                }
            }
        }

    private fun mediaServer(): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val playlist = exchange.requestURI.path.endsWith(".m3u8")
                val body = if (playlist) "#EXTM3U\n#EXTINF:2,\nsegment.ts\n#EXT-X-ENDLIST" else "segment"
                exchange.responseHeaders.add("Content-Type", if (playlist) "application/vnd.apple.mpegurl" else "video/mp2t")
                exchange.sendResponseHeaders(200, body.length.toLong())
                exchange.responseBody.use { it.write(body.toByteArray()) }
                exchange.close()
            }
            start()
        }

    private fun relayGet(url: String): Pair<Int, String> {
        val connection = URI(url).toURL().openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        return try {
            connection.responseCode to
                if (connection.responseCode == 200) connection.inputStream.bufferedReader().use { it.readText() } else ""
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun routeInvalidationDisposesActiveAndRetainedNativeLoads() =
        runTest {
            withProxyRepository { repository ->
                for (retained in listOf(false, true)) {
                    repository.selectGlobal(ProxyPolicy(ProxyMode.SYSTEM))
                    val route = ProxyNetwork.snapshot(ProxyRef.Global)
                    val players = mutableListOf<ControlledPlayer>()
                    val manager = SurfaceBindingManager(this) { ControlledPlayer().also(players::add).player }
                    val timeline = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), MediaPlaybackMemory())
                    val uri = "https://media.example/video.mpd"
                    val binding = manager.register(MediaPlaybackSource(uri, route.key, snapshot = route), timeline) {}
                    try {
                        binding.setActive(true)
                        if (retained) binding.dispose()
                        repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT))
                        runCurrent()
                        assertEquals(
                            1,
                            players.single().disposals,
                            "A pending native load must be disposed even without an attached surface",
                        )
                        val replacement = ProxyNetwork.snapshot(ProxyRef.Global)
                        val url = "http://127.0.0.1/new-route.mp4"
                        manager
                            .register(
                                MediaPlaybackSource(url, replacement.key, originalUrl = uri, snapshot = replacement),
                                timeline,
                            ) {}
                            .setActive(true)
                        assertEquals(2, players.size)
                        assertEquals(listOf(url), players.last().opened)
                        players.last().finishLoading()
                        Snapshot.sendApplyNotifications()
                        runCurrent()
                        assertTrue(players.last().playing)
                    } finally {
                        timeline.close()
                        manager.close()
                    }
                }
            }
        }

    @Test
    fun invalidatingAnOldNativeOpenUnblocksTheAlreadyRequestedNewRoute() =
        runTest {
            withProxyRepository { repository ->
                val route = ProxyNetwork.snapshot(ProxyRef.Global)
                val players = mutableListOf<ControlledPlayer>()
                val manager = SurfaceBindingManager(this) { ControlledPlayer().also(players::add).player }
                val timeline = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), MediaPlaybackMemory())
                try {
                    manager.register(MediaPlaybackSource("old", route.key, snapshot = route), timeline) {}.setActive(true)
                    repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT))
                    val replacement = ProxyNetwork.snapshot(ProxyRef.Global)
                    manager.register(MediaPlaybackSource("new", replacement.key, snapshot = replacement), timeline) {}.setActive(true)
                    runCurrent()
                    assertEquals(1, players.first().disposals)
                    assertEquals(listOf("new"), players.last().opened)
                    players.last().finishLoading()
                    Snapshot.sendApplyNotifications()
                    runCurrent()
                    assertTrue(players.last().playing)
                } finally {
                    timeline.close()
                    manager.close()
                }
            }
        }

    @Test
    fun unrelatedGlobalChangesKeepAnExplicitAccountLoad() =
        runTest {
            withProxyRepository { repository ->
                val account = MicroBlogKey("video-viewer", "example.social")
                repository.selectAccount(account, ProxyPolicy(ProxyMode.DIRECT))
                val route = ProxyNetwork.snapshot(ProxyRef.Account(account))
                val fake = ControlledPlayer()
                val manager = SurfaceBindingManager(this) { fake.player }
                val timeline = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), MediaPlaybackMemory())
                try {
                    manager.register(MediaPlaybackSource("video", route.key, snapshot = route), timeline) {}.setActive(true)
                    ProxyNetwork.snapshot(ProxyRef.Global)
                    repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT))
                    runCurrent()
                    assertEquals(0, fake.disposals)
                    assertEquals(listOf("video"), fake.opened)
                    fake.finishLoading()
                    Snapshot.sendApplyNotifications()
                    runCurrent()
                    assertTrue(fake.playing)
                } finally {
                    timeline.close()
                    manager.close()
                }
            }
        }

    @Test
    fun replacingAnInvalidatedPlayerRestoresTheCurrentVideoPosition() =
        runTest {
            withProxyRepository { repository ->
                val players = mutableListOf<ControlledPlayer>()
                val manager = SurfaceBindingManager(this) { ControlledPlayer().also(players::add).player }
                val memory = MediaPlaybackMemory()
                val timeline = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), memory)
                val route = ProxyNetwork.snapshot(ProxyRef.Global)
                try {
                    manager.register(MediaPlaybackSource("video", route.key, snapshot = route), timeline) {}.setActive(true)
                    players.single().finishLoading()
                    Snapshot.sendApplyNotifications()
                    runCurrent()
                    players.single().time = 37.0
                    repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT))
                    runCurrent()
                    assertEquals(37.0, memory.position("video"), 0.001)
                    val replacement = ProxyNetwork.snapshot(ProxyRef.Global)
                    manager.register(MediaPlaybackSource("video", replacement.key, snapshot = replacement), timeline) {}.setActive(true)
                    assertEquals(2, players.size)
                    players.last().finishLoading()
                    Snapshot.sendApplyNotifications()
                    runCurrent()
                    assertEquals(37.0, players.last().time, 0.001)
                    assertTrue(players.last().playing)
                } finally {
                    timeline.close()
                    manager.close()
                }
            }
        }

    @Test
    fun backgroundingSeveralTimelinesDoesNotLeaveTheEarlierOnePermanentlySuspended() =
        runTest {
            val fake = ControlledPlayer()
            val manager = SurfaceBindingManager(this) { fake.player }
            val arbiter = VideoPlaybackArbiter()
            val first = TimelinePlaybackCoordinator(this, arbiter, MediaPlaybackMemory())
            val second = TimelinePlaybackCoordinator(this, arbiter, MediaPlaybackMemory())
            val a = manager.register(MediaPlaybackSource("a", "a"), first) {}
            val b = manager.register(MediaPlaybackSource("b", "b"), second) {}
            first.register("a", a::setActive)
            second.register("b", b::setActive)
            first.update(TimelineAutoplayPolicy.Candidate("a", visible = true, canStart = true, distance = 0f))
            advanceTimeBy(200)
            runCurrent()
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            second.update(TimelineAutoplayPolicy.Candidate("b", visible = true, canStart = true, distance = 0f))
            arbiter.interacted(second)
            manager.setForeground(false)
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf("a"), fake.opened)
            manager.setForeground(true)
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf("a", "b"), fake.opened)
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            first.setScrolling("vertical", scrolling = true, vertical = true)
            first.setScrolling("vertical", scrolling = false, vertical = true)
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf("a", "b", "a"), fake.opened)
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            first.close()
            second.close()
            manager.close()
        }

    @Test
    fun navigationKeepsTheSameVideoRunningButBackgroundingStopsAndReleasesIt() =
        runTest {
            val fake = ControlledPlayer()
            val manager = SurfaceBindingManager(this) { fake.player }
            val arbiter = VideoPlaybackArbiter()
            val timeline = TimelinePlaybackCoordinator(this, arbiter, MediaPlaybackMemory())
            val viewer = TimelinePlaybackCoordinator(this, arbiter, MediaPlaybackMemory())
            val inline = manager.register(MediaPlaybackSource("a", "a"), timeline) {}
            val detail = manager.register(MediaPlaybackSource("a", "a"), viewer, muted = false) {}
            timeline.register("inline", inline::setActive)
            viewer.register("detail", detail::setActive)
            timeline.update(TimelineAutoplayPolicy.Candidate("inline", visible = true, canStart = true, distance = 0f, mediaUri = "a"))
            advanceTimeBy(200)
            runCurrent()
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            val pauses = fake.pauses
            viewer.selectViewerMedia(listOf("a"), 0)
            viewer.present()
            assertTrue(fake.playing, "The outgoing media keeps running until its new surface attaches")
            viewer.update(TimelineAutoplayPolicy.Candidate("detail", visible = true, canStart = true, distance = 0f, mediaUri = "a"))
            assertEquals(listOf("a"), fake.opened)
            assertEquals(pauses, fake.pauses)
            viewer.close()
            assertTrue(fake.playing)
            assertEquals(pauses, fake.pauses)
            manager.setForeground(false)
            assertFalse(fake.playing)
            assertFalse(fake.hasMedia)
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(listOf("a"), fake.opened, "Pending selection must not restart playback in the background")
            manager.setForeground(true)
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf("a", "a"), fake.opened)
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertTrue(fake.playing)
            timeline.close()
            manager.close()
        }

    @Test
    fun sameVideoHandoffKeepsLoadedMediaAndIdleBufferExpiresAfterFiveSeconds() =
        runTest {
            val fake = ControlledPlayer()
            val manager = SurfaceBindingManager(this) { fake.player }
            val memory = MediaPlaybackMemory()
            val arbiter = VideoPlaybackArbiter()
            val timeline = TimelinePlaybackCoordinator(this, arbiter, memory)
            val viewer = TimelinePlaybackCoordinator(this, arbiter, memory)
            val inline = manager.register(MediaPlaybackSource("a", "a"), timeline) {}
            val detail = manager.register(MediaPlaybackSource("a", "a"), viewer, muted = false) {}
            inline.setActive(true)
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            fake.time = 37.0
            detail.setActive(true)
            assertEquals(listOf("a"), fake.opened)
            assertEquals(37.0, fake.time)
            viewer.close()
            assertFalse(fake.playing)
            assertTrue(fake.hasMedia)
            advanceTimeBy(4999)
            runCurrent()
            assertTrue(fake.hasMedia)
            inline.setActive(true)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf("a"), fake.opened)
            assertTrue(fake.playing)
            inline.setActive(false)
            assertFalse(fake.playing)
            advanceTimeBy(5000)
            runCurrent()
            assertFalse(fake.hasMedia)
            assertEquals(37.0, memory.position("a"))
            timeline.close()
            manager.close()
        }

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
            val a = manager.register(MediaPlaybackSource("http://127.0.0.1/a", "a-proxy", "a"), timeline) { observed += it }
            val b = manager.register(MediaPlaybackSource("http://127.0.0.1/b", "b-proxy", "b"), timeline) { observed += it }

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
            assertEquals(listOf("http://127.0.0.1/a", "http://127.0.0.1/b"), fake.opened)
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
            advanceTimeBy(5000)
            runCurrent()
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
            val a = manager.register(MediaPlaybackSource("a", "a"), first) {}
            val b = manager.register(MediaPlaybackSource("b", "b"), second) {}
            val c = manager.register(MediaPlaybackSource("c", "c"), second) {}
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
            advanceTimeBy(5000)
            runCurrent()
            assertFalse(fake.hasMedia)
            manager.close()
        }

    @Test
    fun closingDuringLoadReleasesItsResultWithoutStartingPlayback() =
        runTest {
            val fake = ControlledPlayer()
            val manager = SurfaceBindingManager(this) { fake.player }
            val timeline = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), MediaPlaybackMemory())
            manager.register(MediaPlaybackSource("a", "a"), timeline) {}.setActive(true)
            timeline.close()
            assertFalse(fake.playing)
            fake.finishLoading()
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertTrue(fake.played.isEmpty())
            advanceTimeBy(5000)
            runCurrent()
            assertFalse(fake.hasMedia)
            manager.close()
        }

    @Test
    fun switchingSourceWaitsForTheNativeStopToComplete() =
        runTest {
            val fake = ControlledPlayer()
            val manager = SurfaceBindingManager(this) { fake.player }
            val timeline = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), MediaPlaybackMemory())
            val a = manager.register(MediaPlaybackSource("a", "a"), timeline) {}
            val b = manager.register(MediaPlaybackSource("b", "b"), timeline) {}
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
            manager.register(MediaPlaybackSource("a", "a"), timeline) {}.setActive(true)
            manager.register(MediaPlaybackSource("b", "b"), timeline) {}.setActive(true)
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
            val inlineA = manager.register(MediaPlaybackSource("a", "a"), timeline) {}
            val inlineB = manager.register(MediaPlaybackSource("b", "b"), timeline) {}
            val detailA = manager.register(MediaPlaybackSource("a", "a"), viewer, muted = false) {}
            val detailB = manager.register(MediaPlaybackSource("b", "b"), viewer, muted = false) {}

            fun activate(binding: SurfaceBindingManager.Binding) {
                binding.setActive(true)
                if (fake.loading) fake.finishLoading()
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
            val a = manager.register(MediaPlaybackSource("a", "a"), timeline) {}
            val b = manager.register(MediaPlaybackSource("b", "b"), timeline) {}
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
            val binding = manager.register(MediaPlaybackSource("a", "a"), timeline) {}
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

    private suspend fun withProxyRepository(block: suspend (ProxyRepository) -> Unit) {
        val repository =
            ProxyRepository(
                object : DataStore<ProxySettings> {
                    override val data = MutableStateFlow(ProxySettings())

                    override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings =
                        transform(data.value).also { data.value = it }
                },
            )
        startKoin { modules(module { single { repository } }) }
        try {
            repository.selectGlobal(ProxyPolicy(ProxyMode.SYSTEM))
            block(repository)
        } finally {
            repository.selectGlobal(ProxyPolicy(ProxyMode.SYSTEM))
            stopKoin()
        }
    }

    private class ControlledPlayer {
        var loading by mutableStateOf(false)
        var hasMedia by mutableStateOf(false)
        private var error by mutableStateOf<VideoPlayerError?>(null)
        var time by mutableStateOf(0.0)
        var playing = false
        var pauses = 0
        var disposals = 0
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

                    "isPlaying" -> {
                        playing
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
                        pauses++
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

                    "dispose" -> {
                        disposals++
                        opening = null
                        finishStop()
                        null
                    }

                    "setLoop", "setVolume" -> {
                        null
                    }

                    else -> {
                        error("Unexpected player call: ${method.name}")
                    }
                }
            } as VideoPlayerState
    }
}
