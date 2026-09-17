package dev.dimension.flare.ui.component

import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class VideoCountdownTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun progressTicksDoNotRecomposeTheParentOrPollEveryFrame() {
        val player = TestPlayer { composeRule.mainClock.currentTime }
        val counts = CompositionCounts()
        composeRule.setContent { CountdownHost(player.player, counts) }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        val readsBefore = player.positionReads

        repeat(65) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithText("9000").assertExists()
        assertEquals(1, counts.parent)
        assertTrue("Countdown recomposed ${counts.content} times", counts.content <= 3)
        assertTrue("Progress polled ${player.positionReads - readsBefore} times", player.positionReads - readsBefore < 12)
    }

    @Test
    fun pausedSeekAndLoopRefreshWithoutWaitingForTheNextTick() {
        val player = TestPlayer { composeRule.mainClock.currentTime }
        player.playing = false
        composeRule.setContent { CountdownHost(player.player, CompositionCounts()) }
        composeRule.onNodeWithText("10000").assertExists()

        composeRule.runOnIdle { player.seek(6_000L) }
        composeRule.onNodeWithText("4000").assertExists()
        composeRule.runOnIdle { player.seek(0L) }
        composeRule.onNodeWithText("10000").assertExists()
    }

    @Test
    fun playbackSpeedChangesRescheduleTheNextTick() {
        val player = TestPlayer { composeRule.mainClock.currentTime }
        composeRule.setContent { CountdownHost(player.player, CompositionCounts()) }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { player.changeSpeed(2f) }

        composeRule.mainClock.advanceTimeBy(550L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("9000").assertExists()
    }

    @Test
    fun unknownDurationAndPastEndPositionsNeverDisplayNegativeTime() {
        val player = TestPlayer { composeRule.mainClock.currentTime }
        player.playing = false
        player.duration = C.TIME_UNSET
        composeRule.setContent { CountdownHost(player.player, CompositionCounts()) }
        composeRule.onNodeWithText("0").assertExists()

        composeRule.runOnIdle {
            player.duration = 5_000L
            player.seek(6_000L)
        }
        composeRule.onNodeWithText("0").assertExists()
    }

    @Test
    fun replacingOrHidingTheCountdownReleasesThePreviousPlayerObserver() {
        val first = TestPlayer { composeRule.mainClock.currentTime }
        val second = TestPlayer { composeRule.mainClock.currentTime }
        first.playing = false
        second.playing = false
        second.duration = 20_000L
        val player = mutableStateOf(first.player)
        val visible = mutableStateOf(true)
        composeRule.setContent {
            if (visible.value) CountdownHost(player.value, CompositionCounts())
        }
        composeRule.onNodeWithText("10000").assertExists()
        assertEquals(1, first.listeners.size)

        composeRule.runOnIdle { player.value = second.player }
        composeRule.onNodeWithText("20000").assertExists()
        assertTrue(first.listeners.isEmpty())
        assertEquals(1, second.listeners.size)

        composeRule.runOnIdle { visible.value = false }
        composeRule.waitForIdle()
        val readsAfterDisposal = second.positionReads
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.waitForIdle()
        assertTrue(second.listeners.isEmpty())
        assertEquals(readsAfterDisposal, second.positionReads)
    }

    @Composable
    private fun CountdownHost(
        player: Player,
        counts: CompositionCounts,
    ) {
        SideEffect { counts.parent++ }
        Box {
            VideoCountdown(player) {
                SideEffect { counts.content++ }
                Text(it.toString())
            }
        }
    }

    private class CompositionCounts {
        var parent = 0
        var content = 0
    }

    /** Media time advances with the Compose test clock; events use the real Player listener API. */
    private class TestPlayer(
        private val timeMs: () -> Long,
    ) {
        var duration = 10_000L
        var playing = true
        var positionReads = 0
        val listeners = linkedSetOf<Player.Listener>()
        private var speed = 1f
        private var basePosition = 0L
        private var baseTime = timeMs()

        private fun position(): Long = basePosition + if (playing) ((timeMs() - baseTime) * speed).toLong() else 0L

        val player: Player =
            Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, method, args ->
                when (method.name) {
                    "getApplicationLooper" -> {
                        Looper.getMainLooper()
                    }

                    "isCommandAvailable" -> {
                        true
                    }

                    "getDuration" -> {
                        duration
                    }

                    "getCurrentPosition" -> {
                        positionReads++
                        position()
                    }

                    "getBufferedPosition" -> {
                        if (duration == C.TIME_UNSET) 0L else duration
                    }

                    "getPlaybackState" -> {
                        Player.STATE_READY
                    }

                    "isPlaying" -> {
                        playing
                    }

                    "getPlaybackParameters" -> {
                        PlaybackParameters(speed)
                    }

                    "addListener" -> {
                        listeners.add(args!![0] as Player.Listener)
                        null
                    }

                    "removeListener" -> {
                        listeners.remove(args!![0] as Player.Listener)
                        null
                    }

                    "equals" -> {
                        proxy === args!![0]
                    }

                    "hashCode" -> {
                        System.identityHashCode(proxy)
                    }

                    "toString" -> {
                        "CountdownTestPlayer"
                    }

                    else -> {
                        error("Unexpected Player call: ${method.name}")
                    }
                }
            } as Player

        fun seek(position: Long) {
            basePosition = position
            baseTime = timeMs()
            emit(Player.EVENT_POSITION_DISCONTINUITY)
        }

        fun changeSpeed(value: Float) {
            basePosition = position()
            baseTime = timeMs()
            speed = value
            emit(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)
        }

        private fun emit(event: Int) {
            val events = Player.Events(FlagSet.Builder().add(event).build())
            listeners.toList().forEach { it.onEvents(player, events) }
        }
    }
}
