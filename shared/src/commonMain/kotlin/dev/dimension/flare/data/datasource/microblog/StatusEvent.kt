package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.model.MicroBlogKey

sealed interface StatusEvent {
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
    }

    interface Misskey : StatusEvent {
        fun react(
            statusKey: MicroBlogKey,
            hasReacted: Boolean,
            reaction: String,
        )

        fun renote(statusKey: MicroBlogKey)
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
