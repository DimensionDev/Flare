package dev.dimension.flare.ui.screen.media

import android.graphics.Bitmap
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.model.toUiImage
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MediaPostSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun draggingExpandsAndCollapsesWithoutRecreatingMedia() {
        val visible = mutableStateOf(true)
        var mediaMounts = 0
        var mediaDisposals = 0
        val post = testPost()
        composeRule.setContent {
            MaterialExpressiveTheme {
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

    @Test
    fun visibilityAnimatesAndCanReverseWithoutRecreatingMedia() {
        val visible = mutableStateOf(true)
        var bottomInset = 0.dp
        var mediaMounts = 0
        var mediaDisposals = 0
        val post = testPost()
        composeRule.setContent {
            MaterialExpressiveTheme {
                CompositionLocalProvider(LocalTimelineAppearance provides TimelineAppearance(showTranslateButton = false)) {
                    MediaPostSheet(
                        post = post,
                        quotes = persistentListOf(),
                        visible = visible.value,
                        uriHandler =
                            object : UriHandler {
                                override fun openUri(uri: String) = Unit
                            },
                    ) { inset ->
                        SideEffect { bottomInset = inset }
                        DisposableEffect(Unit) {
                            mediaMounts++
                            onDispose { mediaDisposals++ }
                        }
                        Text("Media stays mounted")
                    }
                }
            }
        }
        val initialHandle = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)).fetchSemanticsNode()
        val handle =
            composeRule.onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    initialHandle.config[SemanticsProperties.ContentDescription],
                ),
            )
        val shownInset = bottomInset
        val shownY = initialHandle.positionInRoot.y
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { visible.value = false }
        composeRule.mainClock.advanceTimeBy(64)
        // Content stays composed while moving out, and the controls follow an intermediate inset.
        handle.assertExists()
        assertTrue(handle.fetchSemanticsNode().positionInRoot.y > shownY)
        composeRule.runOnIdle { assertTrue(bottomInset > 0.dp && bottomInset < shownInset) }

        composeRule.runOnIdle { visible.value = true }
        composeRule.mainClock.advanceTimeBy(64)
        handle.assertExists()
        composeRule.mainClock.advanceTimeBy(1_000)
        handle.assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(shownInset, bottomInset) }

        composeRule.runOnIdle { visible.value = false }
        composeRule.mainClock.advanceTimeBy(1_000)
        handle.assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(0.dp, bottomInset)
            assertEquals(1, mediaMounts)
            assertEquals(0, mediaDisposals)
        }
        composeRule.runOnIdle { visible.value = true }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)).assertIsDisplayed()
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, mediaMounts)
            assertEquals(0, mediaDisposals)
        }
    }

    @Test
    fun sharedContentFollowsTheFingerAndCanReverseBeforeRelease() {
        val post = testPost()
        composeRule.setContent {
            MaterialExpressiveTheme {
                CompositionLocalProvider(LocalTimelineAppearance provides TimelineAppearance(showTranslateButton = false)) {
                    MediaPostSheet(
                        post = post,
                        quotes = persistentListOf(),
                        visible = true,
                        uriHandler =
                            object : UriHandler {
                                override fun openUri(uri: String) = Unit
                            },
                    ) { Text("Media stays mounted") }
                }
            }
        }
        val initialHandle = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)).fetchSemanticsNode()
        val handle =
            composeRule.onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    initialHandle.config[SemanticsProperties.ContentDescription],
                ),
            )
        composeRule.waitUntil(5_000) { runCatching { renderedMarkers() }.isSuccess }
        val collapsedY = initialHandle.boundsInRoot.center.y
        val (collapsedAvatar, collapsedActions) = renderedMarkers("summary")
        val collapsedAvatarSize = collapsedAvatar.width
        val collapsedActionsY = collapsedActions.top - collapsedY
        handle.performSemanticsAction(SemanticsActions.Expand) { it() }
        val expandedY =
            handle
                .fetchSemanticsNode()
                .boundsInRoot.center.y
        val (expandedAvatar, expandedActions) = renderedMarkers("expanded")
        val expandedAvatarSize = expandedAvatar.width
        val expandedActionsY = expandedActions.top - expandedY
        handle.performSemanticsAction(SemanticsActions.Collapse) { it() }
        composeRule.onNodeWithText("Full post body").assertDoesNotExist()

        composeRule.mainClock.autoAdvance = false
        val start = handle.fetchSemanticsNode().boundsInRoot.center
        val distance = collapsedY - expandedY
        composeRule.onRoot().performTouchInput {
            down(start)
            moveTo(start - Offset(0f, distance * 0.35f))
        }
        composeRule.mainClock.advanceTimeBy(96)
        repeat(3) {
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeByFrame()
        }
        val firstY =
            handle
                .fetchSemanticsNode()
                .boundsInRoot.center.y
        val fraction = (collapsedY - firstY) / distance
        assertTrue("Expected a partial drag, got $fraction", fraction > 0.1f && fraction < 0.5f)
        val (firstAvatar, firstActions) = renderedMarkers("partial")
        val firstAvatarSize = firstAvatar.width
        val firstActionsY = firstActions.top - firstY
        assertEquals(collapsedAvatarSize + (expandedAvatarSize - collapsedAvatarSize) * fraction, firstAvatarSize, 2f)
        assertEquals(collapsedActionsY + (expandedActionsY - collapsedActionsY) * fraction, firstActionsY, 3f)

        // Advancing time without moving the finger must not finish the content animation.
        composeRule.mainClock.advanceTimeBy(500)
        assertEquals(firstAvatarSize, renderedMarkers().first.width, 1f)
        assertEquals(
            firstY,
            handle
                .fetchSemanticsNode()
                .boundsInRoot.center.y,
            1f,
        )
        composeRule.onRoot().performTouchInput { moveTo(start - Offset(0f, distance * 0.7f)) }
        composeRule.mainClock.advanceTimeBy(96)
        assertTrue(renderedMarkers().first.width > firstAvatarSize)
        composeRule.onRoot().performTouchInput { moveTo(start - Offset(0f, distance * 0.35f)) }
        composeRule.mainClock.advanceTimeBy(96)
        val (reversedAvatar, reversedActions) = renderedMarkers("reversed")
        assertEquals(firstAvatarSize, reversedAvatar.width, 2f)
        assertEquals(
            firstActionsY,
            reversedActions.top -
                handle
                    .fetchSemanticsNode()
                    .boundsInRoot.center.y,
            3f,
        )

        composeRule.onRoot().performTouchInput {
            moveTo(start - Offset(0f, distance * 0.7f))
            advanceEventTime(200)
            up()
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithText("Full post body").assertIsDisplayed()
        assertEquals(expandedAvatarSize, renderedMarkers().first.width, 1f)
        val expandedStart = handle.fetchSemanticsNode().boundsInRoot.center
        composeRule.mainClock.autoAdvance = false
        composeRule.onRoot().performTouchInput {
            down(expandedStart)
            moveTo(expandedStart + Offset(0f, distance * 0.4f))
        }
        repeat(4) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        val shrinkingY =
            handle
                .fetchSemanticsNode()
                .boundsInRoot.center.y
        val shrinkingFraction = (collapsedY - shrinkingY) / distance
        val (shrinkingAvatar, shrinkingActions) = renderedMarkers("collapsing")
        assertTrue(shrinkingFraction > 0.4f && shrinkingFraction < 0.9f)
        assertEquals(collapsedAvatarSize + (expandedAvatarSize - collapsedAvatarSize) * shrinkingFraction, shrinkingAvatar.width, 2f)
        assertEquals(collapsedActionsY + (expandedActionsY - collapsedActionsY) * shrinkingFraction, shrinkingActions.top - shrinkingY, 3f)
        composeRule.onRoot().performTouchInput {
            moveTo(expandedStart)
            advanceEventTime(200)
            up()
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithText("Full post body").assertIsDisplayed()
        assertEquals(expandedAvatarSize, renderedMarkers().first.width, 1f)
        // The native handle remains the only explicit collapse control.
        composeRule.onNodeWithContentDescription("Collapse post").assertDoesNotExist()
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse)).fetchSemanticsNodes().let {
            assertEquals(1, it.size)
        }
        handle.performSemanticsAction(SemanticsActions.Collapse) { it() }
        composeRule.onNodeWithText("Full post body").assertDoesNotExist()
        assertEquals(collapsedAvatarSize, renderedMarkers().first.width, 1f)
    }

    @Test
    fun longPostScrollsBeforeTheSheetCollapsesAndBackReturnsToSummary() {
        val post = testPost().copy(content = UiTranslatableText("Long post paragraph\n".repeat(100).toUiPlainText()))
        lateinit var backDispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            MaterialExpressiveTheme {
                CompositionLocalProvider(LocalTimelineAppearance provides TimelineAppearance(showTranslateButton = false)) {
                    MediaPostSheet(
                        post = post,
                        quotes = persistentListOf(),
                        visible = true,
                        uriHandler =
                            object : UriHandler {
                                override fun openUri(uri: String) = Unit
                            },
                    ) { Text("Media stays mounted") }
                }
            }
        }
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)).performSemanticsAction(SemanticsActions.Expand) { it() }
        val handle = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
        val expandedY = handle.fetchSemanticsNode().boundsInRoot.top
        val scroll = composeRule.onNode(hasScrollAction())
        scroll.performTouchInput { swipe(center, center - Offset(0f, 500f)) }
        assertTrue(scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        assertEquals(expandedY, handle.fetchSemanticsNode().boundsInRoot.top, 1f)
        scroll.performTouchInput { swipe(center, center + Offset(0f, 150f)) }
        assertEquals(expandedY, handle.fetchSemanticsNode().boundsInRoot.top, 1f)

        val scrollOffset = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -scrollOffset) }
        assertEquals(0f, scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 1f)
        scroll.performTouchInput { swipe(center, center + Offset(0f, 500f)) }
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Like post").assertIsDisplayed()
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)).performSemanticsAction(SemanticsActions.Expand) { it() }
        handle.assertIsDisplayed()
        composeRule.runOnIdle { backDispatcher.onBackPressed() }
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Like post").assertIsDisplayed()
    }

    // Shared elements draw in an overlay: semantic bounds describe their placeholders, so
    // inspect the rendered avatar and action icon to verify the actual in-between geometry.
    private fun renderedMarkers(name: String? = null): Pair<Rect, Rect> {
        val image = composeRule.onRoot().captureToImage()
        if (name != null) {
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "media-post-$name.png")
                .outputStream()
                .use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val pixels = image.toPixelMap()

        fun bounds(predicate: (Color) -> Boolean): Rect {
            var left = pixels.width
            var top = pixels.height
            var right = -1
            var bottom = -1
            for (y in 0 until pixels.height) {
                for (x in 0 until pixels.width) {
                    if (predicate(pixels[x, y])) {
                        left = minOf(left, x)
                        top = minOf(top, y)
                        right = maxOf(right, x)
                        bottom = maxOf(bottom, y)
                    }
                }
            }
            check(right >= left && bottom >= top) { "Marker not rendered" }
            return Rect(left.toFloat(), top.toFloat(), (right + 1).toFloat(), (bottom + 1).toFloat())
        }
        return bounds { it.red - it.green > 0.3f && it.blue - it.green > 0.3f } to
            bounds { it.red - it.green > 0.3f && it.red - it.blue > 0.3f }
    }

    private val avatarFile by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "media-post-avatar.png").apply {
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.MAGENTA)
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun testPost() =
        UiTimelineV2.Post(
            platformId = "mastodon",
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = createSampleUser().copy(avatar = avatarFile.toURI().toString().toUiImage()),
            content = UiTranslatableText("Full post body".toUiPlainText()),
            actions =
                persistentListOf(
                    ActionMenu.Item(icon = UiIcon.Like, text = ActionMenu.Item.Text.Raw("Like post"), color = ActionMenu.Item.Color.Red),
                ),
            poll = null,
            statusKey = MicroBlogKey("post", "example.com"),
            card = null,
            createdAt = Instant.fromEpochMilliseconds(0).toUi(),
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Guest,
        )
}
