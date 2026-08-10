package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.ComposeInitialTextContext
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

class VvoComposeInitialTextTest {
    @Test
    fun resolvesVvoQuoteText() {
        val post = createPost(userName = "Alice", content = "Hello world")

        val result =
            VvoPlatformSpec.resolveInitialText(
                context =
                    ComposeInitialTextContext(
                        post = post,
                        quotes = persistentListOf(createPost()),
                        composeType = ComposeType.Quote,
                        currentUserHandle = UiHandle("current", "example.com"),
                        selectedAccountKey = MicroBlogKey("current", "example.com"),
                    ),
            )

        assertNotNull(result)
        assertEquals("//@Alice:Hello world", result.text)
        assertEquals(0, result.cursorPosition)
    }

    @Test
    fun ignoresComposeWithoutExistingQuote() {
        val post = createPost()

        val result =
            VvoPlatformSpec.resolveInitialText(
                context =
                    ComposeInitialTextContext(
                        post = post,
                        quotes = persistentListOf(),
                        composeType = ComposeType.Quote,
                        currentUserHandle = UiHandle("current", "example.com"),
                        selectedAccountKey = MicroBlogKey("current", "example.com"),
                    ),
            )

        assertNull(result)
    }

    private fun createPost(
        userName: String = "User",
        content: String = "Content",
    ): UiTimelineV2.Post {
        val userKey = MicroBlogKey("user", "example.com")
        val user =
            UiProfile(
                key = userKey,
                handle = UiHandle("user", "example.com"),
                avatar = "",
                nameInternal = userName.toUiPlainText(),
                platformId = VVO_PLATFORM_ID,
                clickEvent = ClickEvent.Noop,
                banner = null,
                description = null,
                matrices = UiProfile.Matrices(0, 0, 0),
                mark = persistentListOf(),
                bottomContent = null,
            )
        return UiTimelineV2.Post(
            platformId = VVO_PLATFORM_ID,
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = user,
            content = UiTranslatableText(content.toUiPlainText()),
            actions = persistentListOf<ActionMenu>(),
            poll = null,
            statusKey = MicroBlogKey("post", "example.com"),
            card = null,
            createdAt = Clock.System.now().toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = null,
            replyToHandle = null,
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Specific(userKey),
        )
    }
}
