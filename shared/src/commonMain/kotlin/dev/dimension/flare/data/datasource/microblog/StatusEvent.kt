package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

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
    }
}
