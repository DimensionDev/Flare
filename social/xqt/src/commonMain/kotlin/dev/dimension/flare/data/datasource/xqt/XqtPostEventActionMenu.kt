package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.ui.model.PostEvent
import dev.dimension.flare.ui.model.mapper.xqtBookmark
import dev.dimension.flare.ui.model.mapper.xqtLike
import dev.dimension.flare.ui.model.mapper.xqtRetweet

internal fun PostEvent.xqtNextActionMenu(): ActionMenu.Item? =
    when (this) {
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

        else -> null
    }
