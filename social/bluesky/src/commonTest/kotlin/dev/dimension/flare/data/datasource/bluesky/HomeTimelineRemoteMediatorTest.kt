package dev.dimension.flare.data.datasource.bluesky

import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ViewerState
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetTimelineResponse
import app.bsky.feed.NotFoundPost
import app.bsky.feed.PostView
import app.bsky.feed.ReplyRef
import app.bsky.feed.ReplyRefParentUnion
import app.bsky.feed.ReplyRefRootUnion
import dev.dimension.flare.di.BlueskyTestKoinModule
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.mapper.bskyJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.modules
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class HomeTimelineRemoteMediatorTest {
    private val accountKey = MicroBlogKey("did:plc:me", "bsky.social")

    @BeforeTest
    fun setup() {
        startKoin { modules(BlueskyTestKoinModule::class) }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun hidesRepliesToUnfollowedTargetsButKeepsOriginalPostsAndFollowedTargets() =
        runTest {
            val original = FeedViewPost(post("original", "alice"))
            val followed =
                reply(
                    "followed",
                    post("parent-followed", "bob", ViewerState(following = AtUri("at://did:plc:me/app.bsky.graph.follow/1"))),
                )
            val unfollowed = reply("unfollowed", post("parent-unfollowed", "carol", ViewerState()))

            val result = GetTimelineResponse(feed = listOf(original, unfollowed, followed)).renderHomeTimeline(accountKey)

            assertEquals(listOf(original.post.uri.atUri, followed.post.uri.atUri), result.data.map { it.statusKey.id })
        }

    @Test
    fun keepsSelfRepliesAndRepliesToViewer() =
        runTest {
            val selfReply = reply("self", post("self-parent", "alice", ViewerState()))
            val toViewer = reply("to-me", post("my-post", "me", ViewerState()))

            val result = GetTimelineResponse(feed = listOf(selfReply, toViewer)).renderHomeTimeline(accountKey)

            assertEquals(2, result.data.size)
        }

    @Test
    fun keepsRepliesWhenFollowingStateOrParentIsUnavailable() =
        runTest {
            val unknown = reply("unknown", post("unknown-parent", "bob"))
            val unavailable =
                unknown.copy(
                    reply =
                        unknown.reply!!.copy(
                            parent =
                                ReplyRefParentUnion.NotFoundPost(
                                    NotFoundPost(AtUri("at://did:plc:bob/app.bsky.feed.post/deleted"), true),
                                ),
                        ),
                )

            assertEquals(2, GetTimelineResponse(feed = listOf(unknown, unavailable)).renderHomeTimeline(accountKey).data.size)
        }

    @Test
    fun usesDirectParentRatherThanThreadRoot() =
        runTest {
            val followed = post("followed-parent", "bob", ViewerState(following = AtUri("at://did:plc:me/app.bsky.graph.follow/1")))
            val unfollowed = post("unfollowed-root", "carol", ViewerState())
            val item = reply("child", followed)
            val result =
                GetTimelineResponse(
                    feed = listOf(item.copy(reply = item.reply!!.copy(root = ReplyRefRootUnion.PostView(unfollowed)))),
                ).renderHomeTimeline(accountKey)

            assertEquals(1, result.data.size)
        }

    @Test
    fun preservesCursorEvenWhenEntirePageIsFiltered() =
        runTest {
            val feed = listOf(reply("hidden", post("parent", "bob", ViewerState())))
            val response = GetTimelineResponse(feed = feed, cursor = "next-page")
            val result = response.renderHomeTimeline(accountKey)

            assertTrue(result.data.isEmpty())
            assertEquals("next-page", result.nextKey)
            assertNull(response.copy(cursor = null).renderHomeTimeline(accountKey).nextKey)
        }

    @Test
    fun sharedRenderingForOtherViewsDoesNotFilterReplies() =
        runTest {
            val feed = listOf(reply("visible-in-other-views", post("parent", "bob", ViewerState())))

            assertEquals(1, feed.renderWithDownloadUrls(accountKey).size)
            assertTrue(GetTimelineResponse(feed = feed).renderHomeTimeline(accountKey).data.isEmpty())
        }

    private fun reply(
        id: String,
        parent: PostView,
    ) = FeedViewPost(
        post = post(id, "alice"),
        reply = ReplyRef(root = ReplyRefRootUnion.PostView(parent), parent = ReplyRefParentUnion.PostView(parent)),
    )

    private fun post(
        id: String,
        author: String,
        viewer: ViewerState? = null,
    ) = PostView(
        uri = AtUri("at://did:plc:$author/app.bsky.feed.post/$id"),
        cid = Cid("cid-$id"),
        author = ProfileViewBasic(did = Did("did:plc:$author"), handle = Handle("$author.bsky.social"), viewer = viewer),
        record = bskyJson.encodeAsJsonContent(buildJsonObject { put("text", JsonPrimitive(id)) }),
        indexedAt = Instant.parse("2024-01-01T00:00:00Z"),
    )
}
