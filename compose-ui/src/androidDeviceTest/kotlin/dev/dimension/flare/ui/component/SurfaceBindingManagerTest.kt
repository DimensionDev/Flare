package dev.dimension.flare.ui.component

import android.app.Activity
import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class SurfaceBindingManagerTest {
    @Test
    fun collectedSuspendedPlaybackDoesNotCrashActivityResume() =
        runTest {
            val playback = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), MediaPlaybackMemory())
            withContext(Dispatchers.Main) {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val manager = SurfaceBindingManager(context, Media3VideoCacheManager(context))
                val activity = Activity()
                val callbacks = activityCallbacks(manager)
                val map = CollectingPlaybackMap()
                val suspended = Collections.newSetFromMap(map)
                try {
                    callbacks.onActivityStopped(activity)
                    playback.setSuspended(true)
                    playback.close()
                    suspended.add(playback)
                    SurfaceBindingManager::class.java.getDeclaredField("suspendedPlaybacks").apply {
                        isAccessible = true
                        set(manager, suspended)
                    }
                    map.collectOnNextSizeRead(playback)

                    callbacks.onActivityResumed(activity)

                    assertEquals(1, map.collections)
                    assertTrue(suspended.isEmpty())
                } finally {
                    playback.close()
                    manager.close()
                }
            }
        }

    @Test
    fun activityResumeClearsSuspendedPlaybacksBeforeRestartingSurvivors() =
        runTest {
            val playback = TimelinePlaybackCoordinator(this, VideoPlaybackArbiter(), MediaPlaybackMemory())
            withContext(Dispatchers.Main) {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val manager = SurfaceBindingManager(context, Media3VideoCacheManager(context))
                val activity = Activity()
                val callbacks = activityCallbacks(manager)
                val suspended = Collections.newSetFromMap(CollectingPlaybackMap())
                val events = mutableListOf<Boolean>()
                try {
                    callbacks.onActivityStopped(activity)
                    playback.setSuspended(true)
                    playback.present()
                    playback.register("video") {
                        assertTrue("Clear the weak set before resuming playback", suspended.isEmpty())
                        events.add(it)
                    }
                    playback.update(TimelineAutoplayPolicy.Candidate("video", visible = true, canStart = true, distance = 0f))
                    suspended.add(playback)
                    SurfaceBindingManager::class.java.getDeclaredField("suspendedPlaybacks").apply {
                        isAccessible = true
                        set(manager, suspended)
                    }

                    callbacks.onActivityResumed(activity)
                    callbacks.onActivityResumed(activity)

                    assertEquals(listOf(true), events)
                    assertTrue(suspended.isEmpty())
                } finally {
                    playback.close()
                    manager.close()
                }
            }
        }

    private fun activityCallbacks(manager: SurfaceBindingManager): Application.ActivityLifecycleCallbacks =
        SurfaceBindingManager::class.java.getDeclaredField("activityCallbacks").run {
            isAccessible = true
            get(manager) as Application.ActivityLifecycleCallbacks
        }
}
