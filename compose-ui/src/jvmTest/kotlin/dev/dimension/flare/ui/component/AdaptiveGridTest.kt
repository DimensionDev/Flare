package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AdaptiveGridTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shrinkingMediaRetainsTheRemainingItem() {
        var media by mutableStateOf(mediaItems(2))
        val probe = MediaProbe()
        composeRule.setContent { probe.Grid(media) }
        val retained =
            composeRule.runOnIdle {
                probe.instances.getValue("media-0").also { media = mediaItems(1) }
            }
        composeRule.runOnIdle {
            assertEquals(setOf("media-0"), probe.instances.keys)
            assertSame(retained, probe.instances.getValue("media-0"))
            assertEquals(listOf("media-0", "media-1"), probe.created)
            assertEquals(listOf("media-1"), probe.disposed)
        }
    }

    @Test
    fun changingMediaCountAboveTheLimitKeepsTheVisibleItems() {
        var media by mutableStateOf(mediaItems(9))
        val probe = MediaProbe()
        composeRule.setContent { probe.Grid(media) }
        val retained = composeRule.runOnIdle { probe.instances.toMap() }
        for (count in listOf(12, 10, 9)) {
            composeRule.runOnIdle { media = mediaItems(count) }
            composeRule.runOnIdle {
                assertEquals(retained, probe.instances)
                assertEquals(9, probe.created.size)
                assertTrue(probe.disposed.isEmpty())
                assertEquals((count - 9).takeIf { it > 0 }, probe.overflowCount)
            }
        }
    }

    @Test
    fun mediaCanBecomeEmptyAndBePopulatedAgain() {
        var media by mutableStateOf(mediaItems(2))
        val probe = MediaProbe()
        composeRule.setContent { probe.Grid(media) }
        composeRule.runOnIdle { media = mediaItems(0) }
        composeRule.runOnIdle {
            assertTrue(probe.instances.isEmpty())
            assertEquals(setOf("media-0", "media-1"), probe.disposed.toSet())
            assertNull(probe.overflowCount)
            media = mediaItems(3)
        }
        composeRule.runOnIdle {
            assertEquals(media.toSet(), probe.instances.keys)
            assertEquals(5, probe.created.size)
        }
    }

    @Test
    fun zeroVisibleItemsComposeNeitherMediaNorOverflow() {
        val probe = MediaProbe()
        composeRule.setContent { probe.Grid(mediaItems(3), maxItems = 0) }
        composeRule.runOnIdle {
            assertTrue(probe.created.isEmpty())
            assertNull(probe.overflowCount)
        }
    }

    @Test
    fun overflowCoversTheLastVisibleCellInBothDirections() {
        var maxItems by mutableIntStateOf(1)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.size(width = 240.dp, height = 600.dp)) {
                    AdaptiveGrid(
                        itemCount = 12,
                        itemContent = { Box(Modifier.fillMaxSize().testTag("media-$it")) },
                        maxItems = maxItems,
                        overflowContent = { Box(Modifier.fillMaxSize().testTag("overflow")) },
                    )
                }
            }
        }
        for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            for (limit in listOf(1, 2, 3, 4, 5, 7, 8, 9)) {
                composeRule.runOnIdle {
                    direction = layoutDirection
                    maxItems = limit
                }
                assertEquals(
                    composeRule.onNodeWithTag("media-${limit - 1}").fetchSemanticsNode().boundsInRoot,
                    composeRule.onNodeWithTag("overflow").fetchSemanticsNode().boundsInRoot,
                    "The overflow should cover the last cell for $limit items in $layoutDirection",
                )
            }
        }
    }

    private fun mediaItems(count: Int): ImmutableList<String> = (0 until count).map { "media-$it" }.toImmutableList()

    private class MediaProbe {
        val instances = mutableMapOf<String, Any>()
        val created = mutableListOf<String>()
        val disposed = mutableListOf<String>()
        var overflowCount: Int? = null

        @Composable
        fun Grid(
            media: ImmutableList<String>,
            maxItems: Int = 9,
        ) {
            Box(Modifier.size(width = 240.dp, height = 600.dp)) {
                AdaptiveGrid(
                    itemCount = media.size,
                    itemContent = { index ->
                        // Match StatusMediaComponent's indexed callback and nested composition.
                        CompositionLocalProvider(LocalMediaProbe provides true) {
                            Item(media[index])
                        }
                    },
                    maxItems = maxItems,
                    overflowContent = { count ->
                        DisposableEffect(count) {
                            overflowCount = count
                            onDispose { overflowCount = null }
                        }
                        Box(Modifier.fillMaxSize())
                    },
                )
            }
        }

        @Composable
        private fun Item(id: String) {
            val instance = remember { Any() }
            DisposableEffect(id) {
                instances[id] = instance
                created += id
                onDispose {
                    instances.remove(id)
                    disposed += id
                }
            }
            Box(Modifier.fillMaxSize())
        }
    }
}

private val LocalMediaProbe = compositionLocalOf { false }
