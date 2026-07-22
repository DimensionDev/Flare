package dev.dimension.flare.data.datasource.bluesky

import app.bsky.actor.ProfileViewBasic
import app.bsky.embed.RecordView
import app.bsky.embed.RecordViewRecord
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.unspecced.GetPostThreadV2ThreadItem
import app.bsky.unspecced.GetPostThreadV2ThreadItemValueUnion
import app.bsky.unspecced.ThreadItemPost
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.bskyJson
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class StatusDetailRemoteMediatorTest {
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
    fun renderThread_doesNotRepeatVisiblePostsAsParents() {
        val anchor = createPostView("anchor", "anchor post")
        val firstReply = createPostView("reply-1", "first reply")
        val secondReply = createPostView("reply-2", "second reply")

        val rendered =
            listOf(
                createThreadItem(anchor, depth = 0),
                createThreadItem(firstReply, depth = 1),
                createThreadItem(secondReply, depth = 2),
            ).renderThread(accountKey)
                .timelinePostItems()

        val visibleKeys = rendered.map { it.statusKey }.toSet()

        rendered.forEach { post ->
            assertFalse(
                post.presentation.inlineParents.any { it.statusKey in visibleKeys },
                "Visible thread posts should not be repeated in ${post.statusKey.id} parents",
            )
        }
    }

    @Test
    fun renderThread_keepsAncestorsAboveAnchorVisible() {
        val parent = createPostView("parent", "parent post")
        val anchor = createPostView("anchor", "anchor post")
        val reply = createPostView("reply", "reply post")

        val rendered =
            listOf(
                createThreadItem(parent, depth = -1),
                createThreadItem(anchor, depth = 0),
                createThreadItem(reply, depth = 1),
            ).renderThread(accountKey)
                .timelinePostItems()

        assertEquals(
            listOf(parent.uri.atUri, anchor.uri.atUri, reply.uri.atUri),
            rendered.map { it.statusKey.id },
        )
        val visibleKeys = rendered.map { it.statusKey }.toSet()
        rendered.forEach { post ->
            assertFalse(
                post.presentation.inlineParents.any { it.statusKey in visibleKeys },
                "Visible thread posts should not be repeated in ${post.statusKey.id} parents",
            )
        }
    }

    @Test
    fun renderThread_chainsDescendantsBelowAnchor() {
        val anchor = createPostView("anchor", "anchor post")
        val firstReply = createPostView("reply-1", "first reply")
        val secondReply = createPostView("reply-2", "second reply")

        val rendered =
            listOf(
                createThreadItem(anchor, depth = 0),
                createThreadItem(firstReply, depth = 1),
                createThreadItem(secondReply, depth = 2),
            ).renderThread(accountKey)
                .timelinePostItems()

        assertEquals(
            listOf(anchor.uri.atUri, firstReply.uri.atUri, secondReply.uri.atUri),
            rendered.map { it.statusKey.id },
        )
        assertTrue(rendered.all { it.presentation.inlineParents.isEmpty() })
    }

    @Test
    fun renderThread_preservesRootQuotePresentationFromThreadV2() {
        val quotedRecord =
            RecordViewRecord(
                uri = AtUri("at://did:plc:quoted/app.bsky.feed.post/quoted"),
                cid = Cid("cid-quoted"),
                author = createProfile("quoted", "quoted.bsky.social"),
                value = jsonRecord("quoted post"),
                indexedAt = Instant.parse("2024-01-01T00:00:00Z"),
            )
        val root =
            createPostView(
                id = "root",
                text = "root with quote",
                embed =
                    PostViewEmbedUnion.RecordView(
                        RecordView(
                            record = RecordViewRecordUnion.ViewRecord(quotedRecord),
                        ),
                    ),
            )

        val rendered =
            listOf(createThreadItem(root, depth = 0))
                .renderThread(accountKey)
                .single()
        val post = assertIs<UiTimelineV2.TimelinePostItem>(rendered)

        assertEquals(1, post.presentation.quotes.size)
        assertEquals(
            "quoted post",
            post.presentation.quotes
                .first()
                .content.original.innerText,
        )
    }

    private fun List<UiTimelineV2>.timelinePostItems(): List<UiTimelineV2.TimelinePostItem> =
        mapNotNull {
            when (it) {
                is UiTimelineV2.TimelinePostItem -> it
                is UiTimelineV2.Post -> UiTimelineV2.TimelinePostItem(post = it)
                else -> null
            }
        }

    private fun createThreadItem(
        post: PostView,
        depth: Long,
    ): GetPostThreadV2ThreadItem =
        GetPostThreadV2ThreadItem(
            uri = post.uri,
            depth = depth,
            value =
                GetPostThreadV2ThreadItemValueUnion.Post(
                    ThreadItemPost(
                        post = post,
                        moreParents = false,
                        moreReplies = 0,
                        opThread = false,
                        hiddenByThreadgate = false,
                        mutedByViewer = false,
                    ),
                ),
        )

    private fun createPostView(
        id: String,
        text: String,
        embed: PostViewEmbedUnion? = null,
    ): PostView =
        PostView(
            uri = AtUri("at://did:plc:$id/app.bsky.feed.post/$id"),
            cid = Cid("cid-$id"),
            author =
                ProfileViewBasic(
                    did = Did("did:plc:$id"),
                    handle = Handle("$id.bsky.social"),
                    displayName = id,
                ),
            record =
                bskyJson.encodeAsJsonContent(
                    buildJsonObject {
                        put("text", JsonPrimitive(text))
                    },
                ),
            embed = embed,
            indexedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )

    private fun createProfile(
        did: String,
        handle: String,
    ): ProfileViewBasic =
        ProfileViewBasic(
            did = Did("did:plc:$did"),
            handle = Handle(handle),
            displayName = did,
        )

    private fun jsonRecord(text: String) =
        bskyJson.encodeAsJsonContent(
            buildJsonObject {
                put("text", JsonPrimitive(text))
            },
        )
}
