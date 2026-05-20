package dev.dimension.flare.data.datasource.mastodon

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.ui.model.PostEvent
import dev.dimension.flare.ui.model.mapper.mastodonBookmark
import dev.dimension.flare.ui.model.mapper.mastodonLike
import dev.dimension.flare.ui.model.mapper.mastodonRepost

internal fun PostEvent.mastodonNextActionMenu(): ActionMenu.Item? =
    when (this) {
        is PostEvent.Mastodon.Reblog -> {
            ActionMenu.mastodonRepost(
                reblogged = !reblogged,
                reblogsCount = count + if (!reblogged) 1 else -1,
                accountKey = accountKey,
                statusKey = postKey,
            )
        }

        is PostEvent.Mastodon.Like -> {
            ActionMenu.mastodonLike(
                favourited = !liked,
                favouritesCount = count + if (!liked) 1 else -1,
                accountKey = accountKey,
                statusKey = postKey,
            )
        }

        is PostEvent.Mastodon.Bookmark -> {
            ActionMenu.mastodonBookmark(
                bookmarked = !bookmarked,
                accountKey = accountKey,
                statusKey = postKey,
            )
        }

        else -> {
            null
        }
    }
