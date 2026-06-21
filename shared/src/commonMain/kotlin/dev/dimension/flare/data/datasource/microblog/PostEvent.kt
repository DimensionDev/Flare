package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.mapper.blueskyBookmark
import dev.dimension.flare.ui.model.mapper.blueskyLike
import dev.dimension.flare.ui.model.mapper.blueskyReblog
import dev.dimension.flare.ui.model.mapper.fanboxLike
import dev.dimension.flare.ui.model.mapper.mastodonBookmark
import dev.dimension.flare.ui.model.mapper.mastodonLike
import dev.dimension.flare.ui.model.mapper.mastodonRepost
import dev.dimension.flare.ui.model.mapper.misskeyFavourite
import dev.dimension.flare.ui.model.mapper.misskeyReact
import dev.dimension.flare.ui.model.mapper.misskeyRenote
import dev.dimension.flare.ui.model.mapper.nostrLike
import dev.dimension.flare.ui.model.mapper.nostrRepost
import dev.dimension.flare.ui.model.mapper.pixivBookmark
import dev.dimension.flare.ui.model.mapper.vvoFavorite
import dev.dimension.flare.ui.model.mapper.vvoLike
import dev.dimension.flare.ui.model.mapper.vvoLikeComment
import dev.dimension.flare.ui.model.mapper.xqtBookmark
import dev.dimension.flare.ui.model.mapper.xqtLike
import dev.dimension.flare.ui.model.mapper.xqtRetweet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@Serializable
@HiddenFromObjC
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
            public override val postKey: MicroBlogKey,
            public val reblogged: Boolean,
            public val count: Long,
            public val accountKey: MicroBlogKey,
        ) : Mastodon,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.mastodonRepost(
                    reblogged = !reblogged,
                    reblogsCount = count + if (!reblogged) 1 else -1,
                    accountKey = accountKey,
                    statusKey = postKey,
                )
        }

        @Serializable
        public data class Like(
            public override val postKey: MicroBlogKey,
            public val liked: Boolean,
            public val accountKey: MicroBlogKey,
            public val count: Long,
        ) : Mastodon,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.mastodonLike(
                    favourited = !liked,
                    favouritesCount = count + if (!liked) 1 else -1,
                    accountKey = accountKey,
                    statusKey = postKey,
                )
        }

        @Serializable
        public data class Bookmark(
            public override val postKey: MicroBlogKey,
            public val bookmarked: Boolean,
            public val accountKey: MicroBlogKey,
        ) : Mastodon,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.mastodonBookmark(
                    bookmarked = !bookmarked,
                    accountKey = accountKey,
                    statusKey = postKey,
                )
        }

        @Serializable
        public data class Vote(
            public val id: String,
            public override val accountKey: MicroBlogKey,
            public override val postKey: MicroBlogKey,
            public override val options: SerializableImmutableList<Int>,
        ) : Mastodon,
            PollEvent {
            public override fun copyWithOptions(options: List<Int>): PollEvent = copy(options = options.toImmutableList())
        }

        @Serializable
        public data class AcceptFollowRequest(
            public override val postKey: MicroBlogKey,
            public val userKey: MicroBlogKey,
        ) : Mastodon

        @Serializable
        public data class RejectFollowRequest(
            public override val postKey: MicroBlogKey,
            public val userKey: MicroBlogKey,
        ) : Mastodon
    }

    @Serializable
    public sealed interface Pleroma : PostEvent {
        @Serializable
        public data class React(
            public override val postKey: MicroBlogKey,
            public val hasReacted: Boolean,
            public val reaction: String,
        ) : Pleroma
    }

    @Serializable
    public sealed interface Misskey : PostEvent {
        @Serializable
        public data class React(
            public override val postKey: MicroBlogKey,
            public val hasReacted: Boolean,
            public val reaction: String,
            public val count: Long = 0,
            public val accountKey: MicroBlogKey? = null,
        ) : Misskey,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.misskeyReact(
                    postKey = postKey,
                    hasReacted = !hasReacted,
                    reaction = reaction,
                    count = (count + if (!hasReacted) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }

        @Serializable
        public data class Renote(
            public override val postKey: MicroBlogKey,
            public val count: Long = 0,
            public val accountKey: MicroBlogKey? = null,
        ) : Misskey,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.misskeyRenote(
                    postKey = postKey,
                    count = count + 1,
                    accountKey = accountKey,
                )
        }

        @Serializable
        public data class Vote(
            public override val accountKey: MicroBlogKey,
            public override val postKey: MicroBlogKey,
            public override val options: SerializableImmutableList<Int>,
        ) : Misskey,
            PollEvent {
            public override fun copyWithOptions(options: List<Int>): PollEvent = copy(options = options.toImmutableList())
        }

        @Serializable
        public data class Favourite(
            public override val postKey: MicroBlogKey,
            public val favourited: Boolean,
            public val accountKey: MicroBlogKey? = null,
        ) : Misskey,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.misskeyFavourite(
                    postKey = postKey,
                    favourited = !favourited,
                    accountKey = accountKey,
                )
        }

        @Serializable
        public data class AcceptFollowRequest(
            public override val postKey: MicroBlogKey,
            public val userKey: MicroBlogKey,
            public val notificationStatusKey: MicroBlogKey,
        ) : Misskey

        @Serializable
        public data class RejectFollowRequest(
            public override val postKey: MicroBlogKey,
            public val userKey: MicroBlogKey,
            public val notificationStatusKey: MicroBlogKey,
        ) : Misskey
    }

    @Serializable
    public sealed interface Bluesky : PostEvent {
        @Serializable
        public data class Reblog(
            public override val postKey: MicroBlogKey,
            public val count: Long,
            public val cid: String,
            public val uri: String,
            public val repostUri: String?,
            public val accountKey: MicroBlogKey,
        ) : Bluesky,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
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
        public data class Like(
            public override val postKey: MicroBlogKey,
            public val cid: String,
            public val uri: String,
            public val likedUri: String?,
            public val count: Long,
            public val accountKey: MicroBlogKey,
        ) : Bluesky,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
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
        public data class Bookmark(
            public override val postKey: MicroBlogKey,
            public val uri: String,
            public val cid: String,
            public val bookmarked: Boolean,
            public val accountKey: MicroBlogKey,
            public val count: Long,
        ) : Bluesky,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
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
    public sealed interface XQT : PostEvent {
        @Serializable
        public data class Retweet(
            public override val postKey: MicroBlogKey,
            public val retweeted: Boolean,
            public val count: Long = 0,
            public val accountKey: MicroBlogKey,
        ) : XQT,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.xqtRetweet(
                    statusKey = postKey,
                    retweeted = !retweeted,
                    count = (count + if (!retweeted) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }

        @Serializable
        public data class Like(
            public override val postKey: MicroBlogKey,
            public val liked: Boolean,
            public val count: Long = 0,
            public val accountKey: MicroBlogKey,
        ) : XQT,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.xqtLike(
                    statusKey = postKey,
                    liked = !liked,
                    count = (count + if (!liked) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }

        @Serializable
        public data class Bookmark(
            public override val postKey: MicroBlogKey,
            public val bookmarked: Boolean,
            public val count: Long = 0,
            public val accountKey: MicroBlogKey,
        ) : XQT,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.xqtBookmark(
                    statusKey = postKey,
                    bookmarked = !bookmarked,
                    count = (count + if (!bookmarked) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }
    }

    @Serializable
    public sealed interface VVO : PostEvent {
        @Serializable
        public data class Like(
            public override val postKey: MicroBlogKey,
            public val liked: Boolean,
            public val count: Long = 0,
            public val accountKey: MicroBlogKey,
        ) : VVO,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.vvoLike(
                    statusKey = postKey,
                    liked = !liked,
                    count = (count + if (!liked) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }

        @Serializable
        public data class LikeComment(
            public override val postKey: MicroBlogKey,
            public val liked: Boolean,
            public val count: Long = 0,
            public val accountKey: MicroBlogKey,
        ) : VVO,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.vvoLikeComment(
                    statusKey = postKey,
                    liked = !liked,
                    count = (count + if (!liked) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }

        @Serializable
        public data class Favorite(
            public override val postKey: MicroBlogKey,
            public val favorited: Boolean,
            public val accountKey: MicroBlogKey,
        ) : VVO,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.vvoFavorite(
                    statusKey = postKey,
                    favorited = !favorited,
                    accountKey = accountKey,
                )
        }
    }

    @Serializable
    public sealed interface Nostr : PostEvent {
        @Serializable
        public data class Repost(
            public override val postKey: MicroBlogKey,
            public val repostEventId: String?,
            public val count: Long = 0,
            public val accountKey: MicroBlogKey,
        ) : Nostr,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.nostrRepost(
                    statusKey = postKey,
                    repostEventId =
                        if (repostEventId == null) {
                            ""
                        } else {
                            null
                        },
                    count = (count + if (repostEventId == null) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }

        @Serializable
        public data class Like(
            public override val postKey: MicroBlogKey,
            public val reactionEventId: String?,
            public val count: Long = 0,
            public val accountKey: MicroBlogKey,
        ) : Nostr,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.nostrLike(
                    statusKey = postKey,
                    reactionEventId =
                        if (reactionEventId == null) {
                            ""
                        } else {
                            null
                        },
                    count = (count + if (reactionEventId == null) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }

        @Serializable
        public data class Report(
            public override val postKey: MicroBlogKey,
            public val accountKey: MicroBlogKey,
        ) : Nostr
    }

    @Serializable
    public sealed interface Pixiv : PostEvent {
        @Serializable
        public data class Bookmark(
            public override val postKey: MicroBlogKey,
            public val bookmarked: Boolean,
            public val count: Long = 0,
            public val accountKey: MicroBlogKey,
        ) : Pixiv,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.pixivBookmark(
                    statusKey = postKey,
                    bookmarked = !bookmarked,
                    count = (count + if (!bookmarked) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }
    }

    @Serializable
    public sealed interface Fanbox : PostEvent {
        @Serializable
        public data class Like(
            public override val postKey: MicroBlogKey,
            public val liked: Boolean,
            public val count: Long = 0,
            public val accountKey: MicroBlogKey,
        ) : Fanbox,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.fanboxLike(
                    statusKey = postKey,
                    liked = true,
                    count = (count + if (!liked) 1 else 0).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }
    }
}

@HiddenFromObjC
public interface UpdatePostActionMenuEvent : PostEvent {
    public fun nextActionMenu(): ActionMenu.Item
}
