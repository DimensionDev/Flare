package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.mapper.blueskyBookmark
import dev.dimension.flare.ui.model.mapper.blueskyLike
import dev.dimension.flare.ui.model.mapper.blueskyReblog
import dev.dimension.flare.ui.model.mapper.mastodonBookmark
import dev.dimension.flare.ui.model.mapper.mastodonLike
import dev.dimension.flare.ui.model.mapper.mastodonRepost
import dev.dimension.flare.ui.model.mapper.misskeyFavourite
import dev.dimension.flare.ui.model.mapper.misskeyReact
import dev.dimension.flare.ui.model.mapper.misskeyRenote
import dev.dimension.flare.ui.model.mapper.vvoFavorite
import dev.dimension.flare.ui.model.mapper.vvoLike
import dev.dimension.flare.ui.model.mapper.vvoLikeComment
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
            val count: Long,
            val accountKey: MicroBlogKey,
        ) : Mastodon,
            UpdatePostActionMenuEvent {
            override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.mastodonRepost(
                    reblogged = !reblogged,
                    reblogsCount = count + if (!reblogged) 1 else -1,
                    accountKey = accountKey,
                    statusKey = postKey,
                )
        }

        @Serializable
        data class Like(
            override val postKey: MicroBlogKey,
            val liked: Boolean,
            val accountKey: MicroBlogKey,
            val count: Long,
        ) : Mastodon,
            UpdatePostActionMenuEvent {
            override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.mastodonLike(
                    favourited = !liked,
                    favouritesCount = count + if (!liked) 1 else -1,
                    accountKey = accountKey,
                    statusKey = postKey,
                )
        }

        @Serializable
        data class Bookmark(
            override val postKey: MicroBlogKey,
            val bookmarked: Boolean,
            val accountKey: MicroBlogKey,
        ) : Mastodon,
            UpdatePostActionMenuEvent {
            override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.mastodonBookmark(
                    bookmarked = !bookmarked,
                    accountKey = accountKey,
                    statusKey = postKey,
                )
        }

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
            val count: Long = 0,
            val accountKey: MicroBlogKey? = null,
        ) : Misskey,
            UpdatePostActionMenuEvent {
            override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.misskeyReact(
                    postKey = postKey,
                    hasReacted = !hasReacted,
                    reaction = reaction,
                    count = (count + if (!hasReacted) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }

        @Serializable
        data class Renote(
            override val postKey: MicroBlogKey,
            val count: Long = 0,
            val accountKey: MicroBlogKey? = null,
        ) : Misskey,
            UpdatePostActionMenuEvent {
            override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.misskeyRenote(
                    postKey = postKey,
                    count = count + 1,
                    accountKey = accountKey,
                )
        }

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
            val accountKey: MicroBlogKey? = null,
        ) : Misskey,
            UpdatePostActionMenuEvent {
            override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.misskeyFavourite(
                    postKey = postKey,
                    favourited = !favourited,
                    accountKey = accountKey,
                )
        }

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
            val count: Long,
            val cid: String,
            val uri: String,
            val repostUri: String?,
            val accountKey: MicroBlogKey,
        ) : Bluesky,
            UpdatePostActionMenuEvent {
            override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.blueskyReblog(
                    accountKey = accountKey,
                    postKey = postKey,
                    cid = cid,
                    uri = uri,
                    count = count + if (repostUri == null) 1 else -1,
                    repostUri =
                        if (repostUri == null) {
                            ""
                        } else {
                            null
                        },
                )
        }

        @Serializable
        data class Like(
            override val postKey: MicroBlogKey,
            val cid: String,
            val uri: String,
            val likedUri: String?,
            val count: Long,
            val accountKey: MicroBlogKey,
        ) : Bluesky,
            UpdatePostActionMenuEvent {
            override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.blueskyLike(
                    accountKey = accountKey,
                    postKey = postKey,
                    cid = cid,
                    uri = uri,
                    count = count + if (likedUri == null) 1 else -1,
                    likedUri =
                        if (likedUri == null) {
                            ""
                        } else {
                            null
                        },
                )
        }

        @Serializable
        data class Bookmark(
            override val postKey: MicroBlogKey,
            val uri: String,
            val cid: String,
            val bookmarked: Boolean,
            val accountKey: MicroBlogKey,
            val count: Long,
        ) : Bluesky,
            UpdatePostActionMenuEvent {
            override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.blueskyBookmark(
                    accountKey = accountKey,
                    postKey = postKey,
                    cid = cid,
                    uri = uri,
                    bookmarked = !bookmarked,
                    count = count + if (!bookmarked) 1 else -1,
                )
        }
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
            val count: Long = 0,
            val accountKey: MicroBlogKey,
        ) : VVO,
            UpdatePostActionMenuEvent {
            override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.vvoLike(
                    statusKey = postKey,
                    liked = !liked,
                    count = (count + if (!liked) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }

        @Serializable
        data class LikeComment(
            override val postKey: MicroBlogKey,
            val liked: Boolean,
            val count: Long = 0,
            val accountKey: MicroBlogKey,
        ) : VVO,
            UpdatePostActionMenuEvent {
            override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.vvoLikeComment(
                    statusKey = postKey,
                    liked = !liked,
                    count = (count + if (!liked) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }

        @Serializable
        data class Favorite(
            override val postKey: MicroBlogKey,
            val favorited: Boolean,
            val accountKey: MicroBlogKey,
        ) : VVO,
            UpdatePostActionMenuEvent {
            override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.vvoFavorite(
                    statusKey = postKey,
                    favorited = !favorited,
                    accountKey = accountKey,
                )
        }
    }
}

internal interface UpdatePostActionMenuEvent : PostEvent {
    fun nextActionMenu(): ActionMenu.Item
}
