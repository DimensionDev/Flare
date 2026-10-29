package dev.dimension.flare.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.asType
import io.github.composefluent.FluentTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(coil3.annotation.DelicateCoilApi::class)
class TabIconTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val requests = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        SingletonImageLoader.setUnsafe { context ->
            ImageLoader
                .Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            requests += chain.request.data.toString()
                            ErrorResult(null, chain.request, IOException("Missing local icon"))
                        },
                    )
                }.build()
        }
    }

    @After
    fun tearDown() {
        SingletonImageLoader.reset()
    }

    @Test
    fun materialIconsStayOnTheSynchronousPathAndKeepTheirSizeAndLabel() {
        composeRule.setContent {
            FluentTheme { TabIcon(UiIcon.Mastodon.asType(), "Mastodon", size = 14.dp) }
        }
        composeRule.onNodeWithContentDescription("Mastodon").assertWidthIsEqualTo(14.dp).assertHeightIsEqualTo(14.dp)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun recompositionKeepsTheRequestAndChangedIconsLoadTheNewSource() {
        var icon by mutableStateOf<IconType>(IconType.Url("file:///plugins/first.png"))
        var label by mutableStateOf("First")
        composeRule.setContent {
            FluentTheme { TabIcon(icon, label, size = 24.dp) }
        }
        composeRule.waitUntil { requests.size == 1 }
        composeRule.runOnIdle { label = "Renamed" }
        composeRule.onNodeWithContentDescription("Renamed").assertWidthIsEqualTo(24.dp)
        assertEquals(1, requests.size)
        composeRule.runOnIdle { icon = IconType.Url("file:///plugins/second.png") }
        composeRule.waitUntil { requests.size == 2 }
        assertEquals(listOf("file:///plugins/first.png", "file:///plugins/second.png"), requests.toList())
    }

    @Test
    fun missingImagesKeepTheirSizeWithoutAWorldFallback() {
        composeRule.setContent {
            FluentTheme {
                Column(Modifier.background(Color.White)) {
                    TabIcon(IconType.Url("file:///plugins/missing.png"), modifier = Modifier.testTag("image"), size = 24.dp)
                    Box(Modifier.size(24.dp).testTag("empty"))
                }
            }
        }
        composeRule.waitUntil { requests.size == 1 }
        composeRule.waitForIdle()
        val actual = composeRule.onNodeWithTag("image").captureToImage().toPixelMap()
        val expected = composeRule.onNodeWithTag("empty").captureToImage().toPixelMap()
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        for (y in 0 until actual.height) {
            for (x in 0 until actual.width) assertEquals(expected[x, y], actual[x, y], "pixel $x,$y")
        }
    }
}
