package dev.dimension.flare.model

import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.common.encodeProtobuf
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.collections.immutable.persistentListOf
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

class ReplyParticipantInitialTextTest {
    @BeforeTest
    fun setUp() {
        stopKoin()
        startKoin {
            modules(
                module {
                    testSingle<PlatformFormatter> { TestFormatter() }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `resolves current Misskey profile routes and filters duplicates current user and selected account`() {
        val selectedAccountKey = MicroBlogKey("author", "example.com")
        val authorHandle = UiHandle("author", "example.com")
        val currentUserHandle = UiHandle("me", "example.com")
        val mentionedHandle = "@friend@example.com"

        fun profileLink(name: String) =
            DeeplinkRoute.Profile
                .UserNameWithHost(AccountType.Specific(selectedAccountKey), name, "example.com")
                .toUri()
        val post =
            createPost(
                platformId = "Misskey",
                statusKey = MicroBlogKey("post-1", "example.com"),
                user = createUser(handle = authorHandle, key = selectedAccountKey),
                content =
                    text(
                        """
                        <span>
                          <a href="${profileLink("friend")}">@friend</a>
                          <a href="${profileLink("me")}">@me</a>
                          <a href="${profileLink("friend")}">Friend</a>
                          <a href="https://example.com/profile">@external@example.com</a>
                          <a href="${profileLink("author")}">${authorHandle.canonical}</a>
                        </span>
                        """.trimIndent(),
                    ),
            )

        val result =
            ComposeInitialTextContext(
                post = post,
                quotes = persistentListOf(),
                composeType = ComposeType.Reply,
                currentUserHandle = currentUserHandle,
                selectedAccountKey = selectedAccountKey,
            ).resolveReplyParticipantInitialText()

        assertNotNull(result)
        assertEquals("$mentionedHandle ", result.text)
        assertEquals(result.text.length, result.cursorPosition)
    }

    @Test
    fun `stored participants retain canonical identities after protobuf cache round trip`() {
        val post =
            createPost(user = createUser(handle = UiHandle("author", "example.com"))).copy(
                replyMentions =
                    UiTimelineV2.Post.ReplyMentions(
                        persistentListOf(
                            UiHandle("bob", "remote.example", displayOverride = "Bob"),
                            UiHandle("bob", "example.com"),
                            UiHandle("Me", "example.com"),
                            UiHandle("author", "example.com"),
                            UiHandle("BOB", "REMOTE.EXAMPLE"),
                            UiHandle("", "example.com"),
                            UiHandle("missing-host", ""),
                        ),
                    ),
            )
        val restored = assertIs<UiTimelineV2.Post>(post.encodeProtobuf<UiTimelineV2>().decodeProtobuf<UiTimelineV2>())
        val result =
            ComposeInitialTextContext(
                post = restored,
                quotes = persistentListOf(),
                composeType = ComposeType.Reply,
                currentUserHandle = UiHandle("me", "example.com"),
                selectedAccountKey = MicroBlogKey("me", "example.com"),
            ).resolveReplyParticipantInitialText()

        assertEquals(post.replyMentions, restored.replyMentions)
        assertEquals("@author@example.com @bob@remote.example @bob@example.com ", result?.text)
    }

    @Test
    fun `cache without participant metadata remains distinguishable from an empty mention list`() {
        val post = createPost()
        val restored = assertIs<UiTimelineV2.Post>(post.encodeProtobuf<UiTimelineV2>().decodeProtobuf<UiTimelineV2>())
        val withoutMentions = post.copy(replyMentions = UiTimelineV2.Post.ReplyMentions(persistentListOf()))
        val restoredWithoutMentions =
            assertIs<UiTimelineV2.Post>(withoutMentions.encodeProtobuf<UiTimelineV2>().decodeProtobuf<UiTimelineV2>())

        assertNull(restored.replyMentions)
        assertEquals(persistentListOf(), restoredWithoutMentions.replyMentions?.handles)
    }

    @Test
    fun `legacy protobuf search text retains its field number`() {
        val post = createPost()
        val searchText = "legacy cached text".encodeToByteArray()
        // Field 24 was searchText before reply participant metadata was added.
        val legacy = post.encodeProtobuf() + byteArrayOf(0xc2.toByte(), 0x01, searchText.size.toByte()) + searchText
        val restored = legacy.decodeProtobuf<UiTimelineV2.Post>()

        assertEquals("legacy cached text", restored.searchText)
        assertEquals(post.statusKey, restored.statusKey)
        assertNull(restored.replyMentions)
    }

    @Test
    fun `excludes current author by canonical handle when cached ids differ`() {
        val post = createPost(user = createUser(key = MicroBlogKey("old-id", "example.com"), handle = UiHandle("me", "example.com")))
        val result =
            ComposeInitialTextContext(
                post = post,
                quotes = persistentListOf(),
                composeType = ComposeType.Reply,
                currentUserHandle = UiHandle("me", "example.com"),
                selectedAccountKey = MicroBlogKey("current-id", "example.com"),
            ).resolveReplyParticipantInitialText()

        assertEquals("", result?.text)
    }

    @Test
    fun `returns null for non reply mastodon compose state`() {
        val post = createPost(platformId = "Mastodon")

        val result =
            ComposeInitialTextContext(
                post = post,
                quotes = persistentListOf(),
                composeType = ComposeType.Quote,
                currentUserHandle = UiHandle("current", "example.com"),
                selectedAccountKey = MicroBlogKey("current", "example.com"),
            ).resolveReplyParticipantInitialText()

        assertNull(result)
    }

    private fun createPost(
        platformId: String = "Mastodon",
        statusKey: MicroBlogKey = MicroBlogKey("post", "example.com"),
        user: UiProfile = createUser(),
        content: UiRichText = text("<span>content</span>"),
    ): UiTimelineV2.Post =
        UiTimelineV2.Post(
            platformId = platformId,
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = user,
            content = UiTranslatableText(content),
            actions = persistentListOf<ActionMenu>(),
            poll = null,
            statusKey = statusKey,
            card = null,
            createdAt = Clock.System.now().toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = null,
            replyToHandle = null,
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
            platformId = "Mastodon",
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
