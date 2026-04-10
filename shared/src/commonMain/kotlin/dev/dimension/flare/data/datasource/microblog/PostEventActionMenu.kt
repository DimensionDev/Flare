package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.ui.model.mapper.blueskyBookmark
import dev.dimension.flare.ui.model.mapper.blueskyLike
import dev.dimension.flare.ui.model.mapper.blueskyReblog
import dev.dimension.flare.ui.model.mapper.mastodonBookmark
import dev.dimension.flare.ui.model.mapper.mastodonLike
import dev.dimension.flare.ui.model.mapper.mastodonRepost
import dev.dimension.flare.ui.model.mapper.misskeyFavourite
import dev.dimension.flare.ui.model.mapper.misskeyReact
import dev.dimension.flare.ui.model.mapper.misskeyRenote
import dev.dimension.flare.ui.model.mapper.nostrLike
import dev.dimension.flare.ui.model.mapper.nostrRepost
import dev.dimension.flare.ui.model.mapper.vvoFavorite
import dev.dimension.flare.ui.model.mapper.vvoLike
import dev.dimension.flare.ui.model.mapper.vvoLikeComment
import dev.dimension.flare.ui.model.mapper.xqtBookmark
import dev.dimension.flare.ui.model.mapper.xqtLike
import dev.dimension.flare.ui.model.mapper.xqtRetweet

internal fun PostEvent.nextActionMenu(): ActionMenu.Item? =
    when (this) {
        is PostEvent.Mastodon.Reblog ->
            ActionMenu.mastodonRepost(
                reblogged = !reblogged,
                reblogsCount = count + if (!reblogged) 1 else -1,
                accountKey = accountKey,
                statusKey = postKey,
            )
        is PostEvent.Mastodon.Like ->
            ActionMenu.mastodonLike(
                favourited = !liked,
                favouritesCount = count + if (!liked) 1 else -1,
                accountKey = accountKey,
                statusKey = postKey,
            )
        is PostEvent.Mastodon.Bookmark ->
            ActionMenu.mastodonBookmark(
                bookmarked = !bookmarked,
                accountKey = accountKey,
                statusKey = postKey,
            )
        is PostEvent.Misskey.React ->
            ActionMenu.misskeyReact(
                postKey = postKey,
                hasReacted = !hasReacted,
                reaction = reaction,
                count = (count + if (!hasReacted) 1 else -1).coerceAtLeast(0),
                accountKey = accountKey,
            )
        is PostEvent.Misskey.Renote ->
            ActionMenu.misskeyRenote(
                postKey = postKey,
                count = count + 1,
                accountKey = accountKey,
            )
        is PostEvent.Misskey.Favourite ->
            ActionMenu.misskeyFavourite(
                postKey = postKey,
                favourited = !favourited,
                accountKey = accountKey,
            )
        is PostEvent.Bluesky.Reblog ->
            ActionMenu.blueskyReblog(
                accountKey = accountKey,
                postKey = postKey,
                cid = cid,
                uri = uri,
                count = count + if (repostUri == null) 1 else -1,
                repostUri = if (repostUri == null) "" else null,
            )
        is PostEvent.Bluesky.Like ->
            ActionMenu.blueskyLike(
                accountKey = accountKey,
                postKey = postKey,
                cid = cid,
                uri = uri,
                count = count + if (likedUri == null) 1 else -1,
                likedUri = if (likedUri == null) "" else null,
            )
        is PostEvent.Bluesky.Bookmark ->
            ActionMenu.blueskyBookmark(
                accountKey = accountKey,
                postKey = postKey,
                cid = cid,
                uri = uri,
                bookmarked = !bookmarked,
                count = count + if (!bookmarked) 1 else -1,
            )
        is PostEvent.XQT.Retweet ->
            ActionMenu.xqtRetweet(
                statusKey = postKey,
                retweeted = !retweeted,
                count = (count + if (!retweeted) 1 else -1).coerceAtLeast(0),
                accountKey = accountKey,
            )
        is PostEvent.XQT.Like ->
            ActionMenu.xqtLike(
                statusKey = postKey,
                liked = !liked,
                count = (count + if (!liked) 1 else -1).coerceAtLeast(0),
                accountKey = accountKey,
            )
        is PostEvent.XQT.Bookmark ->
            ActionMenu.xqtBookmark(
                statusKey = postKey,
                bookmarked = !bookmarked,
                count = (count + if (!bookmarked) 1 else -1).coerceAtLeast(0),
                accountKey = accountKey,
            )
        is PostEvent.VVO.Like ->
            ActionMenu.vvoLike(
                statusKey = postKey,
                liked = !liked,
                count = (count + if (!liked) 1 else -1).coerceAtLeast(0),
                accountKey = accountKey,
            )
        is PostEvent.VVO.LikeComment ->
            ActionMenu.vvoLikeComment(
                statusKey = postKey,
                liked = !liked,
                count = (count + if (!liked) 1 else -1).coerceAtLeast(0),
                accountKey = accountKey,
            )
        is PostEvent.VVO.Favorite ->
            ActionMenu.vvoFavorite(
                statusKey = postKey,
                favorited = !favorited,
                accountKey = accountKey,
            )
        is PostEvent.Nostr.Repost ->
            ActionMenu.nostrRepost(
                statusKey = postKey,
                repostEventId = if (repostEventId == null) "" else null,
                count = (count + if (repostEventId == null) 1 else -1).coerceAtLeast(0),
                accountKey = accountKey,
            )
        is PostEvent.Nostr.Like ->
            ActionMenu.nostrLike(
                statusKey = postKey,
                reactionEventId = if (reactionEventId == null) "" else null,
                count = (count + if (reactionEventId == null) 1 else -1).coerceAtLeast(0),
                accountKey = accountKey,
            )
        else -> null
    }
