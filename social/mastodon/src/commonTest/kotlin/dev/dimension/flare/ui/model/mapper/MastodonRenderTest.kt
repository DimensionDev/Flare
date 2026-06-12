package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Attachment
import dev.dimension.flare.data.network.mastodon.api.model.MastodonQuote
import dev.dimension.flare.data.network.mastodon.api.model.MediaType
import dev.dimension.flare.data.network.mastodon.api.model.Meta
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.serialization.json.jsonObject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MastodonRenderTest {
    private val accountKey = MicroBlogKey(id = "me", host = "mastodon.social")

    @BeforeTest
    fun setup() {
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
    fun mediaIsRecognized() {
        val status =
            createStatus(
                id = "media-1",
                account = createAccount("media-user"),
                content = "<p>has media</p>",
                mediaAttachments =
                    listOf(
                        Attachment(
                            id = "att-1",
                            type = MediaType.Image,
                            url = "https://mastodon.social/media/full.jpg",
                            previewURL = "https://mastodon.social/media/preview.jpg",
                            description = "image alt",
                            meta = Meta(width = 1200, height = 800),
                        ),
                    ),
            )

        val rendered = assertIs<UiTimelineV2.Post>(status.render(host = accountKey.host, accountKey = accountKey))
        assertEquals(1, rendered.images.size)
        val image = assertIs<UiMedia.Image>(rendered.images.first())
        assertEquals("https://mastodon.social/media/full.jpg", image.url)
        assertEquals("https://mastodon.social/media/preview.jpg", image.previewUrl)
        assertEquals("image alt", image.description)
        assertEquals(1200f, image.width)
        assertEquals(800f, image.height)
    }

    @Test
    fun quoteIsRecognized() {
        val quoted =
            createStatus(
                id = "quote-1",
                account = createAccount("quoted-user"),
                content = "<p>quoted content</p>",
            )
        val status =
            createStatus(
                id = "main-1",
                account = createAccount("main-user"),
                content = "<p>main content</p>",
                quote = quoted,
            )

        val rendered = assertIs<UiTimelineV2.Post>(status.render(host = accountKey.host, accountKey = accountKey))
        assertEquals(1, rendered.quote.size)
        assertEquals(
            "quoted content",
            rendered.quote
                .first()
                .content.innerText,
        )
    }

    @Test
    fun parentsAreRecognizedOnCurrentPath() {
        val status =
            createStatus(
                id = "reply-1",
                account = createAccount("reply-user"),
                content = "<p>reply content</p>",
                inReplyToID = "parent-id",
            )

        val rendered = assertIs<UiTimelineV2.Post>(status.render(host = accountKey.host, accountKey = accountKey))
        assertTrue(rendered.parents.isEmpty())
    }

    @Test
    fun statusListRenderFlattensReplyParents() {
        val root =
            createStatus(
                id = "root",
                account = createAccount("root-user"),
                content = "<p>root</p>",
            )
        val child =
            createStatus(
                id = "child",
                account = createAccount("child-user"),
                content = "<p>child</p>",
                inReplyToID = "root",
            )
        val leaf =
            createStatus(
                id = "leaf",
                account = createAccount("leaf-user"),
                content = "<p>leaf</p>",
                inReplyToID = "child",
            )

        val rendered = listOf(root, child, leaf).render(accountKey).filterIsInstance<UiTimelineV2.Post>()

        assertEquals(listOf("leaf"), rendered.map { it.statusKey.id })
        assertEquals(listOf("root", "child"), rendered.single().parents.map { it.statusKey.id })
        assertTrue(rendered.single().parents.all { it.parents.isEmpty() })
    }

    @Test
    fun statusContextRenderKeepsReturnedDescendantOrderAcrossChains() {
        val root =
            createStatus(
                id = "root",
                account = createAccount("root-user"),
                content = "<p>root</p>",
            )
        val current =
            createStatus(
                id = "current",
                account = createAccount("current-user"),
                content = "<p>current</p>",
                inReplyToID = "root",
            )
        val descendant0 =
            createStatus(
                id = "descendant-0",
                account = createAccount("descendant-user"),
                content = "<p>descendant 0</p>",
                inReplyToID = "current",
            )
        val descendant1 =
            createStatus(
                id = "descendant-1",
                account = createAccount("descendant-user"),
                content = "<p>descendant 1</p>",
                inReplyToID = "descendant-0",
            )
        val descendant2 =
            createStatus(
                id = "descendant-2",
                account = createAccount("descendant-user"),
                content = "<p>descendant 2</p>",
                inReplyToID = "current",
            )
        val descendant3 =
            createStatus(
                id = "descendant-3",
                account = createAccount("descendant-user"),
                content = "<p>descendant 3</p>",
                inReplyToID = "descendant-2",
            )
        val descendant4 =
            createStatus(
                id = "descendant-4",
                account = createAccount("descendant-user"),
                content = "<p>descendant 4</p>",
                inReplyToID = "descendant-3",
            )
        val descendant5 =
            createStatus(
                id = "descendant-5",
                account = createAccount("descendant-user"),
                content = "<p>descendant 5</p>",
                inReplyToID = "descendant-4",
            )

        val rendered =
            renderStatusContext(
                ancestors = listOf(root),
                current = current,
                descendants = listOf(descendant0, descendant1, descendant2, descendant3, descendant4, descendant5),
                accountKey = accountKey,
            ).filterIsInstance<UiTimelineV2.Post>()

        assertEquals(
            listOf("root", "current", "descendant-1", "descendant-5"),
            rendered.map { it.statusKey.id },
        )
        assertEquals(
            listOf("descendant-0"),
            rendered.first { it.statusKey.id == "descendant-1" }.parents.map { it.statusKey.id },
        )
        assertEquals(
            listOf("descendant-2", "descendant-3", "descendant-4"),
            rendered.first { it.statusKey.id == "descendant-5" }.parents.map { it.statusKey.id },
        )
        assertEquals(
            listOf("root", "current", "descendant-0", "descendant-1", "descendant-2", "descendant-3", "descendant-4", "descendant-5"),
            rendered.flatMap { post ->
                (post.parents + post).map { it.statusKey.id }
            },
        )
    }

    @Test
    fun statusContextRenderKeepsFirstDirectChildBeforeItsReplies() {
        val current =
            createStatus(
                id = "116526337257255071",
                account = createAccount("root-user"),
                content = "<p>root</p>",
            )
        val directChild =
            createStatus(
                id = "116526337629636438",
                account = createAccount("child-user"),
                content = "<p>direct child</p>",
                inReplyToID = "116526337257255071",
            )
        val replyToChild =
            createStatus(
                id = "116528933219506929",
                account = createAccount("reply-user"),
                content = "<p>reply to child</p>",
                inReplyToID = "116526337629636438",
            )

        val rendered =
            renderStatusContext(
                ancestors = emptyList(),
                current = current,
                descendants = listOf(directChild, replyToChild),
                accountKey = accountKey,
            ).filterIsInstance<UiTimelineV2.Post>()

        assertEquals(
            listOf(
                "116526337257255071",
                "116526337629636438",
                "116528933219506929",
            ),
            rendered.flatMap { post ->
                (post.parents + post).map { it.statusKey.id }
            },
        )
        assertEquals(
            listOf("116526337629636438"),
            rendered.first { it.statusKey.id == "116528933219506929" }.parents.map { it.statusKey.id },
        )
    }

    @Test
    fun repostShowsMessageAndUsesRebloggedPostContent() {
        val originalStatus =
            createStatus(
                id = "original-1",
                account = createAccount("original-user"),
                content = "<p>original content</p>",
            )
        val reblogWrapper =
            createStatus(
                id = "reblog-wrapper-1",
                account = createAccount("reblogger-user"),
                content = "<p>wrapper content</p>",
                reblog = originalStatus,
            )

        val rendered = assertIs<UiTimelineV2.Post>(reblogWrapper.render(host = accountKey.host, accountKey = accountKey))
        val message = assertNotNull(rendered.message)
        val type = assertIs<UiTimelineV2.Message.Type.Localized>(message.type)
        val repost = assertNotNull(rendered.internalRepost)

        assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, type.data)
        assertEquals("reblogger-user", message.user?.handle?.raw)
        assertEquals("wrapper content", rendered.content.innerText)
        assertEquals("reblog-wrapper-1", rendered.statusKey.id)
        assertEquals("original content", repost.content.innerText)
        assertEquals("original-1", repost.statusKey.id)
    }

    @Test
    fun reblogWithQuoteShowsMessagePostAndQuote() {
        val quotedStatus =
            createStatus(
                id = "quoted-2",
                account = createAccount("quoted-user"),
                content = "<p>quoted content</p>",
            )
        val originalStatus =
            createStatus(
                id = "original-2",
                account = createAccount("original-user"),
                content = "<p>original content</p>",
                quote = quotedStatus,
            )
        val reblogWrapper =
            createStatus(
                id = "reblog-wrapper-2",
                account = createAccount("reblogger-user"),
                content = "<p>wrapper</p>",
                reblog = originalStatus,
            )

        val rendered = assertIs<UiTimelineV2.Post>(reblogWrapper.render(host = accountKey.host, accountKey = accountKey))
        val message = assertNotNull(rendered.message)
        val type = assertIs<UiTimelineV2.Message.Type.Localized>(message.type)
        val repost = assertNotNull(rendered.internalRepost)

        assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, type.data)
        assertEquals("reblogger-user", message.user?.handle?.raw)
        assertEquals("wrapper", rendered.content.innerText)
        assertEquals("reblog-wrapper-2", rendered.statusKey.id)
        assertTrue(rendered.quote.isEmpty())
        assertEquals("original content", repost.content.innerText)
        assertEquals(1, repost.quote.size)
        assertEquals(
            "quoted content",
            repost.quote
                .first()
                .content.innerText,
        )
    }

    private fun createStatus(
        id: String,
        account: Account,
        content: String,
        inReplyToID: String? = null,
        reblog: Status? = null,
        quote: Status? = null,
        mediaAttachments: List<Attachment>? = null,
    ): Status =
        Status(
            id = id,
            account = account,
            content = content,
            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            inReplyToID = inReplyToID,
            reblog = reblog,
            mediaAttachments = mediaAttachments,
            json_quote =
                quote?.let {
                    JSON
                        .encodeToJsonElement(
                            MastodonQuote.serializer(),
                            MastodonQuote(
                                state = "accepted",
                                quoted_status = it,
                            ),
                        ).jsonObject
                },
        )

    private fun createAccount(name: String): Account =
        Account(
            id = name,
            username = name,
            acct = "$name@mastodon.social",
            displayName = name,
            avatar = "https://mastodon.social/$name.png",
            avatarStatic = "https://mastodon.social/$name.png",
        )
}
