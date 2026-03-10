package dev.dimension.flare.ui.presenter.compose

import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.persistentListOf
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

class InitialTextResolverTest {
    @BeforeTest
    fun setUp() {
        stopKoin()
        startKoin {
            modules(
                module {
                    single<PlatformFormatter> { TestFormatter() }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `resolves quote text for vvo quote`() {
        val post =
            createPost(
                platformType = PlatformType.VVo,
                user = createUser(name = "Alice", handle = UiHandle("alice", "weibo.cn")),
                content = text("<span>Hello world</span>"),
                quote = persistentListOf(createPost()),
            )

        val result =
            InitialTextResolver.resolve(
                post = post,
                composeStatus = ComposeStatus.Quote(post.statusKey),
                currentUserHandle = UiHandle("current", "example.com"),
                selectedAccountKey = null,
            )

        assertNotNull(result)
        assertEquals("//@Alice:Hello world", result.text)
        assertEquals(0, result.cursorPosition)
    }

    @Test
    fun `resolves reply mentions for mastodon and filters duplicates current user and selected account`() {
        val selectedAccountKey = MicroBlogKey("author", "example.com")
        val authorHandle = UiHandle("author", "example.com")
        val currentUserHandle = UiHandle("me", "example.com")
        val mentionedHandle = "@friend@example.com"
        val post =
            createPost(
                platformType = PlatformType.Mastodon,
                statusKey = MicroBlogKey("post-1", "example.com"),
                user = createUser(handle = authorHandle, key = selectedAccountKey),
                content =
                    text(
                        """
                        <span>
                          <a href="flare://ProfileWithNameAndHost/friend/example.com?accountKey=author@example.com">$mentionedHandle</a>
                          <a href="flare://ProfileWithNameAndHost/me/example.com?accountKey=author@example.com">@me@example.com</a>
                          <a href="flare://ProfileWithNameAndHost/friend/example.com?accountKey=author@example.com">$mentionedHandle</a>
                          <a href="https://example.com/profile">@external@example.com</a>
                          <a href="flare://ProfileWithNameAndHost/author/example.com?accountKey=author@example.com">${authorHandle.canonical}</a>
                        </span>
                        """.trimIndent(),
                    ),
            )

        val result =
            InitialTextResolver.resolve(
                post = post,
                composeStatus = ComposeStatus.Reply(post.statusKey),
                currentUserHandle = currentUserHandle,
                selectedAccountKey = selectedAccountKey,
            )

        assertNotNull(result)
        assertEquals("$mentionedHandle ", result.text)
        assertEquals(result.text.length, result.cursorPosition)
    }

    @Test
    fun `returns null for non reply mastodon compose state`() {
        val post = createPost(platformType = PlatformType.Mastodon)

        val result =
            InitialTextResolver.resolve(
                post = post,
                composeStatus = ComposeStatus.Quote(post.statusKey),
                currentUserHandle = UiHandle("current", "example.com"),
                selectedAccountKey = null,
            )

        assertNull(result)
    }

    private fun createPost(
        platformType: PlatformType = PlatformType.Mastodon,
        statusKey: MicroBlogKey = MicroBlogKey("post", "example.com"),
        user: UiProfile = createUser(),
        content: UiRichText = text("<span>content</span>"),
        quote: kotlinx.collections.immutable.ImmutableList<UiTimelineV2.Post> = persistentListOf(),
    ): UiTimelineV2.Post =
        UiTimelineV2.Post(
            message = null,
            platformType = platformType,
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = user,
            quote = quote,
            content = content,
            actions = persistentListOf<ActionMenu>(),
            poll = null,
            statusKey = statusKey,
            card = null,
            createdAt = Clock.System.now().toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = null,
            replyToHandle = null,
            parents = persistentListOf(),
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Specific(user.key),
        )

    private fun createUser(
        key: MicroBlogKey = MicroBlogKey("user", "example.com"),
        handle: UiHandle = UiHandle("user", "example.com"),
        name: String = "User",
    ): UiProfile =
        UiProfile(
            key = key,
            handle = handle,
            avatar = "",
            nameInternal = text("<span>$name</span>"),
            platformType = PlatformType.Mastodon,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices =
                UiProfile.Matrices(
                    fansCount = 0,
                    followsCount = 0,
                    statusesCount = 0,
                ),
            mark = persistentListOf(),
            bottomContent = null,
        )

    private fun text(html: String) = Ksoup.parse(html).body().toUi()
}
