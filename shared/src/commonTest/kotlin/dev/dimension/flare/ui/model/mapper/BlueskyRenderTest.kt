package dev.dimension.flare.ui.model.mapper

import app.bsky.actor.ProfileViewBasic
import app.bsky.embed.AspectRatio
import app.bsky.embed.ImagesView
import app.bsky.embed.ImagesViewImage
import app.bsky.embed.RecordView
import app.bsky.embed.RecordViewRecord
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.feed.ReasonRepost
import app.bsky.feed.ReplyRef
import app.bsky.feed.ReplyRefParentUnion
import app.bsky.feed.ReplyRefRootUnion
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Uri
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Instant

class BlueskyRenderTest {
    private val accountKey = MicroBlogKey(id = "me", host = "bsky.social")

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
    fun postRender_mapsMediaImages() {
        val post =
            createPostView(
                uri = "at://did:plc:author/app.bsky.feed.post/1",
                author = createProfile("author", "author.bsky.social"),
                text = "with media",
                embed =
                    PostViewEmbedUnion.ImagesView(
                        ImagesView(
                            images =
                                listOf(
                                    ImagesViewImage(
                                        thumb = Uri("https://cdn.example.com/thumb.jpg"),
                                        fullsize = Uri("https://cdn.example.com/full.jpg"),
                                        alt = "image alt",
                                        aspectRatio = AspectRatio(width = 4, height = 3),
                                    ),
                                ),
                        ),
                    ),
            )

        val rendered = post.render(accountKey)
        assertEquals(1, rendered.images.size)
        val media = assertIs<UiMedia.Image>(rendered.images.first())
        assertEquals("https://cdn.example.com/full.jpg", media.url)
        assertEquals("https://cdn.example.com/thumb.jpg", media.previewUrl)
        assertEquals("image alt", media.description)
        assertEquals(4f, media.width)
        assertEquals(3f, media.height)
    }

    @Test
    fun postRender_mapsQuotePost() {
        val quotedRecord =
            RecordViewRecord(
                uri = AtUri("at://did:plc:quoted/app.bsky.feed.post/2"),
                cid = Cid("cid-quoted"),
                author = createProfile("quoted", "quoted.bsky.social"),
                value = jsonRecord("quoted content"),
                indexedAt = Instant.parse("2024-01-01T00:00:00Z"),
            )
        val mainPost =
            createPostView(
                uri = "at://did:plc:author/app.bsky.feed.post/1",
                author = createProfile("author", "author.bsky.social"),
                text = "main content",
                embed =
                    PostViewEmbedUnion.RecordView(
                        RecordView(
                            record = RecordViewRecordUnion.ViewRecord(quotedRecord),
                        ),
                    ),
            )

        val rendered = mainPost.render(accountKey)
        assertEquals(1, rendered.quote.size)
        assertEquals(
            "quoted content",
            rendered.quote
                .first()
                .content.innerText,
        )
        assertEquals(
            "at://did:plc:quoted/app.bsky.feed.post/2",
            rendered.quote
                .first()
                .statusKey.id,
        )
    }

    @Test
    fun feedRender_mapsParentReply() {
        val parent =
            createPostView(
                uri = "at://did:plc:parent/app.bsky.feed.post/10",
                author = createProfile("parent", "parent.bsky.social"),
                text = "parent content",
            )
        val child =
            createPostView(
                uri = "at://did:plc:child/app.bsky.feed.post/11",
                author = createProfile("child", "child.bsky.social"),
                text = "child content",
            )
        val feed =
            FeedViewPost(
                post = child,
                reply =
                    ReplyRef(
                        root = ReplyRefRootUnion.PostView(parent),
                        parent = ReplyRefParentUnion.PostView(parent),
                    ),
            )

        val rendered = listOf(feed).render(accountKey)
        val post = assertIs<UiTimelineV2.Post>(rendered.single())
        assertEquals(1, post.parents.size)
        assertEquals(
            "at://did:plc:parent/app.bsky.feed.post/10",
            post.parents
                .first()
                .statusKey.id,
        )
        assertEquals(
            "parent content",
            post.parents
                .first()
                .content.innerText,
        )
    }

