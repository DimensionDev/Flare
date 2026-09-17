package dev.dimension.flare.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.mastodon_item_show_more
import dev.dimension.flare.ui.component.status.StatusReactionRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineItemPerformanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gridOnlyComposesVisibleMediaAndRetainsOverflowCount() {
        val mounted = mutableSetOf<Int>()
        val measured = mutableSetOf<Int>()
        var overflow = -1
        composeRule.setContent {
            Box(Modifier.size(300.dp)) {
                AdaptiveGrid(
                    maxItems = 9,
                    itemCount = 12,
                    itemContent = { index ->
                        DisposableEffect(index) {
                            mounted.add(index)
                            onDispose { mounted.remove(index) }
                        }
                        Box(
                            Modifier.layout { measurable, constraints ->
                                measured.add(index)
                                val child = measurable.measure(constraints)
                                layout(child.width, child.height) { child.place(0, 0) }
                            },
                        )
                    },
                    overflowContent = { count ->
                        SideEffect { overflow = count }
                        Box(Modifier)
                    },
                )
            }
        }
        composeRule.runOnIdle {
            assertEquals((0 until 9).toSet(), mounted)
            assertEquals((0 until 9).toSet(), measured)
            assertEquals(3, overflow)
        }
    }

    @Test
    fun reactionRowsOnlyComposeVisibleItemsAndBoundedLookahead() {
        val mounted = mutableSetOf<Int>()
        val initialized = mutableSetOf<Int>()
        val placed = mutableSetOf<Int>()
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.size(140.dp, 500.dp)) {
                    StatusReactionRow(itemCount = 20, isDetail = false) { index ->
                        remember { initialized.add(index) }
                        DisposableEffect(index) {
                            mounted.add(index)
                            onDispose { mounted.remove(index) }
                        }
                        Box(Modifier.size(40.dp).onPlaced { placed.add(index) })
                    }
                }
            }
        }
        composeRule.runOnIdle {
            assertTrue(placed.isNotEmpty())
            assertTrue(placed.size <= 6)
            // One lookahead item and room for the overflow indicator may be measured.
            assertTrue("Composed ${mounted.size} reactions", mounted.size <= 8)
            assertTrue("Initialized ${initialized.size} reactions", initialized.size <= 8)
        }
    }

    @Test
    fun gridReleasesHiddenItemsWhenTheLimitOrDataChanges() {
        val itemCount = mutableIntStateOf(12)
        val limit = mutableIntStateOf(9)
        val mounted = mutableSetOf<Int>()
        var overflow: Int? = null
        composeRule.setContent {
            Box(Modifier.size(300.dp, 500.dp)) {
                AdaptiveGrid(
                    itemCount = itemCount.intValue,
                    maxItems = limit.intValue,
                    itemContent = { index ->
                        DisposableEffect(index) {
                            mounted.add(index)
                            onDispose { mounted.remove(index) }
                        }
                        Box(Modifier.size(40.dp))
                    },
                    overflowContent = { count ->
                        DisposableEffect(count) {
                            overflow = count
                            onDispose { overflow = null }
                        }
                        Box(Modifier)
                    },
                )
            }
        }

        fun assertItems(
            visible: Int,
            hidden: Int?,
        ) {
            composeRule.runOnIdle {
                assertEquals((0 until visible).toSet(), mounted)
                assertEquals(hidden, overflow)
            }
        }
        assertItems(9, 3)
        composeRule.runOnIdle { limit.intValue = 12 }
        assertItems(12, null)
        composeRule.runOnIdle { itemCount.intValue = 5 }
        assertItems(5, null)
        composeRule.runOnIdle { limit.intValue = 3 }
        assertItems(3, 2)
        composeRule.runOnIdle { itemCount.intValue = 0 }
        assertItems(0, null)
        composeRule.runOnIdle { itemCount.intValue = 1 }
        assertItems(1, null)
    }

    @Test
    fun reactionsSupportResizeDetailRtlAndClicks() {
        val itemCount = mutableIntStateOf(20)
        val detail = mutableStateOf(false)
        val width = mutableStateOf(140.dp)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val mounted = mutableSetOf<Int>()
        val left = mutableMapOf<Int, Float>()
        var moreLabel = ""
        var clicks = 0

        fun assertOverflowVisible(visible: Boolean) {
            // The lazy layout also subcomposes an unplaced indicator to measure its size.
            val nodes = composeRule.onAllNodesWithText(moreLabel)
            val displayed = nodes.fetchSemanticsNodes().indices.count { nodes[it].isDisplayed() }
            assertEquals(if (visible) 1 else 0, displayed)
        }
        composeRule.setContent {
            MaterialTheme {
                moreLabel = stringResource(Res.string.mastodon_item_show_more)
                CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                    Box(Modifier.size(width.value, 500.dp)) {
                        StatusReactionRow(itemCount = itemCount.intValue, isDetail = detail.value) { index ->
                            DisposableEffect(index) {
                                mounted.add(index)
                                onDispose { mounted.remove(index) }
                            }
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .testTag("reaction-$index")
                                    .clickable { clicks++ }
                                    .onPlaced { left[index] = it.positionInParent().x },
                            )
                        }
                    }
                }
            }
        }
        assertOverflowVisible(true)
        composeRule.runOnIdle { assertTrue(mounted.size <= 8) }
        composeRule.runOnIdle { width.value = 280.dp }
        composeRule.runOnIdle { assertTrue(mounted.size in 9..14) }
        composeRule.runOnIdle { detail.value = true }
        composeRule.runOnIdle { assertEquals((0 until 20).toSet(), mounted) }
        composeRule.onNodeWithTag("reaction-19").assertIsDisplayed()
        assertOverflowVisible(false)
        composeRule.runOnIdle {
            detail.value = false
            width.value = 140.dp
        }
        composeRule.runOnIdle { assertTrue(mounted.size <= 8) }
        assertOverflowVisible(true)
        composeRule.runOnIdle { itemCount.intValue = 2 }
        composeRule.runOnIdle {
            assertEquals(setOf(0, 1), mounted)
            assertTrue(left.getValue(0) < left.getValue(1))
        }
        assertOverflowVisible(false)
        composeRule.runOnIdle { direction.value = LayoutDirection.Rtl }
        composeRule.runOnIdle { assertTrue(left.getValue(0) > left.getValue(1)) }
        composeRule.onNodeWithTag("reaction-0").performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun viewportChangesDoNotRecomposeTheVideoHost() {
        val counts = Counts()
        withPlayback { playback ->
            composeRule.setContent { AutoplayHost(playback, counts) }
            composeRule.waitForIdle()
            val initial = counts.host
            repeat(30) { index ->
                composeRule.runOnIdle {
                    playback.viewport = Rect(0f, index.toFloat(), 400f, 800f)
                    playback.multipleColumns = index % 2 == 0
                }
                composeRule.waitForIdle()
            }
            composeRule.runOnIdle { assertEquals(initial, counts.host) }
        }
    }

    @Test
    fun viewportOnlyChangesStillPauseAndResumeStationaryVideo() {
        val counts = Counts()
        var playing = false
        withPlayback { playback ->
            composeRule.runOnUiThread { playback.register("video") { playing = it } }
            composeRule.setContent { AutoplayHost(playback, counts) }
            composeRule.waitUntil(2_000) { playing }
            val initial = counts.host
            composeRule.runOnIdle { playback.viewport = Rect.Zero }
            composeRule.waitUntil(2_000) { !playing }
            composeRule.runOnIdle { assertEquals(initial, counts.host) }
            composeRule.runOnIdle { playback.viewport = counts.bounds }
            composeRule.waitUntil(2_000) { playing }
            composeRule.runOnIdle { assertEquals(initial, counts.host) }
        }
    }

    @Test
    fun columnModeChangesReevaluateStationaryVideoDistances() {
        val first = Counts()
        val second = Counts()
        var playing: String? = null
        withPlayback { playback ->
            composeRule.runOnUiThread {
                for (id in listOf("first", "second")) {
                    playback.register(id) { active ->
                        if (active) {
                            playing = id
                        } else if (playing == id) {
                            playing = null
                        }
                    }
                }
            }
            composeRule.setContent {
                Box(Modifier.size(300.dp).onGloballyPositioned { playback.viewport = it.boundsInWindow() }) {
                    AutoplayHost(playback, first, id = "first", modifier = Modifier.offset(0.dp, 100.dp))
                    AutoplayHost(playback, second, id = "second", modifier = Modifier.offset(100.dp, 40.dp))
                }
            }
            composeRule.waitUntil(2_000) { playing == "first" }
            val initial = first.host to second.host
            composeRule.runOnIdle { playback.multipleColumns = true }
            composeRule.waitUntil(2_000) { playing == "second" }
            composeRule.runOnIdle { assertEquals(initial, first.host to second.host) }
            composeRule.runOnIdle { playback.multipleColumns = false }
            composeRule.waitUntil(2_000) { playing == "first" }
        }
    }

    private fun withPlayback(action: (TimelinePlaybackCoordinator) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var playback: TimelinePlaybackCoordinator
        composeRule.runOnUiThread { playback = TimelinePlaybackCoordinator(scope, VideoPlaybackArbiter()) }
        try {
            action(playback)
        } finally {
            composeRule.runOnUiThread { playback.close() }
            scope.cancel()
        }
    }

    @Composable
    private fun AutoplayHost(
        playback: TimelinePlaybackCoordinator,
        counts: Counts,
        id: String = "video",
        modifier: Modifier = Modifier,
    ) {
        SideEffect { counts.host++ }
        Box(
            modifier
                .size(100.dp)
                .timelineVideoAutoplay(playback, id = id, enabled = true, mediaUri = "test://$id")
                .onGloballyPositioned { counts.bounds = it.boundsInWindow() },
        )
    }

    private class Counts {
        var host = 0
        var bounds = Rect.Zero
    }
}
