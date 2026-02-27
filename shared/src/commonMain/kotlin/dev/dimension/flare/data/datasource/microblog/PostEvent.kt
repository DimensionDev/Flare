package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface PostEvent {
    val postKey: MicroBlogKey

    @Serializable
    sealed interface PollEvent : PostEvent {
        val accountKey: MicroBlogKey
        val options: SerializableImmutableList<Int>

        fun copyWithOptions(options: List<Int>): PollEvent
    }

    @Serializable
    sealed interface Mastodon : PostEvent {
        @Serializable
        data class Reblog(
            override val postKey: MicroBlogKey,
            val reblogged: Boolean,
        ) : Mastodon

        @Serializable
        data class Like(
            override val postKey: MicroBlogKey,
            val liked: Boolean,
        ) : Mastodon

        @Serializable
        data class Bookmark(
            override val postKey: MicroBlogKey,
            val bookmarked: Boolean,
        ) : Mastodon

        @Serializable
        data class Vote(
            val id: String,
            override val accountKey: MicroBlogKey,
            override val postKey: MicroBlogKey,
            override val options: SerializableImmutableList<Int>,
        ) : Mastodon,
            PollEvent {
            override fun copyWithOptions(options: List<Int>): PollEvent = copy(options = options.toImmutableList())
        }

        @Serializable
        data class AcceptFollowRequest(
            override val postKey: MicroBlogKey,
            val userKey: MicroBlogKey,
        ) : Mastodon

        @Serializable
        data class RejectFollowRequest(
            override val postKey: MicroBlogKey,
            val userKey: MicroBlogKey,
        ) : Mastodon
    }

    @Serializable
    sealed interface Pleroma : PostEvent {
        @Serializable
        data class React(
            override val postKey: MicroBlogKey,
            val hasReacted: Boolean,
            val reaction: String,
        ) : Pleroma
    }

    @Serializable
    sealed interface Misskey : PostEvent {
        @Serializable
        data class React(
            override val postKey: MicroBlogKey,
            val hasReacted: Boolean,
            val reaction: String,
        ) : Misskey

        @Serializable
        data class Renote(
            override val postKey: MicroBlogKey,
        ) : Misskey

        @Serializable
        data class Vote(
            override val accountKey: MicroBlogKey,
            override val postKey: MicroBlogKey,
            override val options: SerializableImmutableList<Int>,
        ) : Misskey,
            PollEvent {
            override fun copyWithOptions(options: List<Int>): PollEvent = copy(options = options.toImmutableList())
        }

        @Serializable
        data class Favourite(
            override val postKey: MicroBlogKey,
            val favourited: Boolean,
        ) : Misskey

        @Serializable
        data class AcceptFollowRequest(
            override val postKey: MicroBlogKey,
            val userKey: MicroBlogKey,
            val notificationStatusKey: MicroBlogKey,
        ) : Misskey

        @Serializable
        data class RejectFollowRequest(
            override val postKey: MicroBlogKey,
            val userKey: MicroBlogKey,
            val notificationStatusKey: MicroBlogKey,
        ) : Misskey
    }

    @Serializable
    sealed interface Bluesky : PostEvent {
        @Serializable
        data class Reblog(
            override val postKey: MicroBlogKey,
            val reblogged: Boolean,
        ) : Bluesky

        @Serializable
        data class Like(
            override val postKey: MicroBlogKey,
            val liked: Boolean,
        ) : Bluesky

        @Serializable
        data class Bookmark(
            override val postKey: MicroBlogKey,
            val bookmarked: Boolean,
        ) : Bluesky

        @Serializable
        data class Unbookmark(
            override val postKey: MicroBlogKey,
        ) : Bluesky
    }

    @Serializable
    sealed interface XQT : PostEvent {
        @Serializable
        data class Retweet(
            override val postKey: MicroBlogKey,
            val retweeted: Boolean,
        ) : XQT

        @Serializable
        data class Like(
            override val postKey: MicroBlogKey,
            val liked: Boolean,
        ) : XQT

        @Serializable
        data class Bookmark(
            override val postKey: MicroBlogKey,
            val bookmarked: Boolean,
        ) : XQT
    }

    @Serializable
    sealed interface VVO : PostEvent {
        @Serializable
        data class Like(
            override val postKey: MicroBlogKey,
            val liked: Boolean,
        ) : VVO

        @Serializable
        data class LikeComment(
            override val postKey: MicroBlogKey,
            val liked: Boolean,
        ) : VVO

        @Serializable
        data class Favorite(
            override val postKey: MicroBlogKey,
            val favorited: Boolean,
        ) : VVO
    }
}
