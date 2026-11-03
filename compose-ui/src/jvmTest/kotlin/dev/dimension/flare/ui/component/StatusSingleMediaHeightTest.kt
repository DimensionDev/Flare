package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.media_play_video
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.ui.component.status.StatusMediaComponent
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.createSampleStatus
import dev.dimension.flare.ui.model.createSampleUser
import io.github.composefluent.FluentTheme
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusSingleMediaHeightTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun narrowCappedVideoKeepsThePlayIndicatorAndMediaClick() {
        var mediaWidth by mutableStateOf(200f)
        var opens = 0
        var playDescription = ""
        val samplePost = createSampleStatus(createSampleUser())
        compose.setContent {
            val window =
                object : WindowInfo by LocalWindowInfo.current {
                    override val containerSize = IntSize(412, 400)
                }
            val media = UiMedia.Video("", "", null, height = 1_000f, width = mediaWidth)
            val post = samplePost.copy(images = persistentListOf(media))
            FluentTheme {
                playDescription = stringResource(Res.string.media_play_video)
                CompositionLocalProvider(
                    LocalTimelineAppearance provides TimelineAppearance(limitSingleMediaHeight = true, videoAutoplay = VideoAutoplay.NEVER),
                    LocalWindowInfo provides window,
                    LocalDensity provides Density(1f),
                ) {
                    Box(Modifier.requiredSize(412.dp, 400.dp)) {
                        Row {
                            Spacer(Modifier.width(52.dp))
                            StatusMediaComponent(
                                post = post,
                                data = post.images,
                                onMediaClick = { opens++ },
                                sensitive = false,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("media"),
                            )
                        }
                    }
                }
            }
        }
        for ((width, expectedWidth) in listOf(200f to 40.dp, 80f to 16.dp, 900f to 180.dp)) {
            compose.runOnIdle { mediaWidth = width }
            val mediaBounds =
                compose
                    .onNodeWithTag("media")
                    .assertWidthIsEqualTo(expectedWidth)
                    .assertHeightIsEqualTo(200.dp)
                    .fetchSemanticsNode()
                    .boundsInRoot
            val play =
                compose
                    .onNodeWithContentDescription(playDescription, useUnmergedTree = true)
                    .assertIsDisplayed()
                    .assertWidthIsEqualTo(16.dp)
                    .assertHeightIsEqualTo(16.dp)
            val playBounds = play.fetchSemanticsNode().boundsInRoot
            assertTrue(
                playBounds.left >= mediaBounds.left && playBounds.top >= mediaBounds.top &&
                    playBounds.right <= mediaBounds.right && playBounds.bottom <= mediaBounds.bottom,
            )
            play.performTouchInput { click() }
        }
        compose.runOnIdle { assertEquals(3, opens) }
    }

    @Test
    fun limitRequiresOptInAndRestoresTheOriginalWidthWhenDisabled() {
        val file = Files.createTempFile("single-media", ".png").toFile().apply { deleteOnExit() }
        ImageIO.write(BufferedImage(360, 640, BufferedImage.TYPE_INT_RGB), "png", file)
        val media = UiMedia.Image(file.toURI().toString(), file.toURI().toString(), null, height = 640f, width = 360f, sensitive = false)
        var appearance by mutableStateOf(TimelineAppearance.Default)
        var post by mutableStateOf(createSampleStatus(createSampleUser()).copy(images = persistentListOf(media)))
        compose.setContent {
            val window =
                object : WindowInfo by LocalWindowInfo.current {
                    override val containerSize = IntSize(412, 800)
                }
            FluentTheme {
                CompositionLocalProvider(
                    LocalTimelineAppearance provides appearance,
                    LocalWindowInfo provides window,
                    LocalDensity provides Density(1f),
                ) {
                    Box(Modifier.requiredSize(412.dp, 900.dp)) {
                        Row {
                            Spacer(Modifier.width(52.dp))
                            StatusMediaComponent(
                                post = post,
                                data = persistentListOf(media),
                                onMediaClick = {},
                                sensitive = false,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("media"),
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("media").assertWidthIsEqualTo(360.dp).assertHeightIsEqualTo(640.dp)
        compose.runOnIdle { appearance = appearance.copy(limitSingleMediaHeight = true) }
        compose.onNodeWithTag("media").assertWidthIsEqualTo(225.dp).assertHeightIsEqualTo(400.dp)
        compose.runOnIdle { appearance = appearance.copy(expandMediaSize = false) }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose
                .onNodeWithTag("media")
                .fetchSemanticsNode()
                .boundsInRoot.height > 0
        }
        compose.onNodeWithTag("media").assertWidthIsEqualTo(360.dp).assertHeightIsEqualTo(202.dp)
        compose.runOnIdle { appearance = appearance.copy(expandMediaSize = true) }
        compose.onNodeWithTag("media").assertWidthIsEqualTo(225.dp).assertHeightIsEqualTo(400.dp)
        compose.runOnIdle { appearance = appearance.copy(limitSingleMediaHeight = false) }
        compose.onNodeWithTag("media").assertWidthIsEqualTo(360.dp).assertHeightIsEqualTo(640.dp)
        compose.runOnIdle {
            appearance = appearance.copy(limitSingleMediaHeight = true)
            post = post.copy(images = persistentListOf(media, media))
        }
        compose.onNodeWithTag("media").assertWidthIsEqualTo(360.dp).assertHeightIsEqualTo(640.dp)
    }
}
