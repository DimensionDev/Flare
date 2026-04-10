package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

@Serializable
public sealed interface PostEvent {
    public val postKey: MicroBlogKey

    @Serializable
    public sealed interface PollEvent : PostEvent {
        public val accountKey: MicroBlogKey
        public val options: SerializableImmutableList<Int>

        public fun copyWithOptions(options: List<Int>): PollEvent
    }

    @Serializable
    public sealed interface Mastodon : PostEvent {
        @Serializable
        public data class Reblog(
            override val postKey: MicroBlogKey,
            val reblogged: Boolean,
            val count: Long,
            val accountKey: MicroBlogKey,
        ) : Mastodon

        @Serializable
        public data class Like(
            override val postKey: MicroBlogKey,
            val liked: Boolean,
            val accountKey: MicroBlogKey,
            val count: Long,
        ) : Mastodon

        @Serializable
        public data class Bookmark(
            override val postKey: MicroBlogKey,
            val bookmarked: Boolean,
            val accountKey: MicroBlogKey,
        ) : Mastodon

        @Serializable
        public data class Vote(
            val id: String,
            override val accountKey: MicroBlogKey,
            override val postKey: MicroBlogKey,
            override val options: SerializableImmutableList<Int>,
        ) : Mastodon,
            PollEvent {
            override fun copyWithOptions(options: List<Int>): PollEvent = copy(options = options.toImmutableList())
        }

        @Serializable
        public data class AcceptFollowRequest(
            override val postKey: MicroBlogKey,
            val userKey: MicroBlogKey,
        ) : Mastodon

        @Serializable
        public data class RejectFollowRequest(
            override val postKey: MicroBlogKey,
            val userKey: MicroBlogKey,
        ) : Mastodon
    }

    @Serializable
    public sealed interface Pleroma : PostEvent {
        @Serializable
        public data class React(
            override val postKey: MicroBlogKey,
            val hasReacted: Boolean,
            val reaction: String,
        ) : Pleroma
    }

    @Serializable
    public sealed interface Misskey : PostEvent {
        @Serializable
        public data class React(
            override val postKey: MicroBlogKey,
            val hasReacted: Boolean,
            val reaction: String,
            val count: Long = 0,
            val accountKey: MicroBlogKey? = null,
        ) : Misskey

        @Serializable
        public data class Renote(
            override val postKey: MicroBlogKey,
            val count: Long = 0,
            val accountKey: MicroBlogKey? = null,
        ) : Misskey

        @Serializable
        public data class Vote(
            override val accountKey: MicroBlogKey,
            override val postKey: MicroBlogKey,
            override val options: SerializableImmutableList<Int>,
        ) : Misskey,
            PollEvent {
            override fun copyWithOptions(options: List<Int>): PollEvent = copy(options = options.toImmutableList())
        }

        @Serializable
        public data class Favourite(
            override val postKey: MicroBlogKey,
            val favourited: Boolean,
            val accountKey: MicroBlogKey? = null,
        ) : Misskey

        @Serializable
        public data class AcceptFollowRequest(
            override val postKey: MicroBlogKey,
            val userKey: MicroBlogKey,
            val notificationStatusKey: MicroBlogKey,
        ) : Misskey

        @Serializable
        public data class RejectFollowRequest(
            override val postKey: MicroBlogKey,
            val userKey: MicroBlogKey,
            val notificationStatusKey: MicroBlogKey,
        ) : Misskey
    }

    @Serializable
    public sealed interface Bluesky : PostEvent {
        @Serializable
        public data class Reblog(
            override val postKey: MicroBlogKey,
            val count: Long,
            val cid: String,
            val uri: String,
            val repostUri: String?,
            val accountKey: MicroBlogKey,
        ) : Bluesky

        @Serializable
        public data class Like(
            override val postKey: MicroBlogKey,
            val cid: String,
            val uri: String,
            val likedUri: String?,
            val count: Long,
            val accountKey: MicroBlogKey,
        ) : Bluesky

        @Serializable
        public data class Bookmark(
            override val postKey: MicroBlogKey,
            val uri: String,
            val cid: String,
            val bookmarked: Boolean,
            val accountKey: MicroBlogKey,
            val count: Long,
        ) : Bluesky
    }

    @Serializable
    public sealed interface XQT : PostEvent {
        @Serializable
        public data class Retweet(
            override val postKey: MicroBlogKey,
            val retweeted: Boolean,
            val count: Long = 0,
            val accountKey: MicroBlogKey,
        ) : XQT

        @Serializable
        public data class Like(
            override val postKey: MicroBlogKey,
            val liked: Boolean,
            val count: Long = 0,
            val accountKey: MicroBlogKey,
        ) : XQT

        @Serializable
        public data class Bookmark(
            override val postKey: MicroBlogKey,
            val bookmarked: Boolean,
            val count: Long = 0,
            val accountKey: MicroBlogKey,
        ) : XQT
    }

    @Serializable
    public sealed interface VVO : PostEvent {
        @Serializable
        public data class Like(
            override val postKey: MicroBlogKey,
            val liked: Boolean,
            val count: Long = 0,
            val accountKey: MicroBlogKey,
        ) : VVO

        @Serializable
        public data class LikeComment(
            override val postKey: MicroBlogKey,
            val liked: Boolean,
            val count: Long = 0,
            val accountKey: MicroBlogKey,
        ) : VVO

        @Serializable
        public data class Favorite(
            override val postKey: MicroBlogKey,
            val favorited: Boolean,
            val accountKey: MicroBlogKey,
        ) : VVO
    }

    @Serializable
    public sealed interface Nostr : PostEvent {
        @Serializable
        public data class Repost(
            override val postKey: MicroBlogKey,
            val repostEventId: String?,
            val count: Long = 0,
            val accountKey: MicroBlogKey,
        ) : Nostr

        @Serializable
        public data class Like(
            override val postKey: MicroBlogKey,
            val reactionEventId: String?,
            val count: Long = 0,
            val accountKey: MicroBlogKey,
        ) : Nostr

        @Serializable
        public data class Report(
            override val postKey: MicroBlogKey,
            val accountKey: MicroBlogKey,
        ) : Nostr
    }
}
