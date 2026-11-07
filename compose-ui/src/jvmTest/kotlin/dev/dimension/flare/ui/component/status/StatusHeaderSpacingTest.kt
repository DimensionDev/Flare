package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.platform.PlatformTextStyle
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.TranslationDisplayState
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.createSampleStatus
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.theme.PlatformTheme
import io.github.composefluent.FluentTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.time.Instant

class StatusHeaderSpacingTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<PlatformFormatter> {
                        object : PlatformFormatter {
                            override fun formatNumber(number: Long) = number.toString()

                            override fun formatRelativeInstant(instant: Instant) = "5m"

                            override fun formatFullInstant(instant: Instant) = "5m"

                            override fun formatAbsoluteInstant(instant: Instant) = "5m"
                        }
                    }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun bodyAndReplyUseTwoDpWithoutAddingToContentWarningPadding() {
        val sample = samplePost()
        var post by mutableStateOf(sample)
        showPost { post }
        for (warning in listOf(false, true)) {
            for (reply in listOf(false, true)) {
                compose.runOnIdle {
                    post =
                        sample.copy(
                            contentWarning = if (warning) UiTranslatableText("Warning".toUiPlainText()) else null,
                            replyToHandle = if (reply) "@someone@example.org" else null,
                        )
                }
                val header = compose.onNodeWithText("Reader", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val preceding =
                    if (reply) {
                        val prompt =
                            compose
                                .onNodeWithText("@someone@example.org", substring = true, useUnmergedTree = true)
                                .fetchSemanticsNode()
                                .boundsInRoot
                        assertEquals(2f, prompt.top - header.bottom, 0.1f)
                        prompt
                    } else {
                        header
                    }
                val body = compose.onNodeWithText("Body", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                if (warning) {
                    val warningBounds = compose.onNodeWithText("Warning", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertEquals(4f, warningBounds.top - preceding.bottom, 0.1f)
                    assertEquals(4f, body.top - warningBounds.bottom, 0.1f)
                } else {
                    assertEquals(2f, body.top - preceding.bottom, 0.1f)
                }
            }
        }
        compose.runOnIdle {
            post =
                sample.copy(
                    user = null,
                    replyToHandle = "@someone@example.org",
                    content = UiTranslatableText("".toUiPlainText(), "Body".toUiPlainText()),
                    translationDisplayState = TranslationDisplayState.Translated,
                )
        }
        val prompt =
            compose
                .onNodeWithText("@someone@example.org", substring = true, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val body = compose.onNodeWithText("Body", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(2f, body.top - prompt.bottom, 0.1f)
    }

    @Test
    fun mediaOnlyPostKeepsItsExistingEightDpGap() {
        val image = Files.createTempFile("header-spacing-", ".png").toFile().apply { deleteOnExit() }
        ImageIO.write(BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "png", image)
        val url = image.toPath().toUri().toString()
        val post =
            samplePost().copy(
                content = UiTranslatableText("".toUiPlainText()),
                images = persistentListOf(UiMedia.Video(url, url, "Preview", height = 200f, width = 200f)),
            )
        showPost { post }
        compose.waitUntil(5_000) {
            compose
                .onAllNodesWithContentDescription("Preview", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .any { it.boundsInRoot.height > 100 }
        }
        val header = compose.onNodeWithText("Reader", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val media =
            compose
                .onAllNodesWithContentDescription("Preview", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .maxBy { it.boundsInRoot.height }
                .boundsInRoot
        assertEquals(8f, media.top - header.bottom, 0.1f)
    }

    private fun samplePost() =
        createSampleStatus(createSampleUser().copy(nameInternal = "Reader".toUiPlainText(), avatar = null)).copy(
            content = UiTranslatableText("Body".toUiPlainText()),
            actions = persistentListOf(),
        )

    private fun showPost(post: () -> UiTimelineV2.Post) {
        compose.setContent {
            FluentTheme {
                CompositionLocalProvider(
                    LocalTimelineAppearance provides
                        TimelineAppearance(
                            fullWidthPost = false,
                            expandContentWarning = true,
                            showPlatformLogo = false,
                            videoAutoplay = VideoAutoplay.NEVER,
                        ),
                    PlatformTextStyle provides PlatformTheme.typography.body,
                    LocalDensity provides Density(1f),
                ) {
                    Box(Modifier.width(360.dp)) {
                        CommonStatusComponent(post(), isClickable = false)
                    }
                }
            }
        }
    }
}