    @Test
    fun feedRender_repostShowsMessageAndUsesRepostedPostContent() {
        val originalAuthor = createProfile("original", "original.bsky.social")
        val reposter = createProfile("reposter", "reposter.bsky.social")
        val originalPost =
            createPostView(
                uri = "at://did:plc:original/app.bsky.feed.post/99",
                author = originalAuthor,
                text = "original post content",
            )
        val feed =
            FeedViewPost(
                post = originalPost,
                reason =
                    FeedViewPostReasonUnion.ReasonRepost(
                        ReasonRepost(
                            by = reposter,
                            indexedAt = Instant.parse("2024-01-01T02:00:00Z"),
                        ),
                    ),
            )

        val rendered = assertIs<UiTimelineV2.Post>(listOf(feed).render(accountKey).single())
        val message = assertNotNull(rendered.message)

        val type = assertIs<UiTimelineV2.Message.Type.Localized>(message.type)
        assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, type.data)
        assertEquals("did:plc:reposter", message.user?.key?.id)
        assertEquals("did:plc:original", rendered.user?.key?.id)
        assertEquals("original post content", rendered.content.innerText)
    }

    @Test
    fun feedRender_repostWithQuotedPostShowsMessagePostAndQuote() {
        val quotedRecord =
            RecordViewRecord(
                uri = AtUri("at://did:plc:quoted/app.bsky.feed.post/200"),
                cid = Cid("cid-quoted"),
                author = createProfile("quoted", "quoted.bsky.social"),
                value = jsonRecord("quoted payload"),
                indexedAt = Instant.parse("2024-01-01T00:00:00Z"),
            )
        val repostedPost =
            createPostView(
                uri = "at://did:plc:original/app.bsky.feed.post/201",
                author = createProfile("original", "original.bsky.social"),
                text = "original payload",
                embed =
                    PostViewEmbedUnion.RecordView(
                        RecordView(
                            record = RecordViewRecordUnion.ViewRecord(quotedRecord),
                        ),
                    ),
            )
        val feed =
            FeedViewPost(
                post = repostedPost,
                reason =
                    FeedViewPostReasonUnion.ReasonRepost(
                        ReasonRepost(
                            by = createProfile("reposter", "reposter.bsky.social"),
                            indexedAt = Instant.parse("2024-01-01T02:00:00Z"),
                        ),
                    ),
            )

        val rendered = assertIs<UiTimelineV2.Post>(listOf(feed).render(accountKey).single())
        val message = assertNotNull(rendered.message)
        val type = assertIs<UiTimelineV2.Message.Type.Localized>(message.type)

        assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, type.data)
        assertEquals("original payload", rendered.content.innerText)
        assertEquals(1, rendered.quote.size)
        assertEquals(
            "quoted payload",
            rendered.quote
                .first()
                .content.innerText,
        )
        assertEquals("did:plc:reposter", message.user?.key?.id)
    }

    private fun createProfile(
        did: String,
        handle: String,
    ): ProfileViewBasic =
        ProfileViewBasic(
            did = Did("did:plc:$did"),
            handle = Handle(handle),
            displayName = did,
        )

    private fun createPostView(
        uri: String,
        author: ProfileViewBasic,
        text: String,
        embed: PostViewEmbedUnion? = null,
    ): PostView =
        PostView(
            uri = AtUri(uri),
            cid = Cid("cid-${uri.substringAfterLast('/')}"),
            author = author,
            record = jsonRecord(text),
            embed = embed,
            indexedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )

    private fun jsonRecord(text: String) =
        bskyJson.encodeAsJsonContent(
            buildJsonObject {
                put("text", JsonPrimitive(text))
            },
        )
}
