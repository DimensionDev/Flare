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

        fun likeWithResult(
            statusKey: MicroBlogKey,
            shouldLike: Boolean,
        ): StatusActionResult

        fun reblogWithResult(
            statusKey: MicroBlogKey,
            shouldReblog: Boolean,
        ): StatusActionResult

        fun bookmarkWithResult(
            statusKey: MicroBlogKey,
            shouldBookmark: Boolean,
        ): StatusActionResult

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

        fun renoteWithResult(statusKey: MicroBlogKey): StatusActionResult

        fun favouriteWithResult(
            statusKey: MicroBlogKey,
            favourited: Boolean,
        ): StatusActionResult
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

        fun likeWithResult(
            statusKey: MicroBlogKey,
            cid: String,
            uri: String,
            likedUri: String?,
        ): StatusActionResult

        fun reblogWithResult(
            statusKey: MicroBlogKey,
            cid: String,
            uri: String,
            repostUri: String?,
        ): StatusActionResult
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

        fun likeWithResult(
            statusKey: MicroBlogKey,
            shouldLike: Boolean,
        ): StatusActionResult

        fun bookmark(
            statusKey: MicroBlogKey,
            bookmarked: Boolean,
        )

        fun bookmarkWithResult(
            statusKey: MicroBlogKey,
            shouldBookmark: Boolean,
        ): StatusActionResult

        fun retweetWithResult(
            statusKey: MicroBlogKey,
            shouldRetweet: Boolean,
        ): StatusActionResult
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

        fun likeWithResult(
            statusKey: MicroBlogKey,
            shouldLike: Boolean,
        ): StatusActionResult

        fun likeCommentWithResult(
            statusKey: MicroBlogKey,
            shouldLike: Boolean,
        ): StatusActionResult
    }
}
