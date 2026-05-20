package dev.dimension.flare.data.datasource.bluesky

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.ui.model.PostEvent
import dev.dimension.flare.ui.model.mapper.blueskyBookmark
import dev.dimension.flare.ui.model.mapper.blueskyLike
import dev.dimension.flare.ui.model.mapper.blueskyReblog

internal fun PostEvent.blueskyNextActionMenu(): ActionMenu.Item? =
    when (this) {
        is PostEvent.Bluesky.Reblog -> {
            ActionMenu.blueskyReblog(
                accountKey = accountKey,
                postKey = postKey,
                cid = cid,
                uri = uri,
                count = count + if (repostUri == null) 1 else -1,
                repostUri = if (repostUri == null) "" else null,
            )
        }

        is PostEvent.Bluesky.Like -> {
            ActionMenu.blueskyLike(
                accountKey = accountKey,
                postKey = postKey,
                cid = cid,
                uri = uri,
                count = count + if (likedUri == null) 1 else -1,
                likedUri = if (likedUri == null) "" else null,
            )
        }

        is PostEvent.Bluesky.Bookmark -> {
            ActionMenu.blueskyBookmark(
                accountKey = accountKey,
                postKey = postKey,
                cid = cid,
                uri = uri,
                bookmarked = !bookmarked,
                count = count + if (!bookmarked) 1 else -1,
            )
        }

        else -> {
            null
        }
    }
