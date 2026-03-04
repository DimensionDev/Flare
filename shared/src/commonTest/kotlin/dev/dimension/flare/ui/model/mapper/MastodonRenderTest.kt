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

        assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, type.data)
        assertEquals("reblogger-user", message.user?.handle?.raw)
        assertEquals("original content", rendered.content.innerText)
        assertEquals("reblog-wrapper-1", rendered.statusKey.id)
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

        assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, type.data)
        assertEquals("reblogger-user", message.user?.handle?.raw)
        assertEquals("original content", rendered.content.innerText)
        assertEquals("reblog-wrapper-2", rendered.statusKey.id)
        assertEquals(1, rendered.quote.size)
        assertEquals(
            "quoted content",
            rendered.quote
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
