package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.model.MicroBlogKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

internal sealed interface StatusEvent {
    val accountKey: MicroBlogKey

    interface Mastodon : StatusEvent {
        fun reblog(
            statusKey: MicroBlogKey,
            reblogged: Boolean,
        )

        fun like(
            statusKey: MicroBlogKey,
            liked: Boolean,
        )

        fun bookmark(
            statusKey: MicroBlogKey,
            bookmarked: Boolean,
        )

        fun vote(
            statusKey: MicroBlogKey,
            id: String,
            options: List<Int>,
        )

        fun acceptFollowRequest(
            userKey: MicroBlogKey,
            notificationStatusKey: MicroBlogKey,
        )

        fun rejectFollowRequest(
            userKey: MicroBlogKey,
            notificationStatusKey: MicroBlogKey,
        )
    }

    interface Pleroma : Mastodon {
        fun react(
            statusKey: MicroBlogKey,
            hasReacted: Boolean,
            reaction: String,
        )
    }

    interface Misskey : StatusEvent {
        fun react(
            statusKey: MicroBlogKey,
            hasReacted: Boolean,
            reaction: String,
        )

        fun renote(statusKey: MicroBlogKey)

        fun vote(
            statusKey: MicroBlogKey,
            options: List<Int>,
        )

        fun favourite(
            statusKey: MicroBlogKey,
            favourited: Boolean,
        )

        fun favouriteState(statusKey: MicroBlogKey): Flow<Boolean>

        fun acceptFollowRequest(
            userKey: MicroBlogKey,
            notificationStatusKey: MicroBlogKey,
        )

        fun rejectFollowRequest(
            userKey: MicroBlogKey,
            notificationStatusKey: MicroBlogKey,
        )
    }

    interface Bluesky : StatusEvent {
        fun reblog(
            statusKey: MicroBlogKey,
            cid: String,
            uri: String,
            repostUri: String?,
        )

        fun like(
            statusKey: MicroBlogKey,
            cid: String,
            uri: String,
            likedUri: String?,
        )

        fun bookmark(
            statusKey: MicroBlogKey,
            uri: String,
            cid: String,
        )

        fun unbookmark(
            statusKey: MicroBlogKey,
            uri: String,
        )
    }

    interface XQT : StatusEvent {
        fun retweet(
            statusKey: MicroBlogKey,
            retweeted: Boolean,
        )

        fun like(
            statusKey: MicroBlogKey,
            liked: Boolean,
        )

        fun bookmark(
            statusKey: MicroBlogKey,
            bookmarked: Boolean,
        )
    }

    interface VVO : StatusEvent {
        fun like(
            statusKey: MicroBlogKey,
            liked: Boolean,
        )

        fun likeComment(
            statusKey: MicroBlogKey,
            liked: Boolean,
        )

        fun favorite(
            statusKey: MicroBlogKey,
            favorited: Boolean,
        )
    }
}

@Serializable
internal sealed interface PostEvent {
    @Serializable
    sealed interface PollEvent : PostEvent {
        val options: ImmutableList<Int>
    }

    @Serializable
    sealed interface Mastodon : PostEvent {
        @Serializable
        data class Reblog(
            val statusKey: MicroBlogKey,
            val reblogged: Boolean,
        ) : Mastodon

        @Serializable
        data class Like(
            val statusKey: MicroBlogKey,
            val liked: Boolean,
        ) : Mastodon

        @Serializable
        data class Bookmark(
            val statusKey: MicroBlogKey,
            val bookmarked: Boolean,
        ) : Mastodon

        @Serializable
        data class Vote(
            val statusKey: MicroBlogKey,
            override val options: ImmutableList<Int>,
        ) : Mastodon,
            PollEvent

        @Serializable
        data class AcceptFollowRequest(
            val userKey: MicroBlogKey,
            val notificationStatusKey: MicroBlogKey,
        ) : Mastodon

        @Serializable
        data class RejectFollowRequest(
            val userKey: MicroBlogKey,
            val notificationStatusKey: MicroBlogKey,
        ) : Mastodon
    }

    @Serializable
    sealed interface Pleroma : PostEvent {
        @Serializable
        data class React(
            val statusKey: MicroBlogKey,
            val hasReacted: Boolean,
            val reaction: String,
        ) : Pleroma
    }

    @Serializable
    sealed interface Misskey : PostEvent {
        @Serializable
        data class React(
            val statusKey: MicroBlogKey,
            val hasReacted: Boolean,
            val reaction: String,
        ) : Misskey

        @Serializable
        data class Renote(
            val statusKey: MicroBlogKey,
        ) : Misskey

        @Serializable
        data class Vote(
            val statusKey: MicroBlogKey,
            override val options: ImmutableList<Int>,
        ) : Misskey,
            PollEvent

        @Serializable
        data class Favourite(
            val statusKey: MicroBlogKey,
            val favourited: Boolean,
        ) : Misskey

        @Serializable
        data class AcceptFollowRequest(
            val userKey: MicroBlogKey,
            val notificationStatusKey: MicroBlogKey,
        ) : Misskey

        @Serializable
        data class RejectFollowRequest(
            val userKey: MicroBlogKey,
            val notificationStatusKey: MicroBlogKey,
        ) : Misskey
    }

    @Serializable
    sealed interface Bluesky : PostEvent {
        @Serializable
        data class Reblog(
            val statusKey: MicroBlogKey,
            val reblogged: Boolean,
        ) : Bluesky

        @Serializable
        data class Like(
            val statusKey: MicroBlogKey,
            val liked: Boolean,
        ) : Bluesky

        @Serializable
        data class Bookmark(
            val statusKey: MicroBlogKey,
            val bookmarked: Boolean,
        ) : Bluesky

        @Serializable
        data class Unbookmark(
            val statusKey: MicroBlogKey,
        ) : Bluesky
    }

    @Serializable
    sealed interface XQT : PostEvent {
        @Serializable
        data class Retweet(
            val statusKey: MicroBlogKey,
            val retweeted: Boolean,
        ) : XQT

        @Serializable
        data class Like(
            val statusKey: MicroBlogKey,
            val liked: Boolean,
        ) : XQT

        @Serializable
        data class Bookmark(
            val statusKey: MicroBlogKey,
            val bookmarked: Boolean,
        ) : XQT
    }

    @Serializable
    sealed interface VVO : PostEvent {
        @Serializable
        data class Like(
            val statusKey: MicroBlogKey,
            val liked: Boolean,
        ) : VVO

        @Serializable
        data class LikeComment(
            val statusKey: MicroBlogKey,
            val liked: Boolean,
        ) : VVO

        @Serializable
        data class Favorite(
            val statusKey: MicroBlogKey,
            val favorited: Boolean,
        ) : VVO
    }
}
