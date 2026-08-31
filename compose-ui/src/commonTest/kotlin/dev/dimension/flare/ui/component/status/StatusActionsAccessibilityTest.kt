package dev.dimension.flare.ui.component.status

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.toImageVector
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class StatusActionsAccessibilityTest {
    @Test
    fun replyActionExposesOneLabeledClickTarget() =
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalTimelineAppearance provides TimelineAppearance.Default,
                ) {
                    StatusActions(
                        items =
                            persistentListOf(
                                ActionMenu.Item(
                                    icon = UiIcon.Reply,
                                    text =
                                        ActionMenu.Item.Text.Localized(
                                            ActionMenu.Item.Text.Localized.Type.Reply,
                                        ),
                                ),
                            ),
                    )
                }
            }

            onAllNodes(hasClickAction()).assertCountEquals(1)
            onAllNodes(hasClickAction() and hasContentDescription("Reply")).assertCountEquals(1)
        }

    @Test
    fun reportedActionButtonsExposeTheirNames() =
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalTimelineAppearance provides TimelineAppearance.Default,
                ) {
                    StatusActions(
                        items =
                            persistentListOf(
                                action(ActionMenu.Item.Text.Localized.Type.Reply, UiIcon.Reply),
                                action(ActionMenu.Item.Text.Localized.Type.Retweet, UiIcon.Retweet),
                                action(ActionMenu.Item.Text.Localized.Type.Like, UiIcon.Heart),
                                action(ActionMenu.Item.Text.Localized.Type.More, UiIcon.More),
                            ),
                    )
                }
            }

            onAllNodes(hasClickAction()).assertCountEquals(4)
            onAllNodes(hasContentDescription("Reply")).assertCountEquals(1)
            onAllNodes(hasContentDescription("Repost")).assertCountEquals(1)
            onAllNodes(hasContentDescription("Like")).assertCountEquals(1)
            onAllNodes(hasContentDescription("More")).assertCountEquals(1)
        }

    @Test
    fun fixedWidthPlaceholderIsHiddenFromAccessibility() =
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalTimelineAppearance provides TimelineAppearance.Default,
                ) {
                    StatusActionButton(
                        icon = UiIcon.Heart.toImageVector(),
                        number = null,
                        onClicked = {},
                        contentDescription = "Like",
                        withTextMinWidth = true,
                    )
                }
            }

            onAllNodes(hasText("0000")).assertCountEquals(0)
        }

    @Test
    fun imageWithoutAltUsesFallbackLabel() =
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalTimelineAppearance provides TimelineAppearance.Default,
                ) {
                    MediaItem(
                        media =
                            UiMedia.Image(
                                url = "https://example.invalid/image.jpg",
                                previewUrl = "https://example.invalid/image.jpg",
                                description = null,
                                height = 100f,
                                width = 100f,
                                sensitive = false,
                            ),
                    )
                }
            }

            onAllNodes(hasContentDescription("Image, no alternative text provided")).assertCountEquals(1)
        }

    @Test
    fun videoWithoutAltUsesFallbackLabel() =
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalTimelineAppearance provides TimelineAppearance.Default,
                ) {
                    MediaItem(
                        media =
                            UiMedia.Video(
                                url = "https://example.invalid/video.mp4",
                                thumbnailUrl = "https://example.invalid/video.jpg",
                                description = "",
                                height = 100f,
                                width = 100f,
                            ),
                    )
                }
            }

            onAllNodes(hasContentDescription("Video, no alternative text provided")).assertCountEquals(1)
        }

    private fun action(
        type: ActionMenu.Item.Text.Localized.Type,
        icon: UiIcon,
    ): ActionMenu.Item =
        ActionMenu.Item(
            icon = icon,
            text = ActionMenu.Item.Text.Localized(type),
        )
}
