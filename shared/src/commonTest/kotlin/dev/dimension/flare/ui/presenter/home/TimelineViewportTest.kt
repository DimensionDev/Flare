package dev.dimension.flare.ui.presenter.home

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.datastore.model.HomeTimelinePosition
import dev.dimension.flare.data.datastore.model.HomeTimelinePreferences
import dev.dimension.flare.ui.model.TimelineReadingPosition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewportTest {
    @Test
    fun bookmarksRoundTripWithoutPostKeys() {
        val value = HomeTimelinePreferences("tab", mapOf("tab" to HomeTimelinePosition("source", TimelineReadingPosition(54, -17.25), 12)))
        assertEquals(value, Json.decodeFromString<HomeTimelinePreferences>(Json.encodeToString(value)))
    }

    @Test
    fun localPrefixLoadsBeforeRestorationAndRemoteWorkWaitsForTheUI() =
        runTest {
            val store = preferences(54, -17.25)
            val reader = TimelineViewport(store, "source", "tab", this)
            var remoteStarted = false
            val remote =
                launch {
                    reader.awaitRestoration()
                    remoteStarted = true
                }
            reader.restore()
            assertEquals(19, reader.cacheChanged(keys(20), false))
            assertEquals(39, reader.cacheChanged(keys(40), false))
            assertNull(reader.cacheChanged(keys(60), false))
            assertEquals(TimelineReadingPosition(54, -17.25), reader.target.value)
            runCurrent()
            assertFalse(remoteStarted)
            reader.restorationFinished(reader.target.value)
            remote.join()
            assertTrue(remoteStarted)
            assertNull(reader.target.value)
            reader.cacheChanged(listOf("new") + keys(60), false)
            assertNull(reader.target.value, "Later prepends must be anchored by the native list, not a second index restore")
        }

    @Test
    fun missingLocalIndexFallsBackToTheTopBeforeAllowingNetwork() =
        runTest {
            val reader = TimelineViewport(preferences(100, -10.0), "source", "tab", this)
            var network = false
            val pending =
                launch {
                    reader.awaitRestoration()
                    network = true
                }
            reader.restore()
            assertEquals(19, reader.cacheChanged(keys(20), false))
            reader.cacheChanged(keys(30), true)
            assertEquals(TimelineReadingPosition(0, 0.0), reader.target.value)
            runCurrent()
            assertFalse(network)
            reader.restorationFinished(reader.target.value)
            pending.join()
            assertTrue(network)
        }

    @Test
    fun emptyOrFullyFilteredCacheCompletesAtTheTop() =
        runTest {
            val store = preferences(100, -10.0)
            val reader = TimelineViewport(store, "source", "tab", this)
            reader.restore()
            reader.cacheChanged(emptyList(), true)
            reader.awaitRestoration()
            assertNull(reader.target.value)
            reader.savePosition()
            runCurrent()
            assertEquals(
                TimelineReadingPosition(0, 0.0),
                store.data.value.positions["tab"]
                    ?.position,
            )
        }

    @Test
    fun evenAnUnbookmarkedLaunchWaitsForItsFirstCacheFrame() =
        runTest {
            val reader = TimelineViewport(MemoryPreferences(), "source", "tab", this)
            reader.restore()
            var ready = false
            val waiting =
                launch {
                    reader.awaitRestoration()
                    ready = true
                }
            reader.cacheChanged(keys(20), false)
            runCurrent()
            assertFalse(ready)
            reader.restorationFinished(reader.target.value)
            waiting.join()
            assertTrue(ready)
        }

    @Test
    fun perTabBookmarksRejectAnOlderWindowsLateSave() =
        runTest {
            val store = MemoryPreferences()
            val old = TimelineViewport(store, "source", "tab", this)
            val newer = TimelineViewport(store, "source", "tab", this)
            old.restore()
            newer.restore()
            old.positionChanged(10, -18.0)
            newer.positionChanged(20, -27.0)
            newer.savePosition()
            runCurrent()
            old.savePosition()
            runCurrent()
            val reopened = TimelineViewport(store, "source", "tab", this)
            reopened.restore()
            assertEquals(TimelineReadingPosition(20, -27.0), reopened.target.value)
            val anotherTab = TimelineViewport(store, "source", "other", this)
            anotherTab.restore()
            assertEquals(TimelineReadingPosition(0, 0.0), anotherTab.target.value)
        }

    @Test
    fun aChangedSourceDoesNotReuseTheOldIndex() =
        runTest {
            val reader = TimelineViewport(preferences(54, -17.25), "different-source", "tab", this)
            reader.restore()
            assertEquals(TimelineReadingPosition(0, 0.0), reader.target.value)
        }

    @Test
    fun staleRestoreCompletionCannotClearANewerTarget() =
        runTest {
            val store = preferences(2, -20.0)
            val reader = TimelineViewport(store, "source", "tab", this)
            reader.restore()
            val old = reader.target.value
            reader.positionChanged(3, -17.0)
            reader.restoreCurrentPosition()
            reader.restorationFinished(old)
            reader.viewportChanged(0, 0.0)
            assertEquals(TimelineReadingPosition(3, -17.0), reader.target.value)
            reader.restorationFinished(reader.target.value)
            reader.viewportChanged(4, -9.0)
            reader.savePosition()
            runCurrent()
            assertEquals(
                TimelineReadingPosition(4, -9.0),
                store.data.value.positions["tab"]
                    ?.position,
            )
        }

    @Test
    fun anUnscrolledWindowCannotOverwriteARealReadingPosition() =
        runTest {
            val store = MemoryPreferences()
            val active = TimelineViewport(store, "source", "tab", this)
            val untouched = TimelineViewport(store, "source", "tab", this)
            active.restore()
            untouched.restore()
            active.positionChanged(80, -15.0)
            active.savePosition()
            runCurrent()
            untouched.restorationFinished(untouched.target.value)
            untouched.viewportChanged(0, 0.0)
            untouched.savePosition()
            runCurrent()
            assertEquals(
                TimelineReadingPosition(80, -15.0),
                store.data.value.positions["tab"]
                    ?.position,
            )
        }

    @Test
    fun removedTabOrDisabledFeatureCannotBeResurrectedByAQueuedSave() =
        runTest {
            val store = preferences(2, -10.0)
            var active = true
            val reader = TimelineViewport(store, "source", "tab", this) { active }
            reader.restore()
            reader.positionChanged(5, -12.0)
            store.lock.lock()
            reader.savePosition()
            runCurrent()
            active = false
            store.data.value = HomeTimelinePreferences()
            store.lock.unlock()
            runCurrent()
            assertTrue(
                store.data.value.positions
                    .isEmpty(),
            )
        }

    @Test
    fun newContentIsAnnouncedWhenTheOldHeadIsPreservedAndLatestIsExplicit() =
        runTest {
            val reader = TimelineViewport(MemoryPreferences(), "source", "tab", this)
            reader.restore()
            reader.cacheChanged(keys(20), false)
            reader.restorationFinished(reader.target.value)
            reader.cacheChanged(listOf("new") + keys(20), false)
            assertTrue(reader.hasNewContent.value)
            reader.viewportChanged(1, 0.0)
            assertFalse(reader.isAtLatest.value)
            assertNull(reader.target.value)
            reader.showLatest()
            assertFalse(reader.hasNewContent.value)
            assertEquals(TimelineReadingPosition(0, 0.0), reader.target.value)
        }

    @Test
    fun deletingTheHeadIsNotNewContent() =
        runTest {
            val reader = TimelineViewport(MemoryPreferences(), "source", "tab", this)
            reader.restore()
            reader.cacheChanged(keys(20), false)
            reader.restorationFinished(reader.target.value)
            reader.cacheChanged(keys(20).drop(1), true)
            assertFalse(reader.hasNewContent.value)
        }

    @Test
    fun aUserScrollWinsOverALatePreferencesRead() =
        runTest {
            val delivered = kotlinx.coroutines.CompletableDeferred<HomeTimelinePreferences>()
            val store =
                object : DataStore<HomeTimelinePreferences> {
                    override val data = kotlinx.coroutines.flow.flow { emit(delivered.await()) }

                    override suspend fun updateData(transform: suspend (HomeTimelinePreferences) -> HomeTimelinePreferences) =
                        transform(delivered.await())
                }
            val reader = TimelineViewport(store, "source", "tab", this)
            val restoration = launch { reader.restore() }
            runCurrent()
            reader.positionChanged(4, -8.0)
            delivered.complete(preferences(57, -20.0).data.value)
            restoration.join()
            assertNull(reader.target.value)
            reader.restoreCurrentPosition()
            assertEquals(TimelineReadingPosition(4, -8.0), reader.target.value)
        }

    private fun keys(count: Int) = (0 until count).map { "post-$it" }

    private fun preferences(
        index: Int,
        offset: Double,
    ) = MemoryPreferences(
        HomeTimelinePreferences(positions = mapOf("tab" to HomeTimelinePosition("source", TimelineReadingPosition(index, offset), 1))),
    )

    private class MemoryPreferences(
        initial: HomeTimelinePreferences = HomeTimelinePreferences(),
    ) : DataStore<HomeTimelinePreferences> {
        override val data = MutableStateFlow(initial)
        val lock = Mutex()

        override suspend fun updateData(transform: suspend (HomeTimelinePreferences) -> HomeTimelinePreferences): HomeTimelinePreferences =
            lock.withLock { transform(data.value).also { data.value = it } }
    }
}
