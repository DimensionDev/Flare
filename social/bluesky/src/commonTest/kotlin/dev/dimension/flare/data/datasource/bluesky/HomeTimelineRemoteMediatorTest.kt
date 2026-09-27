package dev.dimension.flare.data.datasource.bluesky

import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ViewerState
import app.bsky.feed.FeedViewPost
import app.bsky.feed.NotFoundPost
import app.bsky.feed.PostView
import app.bsky.feed.ReplyRef
import app.bsky.feed.ReplyRefParentUnion
import app.bsky.feed.ReplyRefRootUnion
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.mapper.bskyJson
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class HomeTimelineRemoteMediatorTest {
    private val accountKey = MicroBlogKey("did:plc:me", "bsky.social")

    @Test
    fun hidesRepliesToUnfollowedTargetsButKeepsOriginalPostsAndFollowedTargets() {
        val original = FeedViewPost(post("original", "alice"))
        val followed =
            reply(
                "followed",
                post("parent-followed", "bob", ViewerState(following = AtUri("at://did:plc:me/app.bsky.graph.follow/1"))),
            )
        val unfollowed = reply("unfollowed", post("parent-unfollowed", "carol", ViewerState()))

        assertFalse(original.isReplyToUnfollowedAccount(accountKey))
        assertFalse(followed.isReplyToUnfollowedAccount(accountKey))
        assertTrue(unfollowed.isReplyToUnfollowedAccount(accountKey))
    }

    @Test
    fun keepsSelfRepliesAndRepliesToViewer() {
        val selfReply = reply("self", post("self-parent", "alice", ViewerState()))
        val toViewer = reply("to-me", post("my-post", "me", ViewerState()))

        assertFalse(selfReply.isReplyToUnfollowedAccount(accountKey))
        assertFalse(toViewer.isReplyToUnfollowedAccount(accountKey))
    }

    @Test
    fun keepsRepliesWhenFollowingStateOrParentIsUnavailable() {
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

        assertFalse(unknown.isReplyToUnfollowedAccount(accountKey))
        assertFalse(unavailable.isReplyToUnfollowedAccount(accountKey))
    }

    @Test
    fun usesDirectParentRatherThanThreadRoot() {
        val followed = post("followed-parent", "bob", ViewerState(following = AtUri("at://did:plc:me/app.bsky.graph.follow/1")))
        val unfollowed = post("unfollowed-root", "carol", ViewerState())
        val item = reply("child", followed)
        val followedParent = item.copy(reply = item.reply!!.copy(root = ReplyRefRootUnion.PostView(unfollowed)))
        val unfollowedParent =
            item.copy(
                reply = ReplyRef(root = ReplyRefRootUnion.PostView(followed), parent = ReplyRefParentUnion.PostView(unfollowed)),
            )

        assertFalse(followedParent.isReplyToUnfollowedAccount(accountKey))
        assertTrue(unfollowedParent.isReplyToUnfollowedAccount(accountKey))
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
