package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.platform.PlatformCircularProgressIndicator
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.theme.PlatformTheme
import io.github.composefluent.FluentTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaVideoBadgeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun badgeReservesSpaceForLoadingAndCountdownBeforePadding() {
        var width by mutableStateOf(180.dp)
        var height by mutableStateOf(200.dp)
        var loading by mutableStateOf(true)
        var textOverflows = false
        compose.setContent {
            FluentTheme {
                Box(Modifier.size(width, height).testTag("media")) {
                    MediaVideoBadge(Modifier.align(Alignment.BottomStart)) {
                        if (loading) {
                            PlatformCircularProgressIndicator(Modifier.size(16.dp).testTag("spinner"))
                        } else {
                            PlatformText(
                                "1:23",
                                style = PlatformTheme.typography.caption,
                                maxLines = 1,
                                onTextLayout = { textOverflows = it.hasVisualOverflow },
                            )
                        }
                    }
                }
            }
        }
        for ((w, h) in listOf(180.dp to 200.dp, 40.dp to 200.dp, 16.dp to 200.dp, 180.dp to 16.dp)) {
            compose.runOnIdle {
                width = w
                height = h
            }
            val media =
                compose
                    .onNodeWithTag("media")
                    .assertWidthIsEqualTo(w)
                    .assertHeightIsEqualTo(h)
                    .fetchSemanticsNode()
                    .boundsInRoot
            val spinner =
                compose
                    .onNodeWithTag("spinner")
                    .assertIsDisplayed()
                    .assertWidthIsEqualTo(16.dp)
                    .assertHeightIsEqualTo(16.dp)
                    .fetchSemanticsNode()
                    .boundsInRoot
            assertTrue(
                spinner.left >= media.left && spinner.top >= media.top &&
                    spinner.right <= media.right && spinner.bottom <= media.bottom,
            )
            if (w == 180.dp && h == 200.dp) {
                assertEquals(24f, spinner.left - media.left, 0.5f)
                assertEquals(20f, media.bottom - spinner.bottom, 0.5f)
            }
        }
        compose.runOnIdle {
            loading = false
            width = 40.dp
            height = 200.dp
        }
        val text =
            compose
                .onNodeWithText("1:23")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val media = compose.onNodeWithTag("media").fetchSemanticsNode().boundsInRoot
        assertTrue(
            text.left >= media.left && text.top >= media.top &&
                text.right <= media.right && text.bottom <= media.bottom,
        )
        compose.runOnIdle { assertEquals(false, textOverflows) }
    }
}
