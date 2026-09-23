package dev.dimension.flare.ui.screen.media

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

class MediaPostSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun draggingExpandsAndCollapsesWithoutRecreatingMedia() {
        val visible = mutableStateOf(true)
        var mediaMounts = 0
        var mediaDisposals = 0
        val post =
            UiTimelineV2.Post(
                platformId = "mastodon",
                images = persistentListOf(),
                sensitive = false,
                contentWarning = null,
                user = null,
                content = UiTranslatableText("Full post body".toUiPlainText()),
                actions = persistentListOf(),
                poll = null,
                statusKey = MicroBlogKey("post", "example.com"),
                card = null,
                createdAt = Instant.fromEpochMilliseconds(0).toUi(),
                clickEvent = ClickEvent.Noop,
                accountType = AccountType.Guest,
            )
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalTimelineAppearance provides TimelineAppearance(showTranslateButton = false)) {
                    MediaPostSheet(
                        post = post,
                        quotes = persistentListOf(),
                        visible = visible.value,
                        uriHandler =
                            object : UriHandler {
                                override fun openUri(uri: String) = Unit
                            },
                    ) {
                        DisposableEffect(Unit) {
                            mediaMounts++
                            onDispose { mediaDisposals++ }
                        }
                        Text("Media stays mounted")
                    }
                }
            }
        }
        composeRule.onNodeWithText("Full post body").assertDoesNotExist()
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)).performTouchInput {
            swipe(center, center - Offset(0f, 600f))
        }
        composeRule.onNodeWithText("Full post body").assertIsDisplayed()
        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
            .performSemanticsAction(SemanticsActions.Collapse) { it() }
        composeRule.onNodeWithText("Full post body").assertDoesNotExist()

        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
            .performSemanticsAction(SemanticsActions.Expand) { it() }
        composeRule.onNodeWithText("Full post body").assertIsDisplayed()
        composeRule.runOnIdle { visible.value = false }
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)).assertDoesNotExist()
        composeRule.runOnIdle { visible.value = true }
        composeRule.onNodeWithText("Full post body").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, mediaMounts)
            assertEquals(0, mediaDisposals)
        }
    }
}
